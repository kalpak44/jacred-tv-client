package tv.jacred.client;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** jac.red address, TorrServer connection, TMDB artwork and the default video player. */
public class SettingsActivity extends Activity {
    private Prefs prefs;
    private EditText apiField;
    private EditText serverField;
    private EditText userField;
    private EditText passField;
    private EditText tmdbKeyField;
    private EditText tmdbApiField;
    private EditText tmdbImageField;
    private Button playerButton;
    private TextView testResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Ui.fullScreen(this);
        setContentView(R.layout.activity_settings);
        prefs = new Prefs(this);

        apiField = findViewById(R.id.api_field);
        serverField = findViewById(R.id.server_field);
        userField = findViewById(R.id.user_field);
        passField = findViewById(R.id.pass_field);
        tmdbKeyField = findViewById(R.id.tmdb_key_field);
        tmdbApiField = findViewById(R.id.tmdb_api_field);
        tmdbImageField = findViewById(R.id.tmdb_image_field);
        playerButton = findViewById(R.id.player_button);
        testResult = findViewById(R.id.test_result);
        ((TextView) findViewById(R.id.version_label)).setText(
                getString(R.string.settings_version, Updates.installedVersion(this)));

        apiField.setText(prefs.apiUrl());
        serverField.setText(prefs.torrServerUrl());
        userField.setText(prefs.torrUser());
        passField.setText(prefs.torrPass());
        tmdbKeyField.setText(prefs.tmdbKey());
        tmdbApiField.setText(prefs.tmdbApiUrl());
        tmdbImageField.setText(prefs.tmdbImageUrl());
        updatePlayerButton();

        // While the leanback keyboard is up it swallows the D-pad, so fields never open it by
        // themselves: focus moves freely and OK starts editing.
        for (EditText field : new EditText[]{apiField, serverField, userField, passField,
                tmdbKeyField, tmdbApiField, tmdbImageField}) {
            typeOnOk(field);
        }

        playerButton.setOnClickListener(view -> pickPlayer());
        ((Button) findViewById(R.id.test_button)).setOnClickListener(view -> testConnection());
        ((Button) findViewById(R.id.import_button)).setOnClickListener(view -> importFromServer());
        ((Button) findViewById(R.id.mirror_button)).setOnClickListener(view ->
                tmdbImageField.setText(Prefs.MIRROR_TMDB_IMAGES));
        ((Button) findViewById(R.id.update_button)).setOnClickListener(view -> checkUpdates());
        ((Button) findViewById(R.id.clear_history_button)).setOnClickListener(view -> {
            new History(prefs).clear();
            Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
        });
        ((Button) findViewById(R.id.save_button)).setOnClickListener(view -> {
            save();
            finish();
        });

        apiField.requestFocus();
    }

    private void typeOnOk(final EditText field) {
        field.setShowSoftInputOnFocus(false);
        // A focused EditText consumes up and down for its own cursor before the framework
        // looks for a neighbour, which strands the remote on the first field. Every other
        // field on this screen is reached through here, so the search is done explicitly.
        field.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            int direction;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                direction = View.FOCUS_DOWN;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                direction = View.FOCUS_UP;
            } else {
                return false;
            }
            View next = view.focusSearch(direction);
            return next != null && next.requestFocus(direction);
        });
        field.setOnClickListener(view -> {
            InputMethodManager manager = getSystemService(InputMethodManager.class);
            if (manager != null) {
                manager.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    private void save() {
        prefs.set(Prefs.API, apiField.getText().toString());
        prefs.set(Prefs.TORRSERVER, serverField.getText().toString());
        prefs.set(Prefs.TORR_USER, userField.getText().toString());
        prefs.set(Prefs.TORR_PASS, passField.getText().toString());
        prefs.set(Prefs.TMDB_KEY, tmdbKeyField.getText().toString());
        prefs.set(Prefs.TMDB_API, tmdbApiField.getText().toString());
        prefs.set(Prefs.TMDB_IMAGES, tmdbImageField.getText().toString());
    }

    private void testConnection() {
        save();
        final TorrServer server = new TorrServer(prefs);
        if (!server.isConfigured()) {
            testResult.setText(R.string.test_no_address);
            return;
        }
        testResult.setText(getString(R.string.test_running, server.address()));
        Ui.background(this, server::echo, (version, error) -> {
            if (error != null) {
                testResult.setText(getString(R.string.test_failed, Ui.message(error)));
            } else {
                testResult.setText(getString(R.string.test_ok,
                        version.isEmpty() ? "?" : version));
            }
        });
    }

    /**
     * Asks GitHub for the newest release regardless of when the home screen last looked,
     * because someone pressing this wants an answer now rather than a cached one.
     */
    private void checkUpdates() {
        testResult.setText(R.string.update_checking);
        Ui.background(this, Updates::latest, (release, error) -> {
            if (error != null) {
                testResult.setText(getString(R.string.update_check_failed, Ui.message(error)));
                return;
            }
            Updates.remember(prefs, release);
            if (Updates.step(Updates.installedVersion(this), release.version)
                    == Updates.Step.NONE) {
                testResult.setText(getString(R.string.update_current, release.version));
                return;
            }
            if (!release.hasApk()) {
                testResult.setText(getString(R.string.update_no_asset, release.version));
                return;
            }
            UpdatePrompt.offer(this, release, testResult::setText);
        });
    }

    /** TorrServer already stores a TMDB key and mirror URLs; reuse them when they are set. */
    private void importFromServer() {
        save();
        final TorrServer server = new TorrServer(prefs);
        if (!server.isConfigured()) {
            testResult.setText(R.string.test_no_address);
            return;
        }
        Ui.background(this, server::tmdbSettings, (settings, error) -> {
            if (error != null) {
                testResult.setText(getString(R.string.test_failed, Ui.message(error)));
                return;
            }
            String key = value(settings, "APIKey", "apikey");
            String api = value(settings, "APIURL", "apiurl");
            String image = value(settings, "ImageURL", "imageURL");
            if (!key.isEmpty()) {
                tmdbKeyField.setText(key);
            }
            if (!api.isEmpty()) {
                tmdbApiField.setText(api);
            }
            if (!image.isEmpty()) {
                tmdbImageField.setText(image);
            }
            testResult.setText(key.isEmpty() ? getString(R.string.import_no_key)
                    : getString(R.string.import_ok));
        });
    }

    private static String value(JSONObject json, String... keys) {
        for (String key : keys) {
            String found = json.optString(key, "").trim();
            if (!found.isEmpty()) {
                return found;
            }
        }
        return "";
    }

    private void pickPlayer() {
        List<Players.Player> players = Players.installed(this);
        final List<String> packages = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        packages.add("");
        labels.add(getString(R.string.player_always_ask));
        for (Players.Player player : players) {
            packages.add(player.packageName);
            labels.add(player.label);
        }

        TvDialog picker = TvDialog.create(this, getString(R.string.settings_player),
                getString(R.string.settings_player_hint));
        for (int i = 0; i < labels.size(); i++) {
            picker.add(labels.get(i), packages.get(i), null);
        }
        picker.onPick(index -> {
            prefs.set(Prefs.PLAYER, packages.get(index));
            prefs.set(Prefs.PLAYER_LABEL, index == 0 ? "" : labels.get(index));
            updatePlayerButton();
            return true;
        }).show();
    }

    private void updatePlayerButton() {
        String label = prefs.playerLabel();
        playerButton.setText(label.isEmpty() ? getString(R.string.player_always_ask) : label);
    }
}
