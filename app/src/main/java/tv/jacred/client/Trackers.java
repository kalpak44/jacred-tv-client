package tv.jacred.client;

import java.util.Locale;

/**
 * Tracker artwork. The icons are jac.red's own set, taken from its repository
 * (web/public/img/ico, served live at /img/ico) and converted to PNG, because Android
 * cannot decode .ico. Most of them are 16x16 favicons at source, so they are drawn small
 * inside the badge rather than blown up to fill it.
 *
 * <p>The mapping is a switch rather than a name lookup on purpose: resource shrinking can
 * see every icon that is reachable, and an unknown tracker lands on the neutral default
 * instead of a missing-resource crash.
 */
final class Trackers {
    private Trackers() {
    }

    /** The icon jac.red shows for this tracker, or its neutral default. */
    static int icon(String tracker) {
        switch (tracker.trim().toLowerCase(Locale.US)) {
            case "anibelka":
                return R.drawable.tracker_anibelka;
            case "anidub":
                return R.drawable.tracker_anidub;
            case "anifilm":
                return R.drawable.tracker_anifilm;
            case "aniliberty":
                return R.drawable.tracker_aniliberty;
            case "anilibria":
                return R.drawable.tracker_anilibria;
            case "animelayer":
                return R.drawable.tracker_animelayer;
            case "anistar":
                return R.drawable.tracker_anistar;
            case "baibako":
                return R.drawable.tracker_baibako;
            case "bitru":
                return R.drawable.tracker_bitru;
            case "hdrezka":
                return R.drawable.tracker_hdrezka;
            case "kinozal":
                return R.drawable.tracker_kinozal;
            case "knaben":
                return R.drawable.tracker_knaben;
            case "korsars":
                return R.drawable.tracker_korsars;
            case "leproduction":
                return R.drawable.tracker_leproduction;
            case "lostfilm":
                return R.drawable.tracker_lostfilm;
            case "mazepa":
                return R.drawable.tracker_mazepa;
            case "megapeer":
                return R.drawable.tracker_megapeer;
            case "nnmclub":
                return R.drawable.tracker_nnmclub;
            case "rudub":
                return R.drawable.tracker_rudub;
            case "rutor":
                return R.drawable.tracker_rutor;
            case "rutracker":
                return R.drawable.tracker_rutracker;
            case "selezen":
                return R.drawable.tracker_selezen;
            case "subsplease":
                return R.drawable.tracker_subsplease;
            case "toloka":
                return R.drawable.tracker_toloka;
            case "torrentby":
                return R.drawable.tracker_torrentby;
            case "ultradox":
                return R.drawable.tracker_ultradox;
            case "underverse":
                return R.drawable.tracker_underverse;
            case "viruseproject":
                return R.drawable.tracker_viruseproject;
            default:
                return R.drawable.tracker_default;
        }
    }

    /** A release can sit on several trackers; the badge shows the first. */
    static String first(String tracker) {
        int comma = tracker.indexOf(',');
        return comma < 0 ? tracker : tracker.substring(0, comma);
    }
}
