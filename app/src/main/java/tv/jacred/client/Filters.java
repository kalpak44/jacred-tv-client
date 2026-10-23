package tv.jacred.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The narrowing controls above the release list.
 *
 * <p>Every choice is built from the releases that came back rather than from a fixed list, so
 * the options always match what is actually on offer, and filtering happens in memory — the
 * public jac.red host rate-limits repeated queries, and instant switches feel better on a
 * remote anyway.
 */
final class Filters {
    /** The values one release carries for a facet; empty means it cannot match. */
    interface Values {
        List<String> of(Torrent torrent);
    }

    static final class Facet {
        final int labelRes;
        final Values values;
        final boolean numeric;
        final List<String> options = new ArrayList<>();
        int index;

        Facet(int labelRes, boolean numeric, Values values) {
            this.labelRes = labelRes;
            this.numeric = numeric;
            this.values = values;
        }

        String value() {
            return index > 0 && index < options.size() ? options.get(index) : "";
        }

        boolean hasChoices() {
            return options.size() > 1;
        }
    }

    static final int SORT_SEEDERS = 0;
    static final int SORT_SIZE = 1;
    static final int SORT_ADDED = 2;
    static final int SORT_UPDATED = 3;

    final List<Facet> facets = new ArrayList<>();
    int sort = SORT_SEEDERS;

    Filters() {
        facets.add(new Facet(R.string.facet_type, false, t -> t.types));
        facets.add(new Facet(R.string.facet_tracker, false, Torrent::trackers));
        facets.add(new Facet(R.string.facet_voice, false, t -> t.voices));
        facets.add(new Facet(R.string.facet_video, false,
                t -> one(t.videoType.isEmpty() ? "" : (t.hdr ? "HDR" : "SDR"))));
        facets.add(new Facet(R.string.facet_codec, false, t -> one(t.codecLabel())));
        facets.add(new Facet(R.string.facet_quality, true,
                t -> one(t.quality > 0 ? t.quality + "p" : "")));
        facets.add(new Facet(R.string.facet_year, true,
                t -> one(t.year > 0 ? String.valueOf(t.year) : "")));
        facets.add(new Facet(R.string.facet_season, true, t -> t.seasons));
    }

    private static List<String> one(String value) {
        List<String> values = new ArrayList<>();
        if (value != null && !value.isEmpty()) {
            values.add(value);
        }
        return values;
    }

    /** Rebuilds every option list from a fresh result set, keeping picks that still exist. */
    void collect(List<Torrent> torrents) {
        for (Facet facet : facets) {
            String current = facet.value();
            facet.options.clear();
            facet.options.add("");
            for (Torrent torrent : torrents) {
                for (String value : facet.values.of(torrent)) {
                    if (!value.isEmpty() && !containsIgnoreCase(facet.options, value)) {
                        facet.options.add(value);
                    }
                }
            }
            sortOptions(facet);

            facet.index = 0;
            for (int i = 0; i < facet.options.size(); i++) {
                if (facet.options.get(i).equalsIgnoreCase(current)) {
                    facet.index = i;
                }
            }
        }
    }

    private static boolean containsIgnoreCase(List<String> values, String value) {
        for (String known : values) {
            if (known.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /** Numbers read best high to low; names alphabetically. "All" stays first either way. */
    private static void sortOptions(Facet facet) {
        List<String> tail = new ArrayList<>(facet.options.subList(1, facet.options.size()));
        Collections.sort(tail, facet.numeric
                ? (a, b) -> Integer.compare(number(b), number(a))
                : String.CASE_INSENSITIVE_ORDER);
        facet.options.subList(1, facet.options.size()).clear();
        facet.options.addAll(tail);
    }

    private static int number(String value) {
        StringBuilder digits = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            }
        }
        try {
            return Integer.parseInt(digits.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    boolean isDefault() {
        for (Facet facet : facets) {
            if (!facet.value().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    List<Torrent> apply(List<Torrent> all) {
        List<Torrent> kept = new ArrayList<>();
        for (Torrent torrent : all) {
            if (matches(torrent)) {
                kept.add(torrent);
            }
        }
        sort(kept);
        return kept;
    }

    private boolean matches(Torrent torrent) {
        for (Facet facet : facets) {
            String wanted = facet.value();
            if (!wanted.isEmpty() && !containsIgnoreCase(facet.values.of(torrent), wanted)) {
                return false;
            }
        }
        return true;
    }

    private void sort(List<Torrent> torrents) {
        Comparator<Torrent> comparator;
        if (sort == SORT_SIZE) {
            comparator = (a, b) -> Long.compare(b.size, a.size);
        } else if (sort == SORT_ADDED) {
            comparator = (a, b) -> Long.compare(b.published, a.published);
        } else if (sort == SORT_UPDATED) {
            comparator = (a, b) -> Long.compare(b.updated, a.updated);
        } else {
            comparator = (a, b) -> Integer.compare(b.seeders, a.seeders);
        }
        Collections.sort(torrents, comparator);
    }
}
