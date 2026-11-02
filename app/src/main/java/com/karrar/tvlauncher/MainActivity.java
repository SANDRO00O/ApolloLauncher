package com.karrar.tvlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A lightweight, D-pad-first Android TV launcher.
 * The UI intentionally uses framework widgets only so it remains compatible
 * with Android 5.1 / API 22 and low-memory TV boxes.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "tv_launcher_preferences";
    private static final String PREF_WALLPAPER_MODE = "wallpaper_mode";
    private static final int REQUEST_WALLPAPER = 4102;
    private static final int BACKDROP_WIDTH = 384;
    private static final int BACKDROP_HEIGHT = 216;

    private static final String[] HIDDEN = {
            "com.cultraview.agingmodel", "com.ctvdevicemanger", "com.example.upgrad"
    };

    // English label, Arabic label, package, activity, custom line-icon kind.
    private static final String[][] QUICK = {
            {"TV", "التلفاز", "com.droidlogic.tvsource", "com.droidlogic.tvsource.DroidLogicTv", "0"},
            {"Sources", "المصادر", "com.droidlogic.tvinput", "com.droidlogic.tvinput.shortcut.SourceListActivity", "1"},
            {"Settings", "الإعدادات", "com.cultraview.settings", "com.cultraview.settings.CtvSettings", "2"},
            {"Network", "الشبكة", "com.cultraview.settings.net", "com.cultraview.settings.net.NetworkSettingsActivity", "3"},
            {"Browser", "المتصفح", "com.android.browser", "com.android.browser.BrowserActivity", "4"},
            {"Media", "الوسائط", "com.droidlogic.videoplayer", "com.droidlogic.videoplayer.FileList", "5"},
            {"Cast", "العرض اللاسلكي", "com.droidlogic.miracast", "com.droidlogic.miracast.WiFiDirectMainActivity", "6"}
    };

    private static final int[][] PALETTE = {
            {0xFF5278D8, 0xFF182B5C}, {0xFF9664D5, 0xFF38205E},
            {0xFF28A4B4, 0xFF123F54}, {0xFFD96B83, 0xFF552537},
            {0xFF55A979, 0xFF1B4935}, {0xFFD4A14D, 0xFF573B1B}
    };

    private static class Item {
        String label;
        String packageName;
        Drawable art;
        boolean banner;
        int iconKind;
        Intent intent;
    }

    private float density;
    private SharedPreferences preferences;
    private FrameLayout root;
    private ImageView wallpaperView;
    private LinearLayout appsRow;
    private LinearLayout quickRow;
    private HorizontalScrollView appsScroller;
    private TextView selectedTitle;
    private TextView appSectionLabel;
    private Typeface latinTypeface;
    private Typeface arabicTypeface;
    private Typeface clockTypeface;
    private Item focusedItem;
    private boolean receiverRegistered;
    private volatile int blurRequest = 0;
    private final Handler mainHandler = new Handler();
    private final ThreadPoolExecutor blurExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    private final LruCache<String, Bitmap> blurredBackdrops = new LruCache<String, Bitmap>(5 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadApps();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF070B18));

        loadTypefaces();
        buildInterface();
        registerPackageReceiver();
        loadApps();
        restoreWallpaper();
    }

    private void loadTypefaces() {
        try { clockTypeface = Typeface.createFromAsset(getAssets(), "fonts/InterDisplay-Thin.otf"); }
        catch (Exception ignored) { clockTypeface = Typeface.create("sans-serif-light", Typeface.NORMAL); }
        try { latinTypeface = Typeface.createFromAsset(getAssets(), "fonts/InterDisplay-Medium.otf"); }
        catch (Exception ignored) { latinTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); }
        try { arabicTypeface = Typeface.createFromAsset(getAssets(), "fonts/NotoKufiArabic-Regular.ttf"); }
        catch (Exception ignored) { arabicTypeface = Typeface.create("sans-serif", Typeface.NORMAL); }
    }

    private void buildInterface() {
        root = new FrameLayout(this);
        root.setFocusable(false);

        wallpaperView = new ImageView(this);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperView.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF101D42, 0xFF111329, 0xFF28183F}));
        root.addView(wallpaperView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x40040A18, 0x24050A18, 0x77050A17, 0xD9080B16}));
        root.addView(scrim, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Minimal top bar: the controls remain focusable with a TV remote.
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(48), dp(22), dp(48), 0);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView brandMark = new TextView(this);
        brandMark.setText("TV");
        brandMark.setTextColor(Color.WHITE);
        brandMark.setTypeface(latinTypeface, Typeface.BOLD);
        brandMark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        brandMark.setGravity(Gravity.CENTER);
        brandMark.setBackground(roundRect(0x42FFFFFF, 0x55FFFFFF, 12));
        brand.addView(brandMark, new LinearLayout.LayoutParams(dp(42), dp(34)));
        TextView brandName = new TextView(this);
        brandName.setText(text("  LIVING ROOM", "  غرفة المعيشة"));
        brandName.setTextColor(0xDFFFFFFF);
        brandName.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        brandName.setTypeface(typefaceFor(brandName.getText().toString()));
        brandName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        brandName.setLetterSpacing(isArabicLocale() ? 0f : 0.14f);
        brand.addView(brandName);
        topBar.addView(brand, new LinearLayout.LayoutParams(0, dp(42), 1f));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(makePill(text("Wallpaper", "الخلفية"), 7, new Runnable() {
            @Override public void run() { showWallpaperDialog(); }
        }));
        topBar.addView(actions);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(72), Gravity.TOP);
        root.addView(topBar, topParams);

        // Oversized, vertically stretched clock, deliberately centred on the canvas.
        LinearLayout clockStack = new LinearLayout(this);
        clockStack.setOrientation(LinearLayout.VERTICAL);
        clockStack.setGravity(Gravity.CENTER);
        clockStack.setClipChildren(false);
        clockStack.setClipToPadding(false);

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextColor(Color.WHITE);
        clock.setTypeface(clockTypeface);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSizeSp());
        clock.setGravity(Gravity.CENTER);
        clock.setLetterSpacing(0.025f);
        clock.setShadowLayer(dp(22), 0, dp(6), 0x99000000);
        clock.setScaleY(1.34f);
        clock.setScaleX(1.03f);
        clockStack.addView(clock, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(clockSizeSp() * 1.42f)));

        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE  ·  d MMMM");
        date.setFormat24Hour("EEEE  ·  d MMMM");
        date.setTextColor(0xD8FFFFFF);
        date.setTypeface(typefaceForLocale());
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        date.setGravity(Gravity.CENTER);
        date.setLetterSpacing(0.025f);
        date.setShadowLayer(dp(10), 0, dp(2), 0x88000000);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(12);
        clockStack.addView(date, dateParams);

        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        clockParams.leftMargin = dp(32);
        clockParams.rightMargin = dp(32);
        clockStack.setTranslationY(-dp(Math.min(104, screenHeightDp() * 0.215f)));
        root.addView(clockStack, clockParams);

        // Bottom dock: app artwork and device shortcuts, all traversable with D-pad.
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setPadding(dp(18), dp(12), dp(18), dp(10));
        dock.setBackground(roundRect(0x2AFFFFFF, 0x36FFFFFF, 26));
        dock.setClipChildren(false);
        dock.setClipToPadding(false);

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        appSectionLabel = new TextView(this);
        appSectionLabel.setText(text("YOUR APPS", "تطبيقاتك"));
        appSectionLabel.setTextColor(0xBFFFFFFF);
        appSectionLabel.setTypeface(typefaceFor(text("YOUR APPS", "تطبيقاتك")));
        appSectionLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        appSectionLabel.setLetterSpacing(isArabicLocale() ? 0f : 0.13f);
        heading.addView(appSectionLabel, new LinearLayout.LayoutParams(0, dp(28), 1f));

        selectedTitle = new TextView(this);
        selectedTitle.setTextColor(Color.WHITE);
        selectedTitle.setTypeface(latinTypeface);
        selectedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        selectedTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        selectedTitle.setSingleLine(true);
        selectedTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(selectedTitle, new LinearLayout.LayoutParams(
                0, dp(30), 1.4f));
        dock.addView(heading);

        appsRow = new LinearLayout(this);
        appsRow.setOrientation(LinearLayout.HORIZONTAL);
        appsRow.setGravity(Gravity.CENTER_VERTICAL);
        appsRow.setClipChildren(false);
        appsScroller = horizontalScroller(appsRow, Math.min(136, Math.max(104, (int)(screenHeightDp() * 0.23f))));
        LinearLayout.LayoutParams appScrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, appsScroller.getLayoutParams().height);
        appScrollParams.topMargin = dp(2);
        dock.addView(appsScroller, appScrollParams);

        quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setGravity(Gravity.CENTER_VERTICAL);
        quickRow.setClipChildren(false);
        HorizontalScrollView quickScroller = horizontalScroller(quickRow, 54);
        LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54));
        quickParams.topMargin = dp(5);
        dock.addView(quickScroller, quickParams);

        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        dockParams.leftMargin = dp(34);
        dockParams.rightMargin = dp(34);
        dockParams.bottomMargin = dp(18);
        root.addView(dock, dockParams);

        setContentView(root);
    }

    private int clockSizeSp() {
        float available = screenHeightDp();
        return (int)Math.max(64, Math.min(126, available * 0.235f));
    }

    private float screenHeightDp() {
        return getResources().getDisplayMetrics().heightPixels / density;
    }

    private Locale uiLocale() {
        Locale locale = getResources().getConfiguration().locale;
        return locale != null ? locale : Locale.getDefault();
    }

    private boolean isArabicLocale() {
        return "ar".equals(uiLocale().getLanguage());
    }

    private Typeface typefaceForLocale() {
        return isArabicLocale() ? arabicTypeface : latinTypeface;
    }

    private Typeface typefaceFor(String value) {
        if (value != null && value.matches("(?s).*[\\u0600-\\u06FF\\u0750-\\u077F\\u08A0-\\u08FF].*")) {
            return arabicTypeface;
        }
        return latinTypeface;
    }

    private String text(String english, String arabic) {
        return isArabicLocale() ? arabic : english;
    }

    private GradientDrawable roundRect(int fill, int stroke, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private View makePill(String label, int iconKind, final Runnable action) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(16), 0, dp(18), 0);
        pill.setBackground(roundRect(0x35FFFFFF, 0x55FFFFFF, 24));
        pill.setFocusable(true);
        pill.setClickable(true);
        LineIcon icon = new LineIcon(this, iconKind);
        pill.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView caption = new TextView(this);
        caption.setText(label);
        caption.setTextColor(Color.WHITE);
        caption.setTypeface(typefaceFor(label));
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        caption.setPadding(dp(9), 0, 0, 0);
        pill.addView(caption);
        pill.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { action.run(); }
        });
        pill.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                view.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                        .setDuration(150).start();
                view.setBackground(roundRect(focused ? 0x52FFFFFF : 0x35FFFFFF,
                        focused ? 0xBFFFFFFF : 0x55FFFFFF, 24));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        lp.leftMargin = dp(8);
        pill.setLayoutParams(lp);
        return pill;
    }

    private HorizontalScrollView horizontalScroller(LinearLayout row, int heightDp) {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setFillViewport(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setPadding(dp(6), dp(8), dp(6), dp(8));
        scroller.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroller.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return scroller;
    }

    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter);
        receiverRegistered = true;
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Set<String> hidden = new HashSet<String>();
        Collections.addAll(hidden, HIDDEN);
        List<Item> apps = new ArrayList<Item>();
        Set<String> seen = new HashSet<String>();

        String[] categories = {Intent.CATEGORY_LAUNCHER, "android.intent.category.LEANBACK_LAUNCHER"};
        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(category);
            List<ResolveInfo> matches = pm.queryIntentActivities(query, 0);
            for (ResolveInfo resolveInfo : matches) {
                ActivityInfo info = resolveInfo.activityInfo;
                String packageName = info.packageName;
                if (packageName.equals(getPackageName()) || hidden.contains(packageName) || !seen.add(packageName)) {
                    continue;
                }
                Item item = new Item();
                item.label = String.valueOf(resolveInfo.loadLabel(pm));
                item.packageName = packageName;
                Drawable banner = null;
                try { banner = info.loadBanner(pm); } catch (Exception ignored) { }
                if (banner != null) {
                    item.art = banner;
                    item.banner = true;
                } else {
                    item.art = resolveInfo.loadIcon(pm);
                }
                item.intent = new Intent(Intent.ACTION_MAIN)
                        .setComponent(new ComponentName(packageName, info.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                apps.add(item);
            }
        }

        Collections.sort(apps, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                return a.label.compareToIgnoreCase(b.label);
            }
        });

        List<Item> quick = new ArrayList<Item>();
        for (String[] entry : QUICK) {
            ComponentName component = new ComponentName(entry[2], entry[3]);
            try {
                pm.getActivityInfo(component, 0);
                Item item = new Item();
                item.label = text(entry[0], entry[1]);
                item.packageName = entry[2];
                item.iconKind = Integer.parseInt(entry[4]);
                item.intent = new Intent(Intent.ACTION_MAIN).setComponent(component)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                quick.add(item);
            } catch (PackageManager.NameNotFoundException ignored) { }
        }

        appsRow.removeAllViews();
        quickRow.removeAllViews();
        for (Item item : apps) appsRow.addView(createAppTile(item));
        for (Item item : quick) quickRow.addView(createQuickTile(item));

        if (appsRow.getChildCount() > 0) {
            appsRow.getChildAt(0).requestFocus();
        } else if (quickRow.getChildCount() > 0) {
            quickRow.getChildAt(0).requestFocus();
        } else {
            selectedTitle.setText(text("No apps found", "لم يتم العثور على تطبيقات"));
        }
    }

    private View createAppTile(final Item item) {
        final FrameLayout tile = new FrameLayout(this);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(150), dp(92));
        tileParams.setMargins(dp(7), dp(2), dp(7), dp(4));
        tile.setLayoutParams(tileParams);
        tile.setBackground(appTileBackground(item, false));
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setClipToOutline(true);
        tile.setElevation(dp(3));

        ImageView artwork = new ImageView(this);
        artwork.setScaleType(item.banner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        if (item.art != null) artwork.setImageDrawable(item.art);
        if (item.banner) {
            tile.addView(artwork, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.CENTER);
            artwork.setBackground(roundRect(0x30FFFFFF, 0x30FFFFFF, 17));
            artwork.setPadding(dp(8), dp(8), dp(8), dp(8));
            tile.addView(artwork, iconParams);
            View sheen = new View(this);
            sheen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x16FFFFFF, 0x00FFFFFF}));
            tile.addView(sheen, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }

        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try { startActivity(item.intent); }
                catch (Exception e) {
                    Toast.makeText(MainActivity.this, text("Unable to open ", "تعذر فتح ") + item.label,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean hasFocus) {
                view.animate().scaleX(hasFocus ? 1.10f : 1f).scaleY(hasFocus ? 1.10f : 1f)
                        .translationY(hasFocus ? -dp(7) : 0)
                        .translationZ(hasFocus ? dp(16) : 0).setDuration(170).start();
                view.setBackground(appTileBackground(item, hasFocus));
                if (hasFocus) {
                    focusedItem = item;
                    selectedTitle.setText(item.label);
                    selectedTitle.setTypeface(typefaceFor(item.label));
                    centerInScroller(view);
                    updateDynamicWallpaper(item);
                }
            }
        });
        return tile;
    }

    private Drawable appTileBackground(Item item, boolean focused) {
        int[] colors = PALETTE[(item.label.hashCode() & 0x7fffffff) % PALETTE.length];
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{colors[0], colors[1]});
        background.setCornerRadius(dp(20));
        background.setStroke(dp(focused ? 2 : 1), focused ? 0xEEFFFFFF : 0x48FFFFFF);
        return background;
    }

    private View createQuickTile(final Item item) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.HORIZONTAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(12), 0, dp(14), 0);
        tile.setBackground(roundRect(0x20FFFFFF, 0x32FFFFFF, 20));
        tile.setFocusable(true);
        tile.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        lp.setMargins(dp(5), dp(1), dp(5), dp(1));
        tile.setLayoutParams(lp);

        LineIcon icon = new LineIcon(this, item.iconKind);
        tile.addView(icon, new LinearLayout.LayoutParams(dp(19), dp(19)));
        TextView label = new TextView(this);
        label.setText(item.label);
        label.setTextColor(0xF2FFFFFF);
        label.setTypeface(typefaceFor(item.label));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = dp(8);
        tile.addView(label, labelParams);

        tile.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try { startActivity(item.intent); }
                catch (Exception e) {
                    Toast.makeText(MainActivity.this, text("Shortcut unavailable", "الاختصار غير متاح"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                view.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                        .setDuration(140).start();
                view.setBackground(roundRect(focused ? 0x50FFFFFF : 0x20FFFFFF,
                        focused ? 0xBFFFFFFF : 0x32FFFFFF, 20));
                if (focused) centerInScroller(view);
            }
        });
        return tile;
    }

    private void centerInScroller(View view) {
        if (!(view.getParent() instanceof LinearLayout)) return;
        View parent = (View)view.getParent();
        View scroller = (View)parent.getParent();
        if (scroller instanceof HorizontalScrollView) {
            int target = view.getLeft() + view.getWidth() / 2 - scroller.getWidth() / 2;
            ((HorizontalScrollView)scroller).smoothScrollTo(Math.max(0, target), 0);
        }
    }

    private void restoreWallpaper() {
        String mode = preferences.getString(PREF_WALLPAPER_MODE, "dynamic");
        if ("custom".equals(mode)) {
            File custom = customWallpaperFile();
            if (custom.exists() && custom.length() > 0) {
                showBitmap(loadFileBitmap(custom));
                return;
            }
            preferences.edit().putString(PREF_WALLPAPER_MODE, "dynamic").apply();
        }
        if (isPreset(mode)) {
            showBitmap(loadAssetWallpaper(mode));
        } else if (focusedItem != null) {
            updateDynamicWallpaper(focusedItem);
        }
    }

    private boolean isPreset(String mode) {
        return "aurora".equals(mode) || "ocean".equals(mode)
                || "violet".equals(mode) || "desert".equals(mode);
    }

    private void showWallpaperDialog() {
        final String[] options = {
                text("Dynamic · selected app glow", "ديناميكية · توهج التطبيق المحدد"),
                text("Aurora", "الشفق القطبي"),
                text("Ocean", "المحيط"),
                text("Violet dusk", "الشفق البنفسجي"),
                text("Desert dusk", "غروب الصحراء"),
                text("Choose a photo…", "اختيار صورة مخصصة…"),
                text("Remove custom wallpaper", "إزالة الخلفية المخصصة")
        };
        new AlertDialog.Builder(this)
                .setTitle(text("WALLPAPER", "الخلفية"))
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            preferences.edit().putString(PREF_WALLPAPER_MODE, "dynamic").apply();
                            updateDynamicWallpaper(focusedItem);
                            break;
                        case 1: setPreset("aurora"); break;
                        case 2: setPreset("ocean"); break;
                        case 3: setPreset("violet"); break;
                        case 4: setPreset("desert"); break;
                        case 5: chooseWallpaperImage(); break;
                        case 6:
                            customWallpaperFile().delete();
                            preferences.edit().putString(PREF_WALLPAPER_MODE, "dynamic").apply();
                            updateDynamicWallpaper(focusedItem);
                            break;
                    }
                }).show();
    }

    private void setPreset(String name) {
        preferences.edit().putString(PREF_WALLPAPER_MODE, name).apply();
        showBitmap(loadAssetWallpaper(name));
    }

    private void chooseWallpaperImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(intent, text("Choose wallpaper", "اختر الخلفية")),
                    REQUEST_WALLPAPER);
        } catch (Exception firstError) {
            try {
                Intent document = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                document.addCategory(Intent.CATEGORY_OPENABLE);
                document.setType("image/*");
                startActivityForResult(document, REQUEST_WALLPAPER);
            } catch (Exception secondError) {
                Toast.makeText(this, text("No image picker is available", "لا يوجد منتقي صور متاح"),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_WALLPAPER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            final Uri uri = data.getData();
            Toast.makeText(this, text("Setting wallpaper…", "جارٍ تعيين الخلفية…"), Toast.LENGTH_SHORT).show();
            new AsyncTask<Uri, Void, Boolean>() {
                @Override protected Boolean doInBackground(Uri... values) {
                    return copyCustomWallpaper(values[0]);
                }
                @Override protected void onPostExecute(Boolean success) {
                    if (success) {
                        preferences.edit().putString(PREF_WALLPAPER_MODE, "custom").apply();
                        showBitmap(loadFileBitmap(customWallpaperFile()));
                        Toast.makeText(MainActivity.this,
                                text("Custom wallpaper applied", "تم تعيين الخلفية المخصصة"), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this,
                                text("Could not read that image", "تعذرت قراءة الصورة"), Toast.LENGTH_LONG).show();
                    }
                }
            }.execute(uri);
        }
    }

    private boolean copyCustomWallpaper(Uri uri) {
        InputStream boundsStream = null;
        InputStream imageStream = null;
        Bitmap bitmap = null;
        FileOutputStream output = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            boundsStream = getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(boundsStream, null, bounds);
            if (boundsStream != null) boundsStream.close();
            boundsStream = null;
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false;

            int sample = 1;
            while (bounds.outWidth / sample > 1920 || bounds.outHeight / sample > 1080) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            imageStream = getContentResolver().openInputStream(uri);
            bitmap = BitmapFactory.decodeStream(imageStream, null, options);
            if (bitmap == null) return false;

            output = new FileOutputStream(customWallpaperFile());
            boolean saved = bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output);
            output.flush();
            return saved;
        } catch (Exception ignored) {
            return false;
        } finally {
            try { if (boundsStream != null) boundsStream.close(); } catch (Exception ignored) { }
            try { if (imageStream != null) imageStream.close(); } catch (Exception ignored) { }
            try { if (output != null) output.close(); } catch (Exception ignored) { }
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private File customWallpaperFile() {
        return new File(getFilesDir(), "custom_wallpaper.jpg");
    }

    private Bitmap loadFileBitmap(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            int targetWidth = getResources().getDisplayMetrics().widthPixels;
            int targetHeight = getResources().getDisplayMetrics().heightPixels;
            int sample = 1;
            while (bounds.outWidth / sample > targetWidth * 1.2f
                    || bounds.outHeight / sample > targetHeight * 1.2f) sample *= 2;
            options.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Exception ignored) { return null; }
    }

    private Bitmap loadAssetWallpaper(String name) {
        InputStream boundsStream = null;
        InputStream input = null;
        try {
            String path = "wallpapers/" + name + ".jpg";
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            boundsStream = getAssets().open(path);
            BitmapFactory.decodeStream(boundsStream, null, bounds);
            boundsStream.close();
            boundsStream = null;

            int targetWidth = getResources().getDisplayMetrics().widthPixels;
            int targetHeight = getResources().getDisplayMetrics().heightPixels;
            int sample = 1;
            while (bounds.outWidth / sample > targetWidth * 1.2f
                    || bounds.outHeight / sample > targetHeight * 1.2f) sample *= 2;

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inSampleSize = sample;
            input = getAssets().open(path);
            return BitmapFactory.decodeStream(input, null, options);
        } catch (Exception ignored) {
            return null;
        } finally {
            try { if (boundsStream != null) boundsStream.close(); } catch (Exception ignored) { }
            try { if (input != null) input.close(); } catch (Exception ignored) { }
        }
    }

    private void showBitmap(Bitmap bitmap) {
        if (bitmap == null) return;
        wallpaperView.animate().cancel();
        wallpaperView.setAlpha(0.0f);
        wallpaperView.setImageBitmap(bitmap);
        wallpaperView.animate().alpha(1.0f).setDuration(420).start();
    }

    private void updateDynamicWallpaper(final Item item) {
        String mode = preferences.getString(PREF_WALLPAPER_MODE, "dynamic");
        if (!"dynamic".equals(mode)) return;
        if (item == null || item.art == null) return;

        final int request = ++blurRequest;
        final String key = item.packageName == null ? item.label : item.packageName;
        Bitmap cached = blurredBackdrops.get(key);
        if (cached != null) {
            showBitmap(cached);
            return;
        }

        final Bitmap seed = makeBackdropSeed(item);
        blurExecutor.getQueue().clear();
        blurExecutor.execute(new Runnable() {
            @Override public void run() {
                Bitmap blurred = blurBitmap(seed, 25.0f);
                if (blurred != seed && !seed.isRecycled()) seed.recycle();
                if (blurred == null) return;
                blurredBackdrops.put(key, blurred);
                mainHandler.post(new Runnable() {
                    @Override public void run() {
                        if (request == blurRequest
                                && "dynamic".equals(preferences.getString(PREF_WALLPAPER_MODE, "dynamic"))) {
                            showBitmap(blurred);
                        }
                    }
                });
            }
        });
    }

    private Bitmap makeBackdropSeed(Item item) {
        Bitmap bitmap = Bitmap.createBitmap(BACKDROP_WIDTH, BACKDROP_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int accent = extractAccent(item.art);
        int dark = darken(accent, 0.22f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new LinearGradient(0, 0, BACKDROP_WIDTH, BACKDROP_HEIGHT,
                new int[]{dark, 0xFF10172F, 0xFF170F2B}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, BACKDROP_WIDTH, BACKDROP_HEIGHT, paint);

        paint.setShader(new RadialGradient(BACKDROP_WIDTH * 0.72f, BACKDROP_HEIGHT * 0.40f,
                BACKDROP_WIDTH * 0.78f, new int[]{withAlpha(accent, 190), withAlpha(accent, 0)},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, BACKDROP_WIDTH, BACKDROP_HEIGHT, paint);
        paint.setShader(new RadialGradient(BACKDROP_WIDTH * 0.18f, BACKDROP_HEIGHT * 0.88f,
                BACKDROP_WIDTH * 0.60f, new int[]{0x775B4A9A, 0x00231D60},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, BACKDROP_WIDTH, BACKDROP_HEIGHT, paint);
        paint.setShader(null);

        Drawable artwork = item.art;
        if (artwork.getConstantState() != null) artwork = artwork.getConstantState().newDrawable().mutate();
        int artWidth = item.banner ? 300 : 188;
        int artHeight = item.banner ? 170 : 188;
        int left = (BACKDROP_WIDTH - artWidth) / 2;
        int top = (BACKDROP_HEIGHT - artHeight) / 2;
        artwork.setBounds(left, top, left + artWidth, top + artHeight);
        artwork.setAlpha(215);
        canvas.save();
        canvas.rotate(-7f, BACKDROP_WIDTH / 2f, BACKDROP_HEIGHT / 2f);
        artwork.draw(canvas);
        canvas.restore();
        artwork.setAlpha(255);
        return bitmap;
    }

    private int extractAccent(Drawable drawable) {
        Bitmap sample = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sample);
        Drawable copy = drawable;
        if (drawable.getConstantState() != null) copy = drawable.getConstantState().newDrawable().mutate();
        copy.setBounds(0, 0, 40, 40);
        copy.draw(canvas);
        long red = 0, green = 0, blue = 0, count = 0;
        for (int y = 0; y < 40; y += 2) {
            for (int x = 0; x < 40; x += 2) {
                int color = sample.getPixel(x, y);
                if (Color.alpha(color) < 50) continue;
                int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
                int min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
                if (max - min < 22 || max < 70) continue;
                red += Color.red(color); green += Color.green(color); blue += Color.blue(color); count++;
            }
        }
        sample.recycle();
        if (count == 0) return 0xFF6484D8;
        return Color.rgb((int)(red / count), (int)(green / count), (int)(blue / count));
    }

    private int darken(int color, float factor) {
        return Color.rgb((int)(Color.red(color) * factor),
                (int)(Color.green(color) * factor), (int)(Color.blue(color) * factor));
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private Bitmap blurBitmap(Bitmap source, float radius) {
        RenderScript renderScript = null;
        Allocation input = null;
        Allocation output = null;
        ScriptIntrinsicBlur blur = null;
        try {
            renderScript = RenderScript.create(getApplicationContext());
            input = Allocation.createFromBitmap(renderScript, source);
            output = Allocation.createTyped(renderScript, input.getType());
            blur = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript));
            blur.setRadius(Math.min(25.0f, Math.max(1.0f, radius)));
            blur.setInput(input);
            blur.forEach(output);
            Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
            output.copyTo(result);
            return result;
        } catch (Throwable ignored) {
            return source;
        } finally {
            try { if (blur != null) blur.destroy(); } catch (Exception ignored) { }
            try { if (input != null) input.destroy(); } catch (Exception ignored) { }
            try { if (output != null) output.destroy(); } catch (Exception ignored) { }
            try { if (renderScript != null) renderScript.destroy(); } catch (Exception ignored) { }
        }
    }

    private int dp(float value) {
        return (int)(value * density + 0.5f);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (appsRow != null && appsRow.getChildCount() > 0) {
            appsScroller.smoothScrollTo(0, 0);
            appsRow.getChildAt(0).requestFocus();
        }
    }

    @Override
    public void onBackPressed() {
        // Keep the launcher available as the HOME surface.
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(packageReceiver); } catch (Exception ignored) { }
            receiverRegistered = false;
        }
        blurRequest++;
        blurExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Simple custom vector line icons; no external icon pack or network dependency. */
    private class LineIcon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int kind;

        LineIcon(Context context, int iconKind) {
            super(context);
            kind = iconKind;
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth(), getHeight()) / 24.0f;
            canvas.save();
            canvas.translate((getWidth() - 24 * scale) / 2f, (getHeight() - 24 * scale) / 2f);
            canvas.scale(scale, scale);
            RectF rect = new RectF(3, 4, 21, 17);
            switch (kind) {
                case 0: // television
                    canvas.drawRoundRect(rect, 2, 2, paint);
                    canvas.drawLine(8, 21, 16, 21, paint);
                    canvas.drawLine(12, 17, 12, 21, paint);
                    break;
                case 1: // source windows
                    canvas.drawRoundRect(new RectF(3, 4, 14, 15), 2, 2, paint);
                    canvas.drawRoundRect(new RectF(10, 9, 21, 20), 2, 2, paint);
                    break;
                case 2: // settings gear
                    canvas.drawCircle(12, 12, 7.2f, paint);
                    canvas.drawCircle(12, 12, 2.6f, paint);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI * i / 4.0;
                        canvas.drawLine((float)(12 + Math.cos(a) * 8.1),
                                (float)(12 + Math.sin(a) * 8.1),
                                (float)(12 + Math.cos(a) * 10.2),
                                (float)(12 + Math.sin(a) * 10.2), paint);
                    }
                    break;
                case 3: // wifi
                    Path wifi = new Path();
                    wifi.moveTo(3, 9); wifi.cubicTo(8, 4, 16, 4, 21, 9);
                    canvas.drawPath(wifi, paint);
                    wifi.reset(); wifi.moveTo(6.5f, 12.5f); wifi.cubicTo(10, 9.5f, 14, 9.5f, 17.5f, 12.5f);
                    canvas.drawPath(wifi, paint);
                    wifi.reset(); wifi.moveTo(10, 16); wifi.quadTo(12, 14, 14, 16);
                    canvas.drawPath(wifi, paint);
                    canvas.drawCircle(12, 19, 1, paint);
                    break;
                case 4: // browser globe
                    canvas.drawCircle(12, 12, 8.5f, paint);
                    canvas.drawOval(new RectF(8, 3.5f, 16, 20.5f), paint);
                    canvas.drawLine(4, 9, 20, 9, paint);
                    canvas.drawLine(4, 15, 20, 15, paint);
                    break;
                case 5: // media play
                    canvas.drawRoundRect(new RectF(3, 4, 21, 20), 3, 3, paint);
                    Path play = new Path();
                    play.moveTo(10, 8); play.lineTo(16, 12); play.lineTo(10, 16); play.close();
                    Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
                    fill.setColor(Color.WHITE); fill.setStyle(Paint.Style.FILL);
                    canvas.drawPath(play, fill);
                    break;
                case 6: // cast
                    canvas.drawRoundRect(new RectF(4, 4, 21, 17), 2, 2, paint);
                    canvas.drawArc(new RectF(3, 12, 13, 22), 270, 90, false, paint);
                    canvas.drawArc(new RectF(3, 15, 10, 22), 270, 90, false, paint);
                    canvas.drawCircle(5, 19, 1, paint);
                    break;
                case 7: // wallpaper landscape
                    canvas.drawRoundRect(new RectF(3, 4, 21, 20), 3, 3, paint);
                    canvas.drawCircle(16.5f, 8.5f, 1.7f, paint);
                    Path hill = new Path();
                    hill.moveTo(5, 17); hill.lineTo(9.5f, 11.5f); hill.lineTo(12.5f, 15);
                    hill.lineTo(16, 11); hill.lineTo(20, 17);
                    canvas.drawPath(hill, paint);
                    break;
            }
            canvas.restore();
        }
    }
}
