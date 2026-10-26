package dev.lumen.tv;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

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

    /** Menu icons, stroked in the paint's current color, centered on (cx, cy) in a box of size `size`. */
    static void menu(Canvas c, String name, float cx, float cy, float size, Paint stroke) {
        float h = size / 2;
        stroke.setShader(null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(size * .075f);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        Path p = new Path();
        switch (name) {
            case "move":
                p.moveTo(cx - h * .8f, cy); p.lineTo(cx + h * .8f, cy);
                p.moveTo(cx - h * .45f, cy - h * .35f); p.lineTo(cx - h * .8f, cy); p.lineTo(cx - h * .45f, cy + h * .35f);
                p.moveTo(cx + h * .45f, cy - h * .35f); p.lineTo(cx + h * .8f, cy); p.lineTo(cx + h * .45f, cy + h * .35f);
                break;
            case "add":
                p.moveTo(cx - h * .6f, cy); p.lineTo(cx + h * .6f, cy);
                p.moveTo(cx, cy - h * .6f); p.lineTo(cx, cy + h * .6f);
                break;
            case "remove":
                p.moveTo(cx - h * .6f, cy); p.lineTo(cx + h * .6f, cy);
                break;
            case "shelf":
                p.addRoundRect(cx - h * .8f, cy - h * .65f, cx + h * .8f, cy + h * .25f, h * .15f, h * .15f, Path.Direction.CW);
                p.moveTo(cx - h * .45f, cy + h * .65f); p.lineTo(cx + h * .45f, cy + h * .65f);
                break;
            case "hide":
                p.moveTo(cx - h * .85f, cy);
                p.quadTo(cx, cy - h * .9f, cx + h * .85f, cy);
                p.quadTo(cx, cy + h * .9f, cx - h * .85f, cy);
                p.addCircle(cx, cy, h * .25f, Path.Direction.CW);
                p.moveTo(cx - h * .7f, cy - h * .7f); p.lineTo(cx + h * .7f, cy + h * .7f);
                break;
            case "info":
                p.addCircle(cx, cy, h * .8f, Path.Direction.CW);
                p.moveTo(cx, cy - h * .05f); p.lineTo(cx, cy + h * .4f);
                p.moveTo(cx, cy - h * .38f); p.lineTo(cx, cy - h * .36f);
                break;
            default: {  // gear: a cog outline, eight tapered teeth on a round body, with a hole in the middle
                float root = h * .64f, tip = h * .86f;
                RectF body = new RectF(cx - root, cy - root, cx + root, cy + root);
                for (int i = 0; i < 8; i++) {
                    float a = i * 45 - 90;  // degrees, first tooth at the top
                    point(p, cx, cy, root, a - 14, i == 0);
                    point(p, cx, cy, tip, a - 9, false);
                    point(p, cx, cy, tip, a + 9, false);
                    point(p, cx, cy, root, a + 14, false);
                    p.arcTo(body, a + 14, 17);  // the body between this tooth and the next
                }
                p.close();
                p.addCircle(cx, cy, h * .27f, Path.Direction.CW);
                break;
            }
        }
        c.drawPath(p, stroke);
    }

    /** Moves or draws to the point at radius r and angle deg (clockwise from +x) around (cx, cy). */
    private static void point(Path p, float cx, float cy, float r, float deg, boolean move) {
        double a = Math.toRadians(deg);
        float x = cx + r * (float) Math.cos(a), y = cy + r * (float) Math.sin(a);
        if (move) p.moveTo(x, y); else p.lineTo(x, y);
    }
}
