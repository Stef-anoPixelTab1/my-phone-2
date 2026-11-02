package com.liquidglass.launcher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;

import java.io.File;

/** Sfondo nitido + versione sfocata: il "vetro" disegna la porzione sfocata sotto di sé. */
final class Backdrop {
    static Bitmap sharp, blur;
    static int w, h;

    static void reset() { sharp = null; blur = null; }

    static void build(Context c, int W, int H) {
        if (sharp != null && w == W && h == H) return;
        w = W; h = H;
        sharp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(sharp);
        Bitmap src = null;
        File f = new File(c.getFilesDir(), "wall.jpg");
        if (f.exists()) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getPath(), o);
                int s = 1;
                while (o.outWidth / (s * 2) >= W && o.outHeight / (s * 2) >= H) s *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = s;
                src = BitmapFactory.decodeFile(f.getPath(), o);
            } catch (Throwable t) { src = null; }
        }
        if (src != null) {
            float sc = Math.max(W / (float) src.getWidth(), H / (float) src.getHeight());
            float dx = (W - src.getWidth() * sc) / 2f, dy = (H - src.getHeight() * sc) / 2f;
            Matrix m = new Matrix();
            m.postScale(sc, sc);
            m.postTranslate(dx, dy);
            cv.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
            src.recycle();
        } else {
            defaultWall(cv, W, H);
        }
        blur = makeBlur(sharp);
    }

    static void defaultWall(Canvas c, int W, int H) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, W, H,
                new int[]{0xFF3A6BFF, 0xFF7B4DFF, 0xFFD9549B}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        blob(c, W * .15f, H * .22f, W * .75f, 0xFF00D2FF, 200);
        blob(c, W * .90f, H * .50f, W * .70f, 0xFFFF7AB8, 210);
        blob(c, W * .25f, H * .88f, W * .80f, 0xFFFFB04D, 190);
        blob(c, W * .80f, H * .08f, W * .50f, 0xFF9B7BFF, 170);
    }

    private static void blob(Canvas c, float x, float y, float r, int col, int alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(x, y, r, U.a(alpha, col), U.a(0, col), Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    private static Bitmap makeBlur(Bitmap s) {
        int sw = Math.max(8, s.getWidth() / 10), sh = Math.max(8, s.getHeight() / 10);
        Bitmap scaled = Bitmap.createScaledBitmap(s, sw, sh, true);
        int[] px = new int[sw * sh];
        scaled.getPixels(px, 0, sw, 0, 0, sw, sh);
        int[] tmp = new int[px.length];
        for (int i = 0; i < 3; i++) {
            pass(px, tmp, sw, sh, 4, true);
            pass(tmp, px, sw, sh, 4, false);
        }
        Bitmap small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        small.setPixels(px, 0, sw, 0, 0, sw, sh);

        Bitmap out = Bitmap.createBitmap(s.getWidth(), s.getHeight(), Bitmap.Config.ARGB_8888);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(1.6f);
        ColorMatrix br = new ColorMatrix(new float[]{
                1.1f, 0, 0, 0, 12,
                0, 1.1f, 0, 0, 12,
                0, 0, 1.1f, 0, 12,
                0, 0, 0, 1, 0});
        cm.postConcat(br);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        new Canvas(out).drawBitmap(small, null, new Rect(0, 0, out.getWidth(), out.getHeight()), p);
        return out;
    }

    private static void pass(int[] in, int[] out, int w, int h, int r, boolean horiz) {
        int n = 2 * r + 1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int sr = 0, sg = 0, sb = 0;
                for (int k = -r; k <= r; k++) {
                    int xx = horiz ? Math.min(w - 1, Math.max(0, x + k)) : x;
                    int yy = horiz ? y : Math.min(h - 1, Math.max(0, y + k));
                    int c = in[yy * w + xx];
                    sr += (c >> 16) & 255;
                    sg += (c >> 8) & 255;
                    sb += c & 255;
                }
                out[y * w + x] = 0xFF000000 | ((sr / n) << 16) | ((sg / n) << 8) | (sb / n);
            }
        }
    }
}
