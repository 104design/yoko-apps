package app.yokozurashi.steps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;

/**
 * メインの画面。日本地図に、これまでにずらしたものを出す。
 * 下のカードには、ふだんはいまの場所を出し、ピンをタップするとその地点の名物を出す。
 * 右上の歯車から設定画面（SettingsActivity）を開く。開くたびに歩数を読み直す。
 */
public class MainActivity extends Activity implements MapView.Listener {
    private static final int REQ_PERMISSION = 1;
    private static final int INK = 0xFF141414;
    private static final int GRAY = 0xFF5A5A5A;
    private static final int ACCENT = 0xFFEE7948;
    private static final int ACCENT_DARK = 0xFFC4501F;

    private Typeface black, bold;
    private MapView map;
    private LinearLayout card;
    private TextView subtitle;
    private int picked = -1;
    private int shownIndex = -1, shownLap = -1; // 地図の見せる範囲を決めたときの場所

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        black = getResources().getFont(R.font.zen_maru_black);
        bold = getResources().getFont(R.font.zen_maru_bold);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFFFFFF);
        root.setFitsSystemWindows(true);

        // 見出し：題名と、ずらしたものの数・距離。右に設定の歯車
        FrameLayout header = new FrameLayout(this);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(16), dp(14), dp(64), dp(10));
        titles.addView(text("これまでにずらしたもの", black, 20, INK));
        subtitle = text("", bold, 11, GRAY);
        titles.addView(subtitle);
        header.addView(titles);
        GearView gear = new GearView(this);
        FrameLayout.LayoutParams glp = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.END | Gravity.TOP);
        glp.topMargin = dp(8);
        glp.rightMargin = dp(8);
        gear.setContentDescription("設定");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        header.addView(gear, glp);
        root.addView(header);

        // 地図と、その下に重なるカード
        FrameLayout body = new FrameLayout(this);
        map = new MapView(this);
        map.setListener(this);
        map.setBottomInset(dp(190));
        body.addView(map, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        bg.setStroke(dp(2), INK);
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);
        card.setClickable(true); // カードの上のタップを地図に通さない
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        clp.setMargins(dp(12), 0, dp(12), dp(12));
        body.addView(card, clp);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        show();
        StepReader.read(this, new Runnable() {
            @Override
            public void run() {
                show();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        // onResume がこのあとに呼ばれ、そこで読み直す
    }

    /** 地図とカードを、いまの旅のようすで描き直す。 */
    private void show() {
        Journey j = Journey.at(StepStore.distanceM(this));
        int n = Route.AT_M.length;
        boolean around = j.lap > 1; // 1周以上していれば、すべての地点をずらし終わっている
        int[] visited = new int[around ? n : j.index];
        for (int i = 0; i < visited.length; i++) visited[i] = i;
        map.setJourney(j.index, j.progress, j.pref, visited, around ? n - 1 : j.index);
        map.setPicked(picked);
        if (j.index != shownIndex || j.lap != shownLap) {
            // 進んでいたら、通ったところがぜんぶ見える範囲に合わせ直す
            shownIndex = j.index;
            shownLap = j.lap;
            map.fit();
        }
        int finished = Math.max(0, PassLog.passedCount(j.distanceM) - 1);
        subtitle.setText("ずらしたもの " + finished + " ・ " + Journey.km(j.distanceM) + "km");
        if (picked >= 0) showPicked(picked);
        else showCurrent(j);
    }

    @Override
    public void onPick(int index) {
        picked = index;
        map.setPicked(index);
        showPicked(index);
    }

    @Override
    public void onPickNone() {
        if (picked < 0) return;
        picked = -1;
        map.setPicked(-1);
        showCurrent(Journey.at(StepStore.distanceM(this)));
    }

    /** カード：いまの場所。歩数の読み取りが許可されていなければ、許可を求める。 */
    private void showCurrent(Journey j) {
        card.removeAllViews();
        int status = StepReader.status(this);
        if (status == StepReader.UNSUPPORTED) {
            card.addView(text("この端末では使えません", black, 16, ACCENT_DARK));
            card.addView(spaced(text("ヘルスコネクトが本体に入っている Android 14 以上が必要です。", bold, 12.5f, INK), 8));
            return;
        }
        if (status == StepReader.NO_PERMISSION) {
            card.addView(text("歩数の読み取りが許可されていません", black, 16, ACCENT_DARK));
            card.addView(spaced(text("許可すると、歩いたぶんだけよこずらしが日本を進みます。", bold, 12.5f, INK), 8));
            card.addView(spaced(pill("歩数の読み取りを許可する", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requestPermissions(new String[]{StepReader.PERMISSION_STEPS,
                            StepReader.PERMISSION_BACKGROUND}, REQ_PERMISSION);
                }
            }), 12));
            return;
        }
        card.addView(text("いま" + (j.lap > 1 ? "（" + j.lap + "周目）" : ""), bold, 11, GRAY));
        card.addView(spaced(text(j.pref + " " + j.place, black, 17, ACCENT_DARK), 2));
        card.addView(spaced(text(j.text, bold, 12.5f, INK), 8));
        card.addView(spaced(text((j.sea ? "船で" : "") + j.next + "まで あと" + Journey.km(j.toNextM) + "km",
                bold, 11, GRAY), 8));
    }

    /** カード：タップしたピンの地点。名物、ずらした文、ずらし終わった日、検索のボタン。 */
    private void showPicked(int i) {
        card.removeAllViews();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.BOTTOM);
        TextView place = text(Route.PREF[i] + " " + Route.NAME[i], black, 16, ACCENT_DARK);
        row.addView(place, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        long day = PassLog.finishedDay(PassLog.days(this), i);
        row.addView(text(day < 0 ? "日付を調べています" : date(day) + " ずらし終わり", bold, 10, GRAY));
        card.addView(row);
        card.addView(spaced(text(Route.ITEM[i], black, 19, INK), 6));
        card.addView(spaced(text(pastTense(Route.TEXT[i]), bold, 12.5f, INK), 6));
        final String item = Route.ITEM[i], pref = Route.PREF[i];
        card.addView(spaced(pill(item + "を調べる", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent open = new Intent(Intent.ACTION_VIEW, StepWidgetProvider.searchUrl(item, pref));
                startActivity(open);
            }
        }), 12));
    }

    /** 「〜ずらしています....」を「〜ずらしました」にする。 */
    static String pastTense(String s) {
        String t = s;
        while (t.endsWith(".") || t.endsWith("…") || t.endsWith("。")) t = t.substring(0, t.length() - 1);
        return t.replace("ずらしています", "ずらしました").replace("にしています", "にしました");
    }

    private static String date(long epochDay) {
        LocalDate d = LocalDate.ofEpochDay(epochDay);
        return d.getYear() + "年" + d.getMonthValue() + "月" + d.getDayOfMonth() + "日";
    }

    private TextView text(String s, Typeface face, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(face);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private View spaced(View v, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        v.setLayoutParams(lp);
        return v;
    }

    /** アクセントの色の、角の丸いボタン（ウィジェットの帯と同じ見た目）。 */
    private TextView pill(String s, View.OnClickListener click) {
        TextView b = text(s, bold, 13, INK);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(10), dp(18), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(ACCENT);
        bg.setStroke(dp(2), INK);
        bg.setCornerRadius(dp(20));
        b.setBackground(bg);
        b.setOnClickListener(click);
        return b;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** 設定の歯車。墨の線で描く。 */
    private static class GearView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        GearView(Context ctx) {
            super(ctx);
            p.setColor(INK);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            setClickable(true);
        }

        @Override
        protected void onDraw(Canvas c) {
            float d = getResources().getDisplayMetrics().density;
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = 8 * d;
            p.setStrokeWidth(1.8f * d);
            c.drawCircle(cx, cy, r, p);
            c.drawCircle(cx, cy, 3.2f * d, p);
            p.setStrokeWidth(2.6f * d);
            for (int k = 0; k < 8; k++) {
                double a = Math.toRadians(k * 45);
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                c.drawLine(cx + r * cos, cy + r * sin, cx + (r + 3 * d) * cos, cy + (r + 3 * d) * sin, p);
            }
        }
    }
}
