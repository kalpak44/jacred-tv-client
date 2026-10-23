package tv.jacred.client;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** The recently opened torrents, shown on the home screen so a rewatch is two clicks. */
final class History {
    private static final int LIMIT = 30;

    static final class Entry {
        final String title;
        final String magnet;
        final String poster;
        final String fileName;
        final long time;

        Entry(String title, String magnet, String poster, String fileName, long time) {
            this.title = title;
            this.magnet = magnet;
            this.poster = poster;
            this.fileName = fileName;
            this.time = time;
        }

        private JSONObject toJson() throws Exception {
            JSONObject json = new JSONObject();
            json.put("title", title);
            json.put("magnet", magnet);
            json.put("poster", poster);
            json.put("file", fileName);
            json.put("time", time);
            return json;
        }

        private static Entry fromJson(JSONObject json) {
            return new Entry(
                    json.optString("title", ""),
                    json.optString("magnet", ""),
                    json.optString("poster", ""),
                    json.optString("file", ""),
                    json.optLong("time", 0L));
        }
    }

    private final Prefs prefs;

    History(Prefs prefs) {
        this.prefs = prefs;
    }

    List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs.get(Prefs.HISTORY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    entries.add(Entry.fromJson(item));
                }
            }
        } catch (Exception ignored) {
            // Corrupt history is not worth a crash; start over instead.
        }
        return entries;
    }

    /** Moves this torrent to the front, replacing any earlier entry for the same magnet. */
    void remember(Entry entry) {
        List<Entry> entries = all();
        List<Entry> kept = new ArrayList<>();
        kept.add(entry);
        for (Entry old : entries) {
            if (!old.magnet.equals(entry.magnet) && kept.size() < LIMIT) {
                kept.add(old);
            }
        }
        save(kept);
    }

    /** Removes one torrent from the strip, leaving the rest in order. */
    void remove(String magnet) {
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : all()) {
            if (!entry.magnet.equals(magnet)) {
                kept.add(entry);
            }
        }
        save(kept);
    }

    void clear() {
        prefs.set(Prefs.HISTORY, "[]");
    }

    private void save(List<Entry> entries) {
        try {
            JSONArray array = new JSONArray();
            for (Entry entry : entries) {
                array.put(entry.toJson());
            }
            prefs.set(Prefs.HISTORY, array.toString());
        } catch (Exception ignored) {
            // Failing to persist history must not interrupt playback.
        }
    }
}
