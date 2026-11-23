package app.yokozurashi.steps;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RemoteViews;


/** よこめぐりのウィジェット。 */
public class StepWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_REFRESH = "app.yokozurashi.steps.REFRESH";
    public static final String ACTION_DAY_TICK = "app.yokozurashi.steps.DAY_TICK";

    /** 画像の画素数の上限。RemoteViews に渡せる画像の大きさには限りがある。 */
    private static final float MAX_PIXELS = 1500000f;

    /** どの呼び出しでも、まず歩数を読み直す。読み終わると StepReader が updateAll を呼ぶ。 */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action) || ACTION_DAY_TICK.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
            final PendingResult pending = goAsync();
            StepReader.read(context, new Runnable() {
                @Override
                public void run() {
                    pending.finish();
                }
            });
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onDisabled(Context context) {
        // 旅はアプリの側で続くので、歩数の定期読み取り（StepJob）は止めない
        DayAlarm.cancel(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id,
                                          Bundle options) {
        manager.updateAppWidget(id, build(context, manager, id));
    }

    /** 保存してある歩数で、すべてのウィジェットを描き直す。0時の予約と定期読み取りも入れ直す。 */
    public static void updateAll(Context context) {
        StepJob.ensureScheduled(context);
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, StepWidgetProvider.class));
        if (ids == null || ids.length == 0) return;
        for (int id : ids) {
            manager.updateAppWidget(id, build(context, manager, id));
        }
        DayAlarm.schedule(context);
    }

    private static RemoteViews build(Context context, AppWidgetManager manager, int id) {
        // ウィジェットの大きさ（縦持ちのときの幅と高さ）
        Bundle options = manager.getAppWidgetOptions(id);
        int w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        int h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
        if (w <= 0) w = (int) Panel.BASE_W;
        if (h <= 0) h = (int) Panel.BASE_H;
        Panel.Layout lay = new Panel.Layout(w, h);

        float density = context.getResources().getDisplayMetrics().density;
        float pxPerDp = (float) Math.min(density, Math.sqrt(MAX_PIXELS / (w * (float) h)));

        int status = StepReader.status(context);
        Journey j = Journey.at(StepStore.distanceM(context));
        Panel.Content t = new Panel.Content();
        t.place = (j.goal ? "ゴール！ " : "") + j.place;
        t.pref = j.pref;
        t.picture = j.picture;
        t.prefPicture = j.prefPicture;
        t.distanceM = j.distanceM;
        t.today = StepStore.today(context);
        t.progress = j.progress;
        t.sea = j.sea;
        t.from = j.place;
        t.to = (j.sea ? "船で" : "") + j.next + "まで あと" + Journey.km(j.toNextM) + "km";
        String bandText;
        if (status == StepReader.UNSUPPORTED) {
            bandText = "Android 14 以上が必要です";
        } else if (status == StepReader.NO_PERMISSION) {
            bandText = "タップして歩数の読み取りを許可";
        } else {
            bandText = j.text;
        }

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_steps);
        views.setImageViewBitmap(R.id.panel, Panel.render(context, lay, pxPerDp, t));

        // よこずらしを、線の上の進んだ位置（船の区間では甲板の上）に置く
        place(views, R.id.yoko_h1, R.id.yoko_h2, R.id.yoko_v1, R.id.yoko_v2,
                lay.yokoLeft(j.progress, j.sea) / w, lay.yokoSize(j.sea) / w,
                lay.yokoTop(j.sea) / h, lay.yokoSize(j.sea) / h);

        PendingIntent app = PendingIntent.getActivity(context, 1,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // 帯と更新マーク以外は、どこを押してもアプリを開く
        views.setOnClickPendingIntent(R.id.tap_all, app);

        // 更新マークを押したら、見えない画面を一瞬開いて歩数を読む（ReloadActivity）。
        // 歩数の読み取りが未許可のときは、アプリを開いて許可を求められるようにする
        place(views, R.id.reload_h1, R.id.reload_h2, R.id.reload_v1, R.id.reload_v2,
                (t.reloadX - Panel.RELOAD_TAP / 2) / lay.w, Panel.RELOAD_TAP / lay.w,
                (t.reloadY - Panel.RELOAD_TAP / 2) / lay.h, Panel.RELOAD_TAP / lay.h);
        PendingIntent reload = app;
        if (status == StepReader.OK) {
            Intent open = new Intent(context, ReloadActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            reload = PendingIntent.getActivity(context, 5, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        views.setOnClickPendingIntent(R.id.tap_reload, reload);

        // 帯をタップしたら、いまずらしている名物をブラウザで検索する
        place(views, R.id.band_h1, R.id.band_h2, R.id.band_v1, R.id.band_v2,
                lay.bandLeft() / w, lay.bandWidth() / w, lay.bandTopDp() / h, lay.bandHeight() / h);
        PendingIntent bandTap = app;
        if (status == StepReader.OK) {
            Intent search = new Intent(Intent.ACTION_VIEW, searchUrl(j));
            search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            bandTap = PendingIntent.getActivity(context, 4, search,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        views.setOnClickPendingIntent(R.id.tap_band, bandTap);

        // 帯の中の文。入らない長さなら、右から左へ流れる
        views.removeAllViews(R.id.band_text);
        views.addView(R.id.band_text, Belt.of(context, bandText, lay.bandTextRoom()));
        return views;
    }

    /** 名物の検索結果のURL。同じ名前のものと区別がつくよう、都道府県の名前を添えて検索する。 */
    static Uri searchUrl(Journey j) {
        return searchUrl(j.item, j.pref);
    }

    static Uri searchUrl(String item, String pref) {
        return Uri.parse("https://www.google.com/search?q=" + Uri.encode(item + " " + pref));
    }

    /**
     * 入れ子の4つの枠（widget_steps.xml）の weightSum を変えて、いちばん内側の枠を置く。
     * left・width は幅に対する割合、top・height は高さに対する割合（0〜1）。
     * 割合で決めるので、端末が知らせる大きさと実際の大きさが食い違っていても、下の絵とずれない。
     */
    private static void place(RemoteViews views, int h1, int h2, int v1, int v2,
                              float left, float width, float top, float height) {
        width = Math.max(0.01f, Math.min(1, width));
        height = Math.max(0.01f, Math.min(1, height));
        float right = Math.max(width, Math.min(1, left + width));   // 枠の右端
        float bottom = Math.max(height, Math.min(1, top + height)); // 枠の下端
        views.setFloat(h1, "setWeightSum", 1 / right);
        views.setFloat(h2, "setWeightSum", right / width);
        views.setFloat(v1, "setWeightSum", 1 / bottom);
        views.setFloat(v2, "setWeightSum", bottom / height);
        // weightSum を変えただけでは並べ直しが起きないので、並べ直しを起こす命令も送る
        for (int id : new int[]{h1, h2, v1, v2}) views.setInt(id, "setMinimumHeight", 0);
    }
}
