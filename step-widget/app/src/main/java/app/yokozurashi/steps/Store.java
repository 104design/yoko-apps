package app.yokozurashi.steps;

import android.content.Context;
import android.content.SharedPreferences;

/** 端末に保存する値の置き場（歩数、歩幅、読み取りの時刻）。 */
public class Store {
    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("state", Context.MODE_PRIVATE);
    }
}
