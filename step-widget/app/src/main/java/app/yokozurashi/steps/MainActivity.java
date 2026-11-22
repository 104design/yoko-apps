package app.yokozurashi.steps;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
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
 * アプリの画面。役割は、ヘルスコネクトの歩数を読む許可をもらうこと、旅のようすを見せること、歩幅を決めること。
 * 開くたびに歩数を読み直す。
 */
public class MainActivity extends Activity {
    private static final int REQ_ACTIVITY = 1;
    private static final long BONUS_M = 30000; // 題名の長押しで足す、確認用の距離

    private TextView permissionStatus;
    private Button permissionButton;
    private Button healthButton;
    private TextView journeyStatus;
    private TextView strideStatus;
    private TextView passedList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int pad = dp(24);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad * 2, pad, pad);

        TextView title = text("よこめぐり", 22, 0);
        // 表示の確認用。長押しすると 30km 進む（「旅をやり直す」で消える）
        title.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                StepStore.addBonus(MainActivity.this, BONUS_M);
                Toast.makeText(MainActivity.this, "確認用に 30km 進めました", Toast.LENGTH_SHORT).show();
                StepWidgetProvider.updateAll(MainActivity.this);
                show();
                return true;
            }
        });
        root.addView(title);
        root.addView(text("歩いたぶんだけ、よこずらしが日本を西の端から東の端へ進みます。", 13, dp(6)));

        root.addView(text("歩数", 16, pad));
        permissionStatus = text("", 14, dp(6));
        root.addView(permissionStatus);
        permissionButton = button("歩数の読み取りを許可する");
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestPermissions(new String[]{StepReader.PERMISSION_STEPS,
                        StepReader.PERMISSION_BACKGROUND}, REQ_ACTIVITY);
            }
        });
        root.addView(permissionButton);
        healthButton = button("ヘルスコネクトを開く");
        healthButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent("android.health.connect.action.HEALTH_HOME_SETTINGS"));
                } catch (RuntimeException e) {
                    Toast.makeText(MainActivity.this, "ヘルスコネクトを開けませんでした。端末の設定から開いてください",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(healthButton);

        root.addView(text("旅のようす", 16, pad));
        journeyStatus = text("", 14, dp(6));
        root.addView(journeyStatus);
        Button reread = button("いま読み直す");
        reread.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                read();
            }
        });
        root.addView(reread);

        root.addView(text("歩幅", 16, pad));
        strideStatus = text("", 14, dp(6));
        root.addView(strideStatus);
        LinearLayout strideRow = new LinearLayout(this);
        strideRow.setOrientation(LinearLayout.HORIZONTAL);
        strideRow.addView(strideButton("− 5cm", -5));
        strideRow.addView(strideButton("+ 5cm", 5));
        root.addView(strideRow);

        root.addView(text("ウィジェット", 16, pad));
        Button add = button("ホーム画面にウィジェットを追加");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppWidgetManager manager = AppWidgetManager.getInstance(MainActivity.this);
                if (manager.isRequestPinAppWidgetSupported()) {
                    manager.requestPinAppWidget(
                            new ComponentName(MainActivity.this, StepWidgetProvider.class), null, null);
                } else {
                    Toast.makeText(MainActivity.this,
                            "ホーム画面を長押しして、ウィジェットの一覧から追加してください",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        root.addView(add);
        root.addView(text("ウィジェットのまるい矢印のマークをタップすると、歩数を読み直します。"
                + "オレンジの帯をタップすると名物をブラウザで検索し、"
                + "そのほかの場所をタップするとこの画面が開きます。", 13, dp(6)));

        root.addView(text("これまでにずらしたもの", 16, pad));
        passedList = text("", 14, dp(6));
        root.addView(passedList);

        root.addView(text("やり直し", 16, pad));
        Button restart = button("旅をやり直す");
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmRestart();
            }
        });
        root.addView(restart);

        root.addView(text("歩数はヘルスコネクトから読み、この端末の中だけに保存します。"
                + "距離は「歩数×歩幅」、道のりは地点どうしの直線距離です。", 13, pad));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
        read();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        // onResume がこのあとに呼ばれ、そこで読み直す
    }

    private void read() {
        StepReader.read(this, new Runnable() {
            @Override
            public void run() {
                show();
            }
        });
    }

    private void show() {
        int status = StepReader.status(this);
        if (status == StepReader.UNSUPPORTED) {
            permissionStatus.setText("この端末では使えません。ヘルスコネクトが本体に入っている Android 14 以上が必要です。");
            permissionButton.setVisibility(View.GONE);
            healthButton.setVisibility(View.GONE);
        } else if (status == StepReader.NO_PERMISSION) {
            permissionStatus.setText("ヘルスコネクトの歩数を読む許可がありません。許可すると、歩いたぶんだけ旅が進みます。\n"
                    + "ボタンを押しても何も出ないときは、ヘルスコネクトを開いて「アプリの権限」からこのアプリに歩数を許可してください。");
            permissionButton.setVisibility(View.VISIBLE);
            healthButton.setVisibility(View.VISIBLE);
        } else {
            long read = StepStore.readTime(this);
            boolean background = StepReader.canReadInBackground(this);
            permissionStatus.setText("許可済み。" + (read == 0 ? "まだ歩数を読めていません。"
                    : "最後に読めたのは " + when(read) + "。")
                    + (background ? "\nこの画面を閉じていても、約15分ごとに読みます。"
                    : "\nバックグラウンドでの読み取りが許可されていないので、歩数はこの画面を開いたときにだけ進みます。"
                    + "ヘルスコネクトでこのアプリの「バックグラウンドでのデータの読み取り」を許可すると、閉じていても進みます。")
                    + "\n歩数が0のままのときは、歩数を記録しているアプリや時計が、ヘルスコネクトに歩数を書きこむ設定になっているか確かめてください。");
            permissionButton.setVisibility(background ? View.GONE : View.VISIBLE);
            permissionButton.setText(background ? "歩数の読み取りを許可する" : "バックグラウンドでの読み取りを許可する");
            healthButton.setVisibility(View.VISIBLE);
        }

        int stride = StepStore.stride(this);
        long total = StepStore.total(this);
        Journey j = Journey.at(StepStore.distanceM(this));
        StringBuilder s = new StringBuilder();
        s.append(j.pref).append(" ").append(j.place).append(j.lap > 1 ? "（" + j.lap + "周目）" : "").append("\n");
        s.append(j.text).append("\n");
        s.append(j.sea ? "船で" : "").append(j.next).append("まで あと").append(Journey.km(j.toNextM)).append("km\n\n");
        s.append("累計 ").append(Journey.km(j.distanceM)).append("km（")
                .append(String.format("%,d", total)).append("歩）\n");
        s.append("きょう ").append(String.format("%,d", StepStore.today(this))).append("歩\n");
        long start = StepStore.startTime(this);
        s.append(start == 0 ? "まだ出発していません" : "出発 " + when(start));
        s.append("\nゴールの納沙布岬まで ").append(Route.AT_M[Route.AT_M.length - 1] / 1000).append("km");
        journeyStatus.setText(s);

        strideStatus.setText("1歩 " + stride + "cm（1万歩で " + Journey.km(StepStore.steps2m(10000, stride))
                + "km）\n歩幅を変えると、これまでの距離も計算し直します。");

        StringBuilder passed = new StringBuilder();
        for (int i = j.index - 1; i >= 0; i--) {
            passed.append(Route.ITEM[i]).append("（").append(Route.NAME[i]).append("）\n");
        }
        passedList.setText(passed.length() == 0 ? "まだありません。次の地点に着くと、ここに増えていきます。"
                : passed.toString().trim());
    }

    private void confirmRestart() {
        new AlertDialog.Builder(this)
                .setMessage("累計の歩数を0に戻し、与那国島から出発し直します。元には戻せません。")
                .setPositiveButton("やり直す", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        StepStore.restart(MainActivity.this);
                        StepWidgetProvider.updateAll(MainActivity.this);
                        show();
                    }
                })
                .setNegativeButton("やめる", null)
                .show();
    }

    private Button strideButton(String label, final int change) {
        Button b = button(label);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StepStore.setStride(MainActivity.this, StepStore.stride(MainActivity.this) + change);
                StepWidgetProvider.updateAll(MainActivity.this);
                show();
            }
        });
        return b;
    }

    private static String when(long time) {
        return new SimpleDateFormat("M月d日 H:mm", Locale.JAPAN).format(new Date(time));
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
