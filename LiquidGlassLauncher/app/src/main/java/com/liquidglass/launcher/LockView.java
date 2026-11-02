package com.liquidglass.launcher;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Schermata di blocco interna al launcher (un launcher non può sostituire quella di sistema). */
class LockView extends FrameLayout {
    interface Host {
        void onUnlocked();
        void behindProgress(float p);
    }

    private abstract static class Seek implements SeekBar.OnSeekBarChangeListener {
        abstract void on(int v);
        public void onProgressChanged(SeekBar s, int v, boolean user) { if (user) on(v); }
        public void onStartTrackingTouch(SeekBar s) { }
        public void onStopTrackingTouch(SeekBar s) { }
    }

    private final Host host;
    private final ClockView clock;
    private final TextView date, hint;
    private final GlassView torch, cam, panel;
    private SeekBar heightBar;
    private final Handler handler = new Handler();
    private boolean editing, torchOn, moved, hasHost = true;
    private int mode;
    private float startY, startSy, progress;
    private final int bot;
    private final Runnable longPress = new Runnable() {
        @Override public void run() {
            if (!moved && !editing) { mode = 0; startEdit(); }
        }
    };

    LockView(Context ctx, SharedPreferences sp, int top, int bot, int screenH, Host host) {
        super(ctx);
        this.host = host;
        this.bot = bot;

        date = new TextView(ctx);
        date.setTextColor(0xE6FFFFFF);
        date.setTextSize(20);
        date.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        date.setGravity(Gravity.CENTER);
        date.setShadowLayer(8, 0, 2, 0x55000000);
        LayoutParams dl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.topMargin = top + U.dp(ctx, 56);
        addView(date, dl);

        clock = new ClockView(ctx, sp);
        LayoutParams cl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (screenH * 0.58f));
        cl.topMargin = top + U.dp(ctx, 94);
        addView(clock, cl);

        hint = new TextView(ctx);
        hint.setText("Scorri verso l'alto per sbloccare");
        hint.setTextColor(0xCCFFFFFF);
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        LayoutParams hl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        hl.bottomMargin = bot + U.dp(ctx, 14);
        addView(hint, hl);

        int bs = U.dp(ctx, 54);
        torch = new GlassView(ctx, 27);
        torch.addView(new GlyphView(ctx, 0), new LayoutParams(bs, bs));
        torch.setOnClickListener(v -> toggleTorch());
        LayoutParams tl = new LayoutParams(bs, bs, Gravity.BOTTOM | Gravity.START);
        tl.leftMargin = U.dp(ctx, 40);
        tl.bottomMargin = bot + U.dp(ctx, 62);
        addView(torch, tl);

        cam = new GlassView(ctx, 27);
        cam.addView(new GlyphView(ctx, 1), new LayoutParams(bs, bs));
        cam.setOnClickListener(v -> {
            try {
                Intent i = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(i);
            } catch (Exception ignored) { }
        });
        LayoutParams kl = new LayoutParams(bs, bs, Gravity.BOTTOM | Gravity.END);
        kl.rightMargin = U.dp(ctx, 40);
        kl.bottomMargin = bot + U.dp(ctx, 62);
        addView(cam, kl);

        panel = buildPanel(ctx);
        LayoutParams pl = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        pl.leftMargin = pl.rightMargin = U.dp(ctx, 14);
        pl.bottomMargin = bot + U.dp(ctx, 14);
        panel.setVisibility(GONE);
        addView(panel, pl);

        clock.onChange = () -> {
            if (heightBar != null)
                heightBar.setProgress((int) (100 * (clock.sy - ClockView.MIN_SY) / Math.max(0.01f, clock.maxSy - ClockView.MIN_SY)));
        };
        updateDate();
    }

    void updateTime() {
        clock.updateTime();
        updateDate();
    }

    private void updateDate() {
        String s = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(new Date());
        if (s.length() > 0) s = Character.toUpperCase(s.charAt(0)) + s.substring(1);
        date.setText(s);
    }

    /** Riporta la schermata di blocco allo stato iniziale e la mostra. */
    void show() {
        setVisibility(VISIBLE);
        setTranslationY(0);
        setAlpha(1f);
        progress = 0;
        clock.setShrink(0);
        host.behindProgress(0);
        updateTime();
        clock.playIntro();
    }

    private void toggleTorch() {
        try {
            CameraManager cm = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            String id = cm.getCameraIdList()[0];
            torchOn = !torchOn;
            cm.setTorchMode(id, torchOn);
            torch.setAlpha(torchOn ? 1f : 0.85f);
        } catch (Exception e) {
            torchOn = false;
        }
    }

    // ---------- pannello di personalizzazione ----------

    private TextView label(Context c, String t) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextColor(0xFFFFFFFF);
        v.setTextSize(14);
        v.setPadding(0, U.dp(c, 10), 0, 0);
        return v;
    }

    private SeekBar slider(Context c, LinearLayout col, String name, int prog, Seek l) {
        col.addView(label(c, name));
        SeekBar s = new SeekBar(c);
        s.setMax(100);
        s.setProgress(prog);
        s.setProgressTintList(ColorStateList.valueOf(Color.WHITE));
        s.setThumbTintList(ColorStateList.valueOf(Color.WHITE));
        s.setOnSeekBarChangeListener(l);
        col.addView(s);
        return s;
    }

    private GlassView chip(Context c, String text, View.OnClickListener l, TextView[] out) {
        GlassView g = new GlassView(c, 18);
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(12);
        t.setGravity(Gravity.CENTER);
        g.addView(t, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        g.setOnClickListener(l);
        out[0] = t;
        return g;
    }

    private GlassView buildPanel(Context c) {
        GlassView g = new GlassView(c, 30);
        g.setClickable(true);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(U.dp(c, 20), U.dp(c, 18), U.dp(c, 20), U.dp(c, 18));
        g.addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(c);
        title.setText("Personalizza l'orario");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        slider(c, col, "Peso", (int) (clock.weight * 100), new Seek() {
            @Override void on(int v) { clock.weight = v / 100f; clock.invalidate(); clock.save(); }
        });
        heightBar = slider(c, col, "Altezza (puoi anche trascinare l'orario)",
                (int) (100 * (clock.sy - ClockView.MIN_SY) / Math.max(0.01f, clock.maxSy - ClockView.MIN_SY)), new Seek() {
            @Override void on(int v) {
                clock.setStretch(ClockView.MIN_SY + (clock.maxSy - ClockView.MIN_SY) * v / 100f);
                clock.save();
            }
        });

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, U.dp(c, 54));
        rp.topMargin = U.dp(c, 16);
        col.addView(row, rp);

        final TextView[] t1 = new TextView[1], t2 = new TextView[1], t3 = new TextView[1];
        GlassView a = chip(c, "", v -> {
            clock.font = (clock.font + 1) % ClockView.FONTS.length;
            t1[0].setText("Carattere\n" + ClockView.FONT_NAMES[clock.font]);
            clock.invalidate(); clock.save();
        }, t1);
        t1[0].setText("Carattere\n" + ClockView.FONT_NAMES[clock.font]);
        GlassView b = chip(c, "", v -> {
            clock.color = (clock.color + 1) % ClockView.COLORS.length;
            t2[0].setText("Colore\n" + ClockView.COLOR_NAMES[clock.color]);
            clock.invalidate(); clock.save();
        }, t2);
        t2[0].setText("Colore\n" + ClockView.COLOR_NAMES[clock.color]);
        GlassView d = chip(c, "", v -> {
            clock.setStacked(!clock.stacked);
            t3[0].setText("Stile\n" + (clock.stacked ? "Impilato" : "Su una riga"));
            clock.save();
        }, t3);
        t3[0].setText("Stile\n" + (clock.stacked ? "Impilato" : "Su una riga"));
        for (GlassView x : new GlassView[]{a, b, d}) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            lp.leftMargin = lp.rightMargin = U.dp(c, 4);
            row.addView(x, lp);
        }

        GlassView done = new GlassView(c, 22);
        TextView dt = new TextView(c);
        dt.setText("Fine");
        dt.setTextColor(0xFFFFFFFF);
        dt.setTextSize(17);
        dt.setTypeface(Typeface.DEFAULT_BOLD);
        dt.setGravity(Gravity.CENTER);
        done.addView(dt, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        done.setOnClickListener(v -> endEdit());
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, U.dp(c, 46));
        dp.topMargin = U.dp(c, 14);
        col.addView(done, dp);
        return g;
    }

    boolean isEditing() { return editing; }

    private void startEdit() {
        editing = true;
        panel.setVisibility(VISIBLE);
        panel.setTranslationY(U.dp(getContext(), 360));
        panel.animate().translationY(0).setDuration(320).setInterpolator(new DecelerateInterpolator()).start();
        hint.setVisibility(INVISIBLE);
        torch.setVisibility(INVISIBLE);
        cam.setVisibility(INVISIBLE);
    }

    void endEdit() {
        if (!editing) return;
        editing = false;
        clock.save();
        panel.animate().translationY(U.dp(getContext(), 360)).setDuration(240).withEndAction(() -> panel.setVisibility(GONE)).start();
        hint.setVisibility(VISIBLE);
        torch.setVisibility(VISIBLE);
        cam.setVisibility(VISIBLE);
    }

    // ---------- gesti ----------

    private void apply(float p) {
        progress = p;
        setTranslationY(-p * getHeight() * 0.55f);
        setAlpha(1f - 0.85f * p);
        clock.setShrink(p);
        host.behindProgress(p);
    }

    private void finish(float from) {
        ValueAnimator a = ValueAnimator.ofFloat(from, 1f);
        a.setDuration(230);
        a.addUpdateListener(v -> apply((float) v.getAnimatedValue()));
        a.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator an) {
                setVisibility(GONE);
                host.onUnlocked();
            }
        });
        a.start();
    }

    private void back(float from) {
        ValueAnimator a = ValueAnimator.ofFloat(from, 0f);
        a.setDuration(260);
        a.setInterpolator(new DecelerateInterpolator());
        a.addUpdateListener(v -> apply((float) v.getAnimatedValue()));
        a.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startY = e.getRawY();
                moved = false;
                boolean onClock = clock.hit(e.getY() - clock.getTop()) && e.getY() >= clock.getTop();
                mode = onClock ? 1 : (editing ? 0 : 2);
                startSy = clock.sy;
                handler.postDelayed(longPress, 550);
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dy = e.getRawY() - startY;
                if (Math.abs(dy) > U.dp(getContext(), 8)) {
                    moved = true;
                    handler.removeCallbacks(longPress);
                }
                if (mode == 1 && moved) {
                    clock.setStretch(startSy + dy / Math.max(1f, clock.baseHeight()));
                } else if (mode == 2 && dy < 0) {
                    apply(Math.min(1f, -dy / (getHeight() * 0.45f)));
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPress);
                if (mode == 1) clock.save();
                if (mode == 2 && progress > 0) {
                    if (progress > 0.35f) finish(progress); else back(progress);
                }
                mode = 0;
                return true;
        }
        return true;
    }
}
