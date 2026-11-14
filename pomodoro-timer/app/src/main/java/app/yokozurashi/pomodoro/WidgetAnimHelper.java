package app.yokozurashi.pomodoro;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/** Drives the widget's 1-second flip-book animation while the timer runs. */
public class WidgetAnimHelper {
    public static final String ACTION_FRAME = "app.yokozurashi.pomodoro.WIDGET_FRAME";
    private static final int REQ = 4001;
    private static final long FRAME_MS = 1000;

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
            long at = System.currentTimeMillis() + FRAME_MS;
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC, at, pendingIntent(ctx));
                } else if (Build.VERSION.SDK_INT >= 23) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, pendingIntent(ctx));
                } else {
                    am.setExact(AlarmManager.RTC, at, pendingIntent(ctx));
                }
            } catch (SecurityException e) {
                try { am.setAndAllowWhileIdle(AlarmManager.RTC, at, pendingIntent(ctx)); }
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
