package tv.jacred.client;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Releases of this app, read from the GitHub API and installed through PackageInstaller.
 *
 * <p>A device replaces an installed app only when the new APK carries the same signature, so
 * every release is signed with one long-lived key. An update refused with a signature
 * complaint means the installed copy came from somewhere else — a local build, say — and has
 * to be removed before a release can take over.
 */
final class Updates {
    /** Where the release workflow publishes. Not configurable: it is this app's own origin. */
    private static final String LATEST_RELEASE =
            "https://api.github.com/repos/kalpak44/jacred-tv-client/releases/latest";

    /**
     * How stale a cached answer may get before the home screen asks again. Unauthenticated
     * GitHub allows 60 calls an hour per address, and onResume runs on every return from a
     * title, so an unthrottled check would spend that allowance on one evening of browsing.
     */
    private static final long CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L;

    private static final String INSTALL_STATUS = "tv.jacred.client.INSTALL_STATUS";

    /** Declared in the manifest at signature level; see the receiver below. */
    private static final String INSTALL_STATUS_PERMISSION =
            "tv.jacred.client.permission.INSTALL_STATUS";

    /** The APK is written here, replaced each time; a TV box has little room to spare. */
    private static final String DOWNLOAD_NAME = "update.apk";

    private static final int TIMEOUT_MS = 30000;

    /** How much of a release's own version number differs from the installed one. */
    enum Step {
        NONE, PATCH, MINOR, MAJOR
    }

    interface Progress {
        /** Called on a worker thread, at most once per percent. */
        void onPercent(int percent);
    }

    interface Status {
        void onMessage(String message);
    }

    /** One published release, with the APK the release workflow attached to it. */
    static final class Release {
        final String version;
        final String tag;
        final String apkUrl;
        final long apkBytes;

        Release(String version, String tag, String apkUrl, long apkBytes) {
            this.version = version;
            this.tag = tag;
            this.apkUrl = apkUrl;
            this.apkBytes = apkBytes;
        }

        boolean hasApk() {
            return !apkUrl.isEmpty();
        }
    }

    private Updates() {
    }

    // --- Looking for one -------------------------------------------------------------

    /**
     * The running version. Read from the package rather than from a generated constant:
     * the module builds with buildConfig disabled, and this is the number the installer
     * actually compared against.
     */
    static String installedVersion(Context context) {
        try {
            String name = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            return name == null ? "" : name.trim();
        } catch (PackageManager.NameNotFoundException e) {
            // The package manager cannot fail to find the package it is running.
            return "";
        }
    }

    /** Blocking; callers must stay off the main thread. */
    static Release latest() throws Exception {
        JSONObject json = new JSONObject(Http.get(LATEST_RELEASE, "", ""));
        String tag = json.optString("tag_name", "").trim();
        String version = tag.startsWith("v") ? tag.substring(1) : tag;

        String url = "";
        long size = 0L;
        JSONArray assets = json.optJSONArray("assets");
        for (int i = 0; assets != null && i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && asset.optString("name", "").endsWith(".apk")) {
                url = asset.optString("browser_download_url", "");
                size = asset.optLong("size", 0L);
                break;
            }
        }
        return new Release(version, tag, url, size);
    }

    /**
     * Which part of the version number moved, or {@link Step#NONE} when the candidate is
     * not newer. An unreadable version on either side counts as no update: offering one
     * against a number nothing could parse would mean offering it for ever.
     */
    static Step step(String installed, String candidate) {
        int[] from = parts(installed);
        int[] to = parts(candidate);
        if (from == null || to == null) {
            return Step.NONE;
        }
        if (to[0] != from[0]) {
            return to[0] > from[0] ? Step.MAJOR : Step.NONE;
        }
        if (to[1] != from[1]) {
            return to[1] > from[1] ? Step.MINOR : Step.NONE;
        }
        return to[2] > from[2] ? Step.PATCH : Step.NONE;
    }

    /** @return major/minor/patch, or null when the text is not three numbers. */
    private static int[] parts(String version) {
        String[] fields = version.trim().split("\\.");
        if (fields.length < 3) {
            return null;
        }
        int[] numbers = new int[3];
        for (int i = 0; i < 3; i++) {
            // A release may carry a suffix such as 1.2.0-rc1; the number in front is what
            // orders it, and anything after it is ignored rather than treated as a failure.
            StringBuilder digits = new StringBuilder();
            for (int c = 0; c < fields[i].length(); c++) {
                if (!Character.isDigit(fields[i].charAt(c))) {
                    break;
                }
                digits.append(fields[i].charAt(c));
            }
            if (digits.length() == 0) {
                return null;
            }
            try {
                numbers[i] = Integer.parseInt(digits.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return numbers;
    }

    // --- The cached answer the home screen reads ---------------------------------------

    /** True when the stored answer is old enough that the home screen should ask again. */
    static boolean checkDue(Prefs prefs) {
        return System.currentTimeMillis() - prefs.updateCheckedAt() > CHECK_INTERVAL_MS;
    }

    static void remember(Prefs prefs, Release release) {
        prefs.set(Prefs.UPDATE_LATEST, release.hasApk() ? release.version : "");
        prefs.setUpdateCheckedAt(System.currentTimeMillis());
    }

    // --- Fetching and installing it ----------------------------------------------------

    /** Blocking; callers must stay off the main thread. */
    static File download(Context context, Release release, Progress progress) throws IOException {
        File target = new File(context.getCacheDir(), DOWNLOAD_NAME);
        HttpURLConnection connection = (HttpURLConnection) new URL(release.apkUrl).openConnection();
        try {
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/octet-stream");
            int status = connection.getResponseCode();
            if (status >= 400) {
                throw new IOException("HTTP " + status);
            }

            // The asset URL redirects to a storage host that reports its own length; the
            // release's recorded size is the only figure available before that redirect.
            long total = connection.getContentLength() > 0
                    ? connection.getContentLength() : release.apkBytes;
            long done = 0L;
            int lastPercent = -1;
            try (InputStream in = connection.getInputStream();
                 OutputStream out = new FileOutputStream(target)) {
                byte[] chunk = new byte[16384];
                int count;
                while ((count = in.read(chunk)) != -1) {
                    out.write(chunk, 0, count);
                    done += count;
                    if (total > 0) {
                        int percent = (int) (done * 100 / total);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            progress.onPercent(percent);
                        }
                    }
                }
            }
            // A truncated download installs as a corrupt APK, which the installer reports
            // as a parse error that says nothing about the transfer that caused it.
            if (total > 0 && target.length() != total) {
                throw new IOException("downloaded " + target.length() + " of " + total + " bytes");
            }
            return target;
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Android 8 and newer gate sideloading behind a per-app permission that the app cannot
     * grant itself, so the install has to ask for it before it starts.
     */
    static boolean canInstall(Context context) {
        return context.getPackageManager().canRequestPackageInstalls();
    }

    /** Opens the per-app install permission screen. @return false when the device has none. */
    static boolean openInstallPermission(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName()));
        if (intent.resolveActivity(activity.getPackageManager()) == null) {
            return false;
        }
        activity.startActivity(intent);
        return true;
    }

    /**
     * Hands the APK to the system installer and reports what came back.
     *
     * <p>PackageInstaller rather than an ACTION_VIEW on a content URI: it needs no
     * FileProvider, and it is the only route that says why an install failed. On a TV
     * there is nowhere to read a log, so "signature does not match" has to reach the
     * screen or it looks like nothing happened.
     */
    static void install(Activity activity, File apk, Status status) throws IOException {
        Context app = activity.getApplicationContext();
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(activity.getPackageName());

        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (InputStream in = new FileInputStream(apk);
                 OutputStream out = session.openWrite("update", 0, apk.length())) {
                byte[] chunk = new byte[16384];
                int count;
                while ((count = in.read(chunk)) != -1) {
                    out.write(chunk, 0, count);
                }
                session.fsync(out);
            }
            listen(app, activity, status);
            session.commit(PendingIntent.getBroadcast(app, sessionId,
                    new Intent(INSTALL_STATUS).setPackage(app.getPackageName()),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE)
                    .getIntentSender());
        }
    }

    /**
     * Receives the one status the committed session sends back, then takes itself off. It
     * is registered against the application context so a commit that outlives the screen
     * still delivers, and it unregisters itself rather than on a lifecycle callback.
     */
    private static void listen(final Context app, final Activity activity, final Status status) {
        BroadcastReceiver receiver = new BroadcastReceiver() {
            // getParcelableExtra(String, Class) is the replacement and needs API 33; this
            // app ships to an Android 9 box, so the untyped overload is the only one.
            @Override
            @SuppressWarnings("deprecation")
            public void onReceive(Context context, Intent intent) {
                app.unregisterReceiver(this);
                int state = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                        PackageInstaller.STATUS_FAILURE);
                if (state == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    // The confirmation screen the user approves the install on. Expected on
                    // every run: an ordinary app never installs silently.
                    Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        activity.startActivity(confirm);
                    }
                    return;
                }
                if (state != PackageInstaller.STATUS_SUCCESS) {
                    String message = intent.getStringExtra(
                            PackageInstaller.EXTRA_STATUS_MESSAGE);
                    status.onMessage(message == null || message.isEmpty()
                            ? "install failed (" + state + ")" : message);
                }
                // Success is never reported: the process is replaced on its way out.
            }
        };
        // The permission is what keeps another app from delivering a forged status, and
        // therefore from handing this receiver an intent it would launch. RECEIVER_NOT_EXPORTED
        // would say the same thing but only from API 33; this holds on Android 9 too.
        app.registerReceiver(receiver, new IntentFilter(INSTALL_STATUS),
                INSTALL_STATUS_PERMISSION, null);
    }
}
