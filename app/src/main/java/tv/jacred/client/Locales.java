package tv.jacred.client;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * The interface language, when the television's own is not the one wanted.
 *
 * <p>Applied by wrapping each activity's base context, because resources are resolved
 * once when the context is built: setting a locale any later leaves the screen already
 * on screen in the old language.
 */
final class Locales {
    /** Stored when the app should follow the television instead of overriding it. */
    static final String SYSTEM = "";

    static final String ENGLISH = "en";
    static final String RUSSIAN = "ru";

    private Locales() {
    }

    /** @return {@code base} itself when no override is stored, so the system locale wins. */
    static Context wrap(Context base) {
        String tag = new Prefs(base).language();
        if (tag.isEmpty()) {
            return base;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(tag));
        return base.createConfigurationContext(config);
    }
}
