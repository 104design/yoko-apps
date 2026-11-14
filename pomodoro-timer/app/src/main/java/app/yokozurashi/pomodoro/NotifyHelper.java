package app.yokozurashi.pomodoro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class NotifyHelper {
    public static final String CHANNEL = "timer_events";
    private static int nextId = 3001;

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "タイマーのお知らせ",
                        NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("集中・休憩の切り替わりなどのお知らせ");
                nm.createNotificationChannel(ch);
            }
        }
    }

    public static void post(Context ctx, String title, String text) {
        try {
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent open = new Intent(ctx, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 3002, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, CHANNEL)
                    : new Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
             .setContentTitle(title)
             .setContentText(text.replace("\n", " "))
             .setStyle(new Notification.BigTextStyle().bigText(text))
             .setContentIntent(pi)
             .setAutoCancel(true);
            nm.notify(nextId++, b.build());
        } catch (Exception ignored) {}
    }
}
