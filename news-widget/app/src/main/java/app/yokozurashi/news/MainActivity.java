package app.yokozurashi.news;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 設定画面。役割は、位置情報の許可をもらうことと、ウィジェットの置き方を案内すること。
 * 現在地は、この画面を開いたときに取り直す。
 */
public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 1;

    private TextView locationStatus;
    private Button locationButton;
    private TextView updateStatus;
    private Button refreshButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);

        root.addView(text("よこずらしニュース", 22, 0));

        root.addView(text("現在地の天気", 16, pad));
        locationStatus = text("", 14, dp(6));
        root.addView(locationStatus);
        locationButton = button("位置情報を許可する");
        locationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            }
        });
        root.addView(locationButton);

        root.addView(text("更新", 16, pad));
        updateStatus = text("", 14, dp(6));
        root.addView(updateStatus);
        refreshButton = button("いま更新");
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refresh();
            }
        });
        root.addView(refreshButton);

        root.addView(text("ウィジェット", 16, pad));
        Button add = button("ホーム画面にウィジェットを追加");
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

        root.addView(text("出典", 16, pad));
        root.addView(text("天気: Open-Meteo（open-meteo.com）\nニュース: " + NewsFeed.NAME
                + "\n\n位置は「おおよその位置」だけを使い、天気の問い合わせ以外には送りません。", 13, dp(6)));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (LocationHelper.hasPermission(this)) LocationHelper.capture(this);
        showStatus();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        // onResume がこのあとに呼ばれ、そこで現在地の取得と更新をする
    }

    private void refresh() {
        refreshButton.setEnabled(false);
        refreshButton.setText("更新中…");
        Refresher.runAsync(this, true, new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshButton.setEnabled(true);
                        refreshButton.setText("いま更新");
                        showStatus();
                    }
                });
            }
        });
    }

    private void showStatus() {
        if (!LocationHelper.hasPermission(this)) {
            locationStatus.setText("位置情報が許可されていません。許可すると、現在地の天気を表示します。");
            locationButton.setVisibility(View.VISIBLE);
        } else if (!LocationHelper.hasLocation(this)) {
            locationStatus.setText("許可済み。現在地をまだ取得できていません。端末の位置情報をオンにして、この画面を開き直してください。");
            locationButton.setVisibility(View.GONE);
        } else {
            String place = LocationHelper.place(this);
            locationStatus.setText("許可済み。現在地: " + (place == null ? "（地名は未取得）" : place)
                    + "\nこの画面を開くたびに現在地を取り直します。");
            locationButton.setVisibility(View.GONE);
        }
        updateStatus.setText("天気: " + when(Weather.time(this)) + "\nニュース: " + when(NewsStore.time(this)));
    }

    private static String when(long time) {
        if (time == 0) return "まだ取得できていません";
        return new SimpleDateFormat("M月d日 H:mm", Locale.JAPAN).format(new Date(time)) + " に更新";
    }

    private TextView text(String s, float size, int topMargin) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setPadding(0, topMargin, 0, 0);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
