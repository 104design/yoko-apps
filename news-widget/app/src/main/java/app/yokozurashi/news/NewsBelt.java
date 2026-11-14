package app.yokozurashi.news;

import android.content.Context;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;

/**
 * ニュースの帯の中身を作る。
 * 見出しの長さに合う幅の「ベルト」（tools/make_belts.py が作るレイアウト）を選び、文字を入れる。
 */
public class NewsBelt {
    // tools/make_belts.py の WIDTHS と同じにする（dp）
    private static final int[] WIDTHS = {300, 450, 600, 800, 1000, 1300};
    private static final int[] LAYOUTS = {
            R.layout.belt_300, R.layout.belt_450, R.layout.belt_600,
            R.layout.belt_800, R.layout.belt_1000, R.layout.belt_1300};
    private static final float GAP_DP = 48;      // 文の終わりと、次に入ってくる文の頭のあいだ
    private static final float TEXT_SP = 15;

    /** 右から左へ流れつづける帯。 */
    public static RemoteViews flowing(Context ctx, String line) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        TextPaint paint = new TextPaint();
        paint.setTextSize(TEXT_SP * dm.scaledDensity);

        int last = WIDTHS.length - 1;
        float maxPx = (WIDTHS[last] - GAP_DP) * dm.density;
        String text = TextUtils.ellipsize(line, paint, maxPx, TextUtils.TruncateAt.END).toString();
        float needDp = paint.measureText(text) / dm.density + GAP_DP;
        int pick = last;
        for (int i = 0; i < WIDTHS.length; i++) {
            if (WIDTHS[i] >= needDp) {
                pick = i;
                break;
            }
        }
        RemoteViews belt = new RemoteViews(ctx.getPackageName(), LAYOUTS[pick]);
        belt.setTextViewText(R.id.belt_text_a1, text);
        belt.setTextViewText(R.id.belt_text_a2, text);
        belt.setTextViewText(R.id.belt_text_b1, text);
        belt.setTextViewText(R.id.belt_text_b2, text);
        return belt;
    }

    /** 流さない短い文。 */
    public static RemoteViews still(Context ctx, String text) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.belt_static);
        v.setTextViewText(R.id.belt_text, text);
        return v;
    }
}
