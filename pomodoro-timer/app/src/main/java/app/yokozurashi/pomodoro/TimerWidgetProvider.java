package app.yokozurashi.pomodoro;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Home-screen widget. From the top: 4 tomatoes (pomodoro only), the
 * hand-drawn digits, the character on a short tape, and the hand-drawn
 * play / pause icon.
 *
 * Running or stopped is shown in three places at once:
 *   digits     - full strength while running, faded while stopped
 *   character  - walking (coffee: steaming) while running, pause/wait art while stopped
 *   icon       - pause while running, play while stopped
 * Focus or break is shown by the character (yokozurashi / coffee mug), not by text.
 *
 * Tapping the play/pause strip toggles the timer; tapping anywhere else opens the app.
 */
public class TimerWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TOGGLE = "app.yokozurashi.pomodoro.WIDGET_TOGGLE";

    /** Digits are faded to about 30% while stopped. The 4 and 5 stay filled
     *  (they are the "yoko" mark), so fill is never used to show state. */
    private static final int ALPHA_RUNNING = 255;
    private static final int ALPHA_STOPPED = 77;

    private static final int[] DIGIT_RES = {
            R.drawable.num_0, R.drawable.num_1, R.drawable.num_2, R.drawable.num_3,
            R.drawable.num_4, R.drawable.num_5, R.drawable.num_6, R.drawable.num_7,
            R.drawable.num_8, R.drawable.num_9};
    private static final int[] TOMATO_IDS = {
            R.id.widget_tom_1, R.id.widget_tom_2, R.id.widget_tom_3, R.id.widget_tom_4};
    // fallback flip-book (1 frame per second) when the auto-flipping layout is off
    private static final int[] FOCUS_FRAMES = {
            R.drawable.widget_frame_focus_1, R.drawable.widget_frame_focus_3,
            R.drawable.widget_frame_focus_5};
    private static final int[] BREAK_FRAMES = {
            R.drawable.widget_frame_break_1, R.drawable.widget_frame_break_3,
            R.drawable.widget_frame_break_5};

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        RemoteViews views = buildViews(context, true);
        for (int id : appWidgetIds) manager.updateAppWidget(id, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            TimerState s = TimerState.load(context);
            if (s.running) {
                s.pause();
                AlarmHelper.cancel(context);
            } else if ("timer".equals(s.kind) && s.totalMs() <= 0) {
                // no time set yet: there is nothing to start. Open the app
                // so the time can be set on the tape.
                Intent open = new Intent(context, MainActivity.class);
                open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { context.startActivity(open); } catch (Exception ignored) {}
                return;
            } else {
                s.start();
                AlarmHelper.schedule(context, s.endAtMs);
                KillWatchService.start(context);
            }
            s.save(context);
            updateAll(context);
        }
    }

    /** Full update: everything, including which character part is shown.
     *  Call on any state change. */
    public static void updateAll(Context context) {
        PhaseAdvancer.advanceIfFinished(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, TimerWidgetProvider.class));
        if (ids != null && ids.length > 0) {
            RemoteViews views = buildViews(context, true);
            for (int id : ids) manager.updateAppWidget(id, views);
        }
        WidgetAnimHelper.sync(context);
    }

    /** Once-a-second update while running: only digits, tomatoes and tape.
     *  The character part is left alone so its own frame animation keeps
     *  playing without a restart. */
    public static void updateTick(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, TimerWidgetProvider.class));
        if (ids != null && ids.length > 0) {
            manager.partiallyUpdateAppWidget(ids, buildViews(context, false));
        }
        WidgetAnimHelper.sync(context);
    }

    static RemoteViews buildViews(Context context, boolean full) {
        TimerState s = TimerState.load(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_timer);
        boolean pomo = "pomodoro".equals(s.kind);
        boolean isBreak = pomo && !"focus".equals(s.mode);

        // tomatoes: overall progress of the set (hidden but still taking
        // space in timer mode, so nothing shifts)
        views.setViewVisibility(R.id.widget_toms, pomo ? View.VISIBLE : View.INVISIBLE);
        for (int i = 0; i < 4; i++) {
            views.setImageViewResource(TOMATO_IDS[i],
                    pomo && i < s.cycles ? R.drawable.dot_on : R.drawable.dot_off);
        }

        // digits: mm:ss (mmm:ss past 99 minutes), hand-drawn art
        long totalSec = s.done ? 0 : (long) Math.ceil(s.remainingNow() / 1000.0);
        int min = (int) Math.min(999, totalSec / 60);
        int sec = (int) (totalSec % 60);
        views.setViewVisibility(R.id.widget_d0, min >= 100 ? View.VISIBLE : View.GONE);
        views.setImageViewResource(R.id.widget_d0, DIGIT_RES[(min / 100) % 10]);
        views.setImageViewResource(R.id.widget_d1, DIGIT_RES[(min / 10) % 10]);
        views.setImageViewResource(R.id.widget_d2, DIGIT_RES[min % 10]);
        views.setImageViewResource(R.id.widget_d3, DIGIT_RES[sec / 10]);
        views.setImageViewResource(R.id.widget_d4, DIGIT_RES[sec % 10]);
        int alpha = s.running ? ALPHA_RUNNING : ALPHA_STOPPED;
        for (int id : new int[]{R.id.widget_d0, R.id.widget_d1, R.id.widget_d2,
                R.id.widget_colon, R.id.widget_d3, R.id.widget_d4}) {
            views.setInt(id, "setImageAlpha", alpha);
        }

        views.setImageViewBitmap(R.id.widget_tape, WidgetTape.render(context, s));
        views.setImageViewResource(R.id.widget_toggle_icon,
                s.running ? R.drawable.icon_pause : R.drawable.icon_play);

        boolean flipping = s.running && WidgetAnimHelper.USE_FLIPPER;
        if (full) {
            // character: swap in the part that fits the state
            views.removeAllViews(R.id.widget_char_slot);
            if (flipping) {
                views.addView(R.id.widget_char_slot, new RemoteViews(context.getPackageName(),
                        isBreak ? R.layout.widget_char_break_run : R.layout.widget_char_focus_run));
            } else {
                RemoteViews still = new RemoteViews(context.getPackageName(),
                        R.layout.widget_char_still);
                still.setImageViewResource(R.id.widget_char, stillOrFrame(context, s, isBreak));
                views.addView(R.id.widget_char_slot, still);
            }

            Intent toggleIntent = new Intent(context, TimerWidgetProvider.class);
            toggleIntent.setAction(ACTION_TOGGLE);
            views.setOnClickPendingIntent(R.id.widget_toggle, PendingIntent.getBroadcast(
                    context, 2001, toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(
                    context, 2002, openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        return views;
    }

    /** Stopped: "wait" art when the phase is fresh (or the set is done),
     *  "pause" art when stopped in the middle of a phase (same rule as the app).
     *  Running without the auto-flipping layout: the 1-per-second flip-book. */
    private static int stillOrFrame(Context context, TimerState s, boolean isBreak) {
        if (s.running) {
            int idx = Math.abs(WidgetAnimHelper.frameIndex(context));
            return isBreak ? BREAK_FRAMES[idx % BREAK_FRAMES.length]
                    : FOCUS_FRAMES[idx % FOCUS_FRAMES.length];
        }
        boolean fresh = s.done || s.remainingMs >= s.totalMs();
        if (fresh) return isBreak ? R.drawable.widget_wait_break : R.drawable.widget_wait_focus;
        return isBreak ? R.drawable.widget_pause_break : R.drawable.widget_pause_focus;
    }
}
