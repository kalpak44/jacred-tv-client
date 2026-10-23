package tv.jacred.client;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

/**
 * One search result.
 *
 * <p>jac.red exposes two shapes: the native {@code /api/v1.0/torrents} array (flat, lower-case
 * keys, far better text matching) and the Jackett-compatible {@code /api/v2.0/...} response
 * (capitalised keys, plus {@code ffprobe} stream details). Both are parsed here.
 */
final class Torrent {
    final String title;
    final String magnet;
    final String tracker;
    /** The release's page on the tracker, as the API reports it. */
    final String details;
    final String sizeName;
    final long size;
    final int seeders;
    final int peers;
    final int quality;
    final int year;
    final String videoType;
    final String languages;
    final String name;
    final String originalName;
    /** Dub studios and voice actors, jac.red's only localisation metadata. */
    final java.util.List<String> voices;
    final java.util.List<String> types;
    final java.util.List<String> seasons;
    final String codec;
    final boolean hdr;
    final boolean smooth;
    final long published;
    final long updated;

    private Torrent(JSONObject json) {
        boolean jackett = json.has("Title");
        JSONObject info = json.optJSONObject("info");
        JSONObject meta = info != null ? info : json;

        title = text(json, "Title", "title");
        magnet = text(json, "MagnetUri", "magnet");
        tracker = text(json, "Tracker", "tracker");
        details = text(json, "Details", "url");
        size = json.optLong(jackett ? "Size" : "size", 0L);
        seeders = json.optInt(jackett ? "Seeders" : "sid", 0);
        peers = json.optInt(jackett ? "Peers" : "pir", 0);

        name = meta.optString("name", "").trim();
        originalName = meta.optString("originalname", "").trim();
        year = meta.optInt("relased", 0);
        videoType = meta.optString("videotype", "").trim();
        hdr = "hdr".equalsIgnoreCase(videoType);

        String reported = meta.optString("sizeName", "").trim();
        sizeName = reported.isEmpty() ? humanSize(size) : reported;
        languages = join(json.optJSONArray("languages"));
        voices = list(meta.optJSONArray("voices"));
        types = list(meta.optJSONArray("types"));
        seasons = list(meta.optJSONArray("seasons"));

        // ffprobe, when present, is the only trustworthy source of resolution and codec:
        // the reported quality field calls plenty of 1080p releases 480.
        JSONArray streams = json.optJSONArray("ffprobe");
        int probed = probedQuality(streams);
        quality = probed > 0 ? probed : meta.optInt("quality", 0);

        String probedCodec = videoCodec(streams);
        codec = probedCodec.isEmpty() ? codecFromTitle(title) : probedCodec;
        // Frame rate is in neither API, so 60 fps can only be read off the release name.
        smooth = title.toLowerCase(Locale.US).replace(" ", "").contains("60fps");
        published = parseDate(text(json, "PublishDate", "createTime"));
        long touched = parseDate(json.optString("updateTime", ""));
        updated = touched > 0 ? touched : published;
    }

    static Torrent from(JSONObject json) {
        return new Torrent(json);
    }

    private static String text(JSONObject json, String jackettKey, String nativeKey) {
        String value = json.optString(jackettKey, "").trim();
        return value.isEmpty() ? json.optString(nativeKey, "").trim() : value;
    }

    /** Short label for the release, without the tracker's decorations. */
    String displayName() {
        if (!name.isEmpty()) {
            return originalName.isEmpty() || originalName.equals(name)
                    ? name : name + " / " + originalName;
        }
        return title;
    }

    /** Second line of a result row: quality, size, health, tracker. */
    String subtitle() {
        StringBuilder text = new StringBuilder();
        append(text, quality > 0 ? quality + "p" : "");
        append(text, hdr ? "HDR" : "");
        append(text, codecLabel());
        append(text, smooth ? "60 fps" : "");
        append(text, sizeName);
        append(text, "S " + seeders + " / P " + peers);
        append(text, year > 0 ? String.valueOf(year) : "");
        append(text, languages);
        if (!voices.isEmpty()) {
            append(text, android.text.TextUtils.join(", ", voices));
        }
        append(text, tracker);
        return text.toString();
    }

    String codecLabel() {
        if ("hevc".equals(codec)) {
            return "HEVC";
        }
        if ("av1".equals(codec)) {
            return "AV1";
        }
        if ("h264".equals(codec)) {
            return "H.264";
        }
        return "";
    }

    private static void append(StringBuilder text, String part) {
        if (part == null || part.isEmpty()) {
            return;
        }
        if (text.length() > 0) {
            text.append("  ·  ");
        }
        text.append(part);
    }

    /** Height of the largest video stream, rounded to a familiar label. */
    private static int probedQuality(JSONArray streams) {
        JSONObject best = largestVideoStream(streams);
        if (best == null) {
            return 0;
        }
        int height = best.optInt("height", 0);
        if (height <= 0) {
            return 0;
        }
        if (height > 1600) {
            return 2160;
        }
        if (height > 800) {
            return 1080;
        }
        if (height > 600) {
            return 720;
        }
        if (height > 440) {
            return 576;
        }
        return 480;
    }

    private static String videoCodec(JSONArray streams) {
        JSONObject best = largestVideoStream(streams);
        return best == null ? "" : best.optString("codec_name", "");
    }

    /** Posters and thumbnails ride along as extra video streams, so pick the widest one. */
    private static JSONObject largestVideoStream(JSONArray streams) {
        if (streams == null) {
            return null;
        }
        JSONObject best = null;
        int widest = 0;
        for (int i = 0; i < streams.length(); i++) {
            JSONObject stream = streams.optJSONObject(i);
            if (stream == null || !"video".equals(stream.optString("codec_type"))) {
                continue;
            }
            int width = stream.optInt("width", 0);
            if (width > widest) {
                widest = width;
                best = stream;
            }
        }
        return best;
    }

    /** Fallback for the native API, which carries no ffprobe data. */
    private static String codecFromTitle(String title) {
        String lower = title.toLowerCase(Locale.US);
        if (lower.contains("hevc") || lower.contains("x265") || lower.contains("h.265")
                || lower.contains("h265")) {
            return "hevc";
        }
        if (lower.contains("av1")) {
            return "av1";
        }
        if (lower.contains("avc") || lower.contains("x264") || lower.contains("h.264")
                || lower.contains("h264")) {
            return "h264";
        }
        return "";
    }

    /** Dates arrive in a few shapes; only the leading seconds matter for sorting. */
    private static long parseDate(String text) {
        if (text.length() < 19) {
            return 0L;
        }
        try {
            java.text.SimpleDateFormat format =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return format.parse(text.substring(0, 19)).getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static java.util.List<String> list(JSONArray array) {
        java.util.List<String> values = new java.util.ArrayList<>();
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private static String join(JSONArray array) {
        if (array == null || array.length() == 0) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            if (i > 0) {
                text.append('/');
            }
            text.append(array.optString(i, ""));
        }
        return text.toString();
    }

    /** Trackers arrive as a comma separated list when a release sits on several. */
    java.util.List<String> trackers() {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String part : tracker.split(",")) {
            String name = part.trim();
            if (!name.isEmpty()) {
                names.add(name);
            }
        }
        return names;
    }

    /** yyyy-MM-dd for the row chips; empty when the tracker gave no date. */
    static String shortDate(long millis) {
        if (millis <= 0) {
            return "";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new java.util.Date(millis));
    }

    static String humanSize(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        double value = bytes;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.US, value < 10 ? "%.2f %s" : "%.1f %s", value, units[unit]);
    }
}
