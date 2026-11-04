package com.karrar.tvlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
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
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.MetricAffectingSpan;
import android.view.KeyEvent;
import android.widget.ScrollView;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
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
    private static final String PREF_HIDDEN_APPS = "hidden_apps";
    private static final String PREF_LAST_USED_PREFIX = "last_used_";
    private static final int REQUEST_WALLPAPER = 4102;
    private static final int BACKDROP_WIDTH = 384;
    private static final int BACKDROP_HEIGHT = 216;
    private static final long WEATHER_REFRESH_INTERVAL_MS = 30L * 60L * 1000L;
    private static final long NETWORK_TIME_REFRESH_INTERVAL_MS = 6L * 60L * 60L * 1000L;
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;

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
        Drawable icon;
        boolean banner;
        int iconKind;
        Intent intent;
    }

    private static class WeatherInfo {
        String temperature;
        String condition;
        String icon;
        String city;
    }

    private static class MenuOption {
        final String title;
        final Runnable action;

        MenuOption(int ignoredIconKind, String title, String ignoredSubtitle, Runnable action) {
            this.title = title;
            this.action = action;
        }
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
    private TextView weatherTemperature;
    private TextView weatherCondition;
    private TextView clockView;
    private TextView dateView;
    private TextView weatherGlyph;
    private boolean weatherRequestRunning;
    private boolean weatherLoaded;
    private long lastWeatherRequestAt;
    private String lastWeatherLocationKey = "";
    private boolean activityDestroyed;
    private boolean appLaunchPending;
    private HorizontalScrollView quickScroller;
    private Typeface latinTypeface;
    private Typeface arabicTypeface;
    private Typeface clockTypeface;
    private Typeface weatherIconsTypeface;
    private Item focusedItem;
    private boolean receiverRegistered;
    private boolean systemReceiverRegistered;
    private int appLoadRetryAttempt;
    private int weatherRetryAttempt;
    private volatile boolean networkTimeAvailable;
    private volatile long networkTimeAtSyncMs;
    private volatile long networkTimeAtSyncElapsedMs;
    private boolean networkTimeRequestRunning;
    private int networkTimeRetryAttempt;
    private volatile int blurRequest = 0;
    private final Handler mainHandler = new Handler();
    private final Runnable weatherRefreshRunnable = new Runnable() {
        @Override public void run() {
            refreshWeather();
            if (!activityDestroyed) {
                mainHandler.postDelayed(this, WEATHER_REFRESH_INTERVAL_MS);
            }
        }
    };
    private final Runnable clockUpdateRunnable = new Runnable() {
        @Override public void run() {
            updateClockText();
            if (!activityDestroyed) {
                mainHandler.postDelayed(this, 1000L);
            }
        }
    };
    private final Runnable appLoadRetryRunnable = new Runnable() {
        @Override public void run() {
            if (!activityDestroyed && appsRow != null) loadApps();
        }
    };
    private final Runnable weatherRetryRunnable = new Runnable() {
        @Override public void run() {
            if (!activityDestroyed && !weatherLoaded) refreshWeather();
        }
    };
    private final Runnable networkTimeRetryRunnable = new Runnable() {
        @Override public void run() {
            if (!activityDestroyed && !networkTimeAvailable) syncNetworkTime();
        }
    };
    private final Runnable networkTimeRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (!activityDestroyed) syncNetworkTime();
        }
    };
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
            scheduleAppsReload(350L);
        }
    };

    private final BroadcastReceiver systemStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent == null ? null : intent.getAction();
            boolean timeWasSet = Intent.ACTION_TIME_CHANGED.equals(action)
                    || Intent.ACTION_DATE_CHANGED.equals(action);
            boolean timeZoneChanged = Intent.ACTION_TIMEZONE_CHANGED.equals(action);
            if (timeWasSet || timeZoneChanged || Intent.ACTION_TIME_TICK.equals(action)) {
                updateClockText();
            }

            // An early HTTPS weather request can fail while Android still has
            // the factory/boot clock. Retry as soon as Android broadcasts that
            // its wall clock has been corrected.
            if ((timeWasSet || timeZoneChanged) && !weatherLoaded) {
                weatherRetryAttempt = 0;
                mainHandler.removeCallbacks(weatherRetryRunnable);
                mainHandler.postDelayed(weatherRetryRunnable, 900L);
            }

            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || Intent.ACTION_USER_PRESENT.equals(action)
                    || Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)) {
                scheduleAppsReload(900L);
            }

            if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                    || Intent.ACTION_USER_PRESENT.equals(action)
                    || android.net.ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                weatherRetryAttempt = 0;
                mainHandler.removeCallbacks(weatherRetryRunnable);
                mainHandler.postDelayed(weatherRetryRunnable, 1200L);

                if (!networkTimeAvailable) {
                    mainHandler.removeCallbacks(networkTimeRetryRunnable);
                    mainHandler.postDelayed(networkTimeRetryRunnable, 1200L);
                }
            }
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
        showSplashScreen();
        registerPackageReceiver();
        registerSystemStateReceiver();
        restoreWallpaper();
        refreshWeather();
        updateClockText();
        syncNetworkTime();
        mainHandler.postDelayed(weatherRefreshRunnable, WEATHER_REFRESH_INTERVAL_MS);
        mainHandler.post(clockUpdateRunnable);
    }

    private void loadTypefaces() {
        try { latinTypeface = Typeface.createFromAsset(getAssets(), "fonts/InterDisplay-Medium.otf"); }
        catch (Exception ignored) { latinTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); }
        try { clockTypeface = Typeface.createFromAsset(getAssets(), "fonts/manrope-extrabold.ttf"); }
        catch (Exception ignored) { clockTypeface = Typeface.create(latinTypeface, Typeface.BOLD); }
        try { weatherIconsTypeface = Typeface.createFromAsset(getAssets(), "fonts/weathericons-regular-webfont.ttf"); }
        catch (Exception ignored) { weatherIconsTypeface = Typeface.DEFAULT; }
        try { arabicTypeface = Typeface.createFromAsset(getAssets(), "fonts/NotoKufiArabic-Regular.ttf"); }
        catch (Exception ignored) { arabicTypeface = Typeface.create("sans-serif", Typeface.NORMAL); }
    }

    private void buildInterface() {
        root = new FrameLayout(this);
        root.setFocusable(false);
        root.setClipChildren(false);
        root.setClipToPadding(false);

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
        topBar.setPadding(dp(48), dp(10), dp(48), 0);
        topBar.setClipChildren(false);
        topBar.setClipToPadding(false);

        // Live weather comes from Open-Meteo, independent of the TV manufacturer.
        LinearLayout weatherPanel = new LinearLayout(this);
        weatherPanel.setOrientation(LinearLayout.HORIZONTAL);
        weatherPanel.setGravity(Gravity.CENTER_VERTICAL | (isArabicLocale() ? Gravity.RIGHT : Gravity.LEFT));
        weatherPanel.setPadding(dp(8), dp(4), dp(8), dp(4));
        weatherPanel.setBackground(roundRect(0x00000000, 0x00000000, 16));
        weatherPanel.setFocusable(true);
        weatherPanel.setClickable(true);
        weatherPanel.setClipChildren(false);
        weatherPanel.setClipToPadding(false);
        weatherPanel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { openWeatherApp(); }
        });
        weatherPanel.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                view.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f)
                        .translationZ(focused ? dp(8) : 0).setDuration(150).start();
                view.setBackground(roundRect(focused ? 0x35FFFFFF : 0x00000000,
                        focused ? 0xAAFFFFFF : 0x00000000, 16));
            }
        });

        weatherGlyph = new TextView(this);
        weatherGlyph.setGravity(Gravity.CENTER);
        weatherGlyph.setText(String.valueOf((char)0xF00D));
        weatherGlyph.setTextColor(0xFFFFD77A);
        weatherGlyph.setTypeface(weatherIconsTypeface);
        weatherGlyph.setTextSize(TypedValue.COMPLEX_UNIT_SP, 38);
        weatherGlyph.setIncludeFontPadding(false);
        weatherGlyph.setSingleLine(true);
        weatherGlyph.setTextDirection(View.TEXT_DIRECTION_LTR);
        LinearLayout.LayoutParams weatherIconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        if (isArabicLocale()) weatherIconParams.leftMargin = dp(16);
        else weatherIconParams.rightMargin = dp(16);
        weatherPanel.addView(weatherGlyph, weatherIconParams);

        LinearLayout weatherText = new LinearLayout(this);
        weatherText.setOrientation(LinearLayout.VERTICAL);
        weatherText.setGravity(isArabicLocale() ? Gravity.RIGHT : Gravity.LEFT);
        weatherTemperature = new TextView(this);
        weatherTemperature.setText("--°");
        weatherTemperature.setTextColor(Color.WHITE);
        weatherTemperature.setTypeface(clockTypeface);
        weatherTemperature.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        weatherTemperature.setIncludeFontPadding(false);
        weatherTemperature.setSingleLine(true);
        weatherTemperature.setTextDirection(View.TEXT_DIRECTION_LTR);
        weatherText.addView(weatherTemperature, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        weatherCondition = new TextView(this);
        weatherCondition.setText(text("Loading weather…", "جارٍ تحميل الطقس…"));
        weatherCondition.setTextColor(0xDFFFFFFF);
        weatherCondition.setTypeface(typefaceForLocale());
        weatherCondition.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        weatherCondition.setIncludeFontPadding(false);
        weatherCondition.setSingleLine(true);
        weatherCondition.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherCondition.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        weatherText.addView(weatherCondition, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        weatherPanel.addView(weatherText);

        topBar.addView(weatherPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(60)));
        View topBarSpacer = new View(this);
        topBar.addView(topBarSpacer, new LinearLayout.LayoutParams(0, dp(1), 1f));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        actions.addView(makePill(text("Apps", "التطبيقات"), 8, new Runnable() {
            @Override public void run() { showHiddenAppsDialog(); }
        }));
        actions.addView(makePill(text("Wallpaper", "الخلفية"), 7, new Runnable() {
            @Override public void run() { showWallpaperDialog(); }
        }));
        topBar.addView(actions);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(84), Gravity.TOP);
        root.addView(topBar, topParams);

        // Oversized, vertically stretched clock, deliberately centred on the canvas.
        LinearLayout clockStack = new LinearLayout(this);
        clockStack.setOrientation(LinearLayout.VERTICAL);
        clockStack.setGravity(Gravity.CENTER);
        clockStack.setClipChildren(false);
        clockStack.setClipToPadding(false);

        clockView = new TextView(this);
        clockView.setTextColor(Color.WHITE);
        clockView.setTypeface(clockTypeface);
        clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, clockSizeSp());
        clockView.setIncludeFontPadding(false);
        clockView.setGravity(Gravity.CENTER);
        clockView.setTextDirection(View.TEXT_DIRECTION_LTR);
        clockView.setLetterSpacing(0.025f);
        clockView.setScaleY(1.34f);
        clockView.setScaleX(1.03f);
        clockStack.addView(clockView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(clockSizeSp() * 1.50f)));

        dateView = new TextView(this);
        dateView.setTextColor(0xD8FFFFFF);
        dateView.setTypeface(typefaceForLocale());
        dateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        dateView.setIncludeFontPadding(false);
        dateView.setGravity(Gravity.CENTER);
        dateView.setLetterSpacing(0.025f);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dateParams.topMargin = dp(12);
        clockStack.addView(dateView, dateParams);

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
        dock.setClipChildren(true);
        dock.setClipToPadding(true);

        FrameLayout heading = new FrameLayout(this);
        heading.setClipChildren(false);
        heading.setClipToPadding(false);
        appSectionLabel = new TextView(this);
        appSectionLabel.setText(text("YOUR APPS", "تطبيقاتك"));
        appSectionLabel.setTextColor(0xBFFFFFFF);
        appSectionLabel.setTypeface(typefaceFor(text("YOUR APPS", "تطبيقاتك")));
        appSectionLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        appSectionLabel.setLetterSpacing(isArabicLocale() ? 0f : 0.13f);
        appSectionLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        appSectionLabel.setSingleLine(true);
        appSectionLabel.setMaxWidth(dp(132));
        FrameLayout.LayoutParams sectionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42),
                Gravity.START | Gravity.CENTER_VERTICAL);
        heading.addView(appSectionLabel, sectionParams);

        selectedTitle = new TextView(this);
        selectedTitle.setTextColor(Color.WHITE);
        selectedTitle.setTypeface(latinTypeface);
        selectedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        selectedTitle.setGravity(Gravity.CENTER);
        selectedTitle.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        selectedTitle.setIncludeFontPadding(true);
        selectedTitle.setSingleLine(true);
        selectedTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // Symmetric side insets keep the title truly centred without colliding with YOUR APPS.
        selectedTitle.setPadding(dp(136), 0, dp(136), 0);
        FrameLayout.LayoutParams selectedTitleParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(44), Gravity.CENTER);
        heading.addView(selectedTitle, selectedTitleParams);
        dock.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));

        appsRow = new LinearLayout(this);
        appsRow.setOrientation(LinearLayout.HORIZONTAL);
        appsRow.setGravity(Gravity.CENTER_VERTICAL);
        appsRow.setClipChildren(false);
        int appsScrollerHeight = (int)Math.min(148, Math.max(132, screenHeightDp() * 0.26f));
        appsScroller = horizontalScroller(appsRow, appsScrollerHeight);
        LinearLayout.LayoutParams appScrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, appsScroller.getLayoutParams().height);
        appScrollParams.topMargin = dp(2);
        dock.addView(appsScroller, appScrollParams);

        quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        quickRow.setGravity(Gravity.CENTER_VERTICAL);
        quickRow.setClipChildren(false);
        quickScroller = horizontalScroller(quickRow, 54);
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

    private void showSplashScreen() {
        final FrameLayout splash = new FrameLayout(this);
        splash.setBackgroundColor(0xFF070B18);
        splash.setFocusable(true);
        splash.setFocusableInTouchMode(true);
        splash.setClickable(true);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.apollolauncher_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int logoWidth = Math.min((int)(getResources().getDisplayMetrics().widthPixels * 0.68f), dp(680));
        FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(logoWidth, dp(150), Gravity.CENTER);
        splash.addView(logo, logoParams);

        root.addView(splash, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        splash.requestFocus();

        mainHandler.postDelayed(new Runnable() {
            @Override public void run() {
                if (activityDestroyed || root == null || splash.getParent() != root) return;
                splash.animate().alpha(0f).setDuration(280).withEndAction(new Runnable() {
                    @Override public void run() {
                        if (root != null) root.removeView(splash);
                        loadApps();
                        focusFirstAppTile();
                    }
                }).start();
            }
        }, 950L);
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

    private String toWesternDigits(String value) {
        if (value == null || value.length() == 0) return value;
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            int codePoint = (int)c;
            if (codePoint >= 1632 && codePoint <= 1641) {
                c = (char)('0' + (codePoint - 1632));
            } else if (codePoint >= 1776 && codePoint <= 1785) {
                c = (char)('0' + (codePoint - 1776));
            }
            result.append(c);
        }
        return result.toString();
    }

    private CharSequence formatDigits(CharSequence value) {
        String normalized = toWesternDigits(value == null ? "" : value.toString());
        SpannableString styled = new SpannableString(normalized);
        int start = -1;
        for (int i = 0; i <= normalized.length(); i++) {
            boolean digit = i < normalized.length() && normalized.charAt(i) >= '0'
                    && normalized.charAt(i) <= '9';
            if (digit && start < 0) start = i;
            if (!digit && start >= 0) {
                styled.setSpan(new LatinDigitsSpan(latinTypeface), start, i,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = -1;
            }
        }
        return styled;
    }

    private void updateClockText() {
        if (clockView == null || dateView == null) return;

        long nowMillis;
        if (networkTimeAvailable) {
            long elapsed = Math.max(0L,
                    SystemClock.elapsedRealtime() - networkTimeAtSyncElapsedMs);
            nowMillis = networkTimeAtSyncMs + elapsed;
        } else {
            nowMillis = System.currentTimeMillis();
            if (isSystemClockObviouslyInvalid(nowMillis)) {
                // Do not flash the firmware's stale date while Android is
                // waiting for its first automatic time synchronization.
                clockView.setText("--:--");
                dateView.setTypeface(typefaceForLocale());
                dateView.setText(text("Synchronizing time…", "جارٍ مزامنة الوقت…"));
                return;
            }
        }

        Date now = new Date(nowMillis);
        String timePattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm";
        SimpleDateFormat timeFormat = new SimpleDateFormat(timePattern, Locale.US);
        clockView.setText(timeFormat.format(now));

        SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE  ·  d MMMM", uiLocale());
        dateView.setTypeface(typefaceForLocale());
        dateView.setText(formatDigits(dateFormat.format(now)));
    }

    private boolean isSystemClockObviouslyInvalid(long timeMillis) {
        // Most legacy TV boxes restore their firmware-build date after a cold
        // boot until NTP finishes. A real current clock should be newer than
        // the firmware image by at least one day.
        long minimumValidTime = Build.TIME > 0L
                ? Build.TIME + DAY_MS : 946684800000L;
        return timeMillis < minimumValidTime;
    }

    private void syncNetworkTime() {
        if (activityDestroyed || networkTimeRequestRunning) return;
        networkTimeRequestRunning = true;

        new AsyncTask<Void, Void, Long>() {
            @Override protected Long doInBackground(Void... ignored) {
                try {
                    return NetworkTimeClient.fetchUtcTimeMillis();
                } catch (Exception error) {
                    android.util.Log.w("ApolloClock",
                            "Network time synchronization failed: " + error.getMessage());
                    return null;
                }
            }

            @Override protected void onPostExecute(Long serverTimeMillis) {
                networkTimeRequestRunning = false;
                if (activityDestroyed) return;

                if (serverTimeMillis != null && serverTimeMillis > 0L) {
                    networkTimeAtSyncMs = serverTimeMillis;
                    networkTimeAtSyncElapsedMs = SystemClock.elapsedRealtime();
                    networkTimeAvailable = true;
                    networkTimeRetryAttempt = 0;
                    mainHandler.removeCallbacks(networkTimeRetryRunnable);
                    updateClockText();

                    mainHandler.removeCallbacks(networkTimeRefreshRunnable);
                    mainHandler.postDelayed(networkTimeRefreshRunnable,
                            NETWORK_TIME_REFRESH_INTERVAL_MS);
                } else if (networkTimeAvailable) {
                    // Keep the last good monotonic clock if a later refresh fails.
                    mainHandler.removeCallbacks(networkTimeRefreshRunnable);
                    mainHandler.postDelayed(networkTimeRefreshRunnable,
                            NETWORK_TIME_REFRESH_INTERVAL_MS);
                } else {
                    scheduleNetworkTimeRetry();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void scheduleNetworkTimeRetry() {
        if (activityDestroyed || networkTimeAvailable) return;
        long[] delays = {2000L, 4000L, 8000L, 15000L, 30000L, 60000L};
        long delay = delays[Math.min(networkTimeRetryAttempt, delays.length - 1)];
        if (networkTimeRetryAttempt < delays.length - 1) networkTimeRetryAttempt++;
        mainHandler.removeCallbacks(networkTimeRetryRunnable);
        mainHandler.postDelayed(networkTimeRetryRunnable, delay);
    }

    private void openWeatherApp() {
        try {
            startActivity(new Intent(MainActivity.this, WeatherActivity.class));
        } catch (Exception error) {
            Toast.makeText(this, text("Could not open the weather page", "تعذر فتح صفحة الطقس"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshWeather() {
        if (weatherRequestRunning || activityDestroyed) return;
        final WeatherService.Place place = WeatherService.getSavedLocation(this);
        final String locationKey = place.latitude + "," + place.longitude;
        long now = System.currentTimeMillis();
        if (weatherLoaded && locationKey.equals(lastWeatherLocationKey)
                && now - lastWeatherRequestAt < 10L * 60L * 1000L) return;

        weatherRequestRunning = true;
        lastWeatherRequestAt = now;
        lastWeatherLocationKey = locationKey;
        new AsyncTask<Void, Void, WeatherInfo>() {
            @Override protected WeatherInfo doInBackground(Void... ignored) {
                try {
                    JSONObject forecast = WeatherService.fetchForecast(MainActivity.this, place.latitude, place.longitude);
                    JSONObject current = forecast.getJSONObject("current");
                    WeatherInfo info = new WeatherInfo();
                    double value = current.optDouble("temperature_2m", Double.NaN);
                    info.temperature = Double.isNaN(value) ? "--°" : Math.round(value) + "\u00B0";
                    int code = current.optInt("weather_code", -1);
                    info.condition = WeatherService.condition(code, isArabicLocale());
                    info.icon = WeatherService.icon(code, current.optInt("is_day", 1) == 1);
                    info.city = place.shortName();
                    return info;
                } catch (Exception ignoredError) { android.util.Log.e("ApolloWeather", "Launcher weather request failed", ignoredError); return null; }
            }

            @Override protected void onPostExecute(WeatherInfo info) {
                weatherRequestRunning = false;
                if (activityDestroyed || weatherTemperature == null || weatherCondition == null) return;
                if (info != null) {
                    weatherTemperature.setText(formatDigits(info.temperature));
                    weatherCondition.setText(formatDigits(info.condition + " · " + info.city));
                    if (weatherGlyph != null) {
                        weatherGlyph.setText(info.icon);
                        weatherGlyph.setContentDescription(info.condition);
                    }
                    weatherLoaded = true;
                    weatherRetryAttempt = 0;
                    mainHandler.removeCallbacks(weatherRetryRunnable);
                } else if (!weatherLoaded) {
                    weatherTemperature.setText("--°");
                    weatherCondition.setText(text("Weather unavailable", "الطقس غير متاح"));
                    scheduleWeatherRetry();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
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
        pill.setClipChildren(false);
        pill.setClipToPadding(false);
        pill.setElevation(dp(3));
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
                view.animate().scaleX(focused ? 1.10f : 1f).scaleY(focused ? 1.10f : 1f)
                        .translationZ(focused ? dp(10) : 0)
                        .setDuration(150).start();
                view.setBackground(roundRect(focused ? 0x52FFFFFF : 0x35FFFFFF,
                        focused ? 0xBFFFFFFF : 0x55FFFFFF, 24));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
        lp.setMargins(dp(5), dp(4), dp(5), dp(4));
        pill.setLayoutParams(lp);
        return pill;
    }

    private HorizontalScrollView horizontalScroller(LinearLayout row, int heightDp) {
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(true);
        scroller.setClipToPadding(true);
        scroller.setFillViewport(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        int layoutDirection = isArabicLocale()
                ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
        scroller.setLayoutDirection(layoutDirection);
        row.setLayoutDirection(layoutDirection);
        int verticalPadding = heightDp <= 64 ? 4 : 12;
        scroller.setPadding(dp(8), dp(verticalPadding), dp(8), dp(verticalPadding));
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

    private void registerSystemStateReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        filter.addAction(android.net.ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIME_TICK);
        registerReceiver(systemStateReceiver, filter);
        systemReceiverRegistered = true;
    }

    private void scheduleAppsReload(long delayMillis) {
        if (activityDestroyed || appsRow == null) return;
        mainHandler.removeCallbacks(appLoadRetryRunnable);
        mainHandler.postDelayed(appLoadRetryRunnable, Math.max(0L, delayMillis));
    }

    private void scheduleAppsRetry() {
        if (activityDestroyed) return;
        // PackageManager on older Android TV firmware may not expose all
        // launcher activities until late in boot. Keep retrying, with a capped
        // backoff, instead of giving up after the first few seconds.
        long[] delays = {600L, 1200L, 2500L, 5000L, 10000L, 15000L, 20000L, 30000L};
        long delay = delays[Math.min(appLoadRetryAttempt, delays.length - 1)];
        if (appLoadRetryAttempt < delays.length - 1) appLoadRetryAttempt++;
        mainHandler.removeCallbacks(appLoadRetryRunnable);
        mainHandler.postDelayed(appLoadRetryRunnable, delay);
    }

    private void scheduleWeatherRetry() {
        if (activityDestroyed || weatherLoaded) return;
        long[] delays = {3000L, 5000L, 10000L, 20000L, 30000L, 60000L};
        long delay = delays[Math.min(weatherRetryAttempt, delays.length - 1)];
        if (weatherRetryAttempt < delays.length - 1) weatherRetryAttempt++;
        mainHandler.removeCallbacks(weatherRetryRunnable);
        mainHandler.postDelayed(weatherRetryRunnable, delay);
    }

    private Set<String> getUserHiddenApps() {
        Set<String> saved = preferences.getStringSet(PREF_HIDDEN_APPS,
                Collections.<String>emptySet());
        return new HashSet<String>(saved);
    }

    private void hideApp(String packageName) {
        Set<String> hidden = getUserHiddenApps();
        hidden.add(packageName);
        preferences.edit().putStringSet(PREF_HIDDEN_APPS, hidden).apply();
        loadApps();
    }

    private void restoreHiddenApp(String packageName) {
        Set<String> hidden = getUserHiddenApps();
        hidden.remove(packageName);
        preferences.edit().putStringSet(PREF_HIDDEN_APPS, hidden).apply();
        loadApps();
    }

    private boolean isSystemApp(String packageName) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(packageName, 0);
            return (info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    private void openAppSettings(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception ignored) {
            Toast.makeText(this, text("Could not open app settings", "تعذر فتح إعدادات التطبيق"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void uninstallApp(String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
            startActivity(intent);
        } catch (Exception ignored) {
            Toast.makeText(this, text("Uninstall is unavailable", "إلغاء التثبيت غير متاح"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    private Drawable menuPanelBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xF01B202B, 0xF0131720});
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), 0x66FFFFFF);
        return background;
    }

    private Drawable menuRowBackground(boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(focused ? 0x32FFFFFF : 0x00000000);
        background.setCornerRadius(dp(9));
        background.setStroke(dp(1), focused ? 0xCCFFFFFF : 0x00000000);
        return background;
    }

    private void showLauncherMenu(String title, List<MenuOption> entries) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(13), dp(14), dp(13));
        panel.setBackground(menuPanelBackground());
        panel.setElevation(dp(8));
        panel.setClipChildren(false);
        panel.setClipToPadding(false);

        TextView headingTitle = new TextView(this);
        headingTitle.setText(formatDigits(title));
        headingTitle.setTextColor(Color.WHITE);
        headingTitle.setTypeface(typefaceFor(title));
        headingTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        headingTitle.setIncludeFontPadding(true);
        headingTitle.setSingleLine(true);
        headingTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        headingTitle.setGravity(isArabicLocale() ? Gravity.RIGHT : Gravity.LEFT);
        panel.addView(headingTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

        View divider = new View(this);
        divider.setBackgroundColor(0x40FFFFFF);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.topMargin = dp(5);
        dividerParams.bottomMargin = dp(7);
        panel.addView(divider, dividerParams);

        LinearLayout optionList = new LinearLayout(this);
        optionList.setOrientation(LinearLayout.VERTICAL);
        optionList.setPadding(dp(1), dp(1), dp(1), dp(1));
        optionList.setClipChildren(false);
        optionList.setClipToPadding(false);
        for (MenuOption entry : entries) addOptionRow(optionList, dialog, entry);

        BoundedScrollView scroll = new BoundedScrollView(this,
                dp(Math.min(360, Math.max(140, screenHeightDp() * 0.52f))));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.setClipChildren(true);
        scroll.setClipToPadding(true);
        scroll.addView(optionList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(panel);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = Math.min(dp(460),
                    (int)(getResources().getDisplayMetrics().widthPixels * 0.56f));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            params.dimAmount = 0.50f;
            window.setAttributes(params);
        }
        if (optionList.getChildCount() > 0) optionList.getChildAt(0).requestFocus();
    }

    private void addOptionRow(LinearLayout container, final Dialog dialog,
                              final MenuOption option) {
        TextView row = new TextView(this);
        row.setText(formatDigits(option.title));
        row.setTextColor(Color.WHITE);
        row.setTypeface(typefaceFor(option.title));
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        row.setIncludeFontPadding(true);
        row.setSingleLine(true);
        row.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.setGravity((isArabicLocale() ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setBackground(menuRowBackground(false));
        row.setFocusable(true);
        row.setClickable(true);
        row.setMinimumHeight(dp(46));
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean focused) {
                view.setBackground(menuRowBackground(focused));
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                dialog.dismiss();
                if (option.action != null) option.action.run();
            }
        });

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(2), 0, dp(2));
        container.addView(row, rowParams);
    }

    private void showAppOptions(final Item item) {
        List<MenuOption> entries = new ArrayList<MenuOption>();
        entries.add(new MenuOption(9,
                text("Hide app", "إخفاء التطبيق"),
                text("Remove it from this launcher", "إزالته من هذه الواجهة"),
                new Runnable() {
                    @Override public void run() {
                        hideApp(item.packageName);
                        Toast.makeText(MainActivity.this,
                                text("App hidden", "تم إخفاء التطبيق"), Toast.LENGTH_SHORT).show();
                    }
                }));

        if (!isSystemApp(item.packageName)) {
            entries.add(new MenuOption(10,
                    text("Uninstall app", "إلغاء تثبيت التطبيق"),
                    text("Android will ask you to confirm", "سيطلب Android تأكيد العملية"),
                    new Runnable() {
                        @Override public void run() { uninstallApp(item.packageName); }
                    }));
        }

        entries.add(new MenuOption(11,
                text("Force stop / background", "إيقاف التطبيق / الخلفية"),
                text("Open Android app settings", "فتح إعدادات التطبيق لإيقافه"),
                new Runnable() {
                    @Override public void run() { openAppSettings(item.packageName); }
                }));

        showLauncherMenu(item.label, entries);
    }

    private void showHiddenAppsDialog() {
        final Set<String> hidden = getUserHiddenApps();
        if (hidden.isEmpty()) {
            Toast.makeText(this, text("No hidden apps", "لا توجد تطبيقات مخفية"),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        final List<String> packages = new ArrayList<String>(hidden);
        Collections.sort(packages);
        List<MenuOption> entries = new ArrayList<MenuOption>();
        PackageManager pm = getPackageManager();
        for (final String packageName : packages) {
            String label;
            try {
                ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
                label = String.valueOf(pm.getApplicationLabel(info));
            } catch (PackageManager.NameNotFoundException ignored) {
                label = packageName;
            }
            final String appLabel = label;
            entries.add(new MenuOption(12, appLabel,
                    text("Restore to launcher", "إعادة إظهاره في الواجهة"),
                    new Runnable() {
                        @Override public void run() { restoreHiddenApp(packageName); }
                    }));
        }
        showLauncherMenu(text("Hidden apps", "التطبيقات المخفية"), entries);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Set<String> hidden = new HashSet<String>();
        Collections.addAll(hidden, HIDDEN);
        hidden.addAll(getUserHiddenApps());
        List<Item> apps = new ArrayList<Item>();
        Set<String> seen = new HashSet<String>();

        String[] categories = {Intent.CATEGORY_LAUNCHER, "android.intent.category.LEANBACK_LAUNCHER"};
        for (String category : categories) {
            Intent query = new Intent(Intent.ACTION_MAIN);
            query.addCategory(category);
            List<ResolveInfo> matches;
            try {
                matches = pm.queryIntentActivities(query, 0);
            } catch (RuntimeException packageManagerNotReady) {
                android.util.Log.w("ApolloLauncher",
                        "PackageManager is not ready; launcher app scan will retry",
                        packageManagerNotReady);
                continue;
            }
            if (matches == null) continue;
            for (ResolveInfo resolveInfo : matches) {
                if (resolveInfo == null || resolveInfo.activityInfo == null) continue;
                ActivityInfo info = resolveInfo.activityInfo;
                String packageName = info.packageName;
                if (packageName.equals(getPackageName()) || hidden.contains(packageName) || !seen.add(packageName)) {
                    continue;
                }
                Item item = new Item();
                item.label = toWesternDigits(String.valueOf(resolveInfo.loadLabel(pm)));
                item.packageName = packageName;
                try { item.icon = resolveInfo.loadIcon(pm); } catch (Exception ignored) { }
                Drawable banner = null;
                try { banner = info.loadBanner(pm); } catch (Exception ignored) { }
                if (banner != null) {
                    item.art = banner;
                    item.banner = true;
                } else {
                    item.art = item.icon;
                }
                if (item.icon == null) item.icon = item.art;
                item.intent = new Intent(Intent.ACTION_MAIN)
                        .setComponent(new ComponentName(packageName, info.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                apps.add(item);
            }
        }

        Collections.sort(apps, new Comparator<Item>() {
            @Override public int compare(Item a, Item b) {
                long aLastUsed = preferences.getLong(PREF_LAST_USED_PREFIX + a.packageName, 0L);
                long bLastUsed = preferences.getLong(PREF_LAST_USED_PREFIX + b.packageName, 0L);
                if (aLastUsed != bLastUsed) return aLastUsed > bLastUsed ? -1 : 1;
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

        // Keep the last good app row if PackageManager temporarily reports no
        // launcher activities while Android TV is still completing boot.
        if (!apps.isEmpty() || appsRow.getChildCount() == 0) {
            appsRow.removeAllViews();
            for (Item item : apps) appsRow.addView(createAppTile(item));
        }
        quickRow.removeAllViews();
        for (Item item : quick) quickRow.addView(createQuickTile(item));

        appsRow.requestLayout();
        appsRow.invalidate();
        quickRow.requestLayout();
        quickRow.invalidate();
        appsScroller.requestLayout();
        appsScroller.invalidate();
        quickScroller.requestLayout();
        quickScroller.invalidate();
        root.requestLayout();
        root.invalidate();
        scrollScrollerToStart(appsScroller);
        scrollScrollerToStart(quickScroller);

        if (apps.isEmpty()) {
            scheduleAppsRetry();
            if (appsRow.getChildCount() == 0 && quickRow.getChildCount() == 0) {
                selectedTitle.setText(text("Waiting for apps…", "بانتظار التطبيقات…"));
            }
        } else {
            appLoadRetryAttempt = 0;
            mainHandler.removeCallbacks(appLoadRetryRunnable);
        }
        appsScroller.setVisibility(View.VISIBLE);
        quickScroller.setVisibility(View.VISIBLE);
        focusFirstAppTile();
    }

    private void focusFirstAppTile() {
        if (root == null || appsRow == null) return;
        root.postDelayed(new Runnable() {
            @Override public void run() {
                if (activityDestroyed || appsRow == null || appsScroller == null) return;
                appsScroller.setVisibility(View.VISIBLE);
                if (appsRow.getChildCount() > 0) {
                    final View first = appsRow.getChildAt(0);
                    first.setVisibility(View.VISIBLE);
                    first.setFocusable(true);
                    first.requestFocus();
                    if (!first.hasFocus()) {
                        first.postDelayed(new Runnable() {
                            @Override public void run() {
                                if (!activityDestroyed && first.getParent() == appsRow) first.requestFocus();
                            }
                        }, 160L);
                    }
                } else if (quickRow != null && quickRow.getChildCount() > 0) {
                    quickRow.getChildAt(0).requestFocus();
                } else {
                    root.requestFocus();
                }
            }
        }, 120L);
    }

    private View createAppTile(final Item item) {
        final FrameLayout tile = new FrameLayout(this) {
            @Override
            public boolean onKeyLongPress(int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                        || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                    showAppOptions(item);
                    return true;
                }
                return super.onKeyLongPress(keyCode, event);
            }
        };
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(dp(150), dp(92));
        tileParams.setMargins(dp(7), dp(2), dp(7), dp(4));
        tile.setLayoutParams(tileParams);
        tile.setBackground(appTileBackground(item, false));
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setLongClickable(true);
        tile.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View view) {
                showAppOptions(item);
                return true;
            }
        });
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
                try {
                    startActivity(item.intent);
                    preferences.edit().putLong(PREF_LAST_USED_PREFIX + item.packageName,
                            System.currentTimeMillis()).apply();
                    appLaunchPending = true;
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, text("Unable to open ", "تعذر فتح ") + item.label,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        tile.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View view, boolean hasFocus) {
                view.animate().scaleX(hasFocus ? 1.035f : 1f).scaleY(hasFocus ? 1.035f : 1f)
                        .translationY(0)
                        .translationZ(hasFocus ? dp(8) : 0).setDuration(170).start();
                view.setBackground(appTileBackground(item, hasFocus));
                if (hasFocus) {
                    focusedItem = item;
                    selectedTitle.setText(formatDigits(item.label));
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
        label.setText(formatDigits(item.label));
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
                view.animate().scaleX(focused ? 1.035f : 1f).scaleY(focused ? 1.035f : 1f)
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
        List<MenuOption> entries = new ArrayList<MenuOption>();
        entries.add(new MenuOption(14,
                text("Dynamic · selected app glow", "ديناميكية · توهج التطبيق المحدد"),
                text("Colors extracted from the selected app icon", "ألوان مستخرجة من أيقونة التطبيق المحدد"),
                new Runnable() {
                    @Override public void run() {
                        preferences.edit().putString(PREF_WALLPAPER_MODE, "dynamic").apply();
                        updateDynamicWallpaper(focusedItem);
                    }
                }));
        entries.add(new MenuOption(15, text("Aurora", "الشفق القطبي"),
                text("Soft northern lights", "أضواء قطبية هادئة"),
                new Runnable() { @Override public void run() { setPreset("aurora"); } }));
        entries.add(new MenuOption(16, text("Ocean", "المحيط"),
                text("Deep blue gradient", "تدرج أزرق عميق"),
                new Runnable() { @Override public void run() { setPreset("ocean"); } }));
        entries.add(new MenuOption(17, text("Violet dusk", "الشفق البنفسجي"),
                text("Purple evening tones", "درجات بنفسجية مسائية"),
                new Runnable() { @Override public void run() { setPreset("violet"); } }));
        entries.add(new MenuOption(18, text("Desert dusk", "غروب الصحراء"),
                text("Warm desert colors", "ألوان صحراوية دافئة"),
                new Runnable() { @Override public void run() { setPreset("desert"); } }));
        entries.add(new MenuOption(19, text("Choose a photo…", "اختيار صورة مخصصة…"),
                text("Use an image from storage", "استخدام صورة من التخزين"),
                new Runnable() { @Override public void run() { chooseWallpaperImage(); } }));
        entries.add(new MenuOption(10, text("Remove custom wallpaper", "إزالة الخلفية المخصصة"),
                text("Return to the dynamic background", "العودة إلى الخلفية الديناميكية"),
                new Runnable() {
                    @Override public void run() {
                        customWallpaperFile().delete();
                        preferences.edit().putString(PREF_WALLPAPER_MODE, "dynamic").apply();
                        updateDynamicWallpaper(focusedItem);
                    }
                }));
        showLauncherMenu(text("Wallpaper", "الخلفية"), entries);
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
        if (!"dynamic".equals(mode) || activityDestroyed) return;
        final Drawable sourceIcon = item == null ? null : (item.icon != null ? item.icon : item.art);
        if (item == null || sourceIcon == null) return;

        final int request = ++blurRequest;
        final String key = item.packageName == null ? item.label : item.packageName;
        try {
            Bitmap memoryCached = blurredBackdrops.get(key);
            if (memoryCached != null && !memoryCached.isRecycled()) {
                showBitmap(memoryCached);
                return;
            }

            final File cacheFile = backdropCacheFile(key);
            final Bitmap iconSnapshot = drawableToBitmap(sourceIcon, 96);
            if (iconSnapshot == null) return;

            blurExecutor.getQueue().clear();
            try {
                blurExecutor.execute(new Runnable() {
                    @Override public void run() {
                        Bitmap backdrop = null;
                        try {
                            if (cacheFile.isFile() && cacheFile.length() > 0) {
                                BitmapFactory.Options options = new BitmapFactory.Options();
                                options.inPreferredConfig = Bitmap.Config.RGB_565;
                                backdrop = BitmapFactory.decodeFile(cacheFile.getAbsolutePath(), options);
                                if (backdrop == null) cacheFile.delete();
                            }

                            if (backdrop == null) {
                                Bitmap gradient = makeDreamyGradient(iconSnapshot);
                                backdrop = blurBitmap(gradient, 19.0f);
                                saveBackdropToDisk(backdrop, cacheFile);
                            }

                            if (backdrop != null && !backdrop.isRecycled()) {
                                blurredBackdrops.put(key, backdrop);
                                final Bitmap ready = backdrop;
                                mainHandler.post(new Runnable() {
                                    @Override public void run() {
                                        if (!activityDestroyed && request == blurRequest
                                                && wallpaperView != null
                                                && "dynamic".equals(preferences.getString(PREF_WALLPAPER_MODE, "dynamic"))) {
                                            showBitmap(ready);
                                        }
                                    }
                                });
                            }
                        } catch (OutOfMemoryError memoryError) {
                            android.util.Log.e("TvLauncher", "Not enough memory for app backdrop", memoryError);
                        } catch (Throwable error) {
                            android.util.Log.e("TvLauncher", "Could not create app backdrop", error);
                        } finally {
                            if (!iconSnapshot.isRecycled()) iconSnapshot.recycle();
                        }
                    }
                });
            } catch (RuntimeException rejected) {
                if (!iconSnapshot.isRecycled()) iconSnapshot.recycle();
                android.util.Log.w("TvLauncher", "Backdrop worker is unavailable", rejected);
            }
        } catch (OutOfMemoryError memoryError) {
            android.util.Log.e("TvLauncher", "Not enough memory to prepare app icon backdrop", memoryError);
        } catch (Throwable error) {
            android.util.Log.e("TvLauncher", "Could not prepare app icon backdrop", error);
        }
    }

    private File backdropCacheFile(String key) {
        String safeName = key == null ? "unknown" : key.replaceAll("[^A-Za-z0-9._-]", "_");
        File directory = new File(getCacheDir(), "dreamy_backdrops_dark");
        if (!directory.exists()) directory.mkdirs();
        return new File(directory, safeName + ".png");
    }

    private Bitmap drawableToBitmap(Drawable drawable, int size) {
        if (drawable == null || size <= 0) return null;
        Bitmap bitmap = null;
        Drawable render = drawable;
        try {
            if (drawable.getConstantState() != null) {
                render = drawable.getConstantState().newDrawable().mutate();
            }
            RectF bounds = new RectF(render.getBounds());
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            render.setBounds(0, 0, size, size);
            render.draw(canvas);
            render.setBounds((int)bounds.left, (int)bounds.top,
                    (int)bounds.right, (int)bounds.bottom);
            return bitmap;
        } catch (Throwable error) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            android.util.Log.w("TvLauncher", "Could not snapshot application icon", error);
            return null;
        }
    }

    private void saveBackdropToDisk(Bitmap bitmap, File file) {
        if (bitmap == null || bitmap.isRecycled() || file == null) return;
        FileOutputStream output = null;
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            output = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
            output.flush();
        } catch (Exception error) {
            android.util.Log.w("TvLauncher", "Could not save app backdrop cache", error);
        } finally {
            if (output != null) {
                try { output.close(); } catch (Exception ignored) { }
            }
        }
    }

    private Bitmap makeDreamyGradient(Bitmap icon) {
        int[] palette = extractPalette(icon);
        Bitmap bitmap = Bitmap.createBitmap(BACKDROP_WIDTH, BACKDROP_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int darkA = darken(palette[0], 0.11f);
        int darkB = darken(palette[1], 0.10f);
        int darkC = darken(palette[2], 0.12f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new LinearGradient(0, 0, BACKDROP_WIDTH, BACKDROP_HEIGHT,
                new int[]{darkA, 0xFF080B14, darkB, darkC},
                new float[]{0f, 0.38f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, BACKDROP_WIDTH, BACKDROP_HEIGHT, paint);

        drawColorGlow(canvas, palette[0], BACKDROP_WIDTH * 0.18f,
                BACKDROP_HEIGHT * 0.30f, BACKDROP_WIDTH * 0.78f);
        drawColorGlow(canvas, palette[1], BACKDROP_WIDTH * 0.77f,
                BACKDROP_HEIGHT * 0.30f, BACKDROP_WIDTH * 0.72f);
        drawColorGlow(canvas, palette[2], BACKDROP_WIDTH * 0.55f,
                BACKDROP_HEIGHT * 0.94f, BACKDROP_WIDTH * 0.82f);
        drawColorGlow(canvas, palette[3], BACKDROP_WIDTH * 0.98f,
                BACKDROP_HEIGHT * 0.88f, BACKDROP_WIDTH * 0.56f);

        paint.setShader(new LinearGradient(0, 0, 0, BACKDROP_HEIGHT,
                new int[]{0x26000000, 0x18000000, 0x88020711},
                null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, BACKDROP_WIDTH, BACKDROP_HEIGHT, paint);
        paint.setShader(null);
        return bitmap;
    }

    private void drawColorGlow(Canvas canvas, int color, float centerX, float centerY, float radius) {
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(centerX, centerY, radius,
                new int[]{withAlpha(color, 118), withAlpha(color, 46), withAlpha(color, 0)},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, BACKDROP_WIDTH, BACKDROP_HEIGHT, glow);
        glow.setShader(null);
    }

    private int[] extractPalette(Bitmap icon) {
        final int bins = 4096;
        int[] weights = new int[bins];
        long[] red = new long[bins];
        long[] green = new long[bins];
        long[] blue = new long[bins];

        for (int y = 0; y < icon.getHeight(); y += 2) {
            for (int x = 0; x < icon.getWidth(); x += 2) {
                int color = icon.getPixel(x, y);
                if (Color.alpha(color) < 80) continue;
                int r = Color.red(color);
                int g = Color.green(color);
                int b = Color.blue(color);
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                if (max < 48 || max - min < 18) continue;

                int weight = 1 + (max - min) / 72;
                int index = ((r >> 4) << 8) | ((g >> 4) << 4) | (b >> 4);
                weights[index] += weight;
                red[index] += (long)r * weight;
                green[index] += (long)g * weight;
                blue[index] += (long)b * weight;
            }
        }

        int[] palette = new int[4];
        boolean[] used = new boolean[bins];
        int selected = 0;
        for (int slot = 0; slot < palette.length; slot++) {
            int bestIndex = -1;
            double bestScore = 0;
            for (int i = 0; i < bins; i++) {
                if (used[i] || weights[i] == 0) continue;
                int candidate = Color.rgb((int)(red[i] / weights[i]),
                        (int)(green[i] / weights[i]), (int)(blue[i] / weights[i]));
                double separation = 1.0;
                for (int j = 0; j < selected; j++) {
                    int distance = colorDistance(candidate, palette[j]);
                    separation = Math.min(separation, Math.min(1.0, distance / 130.0));
                }
                double score = weights[i] * (selected == 0 ? 1.0 : 0.25 + 0.75 * separation);
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = i;
                }
            }
            if (bestIndex < 0) break;
            used[bestIndex] = true;
            palette[selected++] = Color.rgb((int)(red[bestIndex] / weights[bestIndex]),
                    (int)(green[bestIndex] / weights[bestIndex]),
                    (int)(blue[bestIndex] / weights[bestIndex]));
        }

        if (selected == 0) {
            palette[0] = 0xFF5278D8;
            palette[1] = 0xFF9664D5;
            palette[2] = 0xFF28A4B4;
            palette[3] = 0xFFD96B83;
        } else {
            if (selected < 2) palette[1] = blendColors(palette[0], 0xFF7768D8, 0.42f);
            if (selected < 3) palette[2] = blendColors(palette[0], 0xFF28A4B4, 0.38f);
            if (selected < 4) palette[3] = blendColors(palette[0], 0xFFD96B83, 0.34f);
        }
        return palette;
    }

    private int colorDistance(int first, int second) {
        int dr = Color.red(first) - Color.red(second);
        int dg = Color.green(first) - Color.green(second);
        int db = Color.blue(first) - Color.blue(second);
        return (int)Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private int blendColors(int first, int second, float amount) {
        float inverse = 1f - amount;
        return Color.rgb(
                Math.round(Color.red(first) * inverse + Color.red(second) * amount),
                Math.round(Color.green(first) * inverse + Color.green(second) * amount),
                Math.round(Color.blue(first) * inverse + Color.blue(second) * amount));
    }

    private int darken(int color, float factor) {
        return Color.rgb((int)(Color.red(color) * factor),
                (int)(Color.green(color) * factor), (int)(Color.blue(color) * factor));
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private Bitmap blurBitmap(Bitmap source, float radius) {
        if (source == null || source.isRecycled()) return source;
        Bitmap reduced = null;
        try {
            int width = Math.max(1, source.getWidth() / 3);
            int height = Math.max(1, source.getHeight() / 3);
            reduced = Bitmap.createScaledBitmap(source, width, height, true);
            Bitmap softened = Bitmap.createScaledBitmap(reduced,
                    source.getWidth(), source.getHeight(), true);
            if (reduced != source && !reduced.isRecycled()) reduced.recycle();
            if (softened != source && !source.isRecycled()) source.recycle();
            return softened;
        } catch (OutOfMemoryError memoryError) {
            if (reduced != null && reduced != source && !reduced.isRecycled()) reduced.recycle();
            android.util.Log.w("TvLauncher", "Using unsoftened gradient after low-memory fallback", memoryError);
            return source;
        } catch (RuntimeException error) {
            if (reduced != null && reduced != source && !reduced.isRecycled()) reduced.recycle();
            android.util.Log.w("TvLauncher", "Using unsoftened gradient after blur fallback", error);
            return source;
        }
    }

    private int dp(float value) {
        return (int)(value * density + 0.5f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (appLaunchPending && appsRow != null) {
            appLaunchPending = false;
            loadApps();
        }
        refreshWeather();
        if (!networkTimeAvailable) {
            mainHandler.removeCallbacks(networkTimeRetryRunnable);
            mainHandler.postDelayed(networkTimeRetryRunnable, 500L);
        }
        if (appsRow != null && appsRow.getChildCount() == 0) {
            scheduleAppsReload(500L);
        }
        focusFirstAppTile();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && !activityDestroyed && appsRow != null) {
            if (appsRow.getChildCount() == 0) scheduleAppsReload(500L);
            else if (appsRow.findFocus() == null) focusFirstAppTile();
        }
    }

    private void scrollScrollerToStart(final HorizontalScrollView scroller) {
        if (scroller == null) return;
        scroller.post(new Runnable() {
            @Override public void run() {
                scroller.scrollTo(isArabicLocale() ? Integer.MAX_VALUE : 0, 0);
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        focusFirstAppTile();
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
        if (systemReceiverRegistered) {
            try { unregisterReceiver(systemStateReceiver); } catch (Exception ignored) { }
            systemReceiverRegistered = false;
        }
        activityDestroyed = true;
        blurRequest++;
        blurExecutor.shutdownNow();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private class BoundedScrollView extends ScrollView {
        private final int maxHeight;

        BoundedScrollView(Context context, int heightLimit) {
            super(context);
            maxHeight = Math.max(1, heightLimit);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int cappedHeight = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, cappedHeight);
        }
    }

    private static class LatinDigitsSpan extends MetricAffectingSpan {
        private final Typeface typeface;

        LatinDigitsSpan(Typeface value) {
            typeface = value;
        }

        @Override public void updateDrawState(TextPaint paint) {
            paint.setTypeface(typeface);
        }

        @Override public void updateMeasureState(TextPaint paint) {
            paint.setTypeface(typeface);
        }
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
                case 8: // app grid
                    canvas.drawRoundRect(new RectF(3, 3, 10, 10), 1.5f, 1.5f, paint);
                    canvas.drawRoundRect(new RectF(14, 3, 21, 10), 1.5f, 1.5f, paint);
                    canvas.drawRoundRect(new RectF(3, 14, 10, 21), 1.5f, 1.5f, paint);
                    canvas.drawRoundRect(new RectF(14, 14, 21, 21), 1.5f, 1.5f, paint);
                    break;
                case 9: // hide / eye off
                    Path eye = new Path();
                    eye.moveTo(2, 12); eye.quadTo(12, 2, 22, 12);
                    eye.quadTo(12, 22, 2, 12);
                    canvas.drawPath(eye, paint);
                    canvas.drawCircle(12, 12, 3, paint);
                    canvas.drawLine(4, 20, 20, 4, paint);
                    break;
                case 10: // uninstall / trash
                    canvas.drawRoundRect(new RectF(6, 8, 18, 21), 1.5f, 1.5f, paint);
                    canvas.drawLine(4, 6, 20, 6, paint);
                    canvas.drawLine(9, 3, 15, 3, paint);
                    canvas.drawLine(10, 11, 10, 18, paint);
                    canvas.drawLine(14, 11, 14, 18, paint);
                    break;
                case 11: // application settings / stop
                    canvas.drawCircle(12, 12, 8.5f, paint);
                    canvas.drawRect(new RectF(9, 9, 15, 15), paint);
                    break;
                case 12: // unhide / eye
                    Path visibleEye = new Path();
                    visibleEye.moveTo(2, 12); visibleEye.quadTo(12, 2, 22, 12);
                    visibleEye.quadTo(12, 22, 2, 12);
                    canvas.drawPath(visibleEye, paint);
                    canvas.drawCircle(12, 12, 3, paint);
                    break;
                case 14: // dynamic color gradient / sparkle
                    canvas.drawCircle(8, 8, 3, paint);
                    canvas.drawCircle(16, 15, 4.5f, paint);
                    canvas.drawLine(17, 3, 17, 8, paint);
                    canvas.drawLine(14.5f, 5.5f, 19.5f, 5.5f, paint);
                    canvas.drawLine(5, 17, 5, 21, paint);
                    canvas.drawLine(3, 19, 7, 19, paint);
                    break;
                case 15: // aurora lights
                    canvas.drawArc(new RectF(2, 3, 22, 20), 200, 145, false, paint);
                    canvas.drawArc(new RectF(5, 7, 19, 23), 205, 135, false, paint);
                    canvas.drawArc(new RectF(8, 11, 16, 25), 210, 120, false, paint);
                    break;
                case 16: // ocean waves
                    Path waves = new Path();
                    waves.moveTo(2, 9); waves.cubicTo(6, 4, 10, 14, 14, 9);
                    waves.cubicTo(18, 4, 21, 8, 22, 9);
                    waves.moveTo(2, 15); waves.cubicTo(6, 10, 10, 20, 14, 15);
                    waves.cubicTo(18, 10, 21, 14, 22, 15);
                    canvas.drawPath(waves, paint);
                    canvas.drawLine(3, 21, 21, 21, paint);
                    break;
                case 17: // violet dusk / moon
                    canvas.drawCircle(12, 12, 8, paint);
                    Paint moonCutout = new Paint(Paint.ANTI_ALIAS_FLAG);
                    moonCutout.setColor(0xFF19233A);
                    moonCutout.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(16, 8, 7, moonCutout);
                    break;
                case 18: // desert dusk
                    canvas.drawCircle(17, 8, 4, paint);
                    Path dunes = new Path();
                    dunes.moveTo(2, 19); dunes.quadTo(8, 10, 14, 18);
                    dunes.quadTo(18, 22, 22, 15);
                    canvas.drawPath(dunes, paint);
                    canvas.drawLine(2, 22, 22, 22, paint);
                    break;
                case 19: // photo
                    canvas.drawRoundRect(new RectF(3, 4, 21, 20), 2, 2, paint);
                    canvas.drawCircle(16, 8, 1.6f, paint);
                    Path imageMountains = new Path();
                    imageMountains.moveTo(5, 17); imageMountains.lineTo(10, 11);
                    imageMountains.lineTo(13, 14); imageMountains.lineTo(16, 11);
                    imageMountains.lineTo(20, 17);
                    canvas.drawPath(imageMountains, paint);
                    break;
            }
            canvas.restore();
        }
    }
}
