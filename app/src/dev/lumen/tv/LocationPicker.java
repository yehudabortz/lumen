package dev.lumen.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Native TV keyboard and D-pad result list; coordinates stay in private on-device preferences. */
final class LocationPicker {
    private final Activity activity;
    private final Prefs prefs;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String query = "";

    LocationPicker(Activity activity, Prefs prefs, Runnable changed) {
        this.activity = activity;
        this.prefs = prefs;
        this.changed = changed;
    }

    void show() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = Math.round(24 * density);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad / 2, pad, 0);
        TextView status = new TextView(activity);
        Weather.Place current = prefs.city();
        status.setText(current == null ? "Using an approximate city from the TV’s time zone."
                : "Current location: " + current.label);
        content.addView(status);
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setHint("City or postal code");
        input.setText(query);
        content.addView(input);
        TextView source = new TextView(activity);
        source.setText("Search: Open-Meteo / GeoNames. Only your search is sent; no device location permission.");
        source.setTextSize(14);
        content.addView(source);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Weather location")
                .setView(content)
                .setPositiveButton("Search", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Use time zone", (d, which) -> { prefs.city(null); changed.run(); })
                .create();
        dialog.setOnShowListener(ignored -> {
            Runnable search = () -> {
                query = input.getText().toString().trim();
                if (query.length() < 2) { status.setText("Enter at least two characters."); return; }
                if (!dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled()) return;
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                status.setText("Searching…");
                final String submitted = query;
                new Thread(() -> {
                    try {
                        List<Weather.Place> places = Weather.search(submitted);
                        main.post(() -> {
                            if (!active(dialog)) return;
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            if (places.isEmpty()) { status.setText("No matches. Try a nearby city or add a country code."); return; }
                            dialog.dismiss();
                            choose(places);
                        });
                    } catch (Exception e) {
                        main.post(() -> {
                            if (!active(dialog)) return;
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            status.setText("Couldn’t search. Check the connection and try again.");
                        });
                    }
                }, "city-search").start();
            };
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> search.run());
            input.setOnEditorActionListener((v, action, event) -> {
                if (action == EditorInfo.IME_ACTION_SEARCH) { search.run(); return true; }
                return false;
            });
        });
        dialog.show();
        input.requestFocus();
    }

    private boolean active(AlertDialog dialog) {
        return dialog.isShowing() && !activity.isFinishing() && !activity.isDestroyed();
    }

    private void choose(List<Weather.Place> places) {
        String[] labels = new String[places.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = places.get(i).label;
        new AlertDialog.Builder(activity)
                .setTitle("Choose your location")
                .setItems(labels, (d, i) -> { prefs.city(places.get(i)); changed.run(); })
                .setNegativeButton("Search again", (d, i) -> show())
                .show();
    }
}
