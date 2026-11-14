package app.yokozurashi.news;

import android.content.Context;

/** 天気とニュースを取り直して、ウィジェットを描き直す。 */
public class Refresher {
    private static final long NEWS_EVERY = 25 * 60 * 1000L;    // 約30分ごと
    private static final long WEATHER_EVERY = 55 * 60 * 1000L; // 約1時間ごと

    /** 通信するので、画面のスレッドでは呼ばない。force なら間隔に関係なく取り直す。 */
    public static synchronized void run(Context ctx, boolean force) {
        long now = System.currentTimeMillis();
        if (force || now - NewsStore.time(ctx) > NEWS_EVERY) {
            NewsStore.fetch(ctx);
        }
        if (LocationHelper.hasLocation(ctx)) {
            LocationHelper.resolvePlace(ctx);
            if (force || now - Weather.time(ctx) > WEATHER_EVERY) {
                Weather.fetch(ctx);
            }
        }
        NewsWidgetProvider.updateAll(ctx);
    }

    public static void runAsync(Context ctx, final boolean force, final Runnable done) {
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                Refresher.run(app, force);
                if (done != null) done.run();
            }
        }).start();
    }
}
