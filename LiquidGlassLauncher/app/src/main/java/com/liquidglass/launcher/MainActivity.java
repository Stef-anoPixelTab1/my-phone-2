package com.liquidglass.launcher;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity implements LockView.Host {
    private static final int REQ_WALL = 7;

    private FrameLayout root;
    private FrameLayout home;
    private LockView lock;
    private Pager pager;
    private SharedPreferences sp;
    private List<AppInfo> apps = new ArrayList<>();
    private boolean built = false, locked = true, firstShow = true;
    private int W, H, top, bot;
    private View overlay;

    /** Vista che disegna lo sfondo (nitido o sfocato). */
    private static class BackdropView extends View {
        final boolean blurred;
        BackdropView(Context c, boolean blurred) {
            super(c);
            this.blurred = blurred;
            setWillNotDraw(false);
        }
        @Override protected void onDraw(Canvas c) {
            Bitmap b = blurred ? Backdrop.blur : Backdrop.sharp;
            if (b != null) c.drawBitmap(b, 0, 0, null);
            if (blurred) c.drawColor(0x55000000);
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(a)) {
                locked = true;
                if (built) showLock();
            } else if (Intent.ACTION_PACKAGE_ADDED.equals(a) || Intent.ACTION_PACKAGE_REMOVED.equals(a)
                    || Intent.ACTION_PACKAGE_CHANGED.equals(a)) {
                loadAppsAsync();
            } else if (lock != null) {
                lock.updateTime();
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("clock", MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        Point pt = new Point();
        getWindowManager().getDefaultDisplay().getRealSize(pt);
        W = Math.min(pt.x, pt.y);
        H = Math.max(pt.x, pt.y);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            top = ins.getSystemWindowInsetTop();
            bot = ins.getSystemWindowInsetBottom();
            if (!built) buildAll();
            return ins;
        });
        root.postDelayed(() -> {
            if (!built) {
                top = U.dp(this, 28);
                bot = 0;
                buildAll();
            }
        }, 400);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(receiver, f);
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(receiver, pf);

        loadAppsAsync();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
    }

    // ---------- caricamento app ----------

    private void loadAppsAsync() {
        new Thread(() -> {
            PackageManager pm = getPackageManager();
            Intent q = new Intent(Intent.ACTION_MAIN, null);
            q.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> l = pm.queryIntentActivities(q, 0);
            int size = U.dp(this, 64);
            List<AppInfo> out = new ArrayList<>();
            for (ResolveInfo ri : l) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName())) continue;
                AppInfo a = new AppInfo();
                a.pkg = pkg;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.cn = new android.content.ComponentName(pkg, ri.activityInfo.name);
                a.kind = IconFactory.kind(pkg);
                try {
                    Drawable d = ri.loadIcon(pm);
                    a.icon = IconFactory.build(a.kind, d, size);
                } catch (Throwable t) {
                    a.icon = IconFactory.build(-1, getDrawable(android.R.drawable.sym_def_app_icon), size);
                }
                out.add(a);
            }
            Collections.sort(out, (x, y) -> x.label.compareToIgnoreCase(y.label));
            runOnUiThread(() -> {
                apps = out;
                if (built) fillHome();
            });
        }).start();
    }

    // ---------- costruzione UI ----------

    private void buildAll() {
        built = true;
        Backdrop.build(this, W, H);
        root.removeAllViews();
        root.addView(new BackdropView(this, false), new FrameLayout.LayoutParams(-1, -1));
        home = new FrameLayout(this);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));
        lock = new LockView(this, sp, top, bot, H, this);
        root.addView(lock, new FrameLayout.LayoutParams(-1, -1));
        fillHome();
        if (locked) {
            lock.show();
        } else {
            lock.setVisibility(View.GONE);
            behindProgress(1f);
        }
    }

    private void showLock() {
        closeOverlay();
        locked = true;
        if (lock != null) lock.show();
    }

    @Override public void onUnlocked() { locked = false; }

    @Override public void behindProgress(float p) {
        if (home == null) return;
        home.setVisibility(p > 0 ? View.VISIBLE : View.INVISIBLE);
        home.setAlpha(p);
        float s = 0.94f + 0.06f * p;
        home.setScaleX(s);
        home.setScaleY(s);
    }

    private void fillHome() {
        home.removeAllViews();
        if (apps.isEmpty()) return;
        final int cols = 4;
        int cellW = W / cols, cellH = U.dp(this, 100);
        int dockH = U.dp(this, 88), searchH = U.dp(this, 40), dotsH = U.dp(this, 18);
        int dockBottom = bot + U.dp(this, 10);
        int searchBottom = dockBottom + dockH + U.dp(this, 12);
        int dotsBottom = searchBottom + searchH + U.dp(this, 6);
        int availH = H - top - U.dp(this, 14) - dotsBottom - dotsH;
        int rows = Math.max(3, availH / cellH);

        // dock: telefono, messaggi, browser, fotocamera (poi le prime app disponibili)
        int[] pref = {IconFactory.PHONE, IconFactory.MSG, IconFactory.WEB, IconFactory.CAM};
        List<AppInfo> dock = new ArrayList<>();
        for (int k : pref) {
            for (AppInfo a : apps) if (a.kind == k && !dock.contains(a)) { dock.add(a); break; }
        }
        for (AppInfo a : apps) { if (dock.size() >= 4) break; if (!dock.contains(a)) dock.add(a); }
        List<AppInfo> grid = new ArrayList<>(apps);
        grid.removeAll(dock);

        int perPage = rows * cols;
        int pages = Math.max(1, (grid.size() + perPage - 1) / perPage);
        pager = new Pager(this);
        pager.pageW = W;
        pager.pages = pages;
        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        for (int pg = 0; pg < pages; pg++) {
            GridLayout g = new GridLayout(this);
            g.setColumnCount(cols);
            g.setPadding(0, U.dp(this, 6), 0, 0);
            g.setOnLongClickListener(v -> { showMainMenu(); return true; });
            for (int i = pg * perPage; i < Math.min(grid.size(), (pg + 1) * perPage); i++) {
                g.addView(makeIcon(grid.get(i), cellW, cellH, true), new ViewGroup.LayoutParams(cellW, cellH));
            }
            strip.addView(g, new LinearLayout.LayoutParams(W, availH));
        }
        pager.addView(strip, new FrameLayout.LayoutParams(W * pages, availH));
        FrameLayout.LayoutParams pgp = new FrameLayout.LayoutParams(W, availH);
        pgp.topMargin = top + U.dp(this, 14);
        home.addView(pager, pgp);

        DotsView dots = new DotsView(this);
        dots.set(pages, 0);
        FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(-1, dotsH, Gravity.BOTTOM);
        dp.bottomMargin = dotsBottom;
        home.addView(dots, dp);
        pager.setOnScrollChangeListener((v, x, y, ox, oy) -> dots.set(pages, x / (float) W));

        // pillola di ricerca
        GlassView search = new GlassView(this, 20);
        LinearLayout sl = new LinearLayout(this);
        sl.setGravity(Gravity.CENTER);
        sl.setOrientation(LinearLayout.HORIZONTAL);
        sl.addView(new GlyphView(this, 2), new LinearLayout.LayoutParams(U.dp(this, 20), U.dp(this, 20)));
        TextView st = new TextView(this);
        st.setText("  Cerca");
        st.setTextColor(0xFFFFFFFF);
        st.setTextSize(15);
        sl.addView(st);
        search.addView(sl, new FrameLayout.LayoutParams(-1, -1));
        search.setOnClickListener(v -> showSearch());
        FrameLayout.LayoutParams sp2 = new FrameLayout.LayoutParams(U.dp(this, 180), searchH, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        sp2.bottomMargin = searchBottom;
        home.addView(search, sp2);

        // dock
        GlassView dk = new GlassView(this, 34);
        LinearLayout dl = new LinearLayout(this);
        dl.setOrientation(LinearLayout.HORIZONTAL);
        dl.setGravity(Gravity.CENTER_VERTICAL);
        for (AppInfo a : dock) {
            dl.addView(makeIcon(a, 0, 0, false), new LinearLayout.LayoutParams(0, -1, 1f));
        }
        dk.addView(dl, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams dkp = new FrameLayout.LayoutParams(-1, dockH, Gravity.BOTTOM);
        dkp.leftMargin = dkp.rightMargin = U.dp(this, 14);
        dkp.bottomMargin = dockBottom;
        home.addView(dk, dkp);
    }

    private View makeIcon(AppInfo a, int cellW, int cellH, boolean label) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(label ? Gravity.CENTER_HORIZONTAL : Gravity.CENTER);
        if (label) l.setPadding(0, U.dp(this, 6), 0, 0);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(a.icon);
        int ts = U.dp(this, label ? 62 : 60);
        l.addView(iv, new LinearLayout.LayoutParams(ts, ts));
        if (label) {
            TextView t = new TextView(this);
            t.setText(a.label);
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            t.setGravity(Gravity.CENTER);
            t.setShadowLayer(6, 0, 1, 0x88000000);
            t.setPadding(U.dp(this, 4), U.dp(this, 5), U.dp(this, 4), 0);
            l.addView(t, new LinearLayout.LayoutParams(-1, -2));
        }
        l.setOnTouchListener((v, e) -> {
            int ac = e.getActionMasked();
            if (ac == MotionEvent.ACTION_DOWN) v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(90).start();
            else if (ac == MotionEvent.ACTION_UP || ac == MotionEvent.ACTION_CANCEL)
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
            return false;
        });
        l.setOnClickListener(v -> launch(a, v));
        l.setOnLongClickListener(v -> { showAppMenu(a); return true; });
        return l;
    }

    private void launch(AppInfo a, View v) {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setComponent(a.cn);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(i, ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.getWidth(), v.getHeight()).toBundle());
        } catch (Exception ignored) { }
    }

    // ---------- menu e ricerca ----------

    private void closeOverlay() {
        if (overlay != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
            root.removeView(overlay);
            overlay = null;
        }
    }

    private void showMenu(String[] labels, Runnable[] acts) {
        closeOverlay();
        FrameLayout scrim = new FrameLayout(this);
        scrim.setBackgroundColor(0x44000000);
        scrim.setOnClickListener(v -> closeOverlay());
        GlassView g = new GlassView(this, 26);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(0, U.dp(this, 6), 0, U.dp(this, 6));
        for (int i = 0; i < labels.length; i++) {
            final int k = i;
            TextView t = new TextView(this);
            t.setText(labels[i]);
            t.setTextColor(0xFFFFFFFF);
            t.setTextSize(17);
            t.setMinWidth(U.dp(this, 230));
            t.setPadding(U.dp(this, 22), U.dp(this, 14), U.dp(this, 22), U.dp(this, 14));
            t.setOnClickListener(v -> { closeOverlay(); acts[k].run(); });
            col.addView(t);
        }
        g.addView(col, new FrameLayout.LayoutParams(-2, -2));
        g.setClickable(true);
        scrim.addView(g, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        g.setScaleX(0.88f);
        g.setScaleY(0.88f);
        g.setAlpha(0f);
        g.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(200).start();
        overlay = scrim;
        root.addView(scrim, new FrameLayout.LayoutParams(-1, -1));
    }

    private void showMainMenu() {
        showMenu(new String[]{"Cambia sfondo", "Blocca schermo", "Impostazioni app predefinite"},
                new Runnable[]{
                        () -> {
                            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                            i.setType("image/*");
                            startActivityForResult(i, REQ_WALL);
                        },
                        this::showLock,
                        () -> startActivity(new Intent(Settings.ACTION_HOME_SETTINGS))
                });
    }

    private void showAppMenu(AppInfo a) {
        showMenu(new String[]{a.label, "Info app", "Disinstalla"},
                new Runnable[]{
                        () -> launch(a, root),
                        () -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + a.pkg))),
                        () -> startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)))
                });
    }

    private void showSearch() {
        closeOverlay();
        FrameLayout s = new FrameLayout(this);
        s.addView(new BackdropView(this, true), new FrameLayout.LayoutParams(-1, -1));

        GlassView box = new GlassView(this, 22);
        EditText et = new EditText(this);
        et.setHint("Cerca app");
        et.setHintTextColor(0x99FFFFFF);
        et.setTextColor(0xFFFFFFFF);
        et.setTextSize(17);
        et.setSingleLine(true);
        et.setBackground(null);
        et.setPadding(U.dp(this, 20), 0, U.dp(this, 20), 0);
        box.addView(et, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, U.dp(this, 48));
        bp.topMargin = top + U.dp(this, 16);
        bp.leftMargin = bp.rightMargin = U.dp(this, 16);
        s.addView(box, bp);

        ScrollView sv = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list, new ViewGroup.LayoutParams(-1, -2));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, -1);
        lp.topMargin = top + U.dp(this, 76);
        s.addView(sv, lp);

        Runnable refresh = () -> {
            list.removeAllViews();
            String q = et.getText().toString().trim().toLowerCase();
            int n = 0;
            for (AppInfo a : apps) {
                if (!q.isEmpty() && !a.label.toLowerCase().contains(q)) continue;
                if (++n > 40) break;
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(U.dp(this, 20), U.dp(this, 8), U.dp(this, 20), U.dp(this, 8));
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(a.icon);
                row.addView(iv, new LinearLayout.LayoutParams(U.dp(this, 46), U.dp(this, 46)));
                TextView t = new TextView(this);
                t.setText(a.label);
                t.setTextColor(0xFFFFFFFF);
                t.setTextSize(17);
                t.setPadding(U.dp(this, 16), 0, 0, 0);
                row.addView(t);
                row.setOnClickListener(v -> { closeOverlay(); launch(a, v); });
                list.addView(row);
            }
        };
        refresh.run();
        et.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            public void onTextChanged(CharSequence c, int a, int b, int d) { refresh.run(); }
            public void afterTextChanged(Editable e) { }
        });
        overlay = s;
        root.addView(s, new FrameLayout.LayoutParams(-1, -1));
        et.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
    }

    // ---------- sfondo personalizzato ----------

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_WALL || res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        new Thread(() -> {
            try {
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    android.graphics.BitmapFactory.decodeStream(in, null, o);
                }
                int s = 1;
                while (o.outWidth / (s * 2) >= W && o.outHeight / (s * 2) >= H) s *= 2;
                o = new android.graphics.BitmapFactory.Options();
                o.inSampleSize = s;
                Bitmap bm;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    bm = android.graphics.BitmapFactory.decodeStream(in, null, o);
                }
                if (bm == null) return;
                try (FileOutputStream out = new FileOutputStream(new java.io.File(getFilesDir(), "wall.jpg"))) {
                    bm.compress(Bitmap.CompressFormat.JPEG, 92, out);
                }
                Backdrop.reset();
                runOnUiThread(this::buildAll);
            } catch (Exception ignored) { }
        }).start();
    }

    // ---------- tasto indietro / Home ----------

    @Override
    public void onBackPressed() {
        if (overlay != null) { closeOverlay(); return; }
        if (lock != null && lock.getVisibility() == View.VISIBLE) {
            if (lock.isEditing()) lock.endEdit();
            return;
        }
        if (pager != null && pager.currentPage() > 0) pager.smoothScrollTo(0, 0);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (overlay != null) closeOverlay();
        if (lock != null && lock.getVisibility() == View.VISIBLE) {
            if (lock.isEditing()) lock.endEdit();
        } else if (pager != null) {
            pager.smoothScrollTo(0, 0);
        }
    }
}
