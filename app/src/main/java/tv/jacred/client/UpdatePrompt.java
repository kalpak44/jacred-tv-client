package tv.jacred.client;

import android.app.Activity;

import java.io.File;

/**
 * The modal behind both ways in — the Settings button and the home-screen banner — so the
 * two cannot drift into offering the same release differently.
 */
final class UpdatePrompt {
    private UpdatePrompt() {
    }

    /**
     * @param status where progress and failures are written: the banner on the home screen,
     *               the result line in Settings.
     */
    static void offer(final Activity activity, final Updates.Release release,
                      final Updates.Status status) {
        String installed = Updates.installedVersion(activity);
        String subtitle = release.apkBytes > 0
                ? activity.getString(R.string.update_subtitle, installed,
                Torrent.humanSize(release.apkBytes))
                : activity.getString(R.string.update_subtitle_unsized, installed);

        TvDialog.create(activity, activity.getString(R.string.update_title, release.version),
                subtitle)
                .add(activity.getString(R.string.update_install), null, null)
                .add(activity.getString(R.string.update_later), null, null)
                .onPick(index -> {
                    if (index == 0) {
                        start(activity, release, status);
                    }
                    return true;
                })
                .show();
    }

    private static void start(final Activity activity, final Updates.Release release,
                              final Updates.Status status) {
        if (!Updates.canInstall(activity)) {
            askPermission(activity, status);
            return;
        }
        status.onMessage(activity.getString(R.string.update_downloading, release.version, 0));
        Ui.background(activity,
                () -> Updates.download(activity, release,
                        percent -> Ui.post(() -> status.onMessage(activity.getString(
                                R.string.update_downloading, release.version, percent)), 0)),
                (File apk, Exception error) -> {
                    if (error != null) {
                        status.onMessage(activity.getString(R.string.update_download_failed,
                                Ui.message(error)));
                        return;
                    }
                    status.onMessage(activity.getString(R.string.update_opening_installer));
                    try {
                        Updates.install(activity, apk, status);
                    } catch (Exception e) {
                        status.onMessage(activity.getString(R.string.update_download_failed,
                                Ui.message(e)));
                    }
                });
    }

    /** The permission is a per-app switch on a system screen; nothing here can set it. */
    private static void askPermission(final Activity activity, final Updates.Status status) {
        TvDialog.create(activity, activity.getString(R.string.update_allow_title),
                activity.getString(R.string.update_allow_message))
                .add(activity.getString(R.string.update_allow_open), null, null)
                .add(activity.getString(R.string.update_allow_cancel), null, null)
                .onPick(index -> {
                    if (index == 0 && !Updates.openInstallPermission(activity)) {
                        status.onMessage(activity.getString(R.string.update_allow_missing));
                    }
                    return true;
                })
                .show();
    }
}
