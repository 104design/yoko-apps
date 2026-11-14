package app.yokozurashi.pomodoro;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Vibrator;

/**
 * Shared phase-transition logic. If the timer is running and its end time
 * has passed, advance to the next phase (auto-starting it for Pomodoro),
 * play the finish sound/vibration, post the notification, and reschedule
 * the finish alarm. Returns the transition message, or null if no
 * transition was due. Safe to call from anywhere, repeatedly.
 */
public class PhaseAdvancer {
    public static String advanceIfFinished(Context ctx) {
        TimerState s = TimerState.load(ctx);
        if (!s.running) return null;
        if (s.remainingNow() > 0) return null;
        String msg = s.advancePhase();
        s.save(ctx);
        if ("timer_end".equals(s.lastEvent)) {
            SoundHelper.startTimerAlert(ctx);
        } else {
            SoundHelper.playEvent(ctx, s.lastEvent);
        }
        if (msg != null && msg.length() > 0 && !MainActivity.foreground) {
            // while the app is on screen, the in-app banner alone announces
            // the change; system notifications are for when it's closed
            String[] lines = msg.split("\n", 2);
            NotifyHelper.post(ctx, lines[0], lines.length > 1 ? lines[1] : "");
        }
        if (s.running) {
            AlarmHelper.schedule(ctx, s.endAtMs);
            KillWatchService.start(ctx);
        } else {
            AlarmHelper.cancel(ctx);
        }
        return msg;
    }
}
