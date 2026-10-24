package tv.jacred.client;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Sends one magnet to TorrServer, shows its files alongside TMDB artwork, and plays them. */
public class DetailActivity extends Activity {
    // Resources are resolved when the context is built, so the language has to be applied
    // here rather than in onCreate.
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Locales.wrap(base));
    }

    static final String EXTRA_TITLE = "title";
    static final String EXTRA_MAGNET = "magnet";
    static final String EXTRA_SUBTITLE = "subtitle";
    static final String EXTRA_POSTER = "poster";
    static final String EXTRA_RELEASE = "release";

    /** How often the screen refreshes TorrServer's transfer figures. */
    private static final long STATS_INTERVAL_MS = 1500L;

    /** Torrent metadata arrives from the swarm, so this can take a while — or never come. */
    private static final int POLL_ATTEMPTS = 40;
    private static final long POLL_DELAY_MS = 1500L;

    private Prefs prefs;
    private TorrServer server;
    private History history;

    private String title;
    private String magnet;
    private String hash = "";
    private String posterUrl = "";

    private Button keepButton;
    /** Whether TorrServer is holding this torrent, which is what the Keep button offers. */
    private boolean onServer;

    private TextView status;
    private TextView stats;
    private boolean watchingStats;
    private ListView fileList;
    private View actionRow;
    private ImageView poster;

    private int videos;
    private final List<TorrFile> files = new ArrayList<>();
    private final List<Integer> viewed = new ArrayList<>();
    private FilesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.fullScreen(this);
        setContentView(R.layout.activity_detail);

        prefs = new Prefs(this);
        server = new TorrServer(prefs);
        history = new History(prefs);
        ImageLoader.init(this);

        title = string(EXTRA_TITLE);
        magnet = string(EXTRA_MAGNET);
        // The poster travels with the intent: the previous screen already looked it up.
        posterUrl = string(EXTRA_POSTER);

        ((TextView) findViewById(R.id.detail_title)).setText(title);
        ((TextView) findViewById(R.id.detail_subtitle)).setText(string(EXTRA_SUBTITLE));
        TextView release = findViewById(R.id.detail_overview);
        if (string(EXTRA_RELEASE).isEmpty()) {
            release.setVisibility(View.GONE);
        } else {
            release.setVisibility(View.VISIBLE);
            release.setText(string(EXTRA_RELEASE));
        }
        status = findViewById(R.id.detail_status);
        stats = findViewById(R.id.detail_stats);
        poster = findViewById(R.id.detail_poster);
        fileList = findViewById(R.id.file_list);
        actionRow = findViewById(R.id.action_row);

        adapter = new FilesAdapter();
        fileList.setAdapter(adapter);
        // Artwork and the release name are for picking a torrent, not for walking its files:
        // they fold away while the list has focus so the files get the whole screen.
        fileList.setOnFocusChangeListener((view, focused) -> showArtwork(!focused));
        // A tap of OK plays the file; holding OK flips its watched mark instead.
        fileList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                play(files.get(position));
            }
        });
        fileList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                                           long id) {
                fileMenu(position);
                return true;
            }
        });

        findViewById(R.id.watch_button).setOnClickListener(view -> watchBestFile());
        keepButton = findViewById(R.id.keep_button);
        keepButton.setOnClickListener(view -> toggleOnServer());
        findViewById(R.id.magnet_button).setOnClickListener(view -> openMagnetExternally());

        if (posterUrl.isEmpty()) {
            poster.setVisibility(View.GONE);
        } else {
            ImageLoader.load(poster, posterUrl, R.drawable.poster_placeholder);
        }

        if (!server.isConfigured()) {
            status.setText(R.string.status_no_torrserver);
            findViewById(R.id.magnet_button).requestFocus();
            return;
        }
        addToServer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        watchingStats = true;
        pollStats();
    }

    @Override
    protected void onPause() {
        super.onPause();
        watchingStats = false;
    }

    /**
     * Shows what TorrServer is doing with this torrent — speed, peers and how much of the
     * preload buffer is filled — refreshed while the screen is in front.
     */
    private void pollStats() {
        if (!watchingStats || hash.isEmpty()) {
            return;
        }
        final String torrentHash = hash;
        Ui.background(this, () -> server.get(torrentHash), (state, error) -> {
            if (error == null && state != null) {
                stats.setText(describe(state));
            }
            if (watchingStats) {
                Ui.post(this::pollStats, STATS_INTERVAL_MS);
            }
        });
    }

    private String describe(TorrStatus state) {
        if (!state.hasFiles()) {
            return state.statString.isEmpty() ? getString(R.string.status_connecting)
                    : state.statString;
        }
        StringBuilder text = new StringBuilder();
        if (state.downloadSpeed > 0) {
            text.append(getString(R.string.stats_speed,
                    Torrent.humanSize((long) state.downloadSpeed)));
        }
        int peers = state.activePeers > 0 ? state.activePeers : state.connectedSeeders;
        if (peers > 0) {
            append(text, getString(R.string.stats_peers, peers));
        }
        if (state.preloadedBytes > 0) {
            append(text, state.preloadSize > 0
                    ? getString(R.string.stats_buffer_of,
                            Torrent.humanSize(state.preloadedBytes),
                            Torrent.humanSize(state.preloadSize))
                    : getString(R.string.stats_buffer, Torrent.humanSize(state.preloadedBytes)));
        }
        if (text.length() == 0) {
            append(text, state.statString.isEmpty() ? getString(R.string.stats_idle)
                    : state.statString);
        }
        return text.toString();
    }

    private static void append(StringBuilder text, String part) {
        if (text.length() > 0) {
            text.append("   ·   ");
        }
        text.append(part);
    }

    /** Folds the poster, release name and hint away, keeping status and transfer figures. */
    private void showArtwork(boolean visible) {
        int state = visible ? View.VISIBLE : View.GONE;
        if (!posterUrl.isEmpty()) {
            poster.setVisibility(state);
        }
        findViewById(R.id.detail_hint).setVisibility(state);
        TextView release = findViewById(R.id.detail_overview);
        if (!string(EXTRA_RELEASE).isEmpty()) {
            release.setVisibility(state);
        }
    }

    private String string(String key) {
        String value = getIntent().getStringExtra(key);
        return value == null ? "" : value;
    }

    /** What opening the screen finds out: whether the torrent was there, and its hash. */
    private static final class Opened {
        final boolean alreadyThere;
        final String hash;

        Opened(boolean alreadyThere, String hash) {
            this.alreadyThere = alreadyThere;
            this.hash = hash;
        }
    }

    private void addToServer() {
        status.setText(getString(R.string.status_adding, server.address()));
        Ui.background(this, () -> {
            // Asked before adding: our own add would make the answer yes either way.
            boolean alreadyThere = server.holds(JacRed.infoHash(magnet));
            return new Opened(alreadyThere, server.add(magnet, title, posterUrl, false));
        }, (opened, error) -> {
            if (error != null) {
                status.setText(getString(R.string.status_add_failed, Ui.message(error)));
                actionRow.setVisibility(View.VISIBLE);
                findViewById(R.id.magnet_button).requestFocus();
                return;
            }
            hash = opened.hash;
            onServer = opened.alreadyThere;
            updateKeepButton();
            poll(POLL_ATTEMPTS);
        });
    }

    /** One button for both directions: keep what is not held, drop what is. */
    private void updateKeepButton() {
        keepButton.setText(onServer ? R.string.remove_from_server : R.string.keep_on_server);
    }

    /**
     * Keeps the torrent or drops it, whichever the button is offering. Keeping re-adds the
     * same magnet with save_to_db so it survives in the TorrServer library; dropping hands
     * the hash back. Playing still works either way — every launch re-adds the magnet.
     */
    private void toggleOnServer() {
        if (onServer) {
            final String torrentHash = hash;
            onServer = false;
            updateKeepButton();
            Ui.background(this, () -> {
                server.remove(torrentHash);
                return null;
            }, (ignored, error) ->
                    Toast.makeText(this, R.string.status_removed, Toast.LENGTH_SHORT).show());
            return;
        }
        Ui.background(this, () -> server.add(magnet, title, posterUrl, true), (kept, error) -> {
            if (error != null) {
                Toast.makeText(this, getString(R.string.status_keep_failed, Ui.message(error)),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            onServer = true;
            updateKeepButton();
            Toast.makeText(this, R.string.status_kept, Toast.LENGTH_SHORT).show();
        });
    }

    private void poll(final int attemptsLeft) {
        Ui.background(this, () -> server.get(hash), (state, error) -> {
            if (error != null) {
                status.setText(getString(R.string.status_files_failed, Ui.message(error)));
                return;
            }
            if (state.hasFiles()) {
                showFiles(state);
                return;
            }
            if (attemptsLeft <= 1) {
                status.setText(R.string.status_metadata_timeout);
                actionRow.setVisibility(View.VISIBLE);
                return;
            }
            // stat_string is TorrServer's own wording, e.g. "Torrent getting info".
            status.setText(getString(R.string.status_waiting,
                    state.statString.isEmpty() ? getString(R.string.status_connecting)
                            : state.statString));
            Ui.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    poll(attemptsLeft - 1);
                }
            }, POLL_DELAY_MS);
        });
    }

    private void showFiles(TorrStatus state) {
        files.clear();
        // Playable files first so the remote lands on something useful.
        for (TorrFile file : state.files) {
            if (file.isVideo()) {
                files.add(file);
            }
        }
        int videoCount = files.size();
        for (TorrFile file : state.files) {
            if (!file.isVideo()) {
                files.add(file);
            }
        }
        adapter.notifyDataSetChanged();
        videos = videoCount;
        showStatus();
        actionRow.setVisibility(View.VISIBLE);
        findViewById(R.id.watch_button).setVisibility(View.VISIBLE);
        findViewById(R.id.keep_button).setVisibility(View.VISIBLE);
        findViewById(R.id.watch_button).requestFocus();
        fileList.setSelection(0);

        final String torrentHash = hash;
        Ui.background(this, () -> server.viewed(torrentHash), (marks, error) -> {
            if (error == null && marks != null && !marks.isEmpty()) {
                viewed.clear();
                viewed.addAll(marks);
                adapter.notifyDataSetChanged();
                showStatus();
            }
        });
    }

    private void play(final TorrFile file) {
        String preferred = prefs.playerPackage();
        if (!preferred.isEmpty()) {
            launch(file, preferred);
            return;
        }

        final List<Players.Player> players = Players.installed(this);
        if (players.isEmpty()) {
            launch(file, "");
            return;
        }
        TvDialog picker = TvDialog.create(this, getString(R.string.dialog_pick_player),
                file.name());
        for (Players.Player player : players) {
            picker.add(player.label, player.packageName, null);
        }
        picker.onPick(index -> {
            launch(file, players.get(index).packageName);
            return true;
        }).show();
    }

    /**
     * Hands one file to a player, re-adding the magnet first.
     *
     * <p>TorrServer drops idle torrents (this server is set to 30 seconds) and the app adds
     * them without saving to the database, so by the time a file is chosen the hash can be
     * gone — /stream then answers 404 and the player opens on a blank screen. Adding is
     * idempotent and returns the same hash, so this simply revives the torrent.
     */
    private void launch(final TorrFile file, final String packageName) {
        status.setText(R.string.status_opening);
        Ui.background(this, () -> server.add(magnet, title, posterUrl, false),
                (freshHash, error) -> {
                    if (error != null) {
                        status.setText(getString(R.string.status_add_failed, Ui.message(error)));
                        return;
                    }
                    hash = freshHash;
                    try {
                        Players.play(this, server.streamUrl(hash, file), title, packageName,
                                "video/*");
                    } catch (Exception e) {
                        Toast.makeText(this, getString(R.string.status_play_failed,
                                Ui.message(e)), Toast.LENGTH_LONG).show();
                        return;
                    }
                    history.remember(new History.Entry(title, magnet, posterUrl, file.name(),
                            System.currentTimeMillis()));
                    markWatched(file.id);
                    showStatus();
                });
    }

    /** Plays the largest video file — the feature, in all but the oddest torrents. */
    private void watchBestFile() {
        TorrFile best = null;
        for (TorrFile file : files) {
            if (file.isVideo() && (best == null || file.length > best.length)) {
                best = file;
            }
        }
        if (best == null) {
            Toast.makeText(this, R.string.status_nothing_playable, Toast.LENGTH_SHORT).show();
            return;
        }
        play(best);
    }

    /** Held OK on a file: play it, continue from it, or change its watched mark. */
    private void fileMenu(final int position) {
        final TorrFile file = files.get(position);
        final boolean watched = viewed.contains(file.id);

        TvDialog menu = TvDialog.create(this, file.name(), Torrent.humanSize(file.length));
        menu.add(getString(R.string.file_menu_play), null, null);
        menu.add(getString(watched ? R.string.file_menu_unmark : R.string.file_menu_mark),
                null, watched);

        menu.onPick(index -> {
            if (index == 0) {
                play(file);
            } else {
                toggleWatched(position);
            }
            return true;
        }).show();
    }


    /** Records a file as watched locally and on the server once playback starts. */
    private void markWatched(final int fileIndex) {
        if (!viewed.contains(fileIndex)) {
            viewed.add(fileIndex);
            adapter.notifyDataSetChanged();
        }
        final String torrentHash = hash;
        Ui.background(this, () -> {
            server.markViewed(torrentHash, fileIndex);
            return null;
        }, (ignored, error) -> {
        });
    }

    /** Flips one file's watched mark, locally first so the tick answers the press at once. */
    private void toggleWatched(int position) {
        TorrFile file = files.get(position);
        final boolean watched = !viewed.contains(file.id);
        if (watched) {
            viewed.add(file.id);
        } else {
            viewed.remove(Integer.valueOf(file.id));
        }
        adapter.notifyDataSetChanged();
        showStatus();
        Toast.makeText(this, getString(watched ? R.string.toast_marked
                : R.string.toast_unmarked, file.name()), Toast.LENGTH_SHORT).show();

        final int fileIndex = file.id;
        final String torrentHash = hash;
        Ui.background(this, () -> {
            server.setViewed(torrentHash, fileIndex, watched);
            return null;
        }, (ignored, error) -> {
        });
    }

    /** Status line doubles as the watched counter once marks come back from the server. */
    private void showStatus() {
        int watched = 0;
        for (TorrFile file : files) {
            if (viewed.contains(file.id)) {
                watched++;
            }
        }
        status.setText(watched == 0
                ? getString(R.string.status_files, videos, files.size())
                : getString(R.string.status_files_watched, videos, files.size(), watched));
        ((TextView) findViewById(R.id.detail_hint)).setText(R.string.hold_for_options);
    }

    private void openMagnetExternally() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(magnet)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.status_no_magnet_handler, Toast.LENGTH_LONG).show();
        }
    }

    /** The views of one file row, found once when the row is inflated. */
    private static final class FileRow {
        final TextView name;
        final TextView meta;

        FileRow(View row) {
            name = row.findViewById(R.id.file_name);
            meta = row.findViewById(R.id.file_meta);
        }
    }

    private final class FilesAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return files.size();
        }

        @Override
        public TorrFile getItem(int position) {
            return files.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            FileRow views;
            if (row == null) {
                row = LayoutInflater.from(DetailActivity.this)
                        .inflate(R.layout.item_file, parent, false);
                views = new FileRow(row);
                row.setTag(views);
            } else {
                views = (FileRow) row.getTag();
            }
            TorrFile file = files.get(position);
            StringBuilder meta = new StringBuilder(Torrent.humanSize(file.length));
            if (!file.isVideo()) {
                append(meta, getString(R.string.file_not_video));
            }
            boolean watched = viewed.contains(file.id);
            if (watched) {
                append(meta, getString(R.string.file_watched));
            }
            views.name.setText(watched
                    ? getString(R.string.file_name_watched, file.name()) : file.name());
            views.meta.setText(meta.toString());
            return row;
        }

        private void append(StringBuilder text, String part) {
            if (text.length() > 0) {
                text.append("  ·  ");
            }
            text.append(part);
        }
    }
}
