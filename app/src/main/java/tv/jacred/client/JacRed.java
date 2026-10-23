package tv.jacred.client;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Search client for jac.red.
 *
 * <p>The native v1.0 endpoint matches titles far better than the Jackett-compatible one —
 * "matrix" returns hundreds of releases there and two via v2.0 — so it is tried first, with
 * v2.0 kept as a fallback for hosts that only expose the Jackett shape.
 */
final class JacRed {
    /**
     * The two endpoints jac.red exposes, as its own site offers them.
     *
     * <p>NATIVE ({@code /api/v1.0/torrents}) matches titles far better and carries voices,
     * seasons and the updated date. JACKETT ({@code /api/v2.0/...}) is the Jackett-compatible
     * shape and the only one with ffprobe streams and language tags, so resolution and codec
     * come from the file itself rather than the tracker's claim.
     */
    enum Source {
        NATIVE, JACKETT;

        static Source of(String name) {
            return JACKETT.name().equalsIgnoreCase(name) ? JACKETT : NATIVE;
        }
    }

    private JacRed() {
    }

    /**
     * Searches every title in {@code queries} and merges the results, keyed by infohash. A
     * film's localised and original names each surface releases the other misses, and TMDB
     * hands us both.
     */
    static List<Torrent> search(String apiUrl, List<String> queries, Source source)
            throws Exception {
        Map<String, Torrent> byHash = new LinkedHashMap<>();
        Exception failure = null;
        for (String query : queries) {
            if (query == null || query.trim().isEmpty()) {
                continue;
            }
            try {
                for (Torrent torrent : search(apiUrl, query.trim(), source)) {
                    String hash = infoHash(torrent.magnet);
                    Torrent existing = byHash.get(hash);
                    // Keep whichever copy reports more seeders; trackers disagree.
                    if (existing == null || torrent.seeders > existing.seeders) {
                        byHash.put(hash, torrent);
                    }
                }
            } catch (Exception e) {
                failure = e;
            }
        }
        if (byHash.isEmpty() && failure != null) {
            throw failure;
        }
        List<Torrent> merged = new ArrayList<>(byHash.values());
        merged.sort((a, b) -> Integer.compare(b.seeders, a.seeders));
        return merged;
    }

    /** The btih of a magnet, lower case; the whole link when it carries none. */
    static String infoHash(String magnet) {
        int start = magnet.indexOf("btih:");
        if (start < 0) {
            return magnet;
        }
        int end = magnet.indexOf('&', start);
        String hash = end < 0 ? magnet.substring(start + 5) : magnet.substring(start + 5, end);
        return hash.toLowerCase(java.util.Locale.US);
    }

    static List<Torrent> search(String apiUrl, String query, Source source) throws Exception {
        String encoded = URLEncoder.encode(query, "UTF-8");
        List<Torrent> torrents = source == Source.JACKETT
                ? parseJackett(Http.get(
                        apiUrl + "/api/v2.0/indexers/all/results?query=" + encoded, "", ""))
                : parseNative(Http.get(
                        apiUrl + "/api/v1.0/torrents?search=" + encoded, "", ""));

        torrents.sort((a, b) -> Integer.compare(b.seeders, a.seeders));
        return torrents;
    }

    private static List<Torrent> parseNative(String body) throws Exception {
        return collect(new JSONArray(body));
    }

    private static List<Torrent> parseJackett(String body) throws Exception {
        return collect(new JSONObject(body).optJSONArray("Results"));
    }

    private static List<Torrent> collect(JSONArray array) {
        List<Torrent> torrents = new ArrayList<>();
        if (array == null) {
            return torrents;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            Torrent torrent = Torrent.from(item);
            if (!torrent.magnet.isEmpty()) {
                torrents.add(torrent);
            }
        }
        return torrents;
    }
}
