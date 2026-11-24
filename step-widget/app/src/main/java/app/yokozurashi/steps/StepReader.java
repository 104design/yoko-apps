package app.yokozurashi.steps;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;

/**
 * ヘルスコネクトから歩数を読み、帳簿（StepStore）を更新する。
 * 読むのは「締めた時刻から今まで」と「きょうの0時から今まで」の2つ。
 * 3日より前のぶんは1日1回、帳簿に足しこんで締める（StepStore の説明を参照）。
 */
public class StepReader {
    public static final int OK = 0, UNSUPPORTED = 1, NO_PERMISSION = 2;

    public static final String PERMISSION_STEPS = "android.permission.health.READ_STEPS";
    public static final String PERMISSION_BACKGROUND =
            "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND";

    private static final long TIMEOUT = 8000;  // 返事が来ないときにあきらめるまで（ms）
    private static final int KEEP_OPEN_DAYS = 2; // きょうと、その前の2日ぶんは締めずに毎回数え直す

    public static int status(Context ctx) {
        if (Build.VERSION.SDK_INT < 34) return UNSUPPORTED;
        return granted(ctx, PERMISSION_STEPS) ? OK : NO_PERMISSION;
    }

    /** アプリを開いていないときにも読めるか。読めなければ、歩数はアプリを開いたときにだけ進む。 */
    public static boolean canReadInBackground(Context ctx) {
        return granted(ctx, PERMISSION_BACKGROUND);
    }

    private static boolean granted(Context ctx, String permission) {
        return ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * 歩数を読んで帳簿を更新し、ウィジェットを描き直してから done を呼ぶ。
     * 読めなかったときも、描き直しと done は行う。done はメインスレッドで呼ばれる。
     */
    public static void read(Context context, final Runnable done) {
        final Context ctx = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] finished = {false};
        final Runnable finish = new Runnable() {
            @Override
            public void run() {
                if (finished[0]) return;
                finished[0] = true;
                handler.removeCallbacksAndMessages(null);
                StepWidgetProvider.updateAll(ctx);
                if (done != null) done.run();
            }
        };
        if (status(ctx) != OK) {
            handler.post(finish);
            return;
        }
        handler.postDelayed(finish, TIMEOUT);

        final long now = System.currentTimeMillis();
        final long closed = StepStore.closedTime(ctx, now);
        final long midnight = midnight(0);
        HealthSteps.count(ctx, closed, now, new HealthSteps.Result() {
            @Override
            public void done(final long open) {
                if (open < 0 || finished[0]) {
                    finish.run();
                    return;
                }
                HealthSteps.count(ctx, midnight, now, new HealthSteps.Result() {
                    @Override
                    public void done(long today) {
                        if (today < 0 || finished[0]) {
                            finish.run();
                            return;
                        }
                        StepStore.update(ctx, open, today, now);
                        close(ctx, closed, new Runnable() {
                            @Override
                            public void run() {
                                fillPassDays(ctx, finish);
                            }
                        });
                    }
                });
            }
        });
    }

    /** 締めた時刻が古くなっていたら、2日前の0時まで進め、そこまでの歩数を帳簿に足しこむ。 */
    private static void close(final Context ctx, final long closed, final Runnable finish) {
        final long until = midnight(-KEEP_OPEN_DAYS);
        if (until <= closed) {
            finish.run();
            return;
        }
        HealthSteps.count(ctx, closed, until, new HealthSteps.Result() {
            @Override
            public void done(long steps) {
                if (steps >= 0) StepStore.close(ctx, closed, until, steps);
                finish.run();
            }
        });
    }

    /**
     * まだ日付のない地点があれば、ヘルスコネクトの日ごとの歩数から日付を割り出す（地図の「ずらし終わった日」）。
     * 読めなかったときは何もしない。次に読めたときにまた割り出す。
     */
    private static void fillPassDays(final Context ctx, final Runnable finish) {
        if (!PassLog.needsDates(ctx)) {
            finish.run();
            return;
        }
        long start = StepStore.startTime(ctx);
        final LocalDate from = start == 0 ? LocalDate.now()
                : Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate();
        HealthSteps.daily(ctx, from, LocalDate.now(), new HealthSteps.DailyResult() {
            @Override
            public void done(long[] steps) {
                if (steps != null) PassLog.fill(ctx, from, steps);
                PassLog.setFailed(ctx, steps == null);
                finish.run();
            }
        });
    }

    /** きょうから days 日ずらした日の0時。 */
    private static long midnight(int days) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, days);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    /** きょうの0時。旅の出発点に使う。 */
    public static long todayStart() {
        return midnight(0);
    }
}
