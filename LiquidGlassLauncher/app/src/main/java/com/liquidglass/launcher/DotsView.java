package com.liquidglass.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

class DotsView extends View {
    private int count = 1;
    private float pos = 0;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    DotsView(Context c) {
        super(c);
        p.setColor(0xFFFFFFFF);
    }

    void set(int count, float pos) {
        this.count = count;
        this.pos = pos;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float gap = U.dp(getContext(), 14), r = U.dp(getContext(), 3.3f);
        float x0 = getWidth() / 2f - (count - 1) * gap / 2f, y = getHeight() / 2f;
        for (int i = 0; i < count; i++) {
            float d = Math.min(1f, Math.abs(i - pos));
            p.setAlpha((int) (255 - d * 150));
            c.drawCircle(x0 + i * gap, y, r, p);
        }
    }
}
