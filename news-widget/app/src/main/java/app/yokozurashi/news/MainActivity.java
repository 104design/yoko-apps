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

/** 検証版の画面。ウィジェットをホーム画面に置く案内だけを出す。 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);

        TextView title = new TextView(this);
        title.setText("よこずらしニュース（検証版）");
        title.setTextSize(20);
        root.addView(title);

        TextView body = new TextView(this);
        body.setText("ホーム画面にウィジェットを置いて、次の3つを見てください。\n\n"
                + "①の帯：文字が右から左へ流れつづけるか\n"
                + "②の帯：文が横スライドで切り替わるか\n"
                + "左上のよこずらし：コマ送りで動くか\n\n"
                + "帯をタップするとブラウザが開きます。");
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
