package app.yokozurashi.news;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

/** 0時に日付を描き直すための予約。 */
public class DateAlarm {
    private static PendingIntent pendingIntent(Context ctx) {
        Intent i = new Intent(ctx, NewsWidgetProvider.class);
        i.setAction(NewsWidgetProvider.ACTION_DATE_TICK);
        return PendingIntent.getBroadcast(ctx, 3, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** 次の0時（の1秒後）に予約する。何度呼んでも予約は1つ。 */
    public static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar next = Calendar.getInstance();
        next.add(Calendar.DAY_OF_MONTH, 1);
        next.set(Calendar.HOUR_OF_DAY, 0);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 1);
        next.set(Calendar.MILLISECOND, 0);
        am.setAndAllowWhileIdle(AlarmManager.RTC, next.getTimeInMillis(), pendingIntent(ctx));
    }

    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pendingIntent(ctx));
    }
}
