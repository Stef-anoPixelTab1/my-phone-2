package com.liquidglass.launcher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.format.DateFormat;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import java.util.Calendar;

/**
 * Orologio della schermata di blocco in stile iOS 26: cifre "di vetro" che si allungano
 * e si restringono (trascinando in verticale), con peso, carattere, colore e stile regolabili.
 */
class ClockView extends View {
    static final String[] FONTS = {"sans-serif-thin", "sans-serif-light", "sans-serif",
            "sans-serif-medium", "sans-serif-condensed", "sans-serif-black"};
    static final String[] FONT_NAMES = {"Sottile", "Leggero", "Normale", "Medio", "Condensato", "Nero"};
    static final int[] COLORS = {0xFFFFFFFF, 0xFFFFD1E8, 0xFFBFE3FF, 0xFFE1D0FF, 0xFFFFDDB8, 0xFFCFFADF, 0xFFFFF3B0};
    static final String[] COLOR_NAMES = {"Bianco", "Rosa", "Cielo", "Lilla", "Pesca", "Menta", "Limone"};
    static final float MIN_SY = 0.55f;

    private final SharedPreferences sp;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect r = new Rect();
    float weight, sy, maxSy = 3f, shrink = 0f, intro = 1f;
    int font, color;
    boolean stacked;
    Runnable onChange;
    private String l1 = "9:41", l2 = null;
    private float drawnH = 0;

    ClockView(Context c, SharedPreferences sp) {
        super(c);
        this.sp = sp;
        weight = sp.getFloat("w", 0.35f);
        sy = sp.getFloat("sy", 1f);
        font = sp.getInt("f", 3);
        color = sp.getInt("c", 0);
        stacked = sp.getBoolean("st", false);
        updateTime();
    }

    void save() {
        sp.edit().putFloat("w", weight).putFloat("sy", sy).putInt("f", font)
                .putInt("c", color).putBoolean("st", stacked).apply();
    }

    void updateTime() {
        Calendar c = Calendar.getInstance();
        boolean h24 = DateFormat.is24HourFormat(getContext());
        int h = c.get(h24 ? Calendar.HOUR_OF_DAY : Calendar.HOUR);
        if (!h24 && h == 0) h = 12;
        int m = c.get(Calendar.MINUTE);
        String mm = (m < 10 ? "0" : "") + m;
        if (stacked) {
            l1 = (h24 && h < 10 ? "0" : "") + h;
            l2 = mm;
        } else {
            l1 = h + ":" + mm;
            l2 = null;
        }
        invalidate();
    }

    void setStacked(boolean s) { stacked = s; updateTime(); }

    void setShrink(float s) { shrink = s; invalidate(); }

    void setStretch(float v) {
        sy = Math.max(MIN_SY, Math.min(maxSy, v));
        invalidate();
        if (onChange != null) onChange.run();
    }

    /** Altezza della cifra a sy=1, per convertire il trascinamento in allungamento. */
    float baseHeight() { return drawnH / Math.max(0.01f, effSy()); }

    private float effSy() {
        return sy * (1f - 0.5f * shrink) * (0.55f + 0.45f * intro);
    }

    boolean hit(float yLocal) { return yLocal >= 0 && yLocal <= drawnH + U.dp(getContext(), 24); }

    /** Animazione d'ingresso: l'orologio si allunga con un leggero rimbalzo. */
    void playIntro() {
        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(750);
        a.setInterpolator(new OvershootInterpolator(1.5f));
        a.addUpdateListener(v -> { intro = (float) v.getAnimatedValue(); invalidate(); });
        a.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        int W = getWidth(), H = getHeight();
        if (W == 0 || l1 == null) return;
        p.setTypeface(Typeface.create(FONTS[font], Typeface.NORMAL));
        p.setTextAlign(Paint.Align.CENTER);

        float targetW = stacked ? 0.60f * W : 0.92f * W;
        p.setTextSize(100f);
        float m = p.measureText(stacked ? "00" : l1);
        float size = 100f * targetW / Math.max(1f, m);
        p.setTextSize(size);
        p.getTextBounds("0", 0, 1, r);
        float sw = weight * size * 0.08f;
        float dh = r.height() + sw;
        float gap = dh * 0.14f;
        float totalBase = stacked ? 2 * dh + gap : dh;
        maxSy = Math.max(MIN_SY + 0.2f, (H - U.dp(getContext(), 8)) / totalBase);
        if (sy > maxSy) sy = maxSy;
        float e = effSy();
        drawnH = totalBase * e;

        float scx = 1f - 0.22f * shrink;
        int lines = stacked ? 2 : 1;
        int col = COLORS[color];
        for (int i = 0; i < lines; i++) {
            String t = i == 0 ? l1 : l2;
            if (t == null) continue;
            c.save();
            c.translate(W / 2f, i * (dh + gap) * e);
            c.scale(scx, e);
            float base = -r.top + sw / 2f;
            // corpo di vetro
            p.setStyle(Paint.Style.FILL_AND_STROKE);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(sw);
            p.setShader(new LinearGradient(0, 0, 0, dh,
                    U.a(0xF2, col), U.a(0xB0, col), Shader.TileMode.CLAMP));
            p.setShadowLayer(U.dp(getContext(), 14), 0, U.dp(getContext(), 5), 0x40000000);
            p.setAlpha((int) (255 * (1f - 0.5f * shrink)));
            c.drawText(t, 0, base, p);
            // bordo luminoso
            p.clearShadowLayer();
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sw + U.dp(getContext(), 1.6f));
            p.setColor(0x78FFFFFF);
            c.drawText(t, 0, base, p);
            c.restore();
        }
        p.setAlpha(255);
    }
}
