package app.yokozurashi.pomodoro;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Drives the widget's periodic tick while the timer runs: fresh countdown
 * frames, tape, tomatoes, and the phase check.
 *
 * The system spaces an app's alarms at least 5 seconds apart, so the tick
 * cannot be once a second. Instead each tick hands the widget the next 20
 * seconds of digit frames, which the home screen flips by itself. Ticks are
 * aimed at the instants where the remaining time is a multiple of 5 seconds,
 * so new frames arrive exactly when a second turns over and the digits do
 * not stutter.
 *
 * Character animation, two ways:
 *  - USE_FLIPPER = true: the widget layout holds all frames in a
 *    ViewFlipper that the home screen flips by itself at the app's own
 *    speed. The tick then only touches digits and tape.
 *  - USE_FLIPPER = false: the old way. Each tick swaps one still frame
 *    (3 frames, one per tick).
 * The flipper was agreed as a trial (2026-11-13): if it stutters, runs at
 * the wrong speed, or drains the battery on the real device, set this to
 * false and rebuild. Nothing else needs to change.
 */
public class WidgetAnimHelper {
    public static final boolean USE_FLIPPER = true;

    /**
     * Countdown digits while running, two ways:
     *  - true: hand-drawn digit frames that the home screen flips, refreshed
     *    by the tick. Looks right, but depends on the tick arriving. If the
     *    system holds the tick back, the digits repeat old frames.
     *  - false: a Chronometer in the system font. Plain, but the home screen
     *    counts it by itself, so it is always correct.
     * Even when true, the Chronometer is used whenever the tick cannot be
     * trusted: while battery saver is on (the system then holds back the
     * ticks of background apps), and when exact alarms are not permitted
     * (the tick would then arrive minutes late).
     * If stale digits are still seen on the real device, set this to false.
     */
    public static final boolean USE_FRAME_DIGITS = true;

    /** True when the tick can be expected to arrive, so frame digits are safe. */
    static boolean frameDigitsUsable(Context ctx) {
        if (!USE_FRAME_DIGITS) return false;
        android.os.PowerManager pm =
                (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isPowerSaveMode()) return false;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
    }

    public static final String ACTION_FRAME = "app.yokozurashi.pomodoro.WIDGET_FRAME";
    private static final int REQ = 4001;
    private static final long TICK_MS = 5000;

    /** Milliseconds until the next tick: the next moment (at least about
     *  5 s away) when the remaining time is a whole multiple of 5 seconds.
     *  The 300 ms tolerance keeps a tick that fired slightly late from
     *  skipping a slot. */
    static long nextTickDelay(long remainingMs) {
        long target = ((remainingMs - TICK_MS + 300) / TICK_MS) * TICK_MS;
        if (remainingMs - TICK_MS + 300 < 0) return TICK_MS; // about to finish anyway
        return Math.max(1000, remainingMs - target);
    }

    private static PendingIntent pendingIntent(Context ctx) {
        Intent i = new Intent(ctx, WidgetFrameReceiver.class);
        i.setAction(ACTION_FRAME);
        return PendingIntent.getBroadcast(ctx, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static int frameIndex(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(TimerState.PREF, Context.MODE_PRIVATE);
        return p.getInt("widgetFrame", 0);
    }

    public static void flipFrame(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(TimerState.PREF, Context.MODE_PRIVATE);
        p.edit().putInt("widgetFrame", frameIndex(ctx) + 1).commit();
    }

    public static boolean hasWidgets(Context ctx) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, TimerWidgetProvider.class));
        return ids != null && ids.length > 0;
    }

    /** Keep the frame chain alive while running (and a widget exists); stop it otherwise. */
    public static void sync(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        TimerState s = TimerState.load(ctx);
        if (s.running && hasWidgets(ctx)) {
            long at = System.currentTimeMillis() + nextTickDelay(s.remainingNow());
            // Deliberately NOT an "allow while idle" alarm. The system gives
            // each app only a small hourly quota of those for doze and
            // battery saver, and a tick every 5 seconds used it all up, so
            // the alarm that ends a phase (AlarmHelper) was held back while
            // the phone slept. The tick is only cosmetic: it can wait until
            // the phone wakes (RTC is a non-wakeup alarm and fires then).
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.set(AlarmManager.RTC, at, pendingIntent(ctx));
                } else {
                    am.setExact(AlarmManager.RTC, at, pendingIntent(ctx));
                }
            } catch (SecurityException e) {
                try { am.set(AlarmManager.RTC, at, pendingIntent(ctx)); }
                catch (Exception ignored) {}
            }
        } else {
            am.cancel(pendingIntent(ctx));
        }
    }

    public static void stop(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pendingIntent(ctx));
    }
}
