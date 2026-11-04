package com.karrar.tvlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** Open-Meteo forecast and geocoding client shared by the launcher and weather page. */
public final class WeatherService {
    public static final String PREFS = "tv_launcher_preferences";
    public static final String PREF_CITY = "weather_city";
    public static final String PREF_LATITUDE = "weather_latitude";
    public static final String PREF_LONGITUDE = "weather_longitude";
    private static final String DEFAULT_CITY = "Basra, Iraq";
    private static final double DEFAULT_LATITUDE = 30.5085;
    private static final double DEFAULT_LONGITUDE = 47.7804;

    private WeatherService() { }

    public static final class Place {
        public final String name;
        public final double latitude;
        public final double longitude;

        public Place(String name, double latitude, double longitude) {
            this.name = name == null || name.length() == 0 ? DEFAULT_CITY : name;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String shortName() {
            int comma = name.indexOf(',');
            return comma > 0 ? name.substring(0, comma).trim() : name;
        }
    }

    public static Place getSavedLocation(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return new Place(prefs.getString(PREF_CITY, DEFAULT_CITY),
                prefs.getFloat(PREF_LATITUDE, (float) DEFAULT_LATITUDE),
                prefs.getFloat(PREF_LONGITUDE, (float) DEFAULT_LONGITUDE));
    }

    public static JSONObject fetchForecast(double latitude, double longitude) throws Exception {
        StringBuilder url = new StringBuilder("https://api.open-meteo.com/v1/forecast?");
        url.append("latitude=").append(latitude).append("&longitude=").append(longitude);
        url.append("&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m,wind_direction_10m,wind_gusts_10m");
        url.append("&hourly=temperature_2m,precipitation_probability,weather_code,wind_speed_10m");
        url.append("&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max,precipitation_probability_max,precipitation_sum");
        url.append("&timezone=auto&forecast_days=7");
        url.append("&temperature_unit=celsius&wind_speed_unit=kmh&precipitation_unit=mm");
        return requestJson(url.toString());
    }

    public static List<Place> searchLocations(String query, boolean arabic) throws Exception {
        String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                + URLEncoder.encode(query, "UTF-8") + "&count=10&language="
                + (arabic ? "ar" : "en") + "&format=json";
        JSONArray results = requestJson(url).optJSONArray("results");
        List<Place> places = new ArrayList<Place>();
        if (results == null) return places;
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.optJSONObject(i);
            if (item == null) continue;
            String city = item.optString("name", "");
            String admin = item.optString("admin1", "");
            String country = item.optString("country", "");
            StringBuilder label = new StringBuilder(city);
            if (admin.length() > 0 && !admin.equalsIgnoreCase(city)) label.append(", ").append(admin);
            if (country.length() > 0) label.append(", ").append(country);
            places.add(new Place(label.toString(),
                    item.optDouble("latitude", DEFAULT_LATITUDE),
                    item.optDouble("longitude", DEFAULT_LONGITUDE)));
        }
        return places;
    }

    private static JSONObject requestJson(String address) throws Exception {
        HttpURLConnection connection = null;
        InputStream stream = null;
        BufferedReader reader = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(15000);
            connection.setUseCaches(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent",
                    "ApolloLauncher/2.0 (https://github.com/SANDRO00O/ApolloLauncher)");
            int status = connection.getResponseCode();
            stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            if (stream == null) throw new IllegalStateException("Weather service HTTP " + status);
            reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            JSONObject json = new JSONObject(body.toString());
            if (status < 200 || status >= 300 || json.optBoolean("error", false)) {
                throw new IllegalStateException(json.optString("reason",
                        "Weather service HTTP " + status));
            }
            return json;
        } finally {
            try { if (reader != null) reader.close(); } catch (Exception ignored) { }
            try { if (stream != null) stream.close(); } catch (Exception ignored) { }
            if (connection != null) connection.disconnect();
        }
    }

    public static String condition(int code, boolean ar) {
        switch (code) {
            case 0: return ar ? "سماء صافية" : "Clear sky";
            case 1: return ar ? "صحو غالباً" : "Mainly clear";
            case 2: return ar ? "غائم جزئياً" : "Partly cloudy";
            case 3: return ar ? "غائم" : "Overcast";
            case 45: return ar ? "ضباب" : "Fog";
            case 48: return ar ? "ضباب متجمد" : "Depositing rime fog";
            case 51: return ar ? "رذاذ خفيف" : "Light drizzle";
            case 53: return ar ? "رذاذ متوسط" : "Moderate drizzle";
            case 55: return ar ? "رذاذ كثيف" : "Dense drizzle";
            case 56: return ar ? "رذاذ متجمد خفيف" : "Light freezing drizzle";
            case 57: return ar ? "رذاذ متجمد كثيف" : "Dense freezing drizzle";
            case 61: return ar ? "مطر خفيف" : "Slight rain";
            case 63: return ar ? "مطر متوسط" : "Moderate rain";
            case 65: return ar ? "مطر غزير" : "Heavy rain";
            case 66: return ar ? "مطر متجمد خفيف" : "Light freezing rain";
            case 67: return ar ? "مطر متجمد غزير" : "Heavy freezing rain";
            case 71: return ar ? "ثلوج خفيفة" : "Slight snowfall";
            case 73: return ar ? "ثلوج متوسطة" : "Moderate snowfall";
            case 75: return ar ? "ثلوج كثيفة" : "Heavy snowfall";
            case 77: return ar ? "حبيبات ثلجية" : "Snow grains";
            case 80: return ar ? "زخات مطر خفيفة" : "Slight rain showers";
            case 81: return ar ? "زخات مطر متوسطة" : "Moderate rain showers";
            case 82: return ar ? "زخات مطر غزيرة" : "Violent rain showers";
            case 85: return ar ? "زخات ثلجية خفيفة" : "Slight snow showers";
            case 86: return ar ? "زخات ثلجية غزيرة" : "Heavy snow showers";
            case 95: return ar ? "عاصفة رعدية" : "Thunderstorm";
            case 96: return ar ? "عاصفة رعدية مع برد خفيف" : "Thunderstorm with slight hail";
            case 99: return ar ? "عاصفة رعدية مع برد كثيف" : "Thunderstorm with heavy hail";
            default: return ar ? "حالة جوية غير معروفة" : "Unknown conditions";
        }
    }

    public static String icon(int code, boolean day) {
        int glyph;
        if (code == 0) glyph = day ? 0xF00D : 0xF02E;
        else if (code == 1 || code == 2) glyph = day ? 0xF002 : 0xF086;
        else if (code == 3) glyph = day ? 0xF013 : 0xF031;
        else if (code == 45 || code == 48) glyph = day ? 0xF003 : 0xF04A;
        else if (code >= 51 && code <= 57) glyph = day ? 0xF00B : 0xF02B;
        else if (code >= 61 && code <= 67) glyph = day ? 0xF008 : 0xF028;
        else if ((code >= 71 && code <= 77) || code == 85 || code == 86) glyph = day ? 0xF00A : 0xF02A;
        else if (code >= 80 && code <= 82) glyph = day ? 0xF009 : 0xF029;
        else if (code >= 95) glyph = day ? 0xF010 : 0xF02D;
        else glyph = 0xF07B;
        return String.valueOf((char) glyph);
    }

    public static String windDirection(int degrees, boolean ar) {
        String[] en = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        String[] arabic = {"ش", "ش ق", "ق", "ج ق", "ج", "ج غ", "غ", "ش غ"};
        return ar ? arabic[(((degrees % 360) + 22) / 45) % 8]
                : en[(((degrees % 360) + 22) / 45) % 8];
    }
}
