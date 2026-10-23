package tv.jacred.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** Discovers the video players installed on the device and hands streams to them. */
final class Players {
    static final class Player {
        final String packageName;
        final String label;

        Player(String packageName, String label) {
            this.packageName = packageName;
            this.label = label;
        }
    }

    private Players() {
    }

    /** Every app on this device that can open an http(s) video stream. */
    static List<Player> installed(Context context) {
        PackageManager manager = context.getPackageManager();
        List<ResolveInfo> matches = manager.queryIntentActivities(probeIntent(), 0);

        List<Player> players = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ResolveInfo info : matches) {
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(context.getPackageName()) || seen.contains(packageName)) {
                continue;
            }
            seen.add(packageName);
            players.add(new Player(packageName, info.loadLabel(manager).toString()));
        }
        return players;
    }

    private static Intent probeIntent() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse("http://127.0.0.1/video.mkv"), "video/*");
        return intent;
    }

    /**
     * Starts playback of {@code url}, trying each mime type in turn. TorrServer serves M3U
     * playlists as audio/x-mpegurl, which not every video player registers for, so the caller
     * passes the alternatives it will accept. When {@code packageName} is empty the system
     * chooser is shown, which is also the fallback if the preferred player has been
     * uninstalled.
     */
    static void play(Activity activity, String url, String title, String packageName,
                     String... mimeTypes) {
        for (String mimeType : mimeTypes) {
            Intent intent = intentFor(url, title, mimeType);
            if (!packageName.isEmpty()) {
                intent.setPackage(packageName);
                // A content:// playlist is unreadable to the player without this grant.
                activity.grantUriPermission(packageName, Uri.parse(url),
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            if (intent.resolveActivity(activity.getPackageManager()) != null) {
                activity.startActivity(intent);
                return;
            }
        }
        // Nothing claimed it outright, so let the system ask.
        activity.startActivity(Intent.createChooser(
                intentFor(url, title, mimeTypes[0]), title));
    }

    private static Intent intentFor(String url, String title, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.parse(url), mimeType);
        intent.putExtra("title", title);
        intent.putExtra("secure_uri", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return intent;
    }
}
