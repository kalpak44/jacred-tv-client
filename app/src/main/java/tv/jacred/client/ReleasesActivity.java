package tv.jacred.client;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The releases jac.red holds for one title.
 *
 * <p>jac.red can narrow server side, but one search already returns the whole catalogue for a
 * query, so the sort order and every facet are applied to the results in memory: instant on a
 * remote, and no repeat requests to a host that rate-limits them.
 */
public class ReleasesActivity extends Activity {
    static final String EXTRA_QUERY = "query";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_ORIGINAL = "original";
    static final String EXTRA_YEAR = "year";
    static final String EXTRA_OVERVIEW = "overview";
    static final String EXTRA_RATING = "rating";
    static final String EXTRA_POSTER = "poster";

    private static final long FOCUS_GRACE_MS = 400L;

    private static final int[] SORT_LABELS = {
            R.string.sort_by_seeds, R.string.sort_by_size,
            R.string.sort_added, R.string.sort_updated,
    };

    private Prefs prefs;
    private long listFocusedAt;

    private String query;
    private String title;
    private String original;
    private String poster;

    private final List<Torrent> found = new ArrayList<>();
    private final List<Torrent> shown = new ArrayList<>();
    private final Filters filters = new Filters();
    private ReleasesAdapter adapter;

    private TextView status;
    private ListView list;
    private LinearLayout sortBar;
    private JacRed.Source source;
    private LinearLayout facetBar;
    private View headerBlock;
    private TextView overviewView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.fullScreen(this);
        setContentView(R.layout.activity_releases);
        prefs = new Prefs(this);
        ImageLoader.init(this);

        source = JacRed.Source.of(prefs.get(Prefs.SOURCE, JacRed.Source.NATIVE.name()));
        query = string(EXTRA_QUERY);
        title = string(EXTRA_TITLE);
        original = string(EXTRA_ORIGINAL);
        poster = string(EXTRA_POSTER);

        bindHeader();

        status = findViewById(R.id.status);
        list = findViewById(R.id.releases);
        headerBlock = findViewById(R.id.header_block);
        sortBar = findViewById(R.id.sort_bar);
        facetBar = findViewById(R.id.facet_bar);

        adapter = new ReleasesAdapter();
        list.setAdapter(adapter);
        // The poster and plot are context for choosing a title, not for walking a long list:
        // they fold away while the list has focus so the rows get the whole screen.
        list.setOnFocusChangeListener((view, focused) -> showArtwork(!focused));
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (System.currentTimeMillis() - listFocusedAt < FOCUS_GRACE_MS) {
                    return;
                }
                open(shown.get(position));
            }
        });

        buildSortBar();
        load();
    }

    private void bindHeader() {
        ((TextView) findViewById(R.id.movie_title)).setText(title);

        String year = string(EXTRA_YEAR);
        String overview = string(EXTRA_OVERVIEW);
        double rating = getIntent().getDoubleExtra(EXTRA_RATING, 0);

        StringBuilder meta = new StringBuilder();
        if (!original.isEmpty() && !original.equals(title)) {
            meta.append(original);
        }
        if (!year.isEmpty()) {
            meta.append(meta.length() > 0 ? "  ·  " : "").append(year);
        }
        if (rating > 0) {
            meta.append(meta.length() > 0 ? "  ·  " : "")
                    .append(getString(R.string.tmdb_rating, rating));
        }
        ((TextView) findViewById(R.id.movie_meta)).setText(meta.toString());

        overviewView = findViewById(R.id.movie_overview);
        // With a key configured there is nothing to advise: a plot is simply missing for
        // this title, or the search came straight here and never looked one up.
        if (!overview.isEmpty()) {
            overviewView.setText(overview);
        } else if (new Tmdb(prefs).isConfigured()) {
            overviewView.setVisibility(View.GONE);
        } else {
            overviewView.setText(R.string.no_tmdb_hint);
        }

        ImageView posterView = findViewById(R.id.movie_poster);
        if (poster.isEmpty()) {
            posterView.setVisibility(View.GONE);
        } else {
            ImageLoader.load(posterView, poster, R.drawable.poster_placeholder);
        }
        findViewById(R.id.alt_title_button).setVisibility(View.GONE);
    }

    /** Folds the poster and plot away, leaving the title line and the controls. */
    private void showArtwork(boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        if (!poster.isEmpty()) {
            findViewById(R.id.movie_poster).setVisibility(state);
        }
        // Same guard as the poster: an empty plot has nothing to fold back out.
        if (overviewView.getText().length() > 0) {
            overviewView.setVisibility(state);
        }
        headerBlock.setVisibility(visible || !title.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String string(String key) {
        String value = getIntent().getStringExtra(key);
        return value == null ? "" : value;
    }

    /** Every name worth asking jac.red about: what TMDB calls it, localised and original. */
    private List<String> queries() {
        List<String> queries = new ArrayList<>();
        for (String candidate : new String[]{query, title, original}) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            boolean seen = false;
            for (String existing : queries) {
                seen |= existing.equalsIgnoreCase(candidate.trim());
            }
            if (!seen) {
                queries.add(candidate.trim());
            }
        }
        return queries;
    }

    private void load() {
        found.clear();
        shown.clear();
        adapter.notifyDataSetChanged();
        status.setText(getString(R.string.status_searching, query));

        final String api = prefs.apiUrl();
        final List<String> queries = queries();
        final JacRed.Source endpoint = source;
        Ui.background(this, () -> JacRed.search(api, queries, endpoint), (results, error) -> {
            if (error != null) {
                status.setText(getString(R.string.status_search_failed, Ui.message(error)));
                return;
            }
            found.addAll(results);
            filters.collect(found);
            buildFacetBar();
            if (found.isEmpty()) {
                status.setText(getString(R.string.status_no_results, query));
                return;
            }
            applyFilters();
            list.requestFocus();
            list.setSelection(0);
            listFocusedAt = System.currentTimeMillis();
        });
    }

    /** Sort choices sit across the top, the active one highlighted. */
    private void buildSortBar() {
        sortBar.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        // jac.red's own site lets you choose the endpoint; the two return different data.
        Button endpoint = (Button) inflater.inflate(R.layout.chip_button, sortBar, false);
        endpoint.setText(getString(R.string.source_chip, source == JacRed.Source.JACKETT
                ? getString(R.string.source_jackett) : getString(R.string.source_native)));
        endpoint.setActivated(source == JacRed.Source.JACKETT);
        endpoint.setOnClickListener(view -> {
            source = source == JacRed.Source.NATIVE
                    ? JacRed.Source.JACKETT : JacRed.Source.NATIVE;
            prefs.set(Prefs.SOURCE, source.name());
            buildSortBar();
            load();
        });
        sortBar.addView(endpoint);

        for (int i = 0; i < SORT_LABELS.length; i++) {
            final int mode = i;
            Button chip = (Button) inflater.inflate(R.layout.chip_button, sortBar, false);
            chip.setText(SORT_LABELS[i]);
            chip.setActivated(filters.sort == i);
            chip.setOnClickListener(view -> {
                filters.sort = mode;
                buildSortBar();
                applyFilters();
            });
            sortBar.addView(chip);
        }
    }

    /** One chip per facet that has something to choose between; OK opens the options. */
    private void buildFacetBar() {
        facetBar.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (final Filters.Facet facet : filters.facets) {
            if (!facet.hasChoices()) {
                continue;
            }
            Button chip = (Button) inflater.inflate(R.layout.chip_button, facetBar, false);
            chip.setText(getString(R.string.facet_chip, getString(facet.labelRes),
                    facet.value().isEmpty() ? getString(R.string.facet_all) : facet.value()));
            chip.setActivated(!facet.value().isEmpty());
            chip.setOnClickListener(view -> pickFacet(facet));
            facetBar.addView(chip);
        }
    }

    private void pickFacet(final Filters.Facet facet) {
        TvDialog picker = TvDialog.create(this,
                getString(R.string.facet_pick, getString(facet.labelRes)), null);
        for (int i = 0; i < facet.options.size(); i++) {
            String option = facet.options.get(i);
            picker.add(option.isEmpty() ? getString(R.string.facet_all) : option, null,
                    i == facet.index);
        }
        picker.onPick(index -> {
            facet.index = index;
            buildFacetBar();
            applyFilters();
            return true;
        }).show();
    }

    /** Re-runs the facets over the last search, without touching the network. */
    private void applyFilters() {
        shown.clear();
        shown.addAll(filters.apply(found));
        adapter.notifyDataSetChanged();

        if (found.isEmpty()) {
            return;
        }
        if (shown.isEmpty()) {
            status.setText(R.string.status_filtered_empty);
        } else if (filters.isDefault()) {
            status.setText(getString(R.string.status_results, shown.size(), query));
        } else {
            status.setText(getString(R.string.status_results_filtered,
                    shown.size(), found.size(), query));
        }
    }

    private void open(Torrent torrent) {
        Intent intent = new Intent(this, DetailActivity.class);
        boolean fromTmdb = !poster.isEmpty() || !string(EXTRA_OVERVIEW).isEmpty();
        intent.putExtra(DetailActivity.EXTRA_TITLE,
                fromTmdb && !title.isEmpty() ? title : torrent.displayName());
        intent.putExtra(DetailActivity.EXTRA_MAGNET, torrent.magnet);
        intent.putExtra(DetailActivity.EXTRA_SUBTITLE, torrent.subtitle());
        intent.putExtra(DetailActivity.EXTRA_POSTER, poster);
        intent.putExtra(DetailActivity.EXTRA_RELEASE, torrent.title);
        startActivity(intent);
    }

    /** The views of one release row, found once when the row is inflated. */
    private static final class Row {
        final TextView title;
        final ImageView tracker;
        final TextView quality;
        final TextView size;
        final TextView seed;
        final TextView peer;
        final TextView voice;

        Row(View row) {
            title = row.findViewById(R.id.row_release);
            tracker = row.findViewById(R.id.row_tracker_badge);
            quality = row.findViewById(R.id.row_quality);
            size = row.findViewById(R.id.chip_size);
            seed = row.findViewById(R.id.chip_seed);
            peer = row.findViewById(R.id.chip_peer);
            voice = row.findViewById(R.id.chip_voice);
        }
    }

    private final class ReleasesAdapter extends BaseAdapter {
        /**
         * Chip colours, resolved once. A release list runs to hundreds of rows and the D-pad
         * rebinds them as it scrolls, so nothing in a bind may touch the resource table.
         */
        private final int colour4k = getColor(R.color.chip_quality_4k_text);
        private final int colour1080 = getColor(R.color.chip_quality_text);
        private final int colour720 = getColor(R.color.chip_seed_text);
        private final int colourLow = getColor(R.color.chip_date_text);

        @Override
        public int getCount() {
            return shown.size();
        }

        @Override
        public Torrent getItem(int position) {
            return shown.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            Row views;
            if (row == null) {
                row = LayoutInflater.from(ReleasesActivity.this)
                        .inflate(R.layout.item_release, parent, false);
                views = new Row(row);
                row.setTag(views);
            } else {
                views = (Row) row.getTag();
            }
            Torrent torrent = shown.get(position);

            views.title.setText(torrent.title);
            views.tracker.setImageResource(Trackers.icon(Trackers.first(torrent.tracker)));

            chip(views.quality, torrent.quality > 0
                    ? torrent.quality + "p" + (torrent.hdr ? " HDR" : "") : "");
            paintQuality(views.quality, torrent.quality);
            chip(views.size, torrent.sizeName);
            chip(views.seed, "↑ " + torrent.seeders);
            chip(views.peer, "↓ " + torrent.peers);
            chip(views.voice, torrent.voices.isEmpty() ? ""
                    : android.text.TextUtils.join(", ", torrent.voices));
            return row;
        }

        /** 4K gold, 1080p blue, 720p green, anything lower grey. */
        private void paintQuality(TextView chip, int quality) {
            int background = R.drawable.chip_date;
            int colour = colourLow;
            if (quality >= 2160) {
                background = R.drawable.chip_quality_4k;
                colour = colour4k;
            } else if (quality >= 1080) {
                background = R.drawable.chip_quality;
                colour = colour1080;
            } else if (quality >= 720) {
                background = R.drawable.chip_seed;
                colour = colour720;
            }
            // setBackgroundResource short-circuits when the row already carries this one.
            chip.setBackgroundResource(background);
            chip.setTextColor(colour);
        }

        private void chip(TextView chip, String text) {
            chip.setText(text);
            chip.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }
}
