package tv.jacred.client;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Artwork and plot lookups against TMDB (or one of its mirrors). */
final class Tmdb {
    /** Poster sizes TMDB serves under /t/p/: the grid uses the small one, screens the large. */
    static final String POSTER_SMALL = "w185";
    static final String POSTER_LARGE = "w342";

    /** A looked-up title. */
    static final class Movie {
        final String title;
        final String originalTitle;
        final String overview;
        final String posterPath;
        final double rating;
        final String year;
        final boolean series;

        private Movie(String title, String originalTitle, String overview, String posterPath,
                      double rating, String year, boolean series) {
            this.title = title;
            this.originalTitle = originalTitle;
            this.overview = overview;
            this.posterPath = posterPath;
            this.rating = rating;
            this.year = year;
            this.series = series;
        }

        boolean isEmpty() {
            return title.isEmpty() && posterPath.isEmpty();
        }

        /** The name jac.red indexes best: its original title when TMDB knows one. */
        String titleForLookup() {
            return originalTitle.isEmpty() ? title : originalTitle;
        }
    }

    private final String key;
    private final String apiUrl;
    private final String imageUrl;

    Tmdb(Prefs prefs) {
        key = prefs.tmdbKey();
        apiUrl = prefs.tmdbApiUrl();
        imageUrl = prefs.tmdbImageUrl();
    }

    boolean isConfigured() {
        return !key.isEmpty();
    }

    /** Every movie and show matching {@code query}; people and collections are skipped. */
    List<Movie> search(String query) throws Exception {
        String url = apiUrl + "/3/search/multi?api_key=" + URLEncoder.encode(key, "UTF-8")
                + "&language=" + language()
                + "&include_adult=false&query=" + URLEncoder.encode(query, "UTF-8");
        JSONArray results = new JSONObject(Http.get(url, "", "")).optJSONArray("results");

        List<Movie> movies = new ArrayList<>();
        if (results == null) {
            return movies;
        }
        for (int i = 0; i < results.length(); i++) {
            Movie movie = parse(results.optJSONObject(i));
            if (movie != null && !movie.isEmpty()) {
                movies.add(movie);
            }
        }
        return movies;
    }

    private static Movie parse(JSONObject item) {
        if (item == null) {
            return null;
        }
        String type = item.optString("media_type", "movie");
        if (!"movie".equals(type) && !"tv".equals(type)) {
            return null;
        }
        String date = item.optString("release_date", item.optString("first_air_date", ""));
        return new Movie(
                item.optString("title", item.optString("name", "")),
                item.optString("original_title", item.optString("original_name", "")),
                item.optString("overview", ""),
                item.optString("poster_path", ""),
                item.optDouble("vote_average", 0),
                date.length() >= 4 ? date.substring(0, 4) : "",
                "tv".equals(type));
    }

    String imageUrl(String path, String size) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return imageUrl + "/t/p/" + size + path;
    }

    private static String language() {
        String lang = Locale.getDefault().getLanguage();
        return "ru".equals(lang) ? "ru-RU" : "en-US";
    }
}
