package app.yokozurashi.news;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/** 天気。Open-Meteo から現在の気温と天気の種類を取る（APIキー不要）。 */
public class Weather {
    public static final int NONE = 0, SUN = 1, CLOUD = 2, FOG = 3, RAIN = 4, SNOW = 5, THUNDER = 6;

    private static final String KEY_TEMP = "weatherTemp";
    private static final String KEY_CODE = "weatherCode";
    private static final String KEY_TIME = "weatherTime";

    /** 取得して保存する。位置がなければ何もしない。成功したら true。 */
    public static boolean fetch(Context ctx) {
        if (!LocationHelper.hasLocation(ctx)) return false;
        try {
            String url = String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f"
                            + "&current=temperature_2m,weather_code&timezone=auto",
                    LocationHelper.lat(ctx), LocationHelper.lon(ctx));
            double[] r = parse(new String(Http.get(url), "UTF-8"));
            Store.prefs(ctx).edit()
                    .putFloat(KEY_TEMP, (float) r[0])
                    .putInt(KEY_CODE, (int) r[1])
                    .putLong(KEY_TIME, System.currentTimeMillis())
                    .apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 応答のJSONから {気温, 天気コード} を取り出す。 */
    static double[] parse(String json) throws JSONException {
        JSONObject current = new JSONObject(json).getJSONObject("current");
        return new double[]{current.getDouble("temperature_2m"), current.getInt("weather_code")};
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

    /** 「晴れ」などの言葉。取れていなければ null。 */
    public static String word(Context ctx) {
        SharedPreferences p = Store.prefs(ctx);
        return p.contains(KEY_TIME) ? wordOf(p.getInt(KEY_CODE, 0)) : null;
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

    static String wordOf(int code) {
        if (code <= 1) return "晴れ";
        if (code <= 3) return "くもり";
        if (code == 45 || code == 48) return "霧";
        if (code >= 51 && code <= 57) return "霧雨";
        if (code >= 61 && code <= 67) return "雨";
        if (code >= 71 && code <= 77) return "雪";
        if (code >= 80 && code <= 82) return "にわか雨";
        if (code == 85 || code == 86) return "にわか雪";
        if (code >= 95) return "雷雨";
        return "雨";
    }
}
