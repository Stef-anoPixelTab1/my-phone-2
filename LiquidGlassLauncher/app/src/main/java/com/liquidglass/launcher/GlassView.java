package com.liquidglass.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.FrameLayout;

/** Pannello "Liquid Glass": sfondo sfocato + tinta + riflesso speculare + bordo luminoso. */
class GlassView extends FrameLayout {
    private final float radius;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];

    GlassView(Context c, float radiusDp) {
        super(c);
        radius = U.dp(c, radiusDp);
        setWillNotDraw(false);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(U.dp(c, 1.4f));
        rim2.setStyle(Paint.Style.STROKE);
        rim2.setStrokeWidth(U.dp(c, 4f));
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        fill.setShader(new LinearGradient(0, 0, 0, h, 0x4CFFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP));
        glow.setShader(new RadialGradient(w * 0.18f, 0, Math.max(w, h) * 0.95f,
                0x66FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, w, h,
                new int[]{0xF2FFFFFF, 0x1AFFFFFF, 0x1AFFFFFF, 0xB8FFFFFF},
                new float[]{0f, 0.38f, 0.62f, 1f}, Shader.TileMode.CLAMP));
        rim2.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x30FFFFFF, 0x00FFFFFF, 0x24FFFFFF}, null, Shader.TileMode.CLAMP));
        path.reset();
        rect.set(0, 0, w, h);
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        c.save();
        c.clipPath(path);
        if (Backdrop.blur != null) {
            getLocationOnScreen(loc);
            c.drawBitmap(Backdrop.blur, -loc[0], -loc[1], bg);
        }
        c.drawRect(0, 0, w, h, fill);
        c.drawRect(0, 0, w, h, glow);
        c.restore();

        float in2 = rim2.getStrokeWidth() / 2f;
        RectF r2 = new RectF(in2, in2, w - in2, h - in2);
        c.drawRoundRect(r2, radius - in2, radius - in2, rim2);
        float in = rim.getStrokeWidth() / 2f;
        RectF r1 = new RectF(in, in, w - in, h - in);
        c.drawRoundRect(r1, radius - in, radius - in, rim);
    }
}
