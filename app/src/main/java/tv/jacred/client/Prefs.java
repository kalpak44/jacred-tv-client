package tv.jacred.client;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything the user can configure, backed by SharedPreferences. */
final class Prefs {
    static final String DEFAULT_API = "https://jac.red";
    static final String DEFAULT_TORRSERVER = "http://192.168.1.7:5665";
    static final String DEFAULT_TMDB_API = "https://api.themoviedb.org";
    static final String DEFAULT_TMDB_IMAGES = "https://image.tmdb.org";
    /** Mirror TorrServer offers for regions where image.tmdb.org is blocked. */
    static final String MIRROR_TMDB_IMAGES = "https://imagetmdb.com";

    static final String API = "api";
    static final String TORRSERVER = "torrserver";
    static final String TORR_USER = "torr_user";
    static final String TORR_PASS = "torr_pass";
    static final String TMDB_KEY = "tmdb_key";
    static final String TMDB_API = "tmdb_api";
    static final String TMDB_IMAGES = "tmdb_images";
    static final String PLAYER = "player";
    static final String PLAYER_LABEL = "player_label";
    static final String HISTORY = "history";
    static final String SOURCE = "source";
    static final String UPDATE_LATEST = "update_latest";
    static final String UPDATE_CHECKED = "update_checked";

    private final SharedPreferences prefs;

    Prefs(Context context) {
        prefs = context.getSharedPreferences("jacred", Context.MODE_PRIVATE);
    }

    String get(String key, String fallback) {
        String value = prefs.getString(key, "");
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    void set(String key, String value) {
        prefs.edit().putString(key, value == null ? "" : value.trim()).apply();
    }

    String apiUrl() {
        return stripSlash(get(API, DEFAULT_API));
    }

    String torrServerUrl() {
        return stripSlash(get(TORRSERVER, DEFAULT_TORRSERVER));
    }

    String torrUser() {
        return get(TORR_USER, "");
    }

    String torrPass() {
        return get(TORR_PASS, "");
    }

    String tmdbKey() {
        return get(TMDB_KEY, "");
    }

    String tmdbApiUrl() {
        return stripSlash(get(TMDB_API, DEFAULT_TMDB_API));
    }

    String tmdbImageUrl() {
        return stripSlash(get(TMDB_IMAGES, DEFAULT_TMDB_IMAGES));
    }

    String playerPackage() {
        return get(PLAYER, "");
    }

    String playerLabel() {
        return get(PLAYER_LABEL, "");
    }

    /** Newest release seen by the last check, so the banner survives a restart. */
    String latestRelease() {
        return get(UPDATE_LATEST, "");
    }

    long updateCheckedAt() {
        try {
            return Long.parseLong(get(UPDATE_CHECKED, "0"));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    void setUpdateCheckedAt(long millis) {
        set(UPDATE_CHECKED, Long.toString(millis));
    }

    private static String stripSlash(String url) {
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }
}
