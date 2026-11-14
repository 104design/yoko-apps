package app.yokozurashi.news;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

/**
 * 検証版のウィジェット。流れる文字とよこずらしのコマ送りが、
 * 実機のホーム画面で動くかを確かめるためのもの（SPEC.md の「作る順番」1）。
 */
public class NewsWidgetProvider extends AppWidgetProvider {
    private static final String SAMPLE_URL = "https://www.example.com/";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) {
            manager.updateAppWidget(id, build(context));
        }
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, NewsWidgetProvider.class));
        for (int id : ids) {
            manager.updateAppWidget(id, build(context));
        }
    }

    private static RemoteViews build(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_verify);

        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(SAMPLE_URL));
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent article = PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // 流れている文字そのものをタップできるかも確かめたいので、帯と文字の両方に付ける
        views.setOnClickPendingIntent(R.id.band_marquee, article);
        views.setOnClickPendingIntent(R.id.news_marquee, article);
        views.setOnClickPendingIntent(R.id.band_slide, article);

        Intent app = new Intent(context, MainActivity.class);
        PendingIntent settings = PendingIntent.getActivity(context, 2, app,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, settings);
        return views;
    }
}
