package dev.lumen.tv;

import android.graphics.Bitmap;

/**
 * Blur for the glass. Lumen never blurs per frame: it blurs a small copy of something static (the scene, a
 * snapshot) once and stretches it. Three box-blur passes look like a Gaussian and run in a few ms on TV chips.
 */
final class Blur {
    private Blur() {}

    /** A blurred, downscaled copy: `width` px wide, blur radius in that small bitmap's pixels. */
    static Bitmap blurred(Bitmap src, int width, int radius) {
        int height = Math.max(1, Math.round(src.getHeight() * (width / (float) src.getWidth())));
        Bitmap small = Bitmap.createScaledBitmap(src, width, height, true);
        if (!small.isMutable() || small == src) small = small.copy(Bitmap.Config.ARGB_8888, true);
        boxBlur(small, radius, 3);
        return small;
    }

    /** In-place separable box blur, repeated `passes` times. */
    static void boxBlur(Bitmap b, int radius, int passes) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < passes; i++) {
            pass(px, tmp, w, h, radius, true);
            pass(tmp, px, w, h, radius, false);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h, div = r * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int a = 0, rr = 0, g = 0, bb = 0;
            for (int i = -r; i <= r; i++) {
                int c = in[index(line, Math.max(0, Math.min(len - 1, i)), w, horizontal)];
                a += c >>> 24; rr += (c >> 16) & 255; g += (c >> 8) & 255; bb += c & 255;
            }
            for (int i = 0; i < len; i++) {
                out[index(line, i, w, horizontal)] = ((a / div) << 24) | ((rr / div) << 16) | ((g / div) << 8) | (bb / div);
                int add = in[index(line, Math.min(len - 1, i + r + 1), w, horizontal)];
                int sub = in[index(line, Math.max(0, i - r), w, horizontal)];
                a += (add >>> 24) - (sub >>> 24);
                rr += ((add >> 16) & 255) - ((sub >> 16) & 255);
                g += ((add >> 8) & 255) - ((sub >> 8) & 255);
                bb += (add & 255) - (sub & 255);
            }
        }
    }

    private static int index(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }
}
