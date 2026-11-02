package com.liquidglass.launcher;

import android.content.Context;

final class U {
    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
    static int a(int alpha, int rgb) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }
}
