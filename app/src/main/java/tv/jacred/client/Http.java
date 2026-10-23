package tv.jacred.client;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Small blocking HTTP helper. Callers must stay off the main thread. */
final class Http {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Linux; Android 9; MIBOX4) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";

    private static final int TIMEOUT_MS = 20000;

    /**
     * Ceiling on a response body. A search for a common word already runs to a few hundred
     * kilobytes of JSON; anything past this is a wrong address answering, not an API, and
     * reading it all would be the one way this app could run a TV box out of memory.
     */
    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;

    private Http() {
    }

    static String get(String url, String user, String pass) throws IOException {
        return request(url, "GET", null, user, pass, TIMEOUT_MS);
    }

    static String postJson(String url, String body, String user, String pass)
            throws IOException {
        return request(url, "POST", body, user, pass, TIMEOUT_MS);
    }

    private static String request(String url, String method, String body, String user, String pass,
                                  int timeoutMs) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        // disconnect() closes the socket and throws away the pooled connection, which costs a
        // fresh TCP and TLS handshake on the next call — three per merged search, and one
        // every 1.5s while the detail screen polls TorrServer. A connection whose body was
        // read to the end goes back in the pool instead, so it is only torn down on failure.
        boolean bodyConsumed = false;
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Referer", origin(url) + "/");
            if (!user.isEmpty() || !pass.isEmpty()) {
                String token = Base64.encodeToString(
                        (user + ":" + pass).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                connection.setRequestProperty("Authorization", "Basic " + token);
            }
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream()
                    : connection.getInputStream();
            String text = stream == null ? "" : read(stream);
            bodyConsumed = true;
            if (status >= 400) {
                throw new IOException("HTTP " + status + (text.isEmpty() ? "" : ": " + trim(text)));
            }
            return text;
        } finally {
            if (!bodyConsumed) {
                connection.disconnect();
            }
        }
    }

    private static String read(InputStream stream) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int count;
            while ((count = in.read(chunk)) != -1) {
                if (buffer.size() + count > MAX_BODY_BYTES) {
                    throw new IOException("Response larger than "
                            + (MAX_BODY_BYTES / (1024 * 1024)) + " MB");
                }
                buffer.write(chunk, 0, count);
            }
            return buffer.toString("UTF-8");
        }
    }

    private static String origin(String url) {
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return url;
        }
        int pathStart = url.indexOf('/', schemeEnd + 3);
        return pathStart < 0 ? url : url.substring(0, pathStart);
    }

    private static String trim(String text) {
        text = text.replace('\n', ' ').trim();
        return text.length() > 180 ? text.substring(0, 180) + "…" : text;
    }
}
