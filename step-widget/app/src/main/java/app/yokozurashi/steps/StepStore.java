package app.yokozurashi.steps;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import java.util.Calendar;

/**
 * 歩数の帳簿。
 * 歩数センサーの値は「端末を起動してからの累計」なので、前に読んだ値との差を足していく。
 * 読むのがときどきでも、起動中に歩いたぶんは取りこぼさない。
 * 再起動をまたぐと、最後に読んでから電源を切るまでのぶんだけは数えられない。
 */
public class StepStore {
    public static final int STRIDE_DEFAULT = 70, STRIDE_MIN = 40, STRIDE_MAX = 100; // 歩幅（cm）

    private static final String COUNTER = "counter"; // 最後に読んだセンサーの値。まだ読んでいなければ -1
    private static final String BOOT = "boot";       // そのときの起動回数
    private static final String TOTAL = "total";     // 旅を始めてからの歩数
    private static final String DAY = "day";         // 「きょうの歩数」の日付（20261115 の形）
    private static final String TODAY = "today";     // その日の歩数
    private static final String START = "start";     // 旅を始めた時刻
    private static final String READ = "read";       // 最後にセンサーを読めた時刻
    private static final String STRIDE = "stride";
    private static final String BONUS = "bonus";     // 表示の確認用に足す距離（m）

    /** センサーの値を1つ受け取り、前回との差を歩数に足す。 */
    public static synchronized void apply(Context ctx, long counter) {
        SharedPreferences p = Store.prefs(ctx);
        long last = p.getLong(COUNTER, -1);
        int boot = bootCount(ctx);
        long delta;
        if (last < 0) {
            delta = 0; // はじめて読んだ。ここを旅の出発点にする
        } else if (boot != p.getInt(BOOT, boot) || counter < last) {
            delta = counter; // 再起動していた。センサーは0から数え直している
        } else {
            delta = counter - last;
        }
        int day = dayKey();
        long today = p.getInt(DAY, 0) == day ? p.getLong(TODAY, 0) : 0;
        SharedPreferences.Editor e = p.edit()
                .putLong(COUNTER, counter)
                .putInt(BOOT, boot)
                .putLong(TOTAL, p.getLong(TOTAL, 0) + delta)
                .putInt(DAY, day)
                .putLong(TODAY, today + delta)
                .putLong(READ, System.currentTimeMillis());
        if (p.getLong(START, 0) == 0) e.putLong(START, System.currentTimeMillis());
        e.apply();
    }

    public static long total(Context ctx) {
        return Store.prefs(ctx).getLong(TOTAL, 0);
    }

    /** きょうの歩数。日付が変わってからまだ読んでいなければ 0。 */
    public static long today(Context ctx) {
        SharedPreferences p = Store.prefs(ctx);
        return p.getInt(DAY, 0) == dayKey() ? p.getLong(TODAY, 0) : 0;
    }

    public static long startTime(Context ctx) {
        return Store.prefs(ctx).getLong(START, 0);
    }

    public static long readTime(Context ctx) {
        return Store.prefs(ctx).getLong(READ, 0);
    }

    public static int stride(Context ctx) {
        return Store.prefs(ctx).getInt(STRIDE, STRIDE_DEFAULT);
    }

    public static void setStride(Context ctx, int cm) {
        Store.prefs(ctx).edit()
                .putInt(STRIDE, Math.max(STRIDE_MIN, Math.min(STRIDE_MAX, cm))).apply();
    }

    /** 旅を始めてから進んだ距離（m）。歩数×歩幅。歩幅を変えると、これまでのぶんも変わる。 */
    public static long distanceM(Context ctx) {
        return steps2m(total(ctx), stride(ctx)) + Store.prefs(ctx).getLong(BONUS, 0);
    }

    public static long steps2m(long steps, int strideCm) {
        return steps * strideCm / 100;
    }

    /** 表示の確認用に、距離を足す。 */
    public static void addBonus(Context ctx, long meters) {
        SharedPreferences p = Store.prefs(ctx);
        p.edit().putLong(BONUS, p.getLong(BONUS, 0) + meters).apply();
    }

    /** 旅を出発点からやり直す。センサーの読み取り位置は残すので、このあと歩いたぶんから数える。 */
    public static void restart(Context ctx) {
        Store.prefs(ctx).edit()
                .putLong(TOTAL, 0)
                .putLong(TODAY, 0)
                .putLong(BONUS, 0)
                .putLong(START, System.currentTimeMillis())
                .apply();
    }

    private static int dayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    /** 端末の起動回数。取れない端末では 0 を返し、そのときはセンサーの値が減ったことで再起動を見分ける。 */
    private static int bootCount(Context ctx) {
        try {
            return Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.BOOT_COUNT);
        } catch (Exception e) {
            return 0;
        }
    }
}
