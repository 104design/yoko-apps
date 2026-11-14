package app.yokozurashi.pomodoro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WidgetFrameReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        TimerState s = TimerState.load(context);
        if (!s.running) {
            WidgetAnimHelper.stop(context);
            return;
        }
        // Even if the finish alarm is delayed by battery saving, the phase
        // still advances here (checked every frame tick) instead of the
        // widget chronometer running into negative time.
        String changed = PhaseAdvancer.advanceIfFinished(context);
        WidgetAnimHelper.flipFrame(context);
        if (changed != null || !WidgetAnimHelper.USE_FLIPPER) {
            // phase changed (character may differ), or the 1-per-second
            // flip-book is in use: rebuild everything
            TimerWidgetProvider.updateAll(context);
        } else {
            // only digits and tape move; the character keeps animating
            TimerWidgetProvider.updateTick(context);
        }
        // both paths re-arm the next tick
    }
}
