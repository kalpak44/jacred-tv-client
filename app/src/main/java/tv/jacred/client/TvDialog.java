package tv.jacred.client;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * A modal styled like the rest of the app: a dark card of big, focusable rows that the remote
 * walks with up/down. Rows can carry a tick, so the same modal works for choosing something
 * and for toggling what has been watched.
 */
final class TvDialog {
    interface OnPick {
        /** @return true to close the modal, false to keep it open for another press. */
        boolean onPick(int index);
    }

    private final Activity activity;
    private final String title;
    private final String subtitle;
    private final List<String> labels = new ArrayList<>();
    private final List<String> details = new ArrayList<>();
    private final List<Boolean> ticks = new ArrayList<>();
    private final List<View> rows = new ArrayList<>();
    private OnPick listener;
    private Dialog dialog;

    private TvDialog(Activity activity, String title, String subtitle) {
        this.activity = activity;
        this.title = title;
        this.subtitle = subtitle;
    }

    static TvDialog create(Activity activity, String title, String subtitle) {
        return new TvDialog(activity, title, subtitle);
    }

    /** @param tick null for a plain row, true/false to show a watched tick. */
    TvDialog add(String label, String detail, Boolean tick) {
        labels.add(label);
        details.add(detail == null ? "" : detail);
        ticks.add(tick);
        return this;
    }

    TvDialog onPick(OnPick listener) {
        this.listener = listener;
        return this;
    }

    void setTick(int index, boolean tick) {
        ticks.set(index, tick);
        bind(rows.get(index), index);
    }

    void show() {
        dialog = new Dialog(activity, android.R.style.Theme_Material_NoActionBar_Fullscreen);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#C4061018")));
        }
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_tv, null);
        dialog.setContentView(root);

        ((TextView) root.findViewById(R.id.dialog_title)).setText(title);
        TextView subtitleView = root.findViewById(R.id.dialog_subtitle);
        if (subtitle == null || subtitle.isEmpty()) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setText(subtitle);
        }

        LinearLayout list = root.findViewById(R.id.dialog_rows);
        LayoutInflater inflater = LayoutInflater.from(activity);
        for (int i = 0; i < labels.size(); i++) {
            final int index = i;
            View row = inflater.inflate(R.layout.dialog_row, list, false);
            bind(row, i);
            row.setOnClickListener(view -> {
                if (listener == null || listener.onPick(index)) {
                    dialog.dismiss();
                }
            });
            list.addView(row);
            rows.add(row);
        }
        dialog.show();
        if (!rows.isEmpty()) {
            rows.get(0).requestFocus();
        }
    }

    private void bind(View row, int index) {
        ((TextView) row.findViewById(R.id.row_label)).setText(labels.get(index));

        TextView detail = row.findViewById(R.id.row_detail);
        if (details.get(index).isEmpty()) {
            detail.setVisibility(View.GONE);
        } else {
            detail.setVisibility(View.VISIBLE);
            detail.setText(details.get(index));
        }

        TextView tick = row.findViewById(R.id.row_tick);
        Boolean checked = ticks.get(index);
        if (checked == null) {
            tick.setVisibility(View.GONE);
        } else {
            tick.setVisibility(View.VISIBLE);
            tick.setText(checked ? "✓" : "○");
            tick.setTextColor(activity.getColor(checked ? R.color.mint : R.color.text_muted));
        }
    }
}
