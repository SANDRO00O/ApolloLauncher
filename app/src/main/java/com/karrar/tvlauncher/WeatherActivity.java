package com.karrar.ApolloLauncher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * A landscape-first weather dashboard designed for Android TV and D-pad use.
 * The page uses two compact columns per row instead of stacking full-width
 * phone-style sections.
 */
public class WeatherActivity extends Activity {
    private static final int BG = 0xFF080D19;
    private static final int CARD = 0xFF121D31;
    private static final int CARD_ALT = 0xFF172640;
    private static final int BORDER = 0xFF293B57;
    private static final int PRIMARY = Color.WHITE;
    private static final int MUTED = 0xFFB4C1D4;
    private static final int SUBTLE = 0xFF8798B2;
    private static final int ACCENT = 0xFFFFD27A;
    private static final int BLUE = 0xFF81C5FF;

    private float density;
    private boolean arabic;
    private boolean busy;
    private boolean dead;
    private Typeface latinTypeface;
    private Typeface arabicTypeface;
    private Typeface weatherTypeface;
    private WeatherService.Place place;

    private TextView locationButton;
    private TextView city;
    private TextView temperature;
    private TextView condition;
    private TextView feelsLike;
    private TextView temperatureRange;
    private TextView status;
    private TextView updated;
    private TextView sunrise;
    private TextView sunset;
    private TextView humidityValue;
    private TextView windValue;
    private TextView windDirectionDetail;
    private TextView rainValue;
    private TextView uvValue;

    private LinearLayout sun;
    private LinearLayout metricsGrid;
    private LinearLayout hoursRow;
    private LinearLayout daysColumn;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        arabic = "ar".equals(getResources().getConfiguration().locale.getLanguage());
        place = WeatherService.getSavedLocation(this);

        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setBackgroundDrawable(new ColorDrawable(BG));

        loadTypefaces();
        build();
        load();
    }

    private void loadTypefaces() {
        try {
            latinTypeface = Typeface.createFromAsset(getAssets(),
                    "fonts/InterDisplay-Medium.otf");
        } catch (Exception ignored) {
            latinTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        }

        try {
            arabicTypeface = Typeface.createFromAsset(getAssets(),
                    "fonts/NotoKufiArabic-Regular.ttf");
        } catch (Exception ignored) {
            arabicTypeface = Typeface.create("sans-serif", Typeface.NORMAL);
        }

        try {
            weatherTypeface = Typeface.createFromAsset(getAssets(),
                    "fonts/weathericons-regular-webfont.ttf");
        } catch (Exception ignored) {
            weatherTypeface = Typeface.DEFAULT;
        }
    }

    private int dp(float value) {
        return (int) (value * density + 0.5f);
    }

    private String t(String english, String arabicText) {
        return arabic ? arabicText : english;
    }

    private LinearLayout linear(boolean vertical) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(vertical
                ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        layout.setLayoutDirection(arabic
                ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        layout.setClipChildren(false);
        layout.setClipToPadding(false);
        return layout;
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sizeSp);
        view.setTypeface(arabic
                ? arabicTypeface
                : (bold ? latinTypeface : Typeface.DEFAULT));
        view.setGravity((arabic ? Gravity.RIGHT : Gravity.LEFT)
                | Gravity.CENTER_VERTICAL);
        view.setTextDirection(arabic
                ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        view.setIncludeFontPadding(true);
        view.setLineSpacing(dp(2), 1.0f);
        return view;
    }

    private TextView numericText(String value, float sizeSp, int color, boolean bold) {
        TextView view = text(value, sizeSp, color, bold);
        view.setTextDirection(View.TEXT_DIRECTION_LTR);
        view.setTypeface(bold ? latinTypeface : Typeface.DEFAULT);
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        return view;
    }

    private GradientDrawable shape(int fill, int stroke, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private LinearLayout card(int fill, int paddingDp) {
        LinearLayout layout = linear(true);
        layout.setPadding(dp(paddingDp), dp(paddingDp),
                dp(paddingDp), dp(paddingDp));
        layout.setBackground(shape(fill, BORDER, 19));
        return layout;
    }

    private LinearLayout.LayoutParams weighted(int height, float weight) {
        return new LinearLayout.LayoutParams(0,
                height < 0 ? height : dp(height), weight);
    }

    private void addSectionTitle(LinearLayout parent, String title) {
        TextView label = text(title, 17, PRIMARY, true);
        label.setPadding(dp(2), 0, dp(2), dp(10));
        parent.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(34)));
    }

    private TextView button(String label, final Runnable action) {
        return button(label, action, true);
    }

    private TextView button(String label, final Runnable action,
                            final boolean scaleOnFocus) {
        TextView view = text(label, 14, PRIMARY, true);
        view.setGravity(Gravity.CENTER);
        view.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        view.setPadding(dp(15), dp(3), dp(15), dp(3));
        view.setMinHeight(dp(46));
        view.setFocusable(true);
        view.setClickable(true);
        view.setBackground(shape(CARD_ALT, BORDER, 13));
        view.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View target, boolean focused) {
                target.setBackground(shape(
                        focused ? 0xFF263B5C : CARD_ALT,
                        focused ? 0xFFE0E9F7 : BORDER, 13));
                float targetScale = scaleOnFocus && focused ? 1.035f : 1.0f;
                target.animate().scaleX(targetScale)
                        .scaleY(targetScale)
                        .setDuration(120L).start();
            }
        });
        if (action != null) {
            view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View target) {
                    action.run();
                }
            });
        }
        return view;
    }

    private void build() {
        LinearLayout root = linear(true);
        root.setBackgroundColor(BG);

        buildHeader(root);
        buildDashboard(root);

        status = text("", 13, 0xFFFFC978, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(28), dp(3), dp(28), dp(5));
        root.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(27)));

        setContentView(root);
        root.post(new Runnable() {
            @Override
            public void run() {
                if (locationButton != null) locationButton.requestFocus();
            }
        });
    }

    private void buildHeader(LinearLayout root) {
        LinearLayout header = linear(false);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(30), dp(8), dp(30), dp(6));

        TextView back = button(t("Back", "رجوع"), new Runnable() {
            @Override
            public void run() {
                finish();
            }
        });
        header.addView(back, new LinearLayout.LayoutParams(dp(104), dp(48)));

        TextView title = text(t("WEATHER", "الطقس"), 21, PRIMARY, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(0, dp(48), 1.0f);
        titleParams.leftMargin = dp(18);
        titleParams.rightMargin = dp(18);
        header.addView(title, titleParams);

        locationButton = button(place.shortName() + "  ▾", new Runnable() {
            @Override
            public void run() {
                chooseCity();
            }
        });
        locationButton.setSingleLine(true);
        locationButton.setEllipsize(TextUtils.TruncateAt.END);
        locationButton.setMaxWidth(dp(230));
        header.addView(locationButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        TextView reload = button("↻", new Runnable() {
            @Override
            public void run() {
                load();
            }
        });
        reload.setTextSize(22);
        LinearLayout.LayoutParams reloadParams =
                new LinearLayout.LayoutParams(dp(54), dp(48));
        reloadParams.leftMargin = dp(9);
        header.addView(reload, reloadParams);

        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(66)));

        View divider = new View(this);
        divider.setBackgroundColor(0x332F4565);
        root.addView(divider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void buildDashboard(LinearLayout root) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);

        LinearLayout body = linear(true);
        body.setPadding(dp(30), dp(14), dp(30), dp(12));

        // First row: current conditions on the left and four compact metrics
        // arranged as a true 2-by-2 grid on the right.
        LinearLayout topRow = linear(false);
        topRow.setGravity(Gravity.FILL);
        buildCurrentCard(topRow);

        LinearLayout metricCard = card(CARD, 14);
        addSectionTitle(metricCard, t("At a glance", "نظرة عامة"));

        metricsGrid = linear(true);
        LinearLayout metricRowOne = linear(false);
        LinearLayout metricRowTwo = linear(false);
        metricsGrid.addView(metricRowOne, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        View metricRowGap = new View(this);
        metricsGrid.addView(metricRowGap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(12)));

        metricsGrid.addView(metricRowTwo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        metricCard.addView(metricsGrid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        // The values are filled by render(); these placeholders keep the
        // dashboard geometry stable while the request is in progress.
        buildMetricPlaceholders(metricRowOne, metricRowTwo);

        LinearLayout.LayoutParams metricParams =
                new LinearLayout.LayoutParams(0, dp(244), 1.18f);
        metricParams.leftMargin = dp(12);
        topRow.addView(metricCard, metricParams);

        body.addView(topRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(244)));

        View rowGap = new View(this);
        body.addView(rowGap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(14)));

        // Second row: hourly timeline and seven-day forecast share the width.
        LinearLayout lowerRow = linear(false);
        lowerRow.setGravity(Gravity.FILL);
        buildHourlyCard(lowerRow);
        buildSevenDayCard(lowerRow);

        body.addView(lowerRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));

        TextView credit = text(
                "Weather data & geocoding by Open-Meteo",
                10, SUBTLE, false);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(7), 0, 0);
        body.addView(credit, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(22)));

        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
    }

    private void buildCurrentCard(LinearLayout parent) {
        LinearLayout currentCard = card(CARD_ALT, 17);
        LinearLayout upper = linear(false);
        upper.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout information = linear(true);
        city = text(place.name, 16, 0xFFDCE7FF, true);
        city.setSingleLine(true);
        city.setEllipsize(TextUtils.TruncateAt.END);
        information.addView(city, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(27)));

        temperature = numericText("--°", 62, PRIMARY, true);
        temperature.setIncludeFontPadding(false);
        information.addView(temperature, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        condition = text(t("Loading weather…", "جارٍ تحميل الطقس…"),
                15, 0xFFE3E9F6, true);
        condition.setMaxLines(2);
        information.addView(condition, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(39)));

        upper.addView(information, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f));

        TextView glyph = new TextView(this);
        glyph.setText(String.valueOf((char) 0xF07B));
        glyph.setTextColor(ACCENT);
        glyph.setTypeface(weatherTypeface);
        glyph.setTextSize(76);
        glyph.setGravity(Gravity.CENTER);
        glyph.setTextDirection(View.TEXT_DIRECTION_LTR);
        upper.addView(glyph, new LinearLayout.LayoutParams(dp(112), dp(130)));
        // Keep a stable reference through the existing icon field.
        weatherGlyph = glyph;

        currentCard.addView(upper, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout detailRow = linear(false);
        feelsLike = text(t("Feels like --°", "المحسوسة --°"),
                12, MUTED, false);
        temperatureRange = text(t("High --°   Low --°",
                "العظمى --°   الصغرى --°"), 12, MUTED, false);
        temperatureRange.setSingleLine(true);
        detailRow.addView(feelsLike, new LinearLayout.LayoutParams(
                0, dp(24), 0.85f));
        detailRow.addView(temperatureRange, new LinearLayout.LayoutParams(
                0, dp(24), 1.15f));
        currentCard.addView(detailRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(24)));

        View separator = new View(this);
        separator.setBackgroundColor(0x33465B78);
        LinearLayout.LayoutParams separatorParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        separatorParams.topMargin = dp(4);
        separatorParams.bottomMargin = dp(4);
        currentCard.addView(separator, separatorParams);

        sun = linear(false);
        sun.setGravity(Gravity.CENTER_VERTICAL);
        currentCard.addView(sun, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

        LinearLayout.LayoutParams currentParams =
                new LinearLayout.LayoutParams(0, dp(244), 1.0f);
        parent.addView(currentCard, currentParams);
    }

    private TextView weatherGlyph;

    private void buildMetricPlaceholders(LinearLayout first, LinearLayout second) {
        humidityValue = metricCard(first, t("Humidity", "الرطوبة"),
                "--", t("Relative humidity", "الرطوبة النسبية"), false);
        windValue = metricCard(first, t("Wind", "الرياح"),
                "--", t("Wind speed", "سرعة الرياح"), true);
        rainValue = metricCard(second, t("Rain chance", "احتمال المطر"),
                "--", t("Today", "اليوم"), false);
        uvValue = metricCard(second, t("UV index", "الأشعة فوق البنفسجية"),
                "--", t("Exposure level", "مستوى التعرض"), false);
    }

    private TextView metricCard(LinearLayout row, String label, String value,
                                String subtitle, boolean isWind) {
        LinearLayout panel = linear(true);
        panel.setPadding(dp(12), dp(6), dp(12), dp(6));
        panel.setBackground(shape(0xFF18263C, BORDER, 14));

        TextView heading = text(label, 13, MUTED, true);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(20)));

        TextView valueView = numericText(value, 23, PRIMARY, true);
        if (isWind) valueView.setTextSize(20);
        panel.addView(valueView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));

        TextView detail = text(subtitle, 11, SUBTLE, false);
        detail.setSingleLine(true);
        detail.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(detail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
        if (isWind) windDirectionDetail = detail;

        if (row.getChildCount() > 0) {
            View spacer = new View(this);
            row.addView(spacer, new LinearLayout.LayoutParams(dp(12),
                    ViewGroup.LayoutParams.MATCH_PARENT));
        }

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
        row.addView(panel, params);

        return valueView;
    }

    private void buildHourlyCard(LinearLayout parent) {
        LinearLayout panel = card(CARD, 14);
        panel.setClipChildren(true);
        panel.setClipToPadding(true);
        addSectionTitle(panel, t("Next hours", "الساعات القادمة"));

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setFillViewport(false);
        scroller.setClipChildren(true);
        scroller.setClipToPadding(true);
        scroller.setPadding(0, dp(2), 0, dp(2));

        hoursRow = linear(false);
        hoursRow.setGravity(Gravity.CENTER_VERTICAL);
        hoursRow.setClipChildren(true);
        hoursRow.setClipToPadding(true);
        scroller.addView(hoursRow, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        panel.addView(scroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(260), 1.22f);
        parent.addView(panel, params);
    }

    private void buildSevenDayCard(LinearLayout parent) {
        LinearLayout panel = card(CARD, 14);
        addSectionTitle(panel, t("7-day forecast", "توقعات 7 أيام"));

        daysColumn = linear(true);
        daysColumn.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(daysColumn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(0, dp(260), 0.98f);
        params.leftMargin = dp(12);
        parent.addView(panel, params);
    }

    private void setMetric(TextView target, String value) {
        if (target != null) target.setText(value);
    }

    private void load() {
        if (busy || dead) return;
        busy = true;
        place = WeatherService.getSavedLocation(this);
        if (locationButton != null) {
            locationButton.setText(place.shortName() + "  ▾");
        }
        status.setText(t("Updating weather…", "جارٍ تحديث الطقس…"));

        final WeatherService.Place requestedPlace = place;
        new AsyncTask<Void, Void, JSONObject>() {
            @Override
            protected JSONObject doInBackground(Void... ignored) {
                try {
                    return WeatherService.fetchForecast(
                            WeatherActivity.this,
                            requestedPlace.latitude,
                            requestedPlace.longitude);
                } catch (Exception error) {
                    android.util.Log.e("ApolloWeather",
                            "Weather page request failed", error);
                    return null;
                }
            }

            @Override
            protected void onPostExecute(JSONObject result) {
                busy = false;
                if (dead || isFinishing()) return;

                if (result == null) {
                    status.setText(t(
                            "Weather unavailable. Check Internet and retry.",
                            "الطقس غير متاح. تحقق من الإنترنت وأعد المحاولة."));
                    return;
                }

                try {
                    render(result, requestedPlace);
                    status.setText("");
                } catch (Exception error) {
                    android.util.Log.e("ApolloWeather",
                            "Could not render weather response", error);
                    status.setText(t(
                            "Could not read the forecast.",
                            "تعذرت قراءة بيانات الطقس."));
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private String degrees(double value) {
        return Double.isNaN(value) ? "--°"
                : Math.round(value) + "°";
    }

    private String hour(String value) {
        return value != null && value.length() >= 16
                ? value.substring(11, 16) : "--:--";
    }

    private void render(JSONObject forecast, WeatherService.Place selected)
            throws Exception {
        JSONObject current = forecast.getJSONObject("current");
        JSONObject daily = forecast.getJSONObject("daily");

        double currentTemperature = current.optDouble(
                "temperature_2m", Double.NaN);
        int code = current.optInt("weather_code", -1);
        boolean isDay = current.optInt("is_day", 1) == 1;

        city.setText(selected.name);
        temperature.setText(degrees(currentTemperature));
        condition.setText(WeatherService.condition(code, arabic));
        weatherGlyph.setText(WeatherService.icon(code, isDay));

        double apparent = current.optDouble(
                "apparent_temperature", currentTemperature);
        feelsLike.setText(t("Feels like ", "المحسوسة ")
                + degrees(apparent));

        JSONArray highs = daily.optJSONArray("temperature_2m_max");
        JSONArray lows = daily.optJSONArray("temperature_2m_min");
        double high = highs == null
                ? Double.NaN : highs.optDouble(0, Double.NaN);
        double low = lows == null
                ? Double.NaN : lows.optDouble(0, Double.NaN);

        temperatureRange.setText(t("High ", "العظمى ")
                + degrees(high) + "     "
                + t("Low ", "الصغرى ") + degrees(low));

        setMetric(humidityValue,
                current.optInt("relative_humidity_2m", -1) < 0
                        ? "--"
                        : current.optInt("relative_humidity_2m") + "%");

        int windSpeed = (int) Math.round(
                current.optDouble("wind_speed_10m", Double.NaN));
        int windDirection = current.optInt("wind_direction_10m", 0);
        windValue.setText(Double.isNaN(
                current.optDouble("wind_speed_10m", Double.NaN))
                ? "--"
                : windSpeed + " km/h");
        if (windDirectionDetail != null) {
            windDirectionDetail.setText(WeatherService.windDirection(
                    windDirection, arabic));
        }

        JSONArray rain = daily.optJSONArray("precipitation_probability_max");
        setMetric(rainValue, rain == null
                ? "--"
                : rain.optInt(0, -1) < 0
                        ? "--" : rain.optInt(0) + "%");

        JSONArray uv = daily.optJSONArray("uv_index_max");
        double uvIndex = uv == null
                ? Double.NaN : uv.optDouble(0, Double.NaN);
        setMetric(uvValue, Double.isNaN(uvIndex)
                ? "--" : String.format(Locale.US, "%.1f", uvIndex));

        String timestamp = current.optString("time", "");
        renderSun(daily);
        updated.setText(t("Updated ", "آخر تحديث ")
                + hour(timestamp));
        renderHours(forecast.optJSONObject("hourly"), timestamp);
        renderDays(daily);
    }

    private void renderSun(JSONObject daily) {
        sun.removeAllViews();

        JSONArray rises = daily.optJSONArray("sunrise");
        JSONArray sets = daily.optJSONArray("sunset");

        sunrise = sunValue(t("Sunrise", "الشروق"),
                rises == null ? "--:--" : hour(rises.optString(0, "")),
                0xFFFFC978);
        sunset = sunValue(t("Sunset", "الغروب"),
                sets == null ? "--:--" : hour(sets.optString(0, "")),
                0xFFFFA56D);
        updated = text("", 11, SUBTLE, false);
        updated.setGravity(Gravity.CENTER);

        addSunColumn(sun, sunrise, 1.0f);
        addSunDivider(sun);
        addSunColumn(sun, sunset, 1.0f);
        addSunDivider(sun);
        addSunColumn(sun, updated, 1.25f);
    }

    private TextView sunValue(String label, String value, int color) {
        TextView view = text(label + "  " + value, 12, color, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
        return view;
    }

    private void addSunColumn(LinearLayout parent, View child, float weight) {
        parent.addView(child, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
    }

    private void addSunDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(0x33465B78);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(dp(1), dp(25));
        params.leftMargin = dp(7);
        params.rightMargin = dp(7);
        parent.addView(divider, params);
    }

    private void renderHours(JSONObject hourly, String currentStamp) {
        hoursRow.removeAllViews();
        if (hourly == null) return;

        JSONArray times = hourly.optJSONArray("time");
        JSONArray temperatures = hourly.optJSONArray("temperature_2m");
        JSONArray codes = hourly.optJSONArray("weather_code");
        JSONArray rain = hourly.optJSONArray("precipitation_probability");
        if (times == null || temperatures == null || codes == null) return;

        int start = 0;
        for (int i = 0; i < times.length(); i++) {
            if (times.optString(i, "").compareTo(currentStamp) >= 0) {
                start = i;
                break;
            }
        }

        int end = Math.min(times.length(), start + 10);
        for (int i = start; i < end; i++) {
            LinearLayout item = linear(true);
            item.setGravity(Gravity.CENTER);
            item.setClipChildren(true);
            item.setClipToPadding(true);
            item.setPadding(dp(6), dp(6), dp(6), dp(6));
            item.setBackground(shape(0xFF18263C, BORDER, 13));

            String time = i == start
                    ? t("Now", "الآن")
                    : hour(times.optString(i, ""));
            TextView timeView = text(time, 12, MUTED, true);
            timeView.setGravity(Gravity.CENTER);
            timeView.setSingleLine(true);
            item.addView(timeView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(23)));

            TextView icon = new TextView(this);
            icon.setText(WeatherService.icon(codes.optInt(i, -1), true));
            icon.setTextColor(ACCENT);
            icon.setTypeface(weatherTypeface);
            icon.setTextSize(27);
            icon.setGravity(Gravity.CENTER);
            icon.setTextDirection(View.TEXT_DIRECTION_LTR);
            LinearLayout.LayoutParams iconParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
            iconParams.topMargin = dp(3);
            iconParams.bottomMargin = dp(3);
            item.addView(icon, iconParams);

            TextView temp = numericText(
                    degrees(temperatures.optDouble(i, Double.NaN)),
                    17, PRIMARY, true);
            temp.setGravity(Gravity.CENTER);
            item.addView(temp, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(25)));

            String rainValue = rain == null
                    ? "--" : rain.optInt(i, -1) < 0
                            ? "--" : rain.optInt(i) + "%";
            TextView rainView = numericText(rainValue, 11, BLUE, false);
            rainView.setGravity(Gravity.CENTER);
            item.addView(rainView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(dp(76), dp(136));
            if (hoursRow.getChildCount() > 0) {
                params.leftMargin = dp(8);
            }
            hoursRow.addView(item, params);
        }
    }

    private void renderDays(JSONObject daily) {
        daysColumn.removeAllViews();

        JSONArray dates = daily.optJSONArray("time");
        JSONArray codes = daily.optJSONArray("weather_code");
        JSONArray highs = daily.optJSONArray("temperature_2m_max");
        JSONArray lows = daily.optJSONArray("temperature_2m_min");
        JSONArray rain = daily.optJSONArray("precipitation_probability_max");

        if (dates == null || codes == null || highs == null || lows == null) {
            return;
        }

        int count = Math.min(7, dates.length());
        for (int i = 0; i < count; i++) {
            LinearLayout row = linear(false);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(5), 0, dp(5), 0);

            TextView day = text(dayName(dates.optString(i, ""), i),
                    13, PRIMARY, true);
            day.setSingleLine(true);
            day.setEllipsize(TextUtils.TruncateAt.END);
            row.addView(day, new LinearLayout.LayoutParams(dp(88), dp(27)));

            TextView icon = new TextView(this);
            icon.setText(WeatherService.icon(codes.optInt(i, -1), true));
            icon.setTypeface(weatherTypeface);
            icon.setTextColor(ACCENT);
            icon.setTextSize(21);
            icon.setGravity(Gravity.CENTER);
            icon.setTextDirection(View.TEXT_DIRECTION_LTR);
            row.addView(icon, new LinearLayout.LayoutParams(dp(35), dp(27)));

            String rainChance = rain == null || rain.optInt(i, -1) < 0
                    ? "--" : rain.optInt(i) + "%";
            TextView rainText = numericText(rainChance, 11, BLUE, false);
            rainText.setGravity(Gravity.CENTER);
            row.addView(rainText, new LinearLayout.LayoutParams(dp(44), dp(27)));

            TextView temperatures = numericText(
                    degrees(lows.optDouble(i, Double.NaN)) + " / "
                            + degrees(highs.optDouble(i, Double.NaN)),
                    13, PRIMARY, true);
            temperatures.setGravity((arabic ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL);
            row.addView(temperatures, new LinearLayout.LayoutParams(
                    0, dp(27), 1.0f));

            daysColumn.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(27)));

            if (i < count - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(0x22364A66);
                daysColumn.addView(divider, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }
    }

    private String dayName(String date, int index) {
        if (index == 0) return t("Today", "اليوم");
        if (index == 1) return t("Tomorrow", "غداً");
        try {
            java.util.Date parsed =
                    new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date);
            return new SimpleDateFormat("EEE",
                    getResources().getConfiguration().locale).format(parsed);
        } catch (Exception ignored) {
            return date;
        }
    }

    private String uvLabel(double value) {
        if (Double.isNaN(value)) return t("UV exposure", "مستوى الأشعة");
        if (value < 3) return t("Low", "منخفض");
        if (value < 6) return t("Moderate", "متوسط");
        if (value < 8) return t("High", "مرتفع");
        if (value < 11) return t("Very high", "مرتفع جداً");
        return t("Extreme", "شديد");
    }

    private void chooseCity() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = linear(true);
        panel.setPadding(dp(22), dp(19), dp(22), dp(19));
        panel.setBackground(shape(CARD, BORDER, 18));

        TextView heading = text(t("Choose a city", "اختر مدينة"),
                19, PRIMARY, true);
        panel.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));

        final EditText queryInput = new EditText(this);
        queryInput.setSingleLine(true);
        queryInput.setTextSize(16);
        queryInput.setTextColor(PRIMARY);
        queryInput.setHintTextColor(SUBTLE);
        queryInput.setTypeface(arabic ? arabicTypeface : latinTypeface);
        queryInput.setHint(t("Search city", "ابحث عن مدينة"));
        queryInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        queryInput.setPadding(dp(13), 0, dp(13), 0);
        queryInput.setBackground(shape(0xFF202D42, BORDER, 11));
        LinearLayout.LayoutParams inputParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        inputParams.topMargin = dp(12);
        panel.addView(queryInput, inputParams);

        TextView search = button(t("Search", "بحث"), null);
        LinearLayout.LayoutParams searchParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        searchParams.topMargin = dp(9);
        panel.addView(search, searchParams);

        final TextView message = text("", 13, ACCENT, false);
        message.setPadding(dp(2), dp(6), dp(2), dp(6));
        panel.addView(message, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));

        ScrollView resultScroller = new ScrollView(this);
        resultScroller.setVerticalScrollBarEnabled(false);
        final LinearLayout results = linear(true);
        resultScroller.addView(results);
        panel.addView(resultScroller, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));

        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String query = queryInput.getText().toString().trim();
                if (query.length() < 2) {
                    message.setText(t(
                            "Enter at least two letters",
                            "أدخل حرفين على الأقل"));
                    return;
                }

                message.setText(t("Searching…", "جارٍ البحث…"));
                new AsyncTask<Void, Void, List<WeatherService.Place>>() {
                    @Override
                    protected List<WeatherService.Place> doInBackground(Void... ignored) {
                        try {
                            return WeatherService.searchLocations(
                                    WeatherActivity.this, query, arabic);
                        } catch (Exception error) {
                            android.util.Log.e("ApolloWeather",
                                    "City search failed", error);
                            return null;
                        }
                    }

                    @Override
                    protected void onPostExecute(List<WeatherService.Place> found) {
                        if (dead || isFinishing()) return;
                        results.removeAllViews();

                        if (found == null || found.isEmpty()) {
                            message.setText(t("No results", "لا توجد نتائج"));
                            return;
                        }

                        message.setText(t("Select a location", "اختر الموقع"));
                        for (final WeatherService.Place result : found) {
                            TextView item = button(result.name, new Runnable() {
                                @Override
                                public void run() {
                                    getSharedPreferences(
                                            WeatherService.PREFS, MODE_PRIVATE)
                                            .edit()
                                            .putString(WeatherService.PREF_CITY,
                                                    result.name)
                                            .putFloat(WeatherService.PREF_LATITUDE,
                                                    (float) result.latitude)
                                            .putFloat(WeatherService.PREF_LONGITUDE,
                                                    (float) result.longitude)
                                            .apply();
                                    place = result;
                                    dialog.dismiss();
                                    load();
                                }
                            }, false);
                            item.setGravity(arabic
                                    ? Gravity.RIGHT | Gravity.CENTER_VERTICAL
                                    : Gravity.LEFT | Gravity.CENTER_VERTICAL);
                            LinearLayout.LayoutParams params =
                                    new LinearLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            dp(44));
                            params.topMargin = dp(4);
                            results.addView(item, params);
                        }
                        if (results.getChildCount() > 0) {
                            results.getChildAt(0).requestFocus();
                        }
                    }
                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }
        });

        dialog.setContentView(panel);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = Math.min(dp(620),
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.72f));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }

        queryInput.requestFocus();
    }

    @Override
    protected void onDestroy() {
        dead = true;
        super.onDestroy();
    }
}
