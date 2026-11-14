package app.yokozurashi.pomodoro;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * While this service lives, swiping the app away from recents counts as
 * "kill the app": the timer is paused, its alarms are cancelled and the
 * home-screen widget stops too (user request, 2026-11-12).
 */
public class KillWatchService extends Service {
    public static void start(Context ctx) {
        try {
            ctx.startService(new Intent(ctx, KillWatchService.class));
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
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
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }
}
