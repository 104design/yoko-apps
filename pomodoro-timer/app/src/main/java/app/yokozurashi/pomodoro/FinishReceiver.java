package app.yokozurashi.pomodoro;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class FinishReceiver extends BroadcastReceiver {
    public static final String ACTION_FINISH = "app.yokozurashi.pomodoro.FINISH";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (PhaseAdvancer.advanceIfFinished(context) != null) {
            TimerWidgetProvider.updateAll(context);
        }
    }
}
