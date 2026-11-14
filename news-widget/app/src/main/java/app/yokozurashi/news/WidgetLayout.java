package app.yokozurashi.news;

/**
 * ウィジェットの大きさから、日付の拡大率とキャラの大きさを決める。
 * 基準は 380×180dp（SPEC.md 仕様書1）。ホーム画面のマス目によって実際の大きさは変わり、
 * 4×2 で約250×172dp、5×3 で約315×260dp になる端末がある。
 * 配置を変えるときは、先に手元のプレビューで見え方を確かめること。
 */
public class WidgetLayout {
    /** キャラの元画像の大きさ（px）と、絵の中の位置。 */
    private static final float CHAR_W = 1190, CHAR_H = 900;
    private static final float CHAR_BLANK_RIGHT = 173; // 右端の余白。ここはウィジェットの外へはみ出させる
    private static final float CHAR_HEAD_LEFT = 430;   // 髪の左端
    private static final float CHAR_HEAD_TOP = 80;     // 頭のてっぺん

    public static final float DATE_LEFT = 20, DATE_TOP = 10; // 日付のブロックの左上（dp）
    private static final float CHAR_BELOW = 10;               // キャラを下へはみ出させる量（dp）

    public final float dateScale;   // 日付のブロックの拡大率
    public final float charWidth, charHeight, charMarginRight, charMarginBottom; // キャラの画像（dp）
    public final float dateWidth;   // 日付のブロックの画像の幅（dp）
    public final float labelRight;  // 天気・地名を収める右端。日付のブロックの左端から測る（dp）

    public WidgetLayout(float w, float h) {
        // 日付は、高さと幅に余裕があるぶんだけ大きくする
        dateScale = clamp(Math.min(h / 180f, w / 250f), 1f, 1.4f);
        float dateRowRight = DATE_LEFT + 150 * dateScale; // いちばん幅の広い日付（12月28日など）の右端
        float labelRowBottom = DATE_TOP + 62 * dateScale; // 日付の行と、曜日・天気の行の下端

        // キャラの縮尺（元画像1pxあたりのdp）。次の3つから決める
        float fitHeight = (h + 18) / CHAR_H;                                   // 上下に収まる大きさ
        float besideDate = (w - dateRowRight - 4) / (CHAR_W - CHAR_BLANK_RIGHT - CHAR_HEAD_LEFT); // 髪が日付の右に来る
        float belowDate = (h + CHAR_BELOW - labelRowBottom) / (CHAR_H - CHAR_HEAD_TOP);         // 頭が日付と曜日の行の下に来る
        float s = Math.min(fitHeight, Math.max(besideDate, belowDate));
        s = Math.max(s, Math.min(fitHeight, 0.18f)); // 小さいウィジェットでも小さくしすぎない

        charWidth = CHAR_W * s;
        charHeight = CHAR_H * s;
        charMarginRight = -CHAR_BLANK_RIGHT * s;
        charMarginBottom = -CHAR_BELOW;

        dateWidth = Math.max(60, w - DATE_LEFT - 8);
        float headTop = h + CHAR_BELOW - (CHAR_H - CHAR_HEAD_TOP) * s;
        float headLeft = w - (CHAR_W - CHAR_BLANK_RIGHT - CHAR_HEAD_LEFT) * s;
        if (headTop >= labelRowBottom - 1) {
            labelRight = dateWidth - 8;                              // 頭が行の下にあるので、右端近くまで使える
        } else {
            labelRight = Math.max(60, headLeft - DATE_LEFT - 6);     // 髪の手前まで
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
