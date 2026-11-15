package app.yokozurashi.steps;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * 歩数の帳簿。歩数そのものはヘルスコネクトが持っていて、ここには読んだ結果を控える。
 *
 * 累計は「締めたぶん（BASE）」と「まだ締めていないぶん（OPEN）」の足し算。
 * 締めていない期間（締めた時刻から今まで）は、読むたびにヘルスコネクトで数え直す。
 * だから、時計があとからまとめて送ってきた歩数も、2日ほどのあいだは取りこめる。
 * それより前は締めて BASE に足しこむ。ヘルスコネクトの古いデータが消されても、累計は減らない。
 */
public class StepStore {
    public static final int STRIDE_DEFAULT = 70, STRIDE_MIN = 40, STRIDE_MAX = 100; // 歩幅（cm）

    private static final String BASE = "base";       // 締めた時刻までの歩数
    private static final String CLOSED = "closed";   // 締めた時刻。はじめは旅を始めた時刻と同じ
    private static final String OPEN = "open";       // 締めた時刻から、最後に読んだときまでの歩数
    private static final String DAY = "day";         // 「きょうの歩数」の日付（20261115 の形）
    private static final String TODAY = "today";     // その日の歩数
    private static final String START = "start";     // 旅を始めた時刻
    private static final String READ = "read";       // 最後に読めた時刻
    private static final String STRIDE = "stride";
    private static final String BONUS = "bonus";     // 表示の確認用に足す距離（m）

    /**
     * どこから数えるか（締めた時刻）を返す。はじめて呼ばれたときが旅の出発で、
     * その日の0時から数える（出発した日の歩数は、朝のぶんから入る）。
     */
    public static synchronized long closedTime(Context ctx, long now) {
        SharedPreferences p = Store.prefs(ctx);
        long closed = p.getLong(CLOSED, 0);
        if (closed == 0) {
            closed = Math.min(now, StepReader.todayStart());
            p.edit().putLong(CLOSED, closed).putLong(START, closed).apply();
        }
        return closed;
    }

    /** 読んだ結果を控える。open は締めた時刻から今まで、today はきょうの0時から今までの歩数。 */
    public static synchronized void update(Context ctx, long open, long today, long now) {
        Store.prefs(ctx).edit()
                .putLong(OPEN, open)
                .putInt(DAY, dayKey())
                .putLong(TODAY, today)
                .putLong(READ, now)
                .apply();
    }

    /** from から until までを締める。steps はその期間の歩数。 */
    public static synchronized void close(Context ctx, long from, long until, long steps) {
        SharedPreferences p = Store.prefs(ctx);
        if (p.getLong(CLOSED, 0) != from) return; // そのあいだに旅をやり直していた
        p.edit()
                .putLong(BASE, p.getLong(BASE, 0) + steps)
                .putLong(CLOSED, until)
                .putLong(OPEN, Math.max(0, p.getLong(OPEN, 0) - steps))
                .apply();
    }

    /** 旅を始めてからの歩数。 */
    public static long total(Context ctx) {
        SharedPreferences p = Store.prefs(ctx);
        return p.getLong(BASE, 0) + p.getLong(OPEN, 0);
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

    /** 旅を出発点からやり直す。いまより後に歩いたぶんから数える。 */
    public static synchronized void restart(Context ctx) {
        long now = System.currentTimeMillis();
        Store.prefs(ctx).edit()
                .putLong(BASE, 0)
                .putLong(OPEN, 0)
                .putLong(BONUS, 0)
                .putLong(CLOSED, now)
                .putLong(START, now)
                .apply();
    }

    private static int dayKey() {
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }
}
