package dev.lumen.tv;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

/** Accessible source links next to the forecast in Settings, including a no-browser fallback for TVs. */
final class Sources {
    static void show(Context context) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        link(text, "Weather data by Open-Meteo", "https://open-meteo.com/", context);
        text.append("\nForecast data are displayed in the selected units.\n");
        link(text, "CC BY 4.0 license", "https://creativecommons.org/licenses/by/4.0/", context);
        text.append("\n\nLocation search uses GeoNames through Open-Meteo.\n");
        link(text, "GeoNames", "https://www.geonames.org/", context);
        text.append(" · ");
        link(text, "CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/", context);
        text.append("\n\nNASA photo titles and creator credits are shown on the image. Display crops and shading are applied.\n");
        link(text, "NASA media guidelines", "https://www.nasa.gov/nasa-brand-center/images-and-media/", context);
        TextView view = new TextView(context);
        view.setText(text); view.setTextSize(18); view.setTextColor(Ui.TEXT); view.setLinkTextColor(0xff9dcaff);
        view.setMovementMethod(LinkMovementMethod.getInstance()); view.setFocusable(true);
        int padding = Math.round(24 * context.getResources().getDisplayMetrics().density);
        view.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(context); scroll.addView(view);
        new AlertDialog.Builder(context).setTitle("Data sources & licenses").setView(scroll)
                .setPositiveButton("Done", null).show();
    }

    private static void link(SpannableStringBuilder text, String label, String url, Context context) {
        int start = text.length(); text.append(label);
        text.setSpan(new ClickableSpan() {
            @Override public void onClick(View view) {
                try { context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
                catch (ActivityNotFoundException exception) {
                    new AlertDialog.Builder(context).setTitle(label).setMessage(url).setPositiveButton("Done", null).show();
                }
            }
        }, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    private Sources() {}
}
