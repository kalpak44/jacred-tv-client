package tv.jacred.client;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Home screen: search TMDB for a title, pick it from a poster wall, and carry it over to the
 * releases screen. Without a TMDB key the query goes straight to jac.red instead.
 */
public class MainActivity extends Activity {
    /** Clicks this soon after the grid takes focus are a stray key-up, not a choice. */
    private static final long FOCUS_GRACE_MS = 400L;

    private long gridFocusedAt;
    private Prefs prefs;
    private History history;
    private Tmdb tmdb;

    private EditText queryField;
    private TextView status;
    private TextView serverChip;
    private GridView grid;
    private View emptyState;
    private View settingsButton;
    private View searchButton;
    /** First card of the history row, the landing spot when moving down from the search box. */
    private View firstHistoryCard;
    private View historySection;
    private LinearLayout historyRow;
    /** The stored history the strip on screen was built from; see {@link #showHistory()}. */
    private String historyShown;

    private final List<Tmdb.Movie> movies = new ArrayList<>();
    private MoviesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.fullScreen(this);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        history = new History(prefs);
        tmdb = new Tmdb(prefs);
        ImageLoader.init(this);

        queryField = findViewById(R.id.query);
        status = findViewById(R.id.status);
        serverChip = findViewById(R.id.server_chip);
        grid = findViewById(R.id.movies);
        emptyState = findViewById(R.id.empty_state);
        historySection = findViewById(R.id.history_section);
        historyRow = findViewById(R.id.history_row);

        adapter = new MoviesAdapter();
        grid.setAdapter(adapter);
        grid.setOnKeyListener((view, keyCode, event) -> {
            boolean onTopRow = grid.getSelectedItemPosition() < grid.getNumColumns();
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_DPAD_UP && onTopRow) {
                return historySection.getVisibility() == View.VISIBLE && firstHistoryCard != null
                        ? firstHistoryCard.requestFocus() : queryField.requestFocus();
            }
            return false;
        });
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (System.currentTimeMillis() - gridFocusedAt < FOCUS_GRACE_MS) {
                    return;
                }
                openReleases(movies.get(position));
            }
        });

        searchButton = findViewById(R.id.search_button);
        settingsButton = findViewById(R.id.settings_button);
        searchButton.setOnClickListener(view -> search());
        settingsButton.setOnClickListener(view ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        // A focused EditText swallows up/down before the framework can look for a neighbour,
        // which strands the remote in the search box. Move focus explicitly instead.
        View.OnKeyListener verticalSteps = (view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return settingsButton.requestFocus();
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return focusBelowSearch();
            }
            return false;
        };
        queryField.setOnKeyListener(verticalSteps);
        searchButton.setOnKeyListener(verticalSteps);

        queryField.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                search();
                return true;
            }
            return false;
        });

        // On a TV the keyboard must not open just because the field holds focus: while it is
        // up the IME swallows the D-pad and nothing else on the screen can be reached. It
        // opens on OK instead.
        queryField.setShowSoftInputOnFocus(false);
        queryField.setOnClickListener(view -> {
            InputMethodManager manager = getSystemService(InputMethodManager.class);
            if (manager != null) {
                manager.showSoftInput(queryField, InputMethodManager.SHOW_IMPLICIT);
            }
        });

        // The previous query comes back selected, so typing over it replaces it.
        queryField.setText(prefs.get(Prefs.LAST_QUERY, ""));
        queryField.selectAll();
        queryField.requestFocus();
    }

    /**
     * Back steps out of a wall of search results first — that is the way home to the
     * recently watched row — and only offers to leave once the home screen is plain.
     * OnBackPressedDispatcher is the modern route, but it lives in AndroidX and this app
     * has no dependencies.
     */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!movies.isEmpty()) {
            clearResults();
            return;
        }
        // Staying is the first row, so it holds focus and a stray second Back cannot quit.
        TvDialog.create(this, getString(R.string.exit_title), getString(R.string.exit_message))
                .add(getString(R.string.exit_stay), null, null)
                .add(getString(R.string.exit_confirm), null, null)
                .onPick(index -> {
                    if (index == 1) {
                        finish();
                    }
                    return true;
                })
                .show();
    }

    /** Drops the result wall and puts the home screen back the way it starts. */
    private void clearResults() {
        movies.clear();
        adapter.notifyDataSetChanged();
        status.setText(tmdb.isConfigured() ? getString(R.string.status_idle)
                : getString(R.string.status_idle_no_tmdb));
        showHistory();
        // The search box is where a second attempt starts, with the old query selected so
        // typing replaces it.
        queryField.requestFocus();
        queryField.selectAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        tmdb = new Tmdb(prefs);
        status.setText(tmdb.isConfigured() ? getString(R.string.status_idle)
                : getString(R.string.status_idle_no_tmdb));
        showHistory();
        pingServer();
    }

    private void pingServer() {
        final TorrServer server = new TorrServer(prefs);
        serverChip.setText(R.string.chip_checking);
        Ui.background(this, server::echo, (version, error) -> {
            if (error != null) {
                serverChip.setText(R.string.chip_offline);
                serverChip.setTextColor(getColor(R.color.warning));
            } else {
                serverChip.setText(getString(R.string.chip_online, version));
                serverChip.setTextColor(getColor(R.color.mint));
            }
        });
    }

    /**
     * The "search a film or show" hint explains an empty home screen. With a recently
     * watched row on screen there is nothing left to explain and no room left to say it
     * in — the sentence was being cut in half — so the hint steps aside for the strip.
     */
    private void updateEmptyState() {
        boolean nothingElseToShow = movies.isEmpty()
                && historySection.getVisibility() != View.VISIBLE;
        emptyState.setVisibility(nothingElseToShow ? View.VISIBLE : View.GONE);
    }

    /** Takes the strip off screen and records that, so it can be built again later. */
    private void hideHistory() {
        historySection.setVisibility(View.GONE);
        historyShown = "";
        updateEmptyState();
    }

    /** Down from the search row: the history strip when it is there, otherwise the results. */
    private boolean focusBelowSearch() {
        if (historySection.getVisibility() == View.VISIBLE && firstHistoryCard != null) {
            return firstHistoryCard.requestFocus();
        }
        return !movies.isEmpty() && grid.requestFocus();
    }

    /**
     * Builds the recently-watched strip, but only when it would come out different.
     *
     * <p>This runs on every return to the home screen, and a full strip is thirty inflated
     * cards: rebuilding it each time cost a visible stall on the way back from a title. The
     * stored history is compared as the raw string it is kept as, so an unchanged strip
     * costs one string compare and not even a JSON parse.
     */
    private void showHistory() {
        // Results own the screen once a search has run: showing both crushes the grid into
        // an unusable sliver when coming back from a title.
        String stored = movies.isEmpty() ? prefs.get(Prefs.HISTORY, "[]") : "";
        if (stored.equals(historyShown)) {
            return;
        }
        historyShown = stored;
        historyRow.removeAllViews();
        firstHistoryCard = null;

        List<History.Entry> entries = stored.isEmpty() ? new ArrayList<>() : history.all();
        if (entries.isEmpty()) {
            historySection.setVisibility(View.GONE);
            updateEmptyState();
            return;
        }

        historySection.setVisibility(View.VISIBLE);
        updateEmptyState();

        LayoutInflater inflater = LayoutInflater.from(this);
        for (final History.Entry entry : entries) {
            View card = inflater.inflate(R.layout.item_history, historyRow, false);
            ((TextView) card.findViewById(R.id.card_title)).setText(entry.title);
            ((TextView) card.findViewById(R.id.card_file)).setText(entry.fileName);
            ImageLoader.load(card.findViewById(R.id.card_poster), entry.poster,
                    R.drawable.poster_placeholder);
            // Tap plays it again; holding OK opens the card's options.
            card.setOnClickListener(view -> replay(entry));
            card.setOnLongClickListener(view -> {
                historyMenu(entry);
                return true;
            });
            // Up leaves the strip for the search box, down continues into the results.
            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    return queryField.requestFocus();
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    return !movies.isEmpty() && grid.requestFocus();
                }
                return false;
            });
            historyRow.addView(card);
            if (firstHistoryCard == null) {
                firstHistoryCard = card;
            }
        }
    }

    /** Held OK on a recent card: play it again, or drop it from the strip. */
    private void historyMenu(final History.Entry entry) {
        TvDialog.create(this, entry.title, entry.fileName)
                .add(getString(R.string.history_play), null, null)
                .add(getString(R.string.history_remove), null, null)
                .onPick(index -> {
                    if (index == 0) {
                        replay(entry);
                    } else {
                        history.remove(entry.magnet);
                        showHistory();
                    }
                    return true;
                })
                .show();
    }

    /** History goes straight back to the torrent it came from, skipping both searches. */
    private void replay(History.Entry entry) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_TITLE, entry.title);
        intent.putExtra(DetailActivity.EXTRA_MAGNET, entry.magnet);
        intent.putExtra(DetailActivity.EXTRA_SUBTITLE, entry.fileName);
        intent.putExtra(DetailActivity.EXTRA_POSTER, entry.poster);
        startActivity(intent);
    }

    private void search() {
        final String query = queryField.getText().toString().trim();
        if (query.isEmpty()) {
            status.setText(R.string.status_enter_query);
            return;
        }
        hideKeyboard();
        prefs.set(Prefs.LAST_QUERY, query);

        if (!tmdb.isConfigured()) {
            // No artwork available, so skip the browse step entirely.
            searchJacRedDirectly(query);
            return;
        }

        movies.clear();
        adapter.notifyDataSetChanged();
        status.setText(getString(R.string.status_searching_tmdb, query));

        Ui.background(this, () -> tmdb.search(query), (found, error) -> {
            // TMDB is only a nicer way in. When it knows nothing — or cannot be reached —
            // the query still goes to jac.red rather than dead-ending here.
            if (error != null) {
                status.setText(getString(R.string.status_tmdb_failed, Ui.message(error)));
                searchJacRedDirectly(query);
                return;
            }
            movies.addAll(found);
            adapter.notifyDataSetChanged();
            if (movies.isEmpty()) {
                status.setText(getString(R.string.status_no_titles, query));
                searchJacRedDirectly(query);
                return;
            }
            hideHistory();
            status.setText(getString(R.string.status_titles, movies.size()));
            grid.requestFocus();
            grid.setSelection(0);
            // The key-up of the OK press that started the search would otherwise land on
            // the freshly focused grid and open the first title by itself.
            gridFocusedAt = System.currentTimeMillis();
        });
    }

    /** Sends the typed text straight to jac.red, skipping the TMDB browse step. */
    private void searchJacRedDirectly(String query) {
        Intent intent = new Intent(this, ReleasesActivity.class);
        intent.putExtra(ReleasesActivity.EXTRA_QUERY, query);
        intent.putExtra(ReleasesActivity.EXTRA_TITLE, query);
        startActivity(intent);
    }

    private void openReleases(Tmdb.Movie movie) {
        Intent intent = new Intent(this, ReleasesActivity.class);
        intent.putExtra(ReleasesActivity.EXTRA_QUERY, movie.titleForLookup());
        intent.putExtra(ReleasesActivity.EXTRA_TITLE, movie.title);
        intent.putExtra(ReleasesActivity.EXTRA_ORIGINAL, movie.originalTitle);
        intent.putExtra(ReleasesActivity.EXTRA_YEAR, movie.year);
        intent.putExtra(ReleasesActivity.EXTRA_OVERVIEW, movie.overview);
        intent.putExtra(ReleasesActivity.EXTRA_RATING, movie.rating);
        intent.putExtra(ReleasesActivity.EXTRA_POSTER,
                tmdb.imageUrl(movie.posterPath, Tmdb.POSTER_LARGE));
        startActivity(intent);
    }

    /** The leanback keyboard covers the results, so it goes away as soon as a search runs. */
    private void hideKeyboard() {
        InputMethodManager manager = getSystemService(InputMethodManager.class);
        if (manager != null) {
            manager.hideSoftInputFromWindow(queryField.getWindowToken(), 0);
        }
    }

    /** The views of one poster card, found once when the card is inflated. */
    private static final class Card {
        final TextView title;
        final TextView meta;
        final ImageView poster;

        Card(View card) {
            title = card.findViewById(R.id.movie_title);
            meta = card.findViewById(R.id.movie_meta);
            poster = card.findViewById(R.id.movie_poster);
        }
    }

    private final class MoviesAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return movies.size();
        }

        @Override
        public Tmdb.Movie getItem(int position) {
            return movies.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View card = convertView;
            Card views;
            if (card == null) {
                card = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_movie, parent, false);
                views = new Card(card);
                card.setTag(views);
            } else {
                views = (Card) card.getTag();
            }
            Tmdb.Movie movie = movies.get(position);
            views.title.setText(movie.title);

            StringBuilder meta = new StringBuilder();
            if (!movie.year.isEmpty()) {
                meta.append(movie.year);
            }
            if (movie.series) {
                meta.append(meta.length() > 0 ? "  ·  " : "").append(getString(R.string.series));
            }
            if (movie.rating > 0) {
                meta.append(meta.length() > 0 ? "  ·  " : "")
                        .append(String.format(java.util.Locale.US, "%.1f", movie.rating));
            }
            views.meta.setText(meta.toString());
            ImageLoader.load(views.poster,
                    tmdb.imageUrl(movie.posterPath, Tmdb.POSTER_SMALL),
                    R.drawable.poster_placeholder);
            return card;
        }
    }
}
