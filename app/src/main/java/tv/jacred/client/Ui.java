package tv.jacred.client;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared helpers: background work and the ten-foot window setup. */
final class Ui {
    private static final ExecutorService WORKERS =
            Executors.newFixedThreadPool(2, threadFactory("work"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Work<T> {
        T run() throws Exception;
    }

    interface Done<T> {
        void onResult(T result, Exception error);
    }

    private Ui() {
    }

    /**
     * Named daemon threads: a name makes a stack trace or a profiler say which pool the work
     * ran on, and a daemon thread never holds the process up on its own.
     */
    static ThreadFactory threadFactory(final String name) {
        return new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable,
                        "jacred-" + name + "-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };
    }

    /** Runs {@code work} off the main thread and delivers the outcome back on it. */
    static <T> void background(final Activity activity, final Work<T> work, final Done<T> done) {
        WORKERS.execute(new Runnable() {
            @Override
            public void run() {
                T result = null;
                Exception error = null;
                try {
                    result = work.run();
                } catch (Exception e) {
                    error = e;
                }
                final T finalResult = result;
                final Exception finalError = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            done.onResult(finalResult, finalError);
                        }
                    }
                });
            }
        });
    }

    static void post(Runnable runnable, long delayMs) {
        MAIN.postDelayed(runnable, delayMs);
    }

    /**
     * Hides the status and navigation bars. The replacement, WindowInsetsController, needs
     * API 30; this app ships to an Android 9 box, so the old flags are the only way.
     */
    @SuppressWarnings("deprecation")
    static void fullScreen(Activity activity) {
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    static String message(Exception error) {
        String text = error.getMessage();
        return text == null || text.isEmpty() ? error.getClass().getSimpleName() : text;
    }
}
