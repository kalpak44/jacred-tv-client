package tv.jacred.client;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal poster loader: memory cache, disk cache, and a tag check so recycled rows never
 * show the previous row's artwork.
 */
final class ImageLoader {
    private static final ExecutorService POOL =
            Executors.newFixedThreadPool(3, Ui.threadFactory("poster"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> MEMORY =
            new LruCache<String, Bitmap>(12 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };

    private static final int CONNECT_TIMEOUT_MS = 15000;
    /** A TMDB poster is tens of kilobytes; past this the response is not artwork. */
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

    private static File cacheDir;

    private ImageLoader() {
    }

    static void init(Context context) {
        if (cacheDir == null) {
            File dir = new File(context.getCacheDir(), "posters");
            // Without a directory the loader still works, it just re-fetches every time.
            cacheDir = dir.isDirectory() || dir.mkdirs() ? dir : null;
        }
    }

    /** Loads {@code url} into {@code view}; an empty url just clears it. */
    static void load(final ImageView view, final String url, final int placeholder) {
        view.setTag(url);
        if (url == null || url.isEmpty()) {
            view.setImageResource(placeholder);
            return;
        }
        Bitmap cached = MEMORY.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(placeholder);

        POOL.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bitmap = fetch(url);
                if (bitmap == null) {
                    return;
                }
                MEMORY.put(url, bitmap);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (url.equals(view.getTag())) {
                            view.setImageBitmap(bitmap);
                        }
                    }
                });
            }
        });
    }

    private static Bitmap fetch(String url) {
        File file = cacheFile(url);
        if (file != null && file.exists()) {
            Bitmap cached = BitmapFactory.decodeFile(file.getAbsolutePath());
            if (cached != null) {
                return cached;
            }
        }
        byte[] bytes = download(url);
        if (bytes == null) {
            return null;
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        // Only what decoded is worth keeping: a half-written or truncated file would be
        // read back as a permanently broken poster.
        if (bitmap != null) {
            store(file, bytes);
        }
        return bitmap;
    }

    private static byte[] download(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(CONNECT_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "JacRedTV");

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] chunk = new byte[8192];
                int count;
                while ((count = stream.read(chunk)) != -1) {
                    if (buffer.size() + count > MAX_IMAGE_BYTES) {
                        return null;
                    }
                    buffer.write(chunk, 0, count);
                }
            }
            return buffer.toByteArray();
        } catch (Exception e) {
            // A missing poster is never worth more than a placeholder.
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** Writes beside the target and renames, so a reader never sees a partial file. */
    private static void store(File file, byte[] bytes) {
        if (file == null) {
            return;
        }
        File partial = new File(file.getPath() + ".part");
        try {
            try (FileOutputStream out = new FileOutputStream(partial)) {
                out.write(bytes);
            }
            if (!partial.renameTo(file)) {
                // noinspection ResultOfMethodCallIgnored
                partial.delete();
            }
        } catch (Exception e) {
            // noinspection ResultOfMethodCallIgnored
            partial.delete();
        }
    }

    private static File cacheFile(String url) {
        if (cacheDir == null) {
            return null;
        }
        return new File(cacheDir, Integer.toHexString(url.hashCode()) + ".img");
    }
}
