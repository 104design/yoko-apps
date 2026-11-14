package app.yokozurashi.pomodoro;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmHelper {
    private static final int REQ = 1001;

    private static PendingIntent pendingIntent(Context ctx) {
        Intent i = new Intent(ctx, FinishReceiver.class);
        i.setAction(FinishReceiver.ACTION_FINISH);
        return PendingIntent.getBroadcast(ctx, REQ, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void schedule(Context ctx, long endAtMs) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMs, pendingIntent(ctx));
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMs, pendingIntent(ctx));
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, endAtMs, pendingIntent(ctx));
            }
        } catch (SecurityException e) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMs, pendingIntent(ctx)); }
            catch (Exception ignored) {}
        }
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pendingIntent(ctx));
    }
}
