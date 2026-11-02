package com.liquidglass.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Piccoli glifi bianchi: 0 torcia, 1 fotocamera, 2 lente di ricerca. */
class GlyphView extends View {
    private final int kind;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    GlyphView(Context c, int kind) {
        super(c);
        this.kind = kind;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        p.setColor(0xFFFFFFFF);
        if (kind == 0) {
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(w * .38f, h * .42f, w * .62f, h * .80f), w * .08f, w * .08f, p);
            Path t = new Path();
            t.moveTo(w * .32f, h * .20f);
            t.lineTo(w * .68f, h * .20f);
            t.lineTo(w * .62f, h * .42f);
            t.lineTo(w * .38f, h * .42f);
            t.close();
            c.drawPath(t, p);
        } else if (kind == 1) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w * .07f);
            c.drawRoundRect(new RectF(w * .2f, h * .32f, w * .8f, h * .7f), w * .08f, w * .08f, p);
            c.drawCircle(w * .5f, h * .51f, w * .12f, p);
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(new RectF(w * .38f, h * .24f, w * .62f, h * .31f), w * .03f, w * .03f, p);
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w * .1f);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawCircle(w * .44f, h * .44f, w * .24f, p);
            c.drawLine(w * .62f, h * .62f, w * .82f, h * .82f, p);
        }
    }
}
