package app.yokozurashi.news;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

import java.util.Calendar;

/** よこずらしニュースのウィジェット（A案「ニュースデスク」）。 */
public class NewsWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_SHUFFLE = "app.yokozurashi.news.SHUFFLE";
    public static final String ACTION_DATE_TICK = "app.yokozurashi.news.DATE_TICK";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_SHUFFLE.equals(action)) {
            NewsStore.next(context);
            updateNews(context);
        } else if (ACTION_DATE_TICK.equals(action)) {
            updateAll(context);
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateAll(context);
    }

    @Override
    public void onDisabled(Context context) {
        DateAlarm.cancel(context);
    }

    private static int[] ids(Context context, AppWidgetManager manager) {
        return manager.getAppWidgetIds(new ComponentName(context, NewsWidgetProvider.class));
    }

    /** 全体を描き直す。日付の画像も作り直し、次の0時を予約する。 */
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = ids(context, manager);
        if (ids == null || ids.length == 0) return;

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_news);
        // 天気はまだ取得していないので「取れていないとき」の表示（SPEC.md 仕様書2）
        views.setImageViewBitmap(R.id.date_block,
                DateBlock.render(context, Calendar.getInstance(), "--°", "現在地"));
        bindNews(context, views);

        Intent app = new Intent(context, MainActivity.class);
        PendingIntent settings = PendingIntent.getActivity(context, 2, app,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(android.R.id.background, settings);
        views.setOnClickPendingIntent(R.id.date_block, settings);
        views.setOnClickPendingIntent(R.id.character, settings);

        manager.updateAppWidget(ids, views);
        DateAlarm.schedule(context);
    }

    /** ニュースの帯だけを差し替える。よこずらしのアニメや日付は触らない。 */
    private static void updateNews(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = ids(context, manager);
        if (ids == null || ids.length == 0) return;
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_news);
        bindNews(context, views);
        manager.partiallyUpdateAppWidget(ids, views);
    }

    private static void bindNews(Context context, RemoteViews views) {
        NewsStore.Item item = NewsStore.current(context);
        views.setTextViewText(R.id.news_marquee, item.line());

        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent article = PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.band, article);
        views.setOnClickPendingIntent(R.id.news_marquee, article);

        Intent shuffle = new Intent(context, NewsWidgetProvider.class);
        shuffle.setAction(ACTION_SHUFFLE);
        views.setOnClickPendingIntent(R.id.shuffle, PendingIntent.getBroadcast(context, 4, shuffle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
}
