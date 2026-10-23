package tv.jacred.client;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Client for the TorrServer HTTP API (YouROK/TorrServer, verified against MatriX.142).
 *
 * <p>Everything goes through POST /torrents with an {@code action} field; playback uses
 * GET /stream?link=&lt;hash&gt;&amp;index=&lt;file id&gt;&amp;play.
 */
final class TorrServer {
    private final String base;
    private final String user;
    private final String pass;

    TorrServer(Prefs prefs) {
        base = prefs.torrServerUrl();
        user = prefs.torrUser();
        pass = prefs.torrPass();
    }

    boolean isConfigured() {
        return !base.isEmpty();
    }

    String address() {
        return base;
    }

    /** Server version string, e.g. "MatriX.142". Throws when unreachable. */
    String echo() throws IOException {
        return Http.get(base + "/echo", user, pass).trim();
    }

    /** TMDB configuration stored on the server, used to seed the app's own settings. */
    JSONObject tmdbSettings() throws Exception {
        return new JSONObject(Http.get(base + "/tmdb/settings", user, pass));
    }

    /**
     * Adds a magnet and returns its hash. {@code keep} decides whether the torrent stays in
     * the server database after playback (the TorrServer web UI calls this "save").
     */
    String add(String magnet, String title, String poster, boolean keep) throws Exception {
        JSONObject request = new JSONObject();
        request.put("action", "add");
        request.put("link", magnet);
        request.put("title", title);
        request.put("poster", poster);
        request.put("category", "movie");
        request.put("save_to_db", keep);

        JSONObject json = new JSONObject(
                Http.postJson(base + "/torrents", request.toString(), user, pass));
        String hash = json.optString("hash", "").trim();
        if (hash.isEmpty()) {
            throw new IOException("TorrServer did not return a torrent hash");
        }
        return hash;
    }

    /** Current state of one torrent; file_stats stays empty until metadata arrives. */
    TorrStatus get(String hash) throws Exception {
        JSONObject request = new JSONObject();
        request.put("action", "get");
        request.put("hash", hash);
        return new TorrStatus(new JSONObject(
                Http.postJson(base + "/torrents", request.toString(), user, pass)));
    }

    void remove(String hash) {
        try {
            JSONObject request = new JSONObject();
            request.put("action", "rem");
            request.put("hash", hash);
            Http.postJson(base + "/torrents", request.toString(), user, pass);
        } catch (Exception ignored) {
            // Best effort: a stale torrent on the server is harmless.
        }
    }

    /** Marks a file watched so TorrServer clients (including this one) can show it. */
    void markViewed(String hash, int fileIndex) {
        setViewed(hash, fileIndex, true);
    }

    /** Adds or clears the watched mark for one file. */
    void setViewed(String hash, int fileIndex, boolean watched) {
        try {
            JSONObject request = new JSONObject();
            request.put("action", watched ? "set" : "rem");
            request.put("hash", hash);
            request.put("file_index", fileIndex);
            Http.postJson(base + "/viewed", request.toString(), user, pass);
        } catch (Exception ignored) {
            // Watch marks are a convenience, never a reason to block playback.
        }
    }

    /** File indexes already watched for this torrent. */
    List<Integer> viewed(String hash) {
        List<Integer> indexes = new ArrayList<>();
        try {
            JSONObject request = new JSONObject();
            request.put("action", "list");
            request.put("hash", hash);
            JSONArray array = new JSONArray(
                    Http.postJson(base + "/viewed", request.toString(), user, pass));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null && hash.equalsIgnoreCase(item.optString("hash"))) {
                    indexes.add(item.optInt("file_index", -1));
                }
            }
        } catch (Exception ignored) {
            // Older builds may not expose /viewed; treat as "nothing watched".
        }
        return indexes;
    }

    /**
     * The whole torrent as an M3U playlist, so a player can walk the episodes itself.
     * {@code fromLast} starts the list at the file last played rather than the first.
     */
    String playlistUrl(String hash, boolean fromLast) {
        // The .m3u filename is part of the route and gives extension-sniffing players a hint.
        return base + "/playlist/playlist.m3u?hash=" + hash + (fromLast ? "&fromlast=true" : "");
    }

    String streamUrl(String hash, TorrFile file) {
        String name;
        try {
            name = URLEncoder.encode(file.name(), "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            name = "stream";
        }
        return base + "/stream/" + name + "?link=" + hash + "&index=" + file.id + "&play";
    }
}
