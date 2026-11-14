package app.yokozurashi.news;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** 天気。Open-Meteo から、いまの気温と天気の種類、今日の最高・最低気温と降水確率を取る（APIキー不要）。 */
public class Weather {
    public static final int NONE = 0, SUN = 1, CLOUD = 2, FOG = 3, RAIN = 4, SNOW = 5, THUNDER = 6;

    private static final String KEY_TEMP = "weatherTemp";
    private static final String KEY_CODE = "weatherCode";
    private static final String KEY_TIME = "weatherTime";
    private static final String KEY_HIGH = "weatherHigh";
    private static final String KEY_LOW = "weatherLow";
    private static final String KEY_RAIN = "weatherRain";

    /** 取得して保存する。位置がなければ何もしない。成功したら true。 */
    public static boolean fetch(Context ctx) {
        if (!LocationHelper.hasLocation(ctx)) return false;
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                            + "&current=temperature_2m,weather_code"
                            + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                            + "&forecast_days=1&timezone=auto",
                    LocationHelper.lat(ctx), LocationHelper.lon(ctx));
            double[] r = parse(new String(Http.get(url), "UTF-8"));
            SharedPreferences.Editor e = Store.prefs(ctx).edit()
                    .putFloat(KEY_TEMP, (float) r[0])
                    .putInt(KEY_CODE, (int) r[1])
                    .putLong(KEY_TIME, System.currentTimeMillis());
            put(e, KEY_HIGH, r[2]);
            put(e, KEY_LOW, r[3]);
            put(e, KEY_RAIN, r[4]);
            e.apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void put(SharedPreferences.Editor e, String key, double v) {
        if (Double.isNaN(v)) e.remove(key);
        else e.putFloat(key, (float) v);
    }

    /**
     * 応答のJSONから {気温, 天気コード, 最高気温, 最低気温, 降水確率} を取り出す。
     * 今日の3つは、応答に入っていなければ NaN（いまの気温だけでも表示できるように）。
     */
    static double[] parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject current = root.getJSONObject("current");
        JSONObject daily = root.optJSONObject("daily");
        return new double[]{
                current.getDouble("temperature_2m"), current.getInt("weather_code"),
                first(daily, "temperature_2m_max"), first(daily, "temperature_2m_min"),
                first(daily, "precipitation_probability_max")};
    }

    private static double first(JSONObject daily, String key) {
        if (daily == null) return Double.NaN;
        JSONArray arr = daily.optJSONArray(key);
        if (arr == null || arr.length() == 0 || arr.isNull(0)) return Double.NaN;
        return arr.optDouble(0, Double.NaN);
    }

    public static boolean has(Context ctx) {
        return Store.prefs(ctx).contains(KEY_TIME);
    }

    public static long time(Context ctx) {
        return Store.prefs(ctx).getLong(KEY_TIME, 0);
    }

    /** 「18°」の形。取れていなければ「--°」。 */
    public static String tempText(Context ctx) {
        SharedPreferences p = Store.prefs(ctx);
        if (!p.contains(KEY_TIME)) return "--°";
        return Math.round(p.getFloat(KEY_TEMP, 0)) + "°";
    }

    public static int kind(Context ctx) {
        SharedPreferences p = Store.prefs(ctx);
        return p.contains(KEY_TIME) ? kindOf(p.getInt(KEY_CODE, 0)) : NONE;
    }

    /** 今日の最高気温「22°」。取れていなければ null。 */
    public static String highText(Context ctx) {
        return degrees(ctx, KEY_HIGH);
    }

    /** 今日の最低気温「14°」。取れていなければ null。 */
    public static String lowText(Context ctx) {
        return degrees(ctx, KEY_LOW);
    }

    /** 今日の降水確率「30%」。取れていなければ null。 */
    public static String rainText(Context ctx) {
        SharedPreferences p = Store.prefs(ctx);
        return p.contains(KEY_RAIN) ? Math.round(p.getFloat(KEY_RAIN, 0)) + "%" : null;
    }

    private static String degrees(Context ctx, String key) {
        SharedPreferences p = Store.prefs(ctx);
        return p.contains(key) ? Math.round(p.getFloat(key, 0)) + "°" : null;
    }

    /** WMOの天気コードを、アイコンの種類に分ける。 */
    static int kindOf(int code) {
        if (code <= 1) return SUN;
        if (code <= 3) return CLOUD;
        if (code == 45 || code == 48) return FOG;
        if (code >= 71 && code <= 77) return SNOW;
        if (code == 85 || code == 86) return SNOW;
        if (code >= 95) return THUNDER;
        return RAIN;
    }
}
