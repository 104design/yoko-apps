package app.yokozurashi.steps;

import android.content.Context;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;

/**
 * 帯の中の文を作る。よこずらしニュースの NewsBelt と同じしくみ。
 * 帯に入る文はそのまま出し、入らない文は右から左へ流す。
 * 流すときは、文の長さに合う幅の「ベルト」（tools/make_belts.py が作るレイアウト）を選ぶ。
 * ウィジェットの文字部品は独自フォントを使えないので、帯の文だけは端末の標準フォントになる。
 */
public class Belt {
    // ベルトの幅の一覧は tools/make_belts.py が BeltSizes に書き出す
    private static final int[] WIDTHS = BeltSizes.WIDTHS;
    private static final int[] LAYOUTS = BeltSizes.LAYOUTS;
    // 文の終わりと、次に入ってくる文の頭のあいだ。速さ毎秒40dpで0.8秒。
    // 実際のすきまは、ベルトの幅の刻みぶん（最大20dp、0.5秒）長くなることがある
    private static final float GAP_DP = 32;
    private static final float TEXT_SP = 15;

    /** @param roomDp 帯の中で文字に使える幅（dp） */
    public static RemoteViews of(Context ctx, String line, float roomDp) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        TextPaint paint = new TextPaint();
        paint.setTextSize(TEXT_SP * dm.scaledDensity);
        if (paint.measureText(line) <= roomDp * dm.density) return still(ctx, line);

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
        // 表示中の列を選び直すと、列を左へ動かすアニメがすぐ始まる（animateFirstView="true"）。
        // アニメは止まらず繰り返すので、ほかのアプリから戻ったときも流れつづける
        belt.setDisplayedChild(R.id.belt_flipper, 0);
        return belt;
    }

    /** 流さない文。 */
    private static RemoteViews still(Context ctx, String text) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.belt_static);
        v.setTextViewText(R.id.belt_text, text);
        return v;
    }
}
