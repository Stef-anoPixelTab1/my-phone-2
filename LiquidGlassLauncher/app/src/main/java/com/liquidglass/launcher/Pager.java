package com.liquidglass.launcher;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.widget.HorizontalScrollView;

/** Scorrimento orizzontale a pagine con scatto sulla pagina più vicina. */
class Pager extends HorizontalScrollView {
    int pageW, pages = 1;
    private VelocityTracker vt;

    Pager(Context c) {
        super(c);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (vt == null) vt = VelocityTracker.obtain();
        vt.addMovement(e);
        boolean r = super.onTouchEvent(e);
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
            vt.computeCurrentVelocity(1000);
            float v = vt.getXVelocity();
            vt.recycle();
            vt = null;
            snap(v);
        }
        return r;
    }

    @Override
    public void fling(int velocityX) { /* gestito da snap() */ }

    void snap(float vx) {
        if (pageW <= 0) return;
        float cur = getScrollX() / (float) pageW;
        int t = Math.round(cur);
        if (vx < -700) t = (int) Math.floor(cur) + 1;
        else if (vx > 700) t = (int) Math.ceil(cur) - 1;
        t = Math.max(0, Math.min(pages - 1, t));
        smoothScrollTo(t * pageW, 0);
    }

    int currentPage() {
        return pageW > 0 ? Math.round(getScrollX() / (float) pageW) : 0;
    }
}
