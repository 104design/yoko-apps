package app.yokozurashi.news;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

    // 帯にいま出している中身（記事のURL、または「読み込み中…」などの文）と、帯の中に積んだ見出しの数。
    // ニュースが変わったとき、古い見出しを残したまま新しい見出しを足して切り替えると、
    // 古いほうが左へ出て、新しいほうが右から入る。古い見出しは見えないまま残るので、
    // BELT_MAX まで積んだら、次は滑らせずに入れ替えて片付ける
    private static final String KEY_BELT_SHOWN = "beltShown";
    private static final String KEY_BELT_COUNT = "beltCount";
    private static final int BELT_MAX = 12;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_SHUFFLE.equals(action)) {
            NewsStore.next(context);
            CharacterStore.change(context);
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
        views.setImageViewResource(R.id.character, CharacterStore.drawable(context));
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
        DateBlock.WeatherLine line = new DateBlock.WeatherLine();
        line.kind = Weather.kind(context);
        line.temp = Weather.tempText(context);
        line.high = Weather.highText(context);
        line.low = Weather.lowText(context);
        line.rain = Weather.rainText(context);
        line.place = layout.showPlace ? placeLabel(context) : null;
        views.setImageViewBitmap(R.id.date_block,
                DateBlock.render(context, Calendar.getInstance(), layout.dateScale,
                        canResize ? layout.dateWidth : 160, line));
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

    /** 地名の行に出す言葉。地名が分からなければ「現在地」、許可がなければ「位置情報を許可」。 */
    private static String placeLabel(Context context) {
        if (!LocationHelper.hasPermission(context) && !LocationHelper.hasLocation(context)) {
            return "位置情報を許可";
        }
        String place = LocationHelper.place(context);
        return place == null ? "現在地" : place;
    }

    /** ニュースの帯と女の子の絵だけを差し替える。よこずらしのアニメや日付は触らない。 */
    private static void updateNews(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = ids(context, manager);
        if (ids == null || ids.length == 0) return;
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_news);
        views.setImageViewResource(R.id.character, CharacterStore.drawable(context));
        bindNews(context, views);
        manager.partiallyUpdateAppWidget(ids, views);
    }

    private static void bindNews(Context context, RemoteViews views) {
        NewsStore.Item item = NewsStore.current(context);
        PendingIntent tap;
        if (item == null) {
            // 一覧がまだないとき。流さない。タップでアプリを開く
            String text = NewsStore.tried(context) ? "ニュースを取得できませんでした" : "ニュースを読み込み中…";
            putBelt(context, views, "text:" + text, NewsBelt.still(context, text));
            tap = PendingIntent.getActivity(context, 2, new Intent(context, MainActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            putBelt(context, views, "url:" + item.url, NewsBelt.flowing(context, item.line()));
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

    /**
     * 帯に見出しを入れる。いまと違う見出しなら、古いほうを左へ滑らせて出し、新しいほうを右から入れる。
     * 同じ見出し（0時の日付の描き直しなど）なら、滑らせずに入れ直す。
     */
    private static void putBelt(Context context, RemoteViews views, String key, RemoteViews belt) {
        SharedPreferences p = Store.prefs(context);
        String shown = p.getString(KEY_BELT_SHOWN, null);
        int count = p.getInt(KEY_BELT_COUNT, 0);
        if (shown != null && !shown.equals(key) && count > 0 && count < BELT_MAX) {
            // 古い見出しの後ろに足して、次へ送る。送るときに inAnimation と outAnimation が動く
            views.addView(R.id.news_holder, belt);
            views.showNext(R.id.news_holder);
            count++;
        } else {
            views.removeAllViews(R.id.news_holder);
            views.addView(R.id.news_holder, belt);
            count = 1;
        }
        p.edit().putString(KEY_BELT_SHOWN, key).putInt(KEY_BELT_COUNT, count).apply();
    }
}
