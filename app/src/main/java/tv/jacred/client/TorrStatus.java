package tv.jacred.client;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** A TorrServer state.TorrentStatus response. */
final class TorrStatus {
    /** state.TorrentStat in the TorrServer API: 0 added, 1 getting info, 2 preload, 3 working. */
    static final int ADDED = 0;

    final String hash;
    final String name;
    final String title;
    final String poster;
    final int stat;
    final String statString;
    final long torrentSize;
    final double downloadSpeed;
    final int activePeers;
    final int connectedSeeders;
    final long preloadedBytes;
    final long preloadSize;
    final List<TorrFile> files;

    TorrStatus(JSONObject json) {
        hash = json.optString("hash", "");
        name = json.optString("name", "");
        title = json.optString("title", "");
        poster = json.optString("poster", "");
        stat = json.optInt("stat", ADDED);
        statString = json.optString("stat_string", "");
        torrentSize = json.optLong("torrent_size", 0L);
        downloadSpeed = json.optDouble("download_speed", 0);
        activePeers = json.optInt("active_peers", 0);
        connectedSeeders = json.optInt("connected_seeders", 0);
        preloadedBytes = json.optLong("preloaded_bytes", 0L);
        preloadSize = json.optLong("preload_size", 0L);

        files = new ArrayList<>();
        JSONArray stats = json.optJSONArray("file_stats");
        if (stats != null) {
            for (int i = 0; i < stats.length(); i++) {
                JSONObject item = stats.optJSONObject(i);
                if (item != null) {
                    files.add(new TorrFile(
                            item.optInt("id", i + 1),
                            item.optString("path", "file " + (i + 1)),
                            item.optLong("length", 0L)));
                }
            }
        }
    }

    boolean hasFiles() {
        return !files.isEmpty();
    }
}
