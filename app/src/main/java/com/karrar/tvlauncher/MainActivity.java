package com.karrar.tvlauncher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    // Factory/service packages hidden from the apps row. Edit freely.
    private static final String[] HIDDEN = {
            "com.cultraview.agingmodel", "com.ctvdevicemanger", "com.example.upgrad"
    };

    // Quick shortcuts: label, package, activity class (taken from this TV's dumpsys).
    private static final String[][] QUICK = {
            {"التلفاز", "com.droidlogic.tvsource", "com.droidlogic.tvsource.DroidLogicTv"},
            {"المصادر", "com.droidlogic.tvinput", "com.droidlogic.tvinput.shortcut.SourceListActivity"},
            {"الإعدادات", "com.cultraview.settings", "com.cultraview.settings.CtvSettings"},
            {"الشبكة", "com.cultraview.settings.net", "com.cultraview.settings.net.NetworkSettingsActivity"},
            {"المتصفح", "com.android.browser", "com.android.browser.BrowserActivity"},
            {"الوسائط", "com.droidlogic.videoplayer", "com.droidlogic.videoplayer.FileList"},
            {"العرض اللاسلكي", "com.droidlogic.miracast", "com.droidlogic.miracast.WiFiDirectMainActivity"},
    };

    private static final int[][] PALETTE = {
            {0xFF3A4F8F, 0xFF1C2850}, {0xFF6B3FA0, 0xFF2E1B52}, {0xFF1F7A8C, 0xFF0F3D4A},
            {0xFFB0475E, 0xFF55212F}, {0xFF3E8E5A, 0xFF1B4029}, {0xFFB58433, 0xFF553D14},
    };

    private static class Item {
        String label;
        Drawable art;
        boolean banner;
        Intent intent;
    }

    private float density;
    private LinearLayout appsRow, quickRow;
    private TextView title;

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            load();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;

        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0B1020, 0xFF1A1F3D, 0xFF2A1B4A});
        getWindow().setBackgroundDrawable(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipChildren(false);
        root.setClipToPadding(false);
        root.setPadding(dp(48), dp(28), dp(48), dp(16));

        // Header: clock + date, right aligned
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.END);
        TextClock time = new TextClock(this);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("H:mm");
        time.setTextSize(40);
        time.setTextColor(Color.WHITE);
        time.setGravity(Gravity.END);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE d MMMM");
        date.setFormat24Hour("EEEE d MMMM");
        date.setTextSize(16);
        date.setTextColor(0xB3FFFFFF);
        date.setGravity(Gravity.END);
        header.addView(time);
        header.addView(date);
        root.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Spacer
        root.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        // Focused item title
        title = new TextView(this);
        title.setTextSize(36);
        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        title.setPadding(dp(14), 0, 0, dp(4));
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        appsRow = new LinearLayout(this);
        root.addView(scroller(appsRow, dp(176)));

        // Spacer between rows
        root.addView(new View(this), new LinearLayout.LayoutParams(1, dp(6)));

        quickRow = new LinearLayout(this);
        root.addView(scroller(quickRow, dp(150)));

        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        load();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(pkgReceiver);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed again: go back to the first app tile
        if (appsRow.getChildCount() > 0) {
            ((HorizontalScrollView) appsRow.getParent()).smoothScrollTo(0, 0);
            appsRow.getChildAt(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // A launcher never exits on Back
    }

    private HorizontalScrollView scroller(LinearLayout row, int heightPx) {
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        HorizontalScrollView sv = new HorizontalScrollView(this);
        sv.setHorizontalScrollBarEnabled(false);
        sv.setClipChildren(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, dp(18), 0, dp(18));
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(row);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx));
        return sv;
    }

    private void load() {
        PackageManager pm = getPackageManager();
        Set<String> hidden = new HashSet<>();
        for (String h : HIDDEN) hidden.add(h);

        // Apps
        List<Item> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String cat : new String[]{Intent.CATEGORY_LAUNCHER, "android.intent.category.LEANBACK_LAUNCHER"}) {
            Intent q = new Intent(Intent.ACTION_MAIN);
            q.addCategory(cat);
            for (ResolveInfo ri : pm.queryIntentActivities(q, 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || hidden.contains(pkg) || !seen.add(pkg)) continue;
                Item it = new Item();
                it.label = String.valueOf(ri.loadLabel(pm));
                Drawable banner = null;
                try {
                    banner = ri.activityInfo.loadBanner(pm);
                } catch (Exception ignored) {
                }
                if (banner != null) {
                    it.art = banner;
                    it.banner = true;
                } else {
                    it.art = ri.loadIcon(pm);
                }
                it.intent = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(new ComponentName(pkg, ri.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                apps.add(it);
            }
        }
        Collections.sort(apps, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        // Quick shortcuts (only those that exist on this device)
        List<Item> quick = new ArrayList<>();
        for (String[] qd : QUICK) {
            ComponentName cn = new ComponentName(qd[1], qd[2]);
            try {
                ActivityInfo ai = pm.getActivityInfo(cn, 0);
                Item it = new Item();
                it.label = qd[0];
                it.art = ai.loadIcon(pm);
                it.intent = new Intent(Intent.ACTION_MAIN).setComponent(cn)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                quick.add(it);
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        appsRow.removeAllViews();
        quickRow.removeAllViews();
        for (Item it : apps) appsRow.addView(tile(it, 200, 112));
        for (Item it : quick) quickRow.addView(tile(it, 112, 112));

        View first = appsRow.getChildCount() > 0 ? appsRow.getChildAt(0)
                : (quickRow.getChildCount() > 0 ? quickRow.getChildAt(0) : null);
        if (first != null) first.requestFocus();
    }

    private View tile(final Item it, int wDp, int hDp) {
        FrameLayout f = new FrameLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(wDp), dp(hDp));
        lp.setMargins(dp(14), 0, dp(14), 0);
        f.setLayoutParams(lp);

        int[] c = PALETTE[Math.abs(it.label.hashCode()) % PALETTE.length];
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, c);
        bg.setCornerRadius(dp(14));
        f.setBackground(bg);
        f.setClipToOutline(true);
        f.setElevation(dp(4));

        ImageView iv = new ImageView(this);
        iv.setImageDrawable(it.art);
        if (it.banner) {
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            f.addView(iv, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(
                    dp(hDp * 0.6f), dp(hDp * 0.6f), Gravity.CENTER);
            f.addView(iv, ip);
        }

        f.setFocusable(true);
        f.setClickable(true);
        f.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(it.intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "تعذر فتح " + it.label, Toast.LENGTH_SHORT).show();
                }
            }
        });
        f.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean has) {
                v.animate().scaleX(has ? 1.15f : 1f).scaleY(has ? 1.15f : 1f)
                        .translationZ(has ? dp(18) : 0).setDuration(160).start();
                if (has) {
                    title.setText(it.label);
                    centerInScroller(v);
                }
            }
        });
        return f;
    }

    private void centerInScroller(View v) {
        if (!(v.getParent() instanceof LinearLayout)) return;
        View sv = (View) ((View) v.getParent()).getParent();
        if (sv instanceof HorizontalScrollView) {
            int target = v.getLeft() + v.getWidth() / 2 - sv.getWidth() / 2;
            ((HorizontalScrollView) sv).smoothScrollTo(Math.max(0, target), 0);
        }
    }

    private int dp(float v) {
        return (int) (v * density + 0.5f);
    }
}
