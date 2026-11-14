package app.yokozurashi.pomodoro;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

public class TimerWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TOGGLE = "app.yokozurashi.pomodoro.WIDGET_TOGGLE";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int id : appWidgetIds) {
            manager.updateAppWidget(id, buildViews(context));
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_TOGGLE.equals(intent.getAction())) {
            TimerState s = TimerState.load(context);
            if (s.running) {
                s.pause();
                AlarmHelper.cancel(context);
            } else {
                s.start();
                AlarmHelper.schedule(context, s.endAtMs);
                KillWatchService.start(context);
            }
            s.save(context);
            updateAll(context);
        }
    }

    public static void updateAll(Context context) {
        PhaseAdvancer.advanceIfFinished(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName name = new ComponentName(context, TimerWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(name);
        RemoteViews views = buildViews(context);
        for (int id : ids) manager.updateAppWidget(id, views);
        WidgetAnimHelper.sync(context);
    }

    // Widget flip-book uses a thinned 3-frame subset (user choice, 2026-11-12):
    // full loops felt too slow at the widget's 1s-per-frame pace.
    private static final int[] FOCUS_FRAMES = {
            R.drawable.widget_frame_focus_1, R.drawable.widget_frame_focus_3,
            R.drawable.widget_frame_focus_5};
    private static final int[] BREAK_FRAMES = {
            R.drawable.widget_frame_break_1, R.drawable.widget_frame_break_3,
            R.drawable.widget_frame_break_5};

    static int charResource(Context context, TimerState s) {
        boolean isBreak = "pomodoro".equals(s.kind) && !"focus".equals(s.mode);
        if (!s.running) {
            return isBreak ? R.drawable.widget_char_break_still : R.drawable.widget_char_focus_still;
        }
        int idx = WidgetAnimHelper.frameIndex(context);
        if (isBreak) return BREAK_FRAMES[Math.abs(idx) % BREAK_FRAMES.length];
        return FOCUS_FRAMES[Math.abs(idx) % FOCUS_FRAMES.length];
    }

    static RemoteViews buildViews(Context context) {
        TimerState s = TimerState.load(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_timer);

        String title;
        if ("pomodoro".equals(s.kind)) {
            if (s.done) title = "🎉 1セット完了";
            else if ("short".equals(s.mode)) title = "🍅 休憩 " + s.cycles + "/4完了";
            else if ("long".equals(s.mode)) title = "🍅 長い休憩";
            else title = "🍅 集中 " + Math.min(4, s.cycles + 1) + "/4";
        } else if (s.done) {
            title = "⏰ 時間です！";
        } else {
            title = "⏱ タイマー";
        }
        views.setTextViewText(R.id.widget_title, title);
        views.setTextViewText(R.id.widget_toggle, s.running ? "⏸ 一時停止" : "▶ スタート");
        views.setImageViewResource(R.id.widget_char, charResource(context, s));

        if (s.done) {
            views.setViewVisibility(R.id.widget_chronometer, View.GONE);
            views.setViewVisibility(R.id.widget_time, View.VISIBLE);
            views.setTextViewText(R.id.widget_time,
                    "pomodoro".equals(s.kind) ? "🎉 完了！" : "⏰ 時間です！");
        } else if (s.running) {
            views.setViewVisibility(R.id.widget_time, View.GONE);
            views.setViewVisibility(R.id.widget_chronometer, View.VISIBLE);
            long base = SystemClock.elapsedRealtime() + s.remainingNow();
            views.setChronometer(R.id.widget_chronometer, base, null, true);
            views.setChronometerCountDown(R.id.widget_chronometer, true);
        } else {
            views.setViewVisibility(R.id.widget_chronometer, View.GONE);
            views.setViewVisibility(R.id.widget_time, View.VISIBLE);
            views.setTextViewText(R.id.widget_time, TimerState.format(s.remainingNow()));
        }

        Intent toggleIntent = new Intent(context, TimerWidgetProvider.class);
        toggleIntent.setAction(ACTION_TOGGLE);
        PendingIntent togglePending = PendingIntent.getBroadcast(context, 2001, toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_toggle, togglePending);

        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPending = PendingIntent.getActivity(context, 2002, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, openPending);
        return views;
    }
}
