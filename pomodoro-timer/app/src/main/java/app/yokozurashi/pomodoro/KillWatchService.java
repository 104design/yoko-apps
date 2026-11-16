package app.yokozurashi.pomodoro;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Two jobs.
 *
 * 1. Kill watch (as before): while this service lives, swiping the app
 *    away from recents counts as "kill the app". The timer is paused, its
 *    alarms are cancelled and the home-screen widget stops too
 *    (user request, 2026-11-12).
 *
 * 2. Keep the app alive while the timer runs (2026-11-16): the service
 *    goes to the foreground with a quiet ongoing notification. Without
 *    this, phones that stop background apps (POCO / HyperOS with
 *    auto-start off and battery saver on) stopped delivering the widget
 *    tick and the phase-end alarm about 20 seconds after the app was
 *    closed, so the widget ran past 0 and no sound played until the app
 *    was opened. The notification is the price of not being stopped.
 *    It leaves the foreground again as soon as the timer is not running.
 */
public class KillWatchService extends Service {
    private static final String CHANNEL = "timer_running";
    private static final int NOTIF_ID = 5001;
    private static final String EXTRA_FOREGROUND = "foreground";

    private static KillWatchService live;   // the running instance, same process
    private boolean inForeground = false;
    private String shownKey = "";

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Once a second while in the foreground: end the phase on time even if
     *  the alarm is late. While the app screen is open, the screen does this
     *  itself (with its banner), so stay out of its way. */
    private final Runnable watch = new Runnable() {
        @Override public void run() {
            if (!MainActivity.foreground
                    && PhaseAdvancer.advanceIfFinished(KillWatchService.this) != null) {
                TimerWidgetProvider.updateAll(KillWatchService.this);
            }
            if (inForeground) handler.postDelayed(this, 1000);
        }
    };

    /** Start the service. Runs in the foreground if the timer is running. */
    public static void start(Context ctx) {
        try {
            TimerState s = TimerState.load(ctx);
            Intent i = new Intent(ctx, KillWatchService.class);
            if (s.running && Build.VERSION.SDK_INT >= 26) {
                i.putExtra(EXTRA_FOREGROUND, true);
                ctx.startForegroundService(i);
            } else {
                ctx.startService(i);
            }
        } catch (Exception ignored) {
            // not allowed right now (e.g. started from the background):
            // the timer still works through its alarms, as before
        }
    }

    /** Match the saved timer state: foreground while running, not otherwise.
     *  Called after every state change (TimerWidgetProvider.updateAll). */
    public static void sync(Context ctx) {
        TimerState s = TimerState.load(ctx);
        KillWatchService svc = live;
        if (s.running) {
            if (svc != null && svc.inForeground) svc.refresh(s);
            else start(ctx);
        } else if (svc != null) {
            svc.leaveForeground();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        live = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        TimerState s = TimerState.load(this);
        boolean asked = intent != null && intent.getBooleanExtra(EXTRA_FOREGROUND, false);
        // a service started with startForegroundService() must go to the
        // foreground once, even if the timer stopped in the meantime
        if (asked || s.running) enterForeground(s);
        if (!s.running) leaveForeground();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(watch);
        if (live == this) live = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        TimerState s = TimerState.load(this);
        if (s.running) {
            s.pause();
            s.save(this);
            AlarmHelper.cancel(this);
        }
        WidgetAnimHelper.stop(this);
        TimerWidgetProvider.updateAll(this);
        leaveForeground();
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    private void enterForeground(TimerState s) {
        try {
            Notification n = buildNotification(s);
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIF_ID, n);
            }
            shownKey = key(s);
            if (!inForeground) {
                inForeground = true;
                handler.removeCallbacks(watch);
                handler.postDelayed(watch, 1000);
            }
        } catch (Exception e) {
            // the system refused; carry on without the foreground state
            inForeground = false;
        }
    }

    private void leaveForeground() {
        handler.removeCallbacks(watch);
        if (inForeground) {
            inForeground = false;
            shownKey = "";
            try { stopForeground(true); } catch (Exception ignored) {}
        }
    }

    /** Update the notification when the phase or its end time changed. */
    private void refresh(TimerState s) {
        if (key(s).equals(shownKey)) return;
        enterForeground(s);
    }

    private static String key(TimerState s) {
        return s.kind + "/" + s.mode + "/" + s.endAtMs;
    }

    private Notification buildNotification(TimerState s) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            // LOW: shown in the shade, but no sound, no vibration, no pop-up
            NotificationChannel ch = new NotificationChannel(CHANNEL, "タイマー動作中",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("タイマーが動いている間だけ出る通知。これがあると、アプリを閉じていても時間どおりに鳴ります");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        String title;
        if (!"pomodoro".equals(s.kind)) title = "タイマー";
        else if ("short".equals(s.mode)) title = "休憩";
        else if ("long".equals(s.mode)) title = "長い休憩";
        else title = "集中 " + Math.min(4, s.cycles + 1) + "/4";

        Intent open = new Intent(this, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 3003, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
         .setContentTitle(title)
         .setContentText("よこドーロタイマー 動作中")
         .setContentIntent(pi)
         .setOngoing(true)
         .setOnlyAlertOnce(true)
         // the notification counts down by itself to the end of the phase
         .setWhen(s.endAtMs)
         .setShowWhen(true)
         .setUsesChronometer(true);
        if (Build.VERSION.SDK_INT >= 24) b.setChronometerCountDown(true);
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }
}
