package app.yokozurashi.news;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** アプリの画面。いまはウィジェットをホーム画面に置く案内だけを出す。 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);

        TextView title = new TextView(this);
        title.setText("よこずらしニュース");
        title.setTextSize(20);
        root.addView(title);

        TextView body = new TextView(this);
        body.setText("ホーム画面にウィジェットを置いてください。\n\n"
                + "日付は本物です。天気とニュースはまだ仮の表示です。\n"
                + "ニュースの帯をタップするとブラウザが開き、右のボタンでべつのニュースに切り替わります。");
        body.setTextSize(15);
        body.setPadding(0, pad, 0, pad);
        root.addView(body);

        Button add = new Button(this);
        add.setText("ホーム画面にウィジェットを追加");
        add.setAllCaps(false);
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppWidgetManager manager = AppWidgetManager.getInstance(MainActivity.this);
                if (manager.isRequestPinAppWidgetSupported()) {
                    manager.requestPinAppWidget(
                            new ComponentName(MainActivity.this, NewsWidgetProvider.class), null, null);
                } else {
                    Toast.makeText(MainActivity.this,
                            "ホーム画面を長押しして、ウィジェットの一覧から追加してください",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(add);

        setContentView(root);
        NewsWidgetProvider.updateAll(this);
    }
}
