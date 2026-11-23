package app.yokozurashi.steps;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;

/**
 * 地点を通った日の記録。地図の「ずらし終わった日」に使う。
 *
 * 地点は、周回を通した通し番号 k で数える（k = 周回×地点の数 + 地点の番号）。
 * 地点 k を通った日 = 地点 k の名物をずらしはじめた日 = 地点 k-1 の名物をずらし終わった日。
 * 記録は「k=0 から順に、通った日（1970-01-01 からの日数）」をカンマでつないで保存する。
 *
 * 新しく通った地点の日付は、ヘルスコネクトの日ごとの歩数から割り出す（estimate）。
 * 歩数を読むたびではなく、まだ日付のない地点があるときだけ割り出す。
 */
public class PassLog {
    private static final String KEY = "pass_days";
    private static final int N = Route.AT_M.length;

    /** 通し番号 k の地点までの、出発からの道のり（m）。 */
    static long thresholdM(int k) {
        return (long) (k / N) * Route.LAP_M + Route.AT_M[k % N];
    }

    /** 距離 distanceM までに通った地点の数（出発点を含む）。 */
    static int passedCount(long distanceM) {
        long laps = distanceM / Route.LAP_M;
        long within = distanceM % Route.LAP_M;
        int i = 0;
        while (i < N && Route.AT_M[i] <= within) i++;
        return (int) (laps * N + i);
    }

    public static synchronized long[] days(Context ctx) {
        String s = Store.prefs(ctx).getString(KEY, "");
        if (s.isEmpty()) return new long[0];
        String[] parts = s.split(",");
        long[] out = new long[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Long.parseLong(parts[i]);
        return out;
    }

    private static void save(Context ctx, long[] days) {
        StringBuilder b = new StringBuilder();
        for (long d : days) {
            if (b.length() > 0) b.append(',');
            b.append(d);
        }
        Store.prefs(ctx).edit().putString(KEY, b.toString()).apply();
    }

    public static void clear(Context ctx) {
        Store.prefs(ctx).edit().remove(KEY).apply();
    }

    /** まだ日付のない地点があるか。 */
    public static boolean needsDates(Context ctx) {
        return days(ctx).length < passedCount(StepStore.distanceM(ctx));
    }

    /**
     * 日ごとの歩数（firstDay から1日ずつ）から、日付のない地点の日付を割り出して記録する。
     * すでに記録のある地点はそのまま。
     */
    public static synchronized void fill(Context ctx, LocalDate firstDay, long[] dailySteps) {
        long[] have = days(ctx);
        int need = passedCount(StepStore.distanceM(ctx));
        if (have.length >= need) return;
        save(ctx, estimate(have, need, firstDay.toEpochDay(), dailySteps,
                StepStore.stride(ctx), LocalDate.now().toEpochDay()));
    }

    /**
     * 割り出しの中身（端末に頼らない計算だけ）。
     * 地点 k に必要な歩数 = 道のり÷歩幅。日ごとの歩数を足していき、必要な歩数に届いた日をその地点の日とする。
     * 届かない地点（確認用に足した距離で進んだぶんなど）は、きょうにする。
     * 日付は、前の地点より前にならないようにそろえる。
     */
    static long[] estimate(long[] have, int need, long firstDay, long[] dailySteps, int strideCm, long today) {
        long[] out = new long[need];
        System.arraycopy(have, 0, out, 0, have.length);
        long[] cum = new long[dailySteps.length];
        long sum = 0;
        for (int i = 0; i < dailySteps.length; i++) {
            sum += Math.max(0, dailySteps[i]);
            cum[i] = sum;
        }
        int day = 0;
        for (int k = have.length; k < need; k++) {
            long steps = (thresholdM(k) * 100 + strideCm - 1) / strideCm;
            while (day < cum.length && cum[day] < steps) day++;
            long d = day < cum.length ? firstDay + day : today;
            if (steps == 0) d = firstDay;
            if (k > 0 && d < out[k - 1]) d = out[k - 1];
            out[k] = Math.min(d, today);
        }
        return out;
    }

    /**
     * 地点 i（0〜地点の数-1）の名物を、いちばん最近ずらし終わった日。まだなら -1。
     * 名物 i をずらし終わる = 次の地点（通し番号 k+1）を通る。
     */
    static long finishedDay(long[] days, int i) {
        long best = -1;
        for (int k = i; k + 1 < days.length; k += N) best = Math.max(best, days[k + 1]);
        return best;
    }
}
