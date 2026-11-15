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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
        t.from = j.place;
        t.to = (j.sea ? "船で" : "") + j.next + "まで あと" + Journey.km(j.toNextM) + "km";
        long read = StepStore.readTime(context);
        t.time = read == 0 ? null : new SimpleDateFormat("H:mm", Locale.JAPAN).format(new Date(read));
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

        // よこずらしを、線の上の進んだ位置に置く。枠の余白で位置と大きさを決める
        int size = Math.round(lay.yokoSize() * density);
        int wPx = Math.round(w * density), hPx = Math.round(h * density);
        int left = clamp(Math.round(lay.yokoLeft(j.progress) * density), 0, wPx - size);
        int top = clamp(Math.round(lay.yokoTop() * density), 0, hPx - size);
        int right = Math.max(0, wPx - left - size);
        int bottom = Math.max(0, hPx - top - size);
        views.setViewPadding(R.id.yoko_box, left, top, right, bottom);

        PendingIntent app = PendingIntent.getActivity(context, 1,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent refresh = new Intent(context, StepWidgetProvider.class);
        refresh.setAction(ACTION_REFRESH);
        PendingIntent reread = PendingIntent.getBroadcast(context, 2, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.tap_top, app);
        // 裏から読めないとき（許可がまだ、など）は、下半分もアプリを開く。アプリを開けば読める
        boolean canReread = status == StepReader.OK && StepReader.canReadInBackground(context);
        views.setOnClickPendingIntent(R.id.tap_bottom, canReread ? reread : app);

        // 帯をタップしたら、いまずらしている名物をブラウザで検索する
        int bandLeft = clamp(Math.round(lay.bandLeft() * density), 0, wPx);
        int bandTop = clamp(Math.round(lay.bandTopDp() * density), 0, hPx);
        views.setViewPadding(R.id.band_box, bandLeft, bandTop,
                Math.max(0, wPx - bandLeft - Math.round(lay.bandWidth() * density)),
                Math.max(0, hPx - bandTop - Math.round(lay.bandHeight() * density)));
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
        return Uri.parse("https://www.google.com/search?q=" + Uri.encode(j.item + " " + j.pref));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(Math.max(lo, hi), v));
    }
}
