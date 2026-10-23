package tv.jacred.client;

/** One entry of a TorrServer torrent's file_stats. */
final class TorrFile {
    final int id;
    final String path;
    final long length;

    TorrFile(int id, String path, long length) {
        this.id = id;
        this.path = path;
        this.length = length;
    }

    String name() {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    boolean isVideo() {
        String name = name().toLowerCase(java.util.Locale.US);
        return name.endsWith(".mkv") || name.endsWith(".mp4") || name.endsWith(".avi")
                || name.endsWith(".ts") || name.endsWith(".m2ts") || name.endsWith(".mov")
                || name.endsWith(".m4v") || name.endsWith(".webm") || name.endsWith(".mpg")
                || name.endsWith(".mpeg") || name.endsWith(".wmv") || name.endsWith(".flv");
    }
}
