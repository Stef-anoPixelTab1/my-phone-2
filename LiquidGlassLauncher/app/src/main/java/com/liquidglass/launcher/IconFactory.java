package com.liquidglass.launcher;

import android.graphics.AdaptiveIconDrawable;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Icone in stile iOS 26: squircle, gradiente, riflesso di vetro e bordo luminoso. */
final class IconFactory {
    static final int PHONE = 0, MSG = 1, CAM = 2, SET = 3, CLOCK = 4, CALC = 5, WEB = 6, MAIL = 7, PHOTO = 8;

    static int kind(String pkg) {
        String p = pkg.toLowerCase();
        if (p.contains("dialer") || p.endsWith(".phone")) return PHONE;
        if (p.contains("messag") || p.contains(".mms")) return MSG;
        if (p.contains("camera")) return CAM;
        if (p.contains("settings") && !p.contains("gms")) return SET;
        if (p.contains("clock")) return CLOCK;
        if (p.contains("calculator")) return CALC;
        if (p.contains("chrome") || p.contains("browser") || p.contains("firefox")) return WEB;
        if (p.endsWith(".gm") || p.contains("email") || p.contains("mail")) return MAIL;
        if (p.contains("photos") || p.contains("gallery")) return PHOTO;
        return -1;
    }

    static Path squircle(float s) {
        Path p = new Path();
        float a = s / 2f;
        for (int i = 0; i <= 120; i++) {
            double t = i * Math.PI * 2 / 120;
            double ct = Math.cos(t), st = Math.sin(t);
            float x = (float) (a + a * Math.signum(ct) * Math.pow(Math.abs(ct), 0.4));
            float y = (float) (a + a * Math.signum(st) * Math.pow(Math.abs(st), 0.4));
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.close();
        return p;
    }

    static Bitmap build(int kind, Drawable d, int s) {
        Bitmap b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Path sq = squircle(s);
        int sc = c.saveLayer(0, 0, s, s, null);

        if (kind >= 0) drawGlyphIcon(c, kind, s);
        else drawAppIcon(c, d, s);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        // riflesso speculare in alto a sinistra
        p.setShader(new RadialGradient(s * 0.25f, -s * 0.05f, s * 0.95f, 0x70FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, s, s, p);
        // ombra morbida in basso
        p.setShader(new LinearGradient(0, s * 0.55f, 0, s, 0x00000000, 0x28000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, s, s, p);

        // maschera squircle antialiasing
        p.setShader(null);
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        p.setColor(0xFF000000);
        c.drawPath(sq, p);
        c.restoreToCount(sc);

        // bordo luminoso
        float sw = s * 0.026f;
        Path inner = new Path();
        Matrix m = new Matrix();
        float k = 1f - 2f * (sw / 2f) / s;
        m.setScale(k, k, s / 2f, s / 2f);
        sq.transform(m, inner);
        Paint r = new Paint(Paint.ANTI_ALIAS_FLAG);
        r.setStyle(Paint.Style.STROKE);
        r.setStrokeWidth(sw);
        r.setShader(new LinearGradient(0, 0, s, s,
                new int[]{0xF0FFFFFF, 0x22FFFFFF, 0x22FFFFFF, 0xA0FFFFFF},
                new float[]{0f, .4f, .6f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(inner, r);
        return b;
    }

    private static void drawAppIcon(Canvas c, Drawable d, int s) {
        if (d instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable ad = (AdaptiveIconDrawable) d;
            int o = s / 4;
            Drawable bg = ad.getBackground(), fg = ad.getForeground();
            if (bg != null) { bg.setBounds(-o, -o, s + o, s + o); bg.draw(c); }
            if (fg != null) { fg.setBounds(-o, -o, s + o, s + o); fg.draw(c); }
        } else {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setShader(new LinearGradient(0, 0, 0, s, 0xFFFFFFFF, 0xFFE4E4EC, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, s, s, p);
            int i = Math.round(s * 0.15f);
            d.setBounds(i, i, s - i, s - i);
            d.draw(c);
        }
    }

    private static void bg(Canvas c, int s, int top, int bottom) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, s, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, s, s, p);
    }

    private static void drawGlyphIcon(Canvas c, int kind, int s) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float cx = s / 2f, cy = s / 2f;
        switch (kind) {
            case PHONE: {
                bg(c, s, 0xFF6BF57F, 0xFF12B52E);
                p.setColor(0xFFFFFFFF);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * 0.11f);
                p.setStrokeCap(Paint.Cap.ROUND);
                float ox = s * .68f, oy = s * .32f, r = s * .34f;
                c.drawArc(new RectF(ox - r, oy - r, ox + r, oy + r), 90, 90, false, p);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(s * .34f, s * .32f, s * .09f, p);
                c.drawCircle(s * .68f, s * .66f, s * .09f, p);
                break;
            }
            case MSG: {
                bg(c, s, 0xFF6BF56E, 0xFF1DB93A);
                p.setColor(0xFFFFFFFF);
                c.drawOval(new RectF(s * .17f, s * .2f, s * .83f, s * .68f), p);
                Path t = new Path();
                t.moveTo(s * .27f, s * .56f);
                t.lineTo(s * .2f, s * .8f);
                t.lineTo(s * .47f, s * .64f);
                t.close();
                c.drawPath(t, p);
                break;
            }
            case CAM: {
                bg(c, s, 0xFFEDEDF1, 0xFF9B9BA3);
                p.setColor(0xFF2C2C2E);
                c.drawRoundRect(new RectF(s * .14f, s * .3f, s * .86f, s * .76f), s * .08f, s * .08f, p);
                c.drawRoundRect(new RectF(s * .36f, s * .22f, s * .64f, s * .34f), s * .04f, s * .04f, p);
                p.setColor(0xFF555559);
                c.drawCircle(cx, s * .53f, s * .19f, p);
                p.setColor(0xFF0B0B0D);
                c.drawCircle(cx, s * .53f, s * .14f, p);
                p.setColor(0x66FFFFFF);
                c.drawCircle(cx - s * .04f, s * .49f, s * .04f, p);
                break;
            }
            case SET: {
                bg(c, s, 0xFFD2D2D8, 0xFF8A8A92);
                p.setColor(0xFF4A4A50);
                for (int i = 0; i < 8; i++) {
                    c.save();
                    c.rotate(i * 45, cx, cy);
                    c.drawRoundRect(new RectF(cx - s * .055f, cy - s * .37f, cx + s * .055f, cy - s * .22f), s * .02f, s * .02f, p);
                    c.restore();
                }
                c.drawCircle(cx, cy, s * .27f, p);
                p.setColor(0xFFB4B4BA);
                c.drawCircle(cx, cy, s * .11f, p);
                break;
            }
            case CLOCK: {
                bg(c, s, 0xFF2E2E32, 0xFF000000);
                p.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, s * .38f, p);
                p.setColor(0xFF1C1C1E);
                for (int i = 0; i < 12; i++) {
                    c.save();
                    c.rotate(i * 30, cx, cy);
                    c.drawRect(cx - s * .008f, cy - s * .35f, cx + s * .008f, cy - s * .31f, p);
                    c.restore();
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(s * .03f);
                c.drawLine(cx, cy, cx, cy - s * .2f, p);
                c.drawLine(cx, cy, cx + s * .14f, cy + s * .04f, p);
                p.setColor(0xFFFF9500);
                p.setStrokeWidth(s * .014f);
                c.drawLine(cx - s * .04f, cy + s * .08f, cx + s * .05f, cy - s * .27f, p);
                break;
            }
            case CALC: {
                bg(c, s, 0xFF4A4A4C, 0xFF1C1C1E);
                float g = s * .05f, bw = (s * .64f - g) / 2f, x0 = s * .18f, y0 = s * .18f;
                for (int i = 0; i < 2; i++) {
                    for (int j = 0; j < 2; j++) {
                        p.setColor(i == 0 && j == 1 ? 0xFFFF9F0A : 0xFF7A7A80);
                        float x = x0 + j * (bw + g), y = y0 + i * (bw + g);
                        c.drawRoundRect(new RectF(x, y, x + bw, y + bw), s * .08f, s * .08f, p);
                    }
                }
                p.setColor(0xFFFF9F0A);
                float y = y0 + 2 * (bw + g);
                c.drawRoundRect(new RectF(x0, y, x0 + s * .64f, y + bw * .55f), s * .08f, s * .08f, p);
                break;
            }
            case WEB: {
                bg(c, s, 0xFF63D2FF, 0xFF0A6CFF);
                p.setColor(0xFFFFFFFF);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s * .035f);
                c.drawCircle(cx, cy, s * .36f, p);
                p.setStyle(Paint.Style.FILL);
                c.save();
                c.rotate(45, cx, cy);
                Path n = new Path();
                n.moveTo(cx, cy - s * .27f);
                n.lineTo(cx - s * .07f, cy);
                n.lineTo(cx + s * .07f, cy);
                n.close();
                p.setColor(0xFFFF3B30);
                c.drawPath(n, p);
                Path q = new Path();
                q.moveTo(cx, cy + s * .27f);
                q.lineTo(cx - s * .07f, cy);
                q.lineTo(cx + s * .07f, cy);
                q.close();
                p.setColor(0xFFFFFFFF);
                c.drawPath(q, p);
                c.restore();
                break;
            }
            case MAIL: {
                bg(c, s, 0xFF63D2FF, 0xFF1E6BFF);
                p.setColor(0xFFFFFFFF);
                c.drawRoundRect(new RectF(s * .16f, s * .27f, s * .84f, s * .73f), s * .06f, s * .06f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setStrokeWidth(s * .03f);
                p.setColor(0xFF2F7BFF);
                Path v = new Path();
                v.moveTo(s * .2f, s * .33f);
                v.lineTo(cx, s * .56f);
                v.lineTo(s * .8f, s * .33f);
                c.drawPath(v, p);
                break;
            }
            default: {
                bg(c, s, 0xFFFFFFFF, 0xFFF0F0F5);
                int[] cols = {0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF30B0C7, 0xFF007AFF, 0xFF5856D6, 0xFFFF2D55, 0xFFFF3B30};
                for (int i = 0; i < 8; i++) {
                    p.setColor(U.a(190, cols[i]));
                    c.save();
                    c.rotate(i * 45, cx, cy);
                    c.drawCircle(cx, cy - s * .17f, s * .14f, p);
                    c.restore();
                }
            }
        }
    }
}
