package app.yokozurashi.news;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
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
    public void onEnabled(Context context) {
        RefreshJob.runSoon(context);
    }

    @Override
    public void onDisabled(Context context) {
        DateAlarm.cancel(context);
        RefreshJob.cancelAll(context);
    }

    private static int[] ids(Context context, AppWidgetManager manager) {
        return manager.getAppWidgetIds(new ComponentName(context, NewsWidgetProvider.class));
    }

    /** 全体を描き直す。日付の画像も作り直し、次の0時を予約する。 */
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = ids(context, manager);
        if (ids == null || ids.length == 0) return;

        for (int id : ids) {
            manager.updateAppWidget(id, build(context, manager, id));
        }
        DateAlarm.schedule(context);
        RefreshJob.ensureScheduled(context);
    }

    private static RemoteViews build(Context context, AppWidgetManager manager, int id) {
        // ウィジェットの大きさ（縦持ちのときの幅と高さ）から、日付とキャラの大きさを決める
        Bundle options = manager.getAppWidgetOptions(id);
        int w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (w <= 0) w = 315;
        if (h <= 0) h = 260;
        // 大きさの指定は Android 12 以上でしか効かない。それより古い端末はレイアウトXMLの値のまま
        boolean canResize = Build.VERSION.SDK_INT >= 31;
        WidgetLayout layout = canResize ? new WidgetLayout(w, h) : new WidgetLayout(380, 180);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_news);
        if (canResize) {
            views.setViewLayoutWidth(R.id.character, layout.charWidth, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(R.id.character, layout.charHeight, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutMargin(R.id.character, RemoteViews.MARGIN_RIGHT,
                    layout.charMarginRight, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutMargin(R.id.character, RemoteViews.MARGIN_BOTTOM,
                    layout.charMarginBottom, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutWidth(R.id.date_block, layout.dateWidth, TypedValue.COMPLEX_UNIT_DIP);
            views.setViewLayoutHeight(R.id.date_block,
                    DateBlock.HEIGHT_DP * layout.dateScale, TypedValue.COMPLEX_UNIT_DIP);
        }
        float dateWidth = canResize ? layout.dateWidth : 160;
        views.setImageViewBitmap(R.id.date_block,
                DateBlock.render(context, Calendar.getInstance(), layout.dateScale, dateWidth,
                        Math.min(dateWidth, layout.labelRight), Weather.kind(context),
                        Weather.tempText(context), weatherLabel(context)));
        bindNews(context, views);

        Intent app = new Intent(context, MainActivity.class);
        PendingIntent settings = PendingIntent.getActivity(context, 2, app,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(android.R.id.background, settings);
        views.setOnClickPendingIntent(R.id.date_block, settings);
        views.setOnClickPendingIntent(R.id.character, settings);

        return views;
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id,
                                          Bundle options) {
        manager.updateAppWidget(id, build(context, manager, id));
    }

    /** 気温の横に出す言葉。「晴れ・渋谷区」、取れていないときは「現在地」など（SPEC.md 仕様書2・3）。 */
    private static String weatherLabel(Context context) {
        if (!LocationHelper.hasPermission(context) && !LocationHelper.hasLocation(context)) {
            return "位置情報を許可";
        }
        String place = LocationHelper.place(context);
        if (place == null) place = "現在地";
        String word = Weather.word(context);
        return word == null ? place : word + "・" + place;
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
        PendingIntent tap;
        if (item == null) {
            // 一覧がまだないとき。流さない。タップでアプリを開く
            views.removeAllViews(R.id.news_holder);
            views.addView(R.id.news_holder, NewsBelt.still(context, NewsStore.tried(context)
                    ? "ニュースを取得できませんでした" : "ニュースを読み込み中…"));
            tap = PendingIntent.getActivity(context, 2, new Intent(context, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            views.removeAllViews(R.id.news_holder);
            views.addView(R.id.news_holder, NewsBelt.flowing(context, item.line()));
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            tap = PendingIntent.getActivity(context, 1, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        views.setOnClickPendingIntent(R.id.band, tap);

        Intent shuffle = new Intent(context, NewsWidgetProvider.class);
        shuffle.setAction(ACTION_SHUFFLE);
        views.setOnClickPendingIntent(R.id.shuffle, PendingIntent.getBroadcast(context, 4, shuffle,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
}
