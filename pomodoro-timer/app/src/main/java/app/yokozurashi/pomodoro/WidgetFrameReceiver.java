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
        PhaseAdvancer.advanceIfFinished(context);
        WidgetAnimHelper.flipFrame(context);
        TimerWidgetProvider.updateAll(context); // updateAll re-syncs the next frame
    }
}
