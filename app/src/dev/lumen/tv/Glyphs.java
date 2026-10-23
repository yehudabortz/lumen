package dev.lumen.tv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/** Small weather glyphs drawn with paths, centered on (cx, cy) with half-size `r`. */
final class Glyphs {
    private static final int SUN = Color.parseColor("#ffd34d"), MOON = Color.parseColor("#dfe6ff"),
            CLOUD = Color.parseColor("#e8eef8"), RAIN = Color.parseColor("#8fc7ff");

    private Glyphs() {}

    static void weather(Canvas c, String kind, float cx, float cy, float r, Paint fill, Paint stroke) {
        if (kind == null) return;
        fill.setShader(null);
        stroke.setShader(null);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        switch (kind) {
            case "sun": sun(c, cx, cy, r, fill, stroke); break;
            case "moon": moon(c, cx, cy, r, fill); break;
            case "partly": sun(c, cx - r * .3f, cy - r * .3f, r * .7f, fill, stroke); cloud(c, cx + r * .15f, cy + r * .3f, r * .75f, fill); break;
            case "fog":
                stroke.setColor(CLOUD);
                stroke.setStrokeWidth(r * .16f);
                for (int i = -1; i <= 1; i++) c.drawLine(cx - r * .8f + (i == 0 ? r * .2f : 0), cy + i * r * .45f, cx + r * .8f, cy + i * r * .45f, stroke);
                break;
            case "rain": case "storm": case "snow":
                cloud(c, cx, cy - r * .2f, r * .85f, fill);
                if ("snow".equals(kind)) {
                    fill.setColor(Color.WHITE);
                    for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * r * .45f, cy + r * .75f, r * .1f, fill);
                } else if ("storm".equals(kind)) {
                    Path bolt = new Path();
                    bolt.moveTo(cx + r * .1f, cy + r * .25f); bolt.lineTo(cx - r * .2f, cy + r * .7f); bolt.lineTo(cx + r * .05f, cy + r * .7f);
                    bolt.lineTo(cx - r * .1f, cy + r * 1.05f); bolt.lineTo(cx + r * .3f, cy + r * .55f); bolt.lineTo(cx + r * .05f, cy + r * .55f); bolt.close();
                    fill.setColor(SUN);
                    c.drawPath(bolt, fill);
                } else {
                    stroke.setColor(RAIN);
                    stroke.setStrokeWidth(r * .14f);
                    for (int i = -1; i <= 1; i++) c.drawLine(cx + i * r * .45f, cy + r * .5f, cx + i * r * .45f - r * .15f, cy + r * .9f, stroke);
                }
                break;
            default: cloud(c, cx, cy, r, fill); break;
        }
    }

    private static void sun(Canvas c, float cx, float cy, float r, Paint fill, Paint stroke) {
        fill.setColor(SUN);
        c.drawCircle(cx, cy, r * .42f, fill);
        stroke.setColor(SUN);
        stroke.setStrokeWidth(r * .15f);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            float cs = (float) Math.cos(a), sn = (float) Math.sin(a);
            c.drawLine(cx + cs * r * .66f, cy + sn * r * .66f, cx + cs * r * .9f, cy + sn * r * .9f, stroke);
        }
    }

    private static void moon(Canvas c, float cx, float cy, float r, Paint fill) {
        Path moon = new Path(), bite = new Path();
        moon.addCircle(cx, cy, r * .78f, Path.Direction.CW);
        bite.addCircle(cx + r * .42f, cy - r * .32f, r * .66f, Path.Direction.CW);
        moon.op(bite, Path.Op.DIFFERENCE);
        fill.setColor(MOON);
        c.drawPath(moon, fill);
    }

    private static void cloud(Canvas c, float cx, float cy, float r, Paint fill) {
        fill.setColor(CLOUD);
        c.drawCircle(cx - r * .35f, cy + r * .12f, r * .42f, fill);
        c.drawCircle(cx + r * .12f, cy - r * .12f, r * .55f, fill);
        c.drawRoundRect(cx - r * .8f, cy + r * .05f, cx + r * .8f, cy + r * .55f, r * .25f, r * .25f, fill);
    }
}
