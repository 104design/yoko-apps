package app.yokozurashi.news;

import android.content.Context;
import android.content.SharedPreferences;

/** 端末に保存する値の置き場（位置、天気、ニュース一覧、更新時刻）。 */
public class Store {
    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("state", Context.MODE_PRIVATE);
    }
}
