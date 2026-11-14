package app.yokozurashi.news;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 時刻・タイムゾーンの変更、再起動、アプリ更新のあとに、日付を描き直して0時の予約を入れ直す。 */
public class SystemEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NewsWidgetProvider.updateAll(context);
    }
}
