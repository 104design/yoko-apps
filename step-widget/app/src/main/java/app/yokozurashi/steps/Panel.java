package app.yokozurashi.steps;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;

/**
 * ウィジェットの絵（地名、累計の距離、きょうの歩数、帯、道のりの線）を1枚の画像に描く。
 * ウィジェットの文字部品は独自フォントを使えないので、手描き数字と Zen Maru Gothic をここでまとめて描く。
 * 寸法は SPEC.md の「仕様書 1: 画面」。基準は 250×172dp で、単位は dp。
 * tools/preview.py が同じ配置で見本の画像を作る。配置を変えたら両方を直す。
 */
public class Panel {
    public static final float BASE_W = 250, BASE_H = 172;

    private static final int INK = 0xFF141414;
    private static final int MAGENTA = 0xFFC8387A;
    private static final int GRAY = 0xFF5A5A5A;
    private static final int TEAL = 0xFF72CFCB;

    private static final int[] DIGITS = {
            R.drawable.num_0, R.drawable.num_1, R.drawable.num_2, R.drawable.num_3,
            R.drawable.num_4, R.drawable.num_5, R.drawable.num_6, R.drawable.num_7,
            R.drawable.num_8, R.drawable.num_9};

    private static final float SIDE = 16;        // 文字の左右の余白
    private static final float DIGIT_TOP = 32, DIGIT_H = 32, DIGIT_GAP = 3;
    private static final float BAND_SIDE = 10, BAND_TOP = 74, BAND_H = 34;
    private static final float TRACK_SIDE = 26;  // 道のりの線の左右の余白
    private static final float TRACK_UP = 26;    // 線の、下端からの高さ
    private static final float YOKO_BOX = 60;    // よこずらしのコマの枠（正方形）
    private static final float YOKO_FEET = 0.77f; // 枠の上端から足元までの割合

    /** ウィジェットの大きさから決まる配置。 */
    public static class Layout {
        public final float scale;  // 基準寸法に対する拡大率
        public final float w, h;   // 拡大率1に直したときの幅と高さ（dp）
        public final float bandTop, trackY;

        public Layout(float widthDp, float heightDp) {
            scale = Math.max(0.8f, Math.min(1.8f, Math.min(widthDp / BASE_W, heightDp / BASE_H)));
            w = widthDp / scale;
            h = heightDp / scale;
            // 高さの余りは、帯の上と下に分ける
            bandTop = BAND_TOP + Math.max(0, h - BASE_H) * 0.5f;
            trackY = h - TRACK_UP;
        }

        /** よこずらしの枠の左上（実寸のdp）。progress は区間の進み具合（0〜1）。 */
        public float yokoLeft(float progress) {
            float x = TRACK_SIDE + (w - TRACK_SIDE * 2) * progress;
            return (x - YOKO_BOX / 2) * scale;
        }

        public float yokoTop() {
            return (trackY - YOKO_BOX * YOKO_FEET + 1) * scale;
        }

        public float yokoSize() {
            return YOKO_BOX * scale;
        }
    }

    /** 描く内容。 */
    public static class Content {
        public String place;   // 1行目（「沖縄県 那覇」）
        public String note;    // 1行目の右（「2周目 12:34」など）。なければ null
        public long distanceM; // 累計の距離
        public long today;     // きょうの歩数
        public String band;    // 帯の文
        public String from;    // 線の左端の地名
        public String to;      // 線の右端の説明（「読谷まで あと12.3km」）
        public float progress; // 区間の進み具合
    }

    public static Bitmap render(Context ctx, Layout lay, float pxPerDp, Content t) {
        Resources res = ctx.getResources();
        float k = pxPerDp * lay.scale;
        Bitmap bmp = Bitmap.createBitmap(Math.max(1, Math.round(lay.w * k)),
                Math.max(1, Math.round(lay.h * k)), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(k, k); // 以降は「拡大率1のときの dp」で描く
        float right = lay.w - SIDE;

        Typeface black = res.getFont(R.font.zen_maru_black);
        Typeface bold = res.getFont(R.font.zen_maru_bold);
        Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // 1行目：いまの場所。右に小さく、周回と読み取りの時刻
        float noteW = 0;
        if (t.note != null) {
            TextPaint notePaint = text(bold, 10, GRAY);
            noteW = notePaint.measureText(t.note) + 8;
            c.drawText(t.note, right - noteW + 8, 25, notePaint);
        }
        TextPaint placePaint = text(bold, 13, MAGENTA);
        CharSequence place = TextUtils.ellipsize(t.place, placePaint,
                right - SIDE - noteW, TextUtils.TruncateAt.END);
        c.drawText(place, 0, place.length(), SIDE, 25, placePaint);

        // 2行目の左：累計の距離。km の整数部は手描き数字、小数と単位はフォント
        float bottom = DIGIT_TOP + DIGIT_H;
        float x = drawNumber(c, res, img, t.distanceM / 1000, SIDE);
        TextPaint unit = text(black, 15, INK);
        String tail = "." + (t.distanceM % 1000 / 100) + "km";
        c.drawText(tail, x, bottom - 2, unit);

        // 2行目の右：きょうの歩数
        TextPaint label = text(bold, 10, GRAY);
        TextPaint steps = text(black, 16, INK);
        String todayText = String.format("%,d歩", t.today);
        c.drawText("きょう", right - label.measureText("きょう"), bottom - 20, label);
        c.drawText(todayText, right - steps.measureText(todayText), bottom - 2, steps);

        // 帯：いまずらしているもの
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF band = new RectF(BAND_SIDE, lay.bandTop, lay.w - BAND_SIDE, lay.bandTop + BAND_H);
        p.setStyle(Paint.Style.FILL);
        p.setColor(TEAL);
        c.drawRoundRect(band, 14, 14, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(INK);
        c.drawRoundRect(band, 14, 14, p);
        TextPaint bandPaint = text(bold, 14, INK);
        float bandRoom = band.width() - 28;
        if (bandPaint.measureText(t.band) > bandRoom) bandPaint.setTextSize(12);
        CharSequence bandText = TextUtils.ellipsize(t.band, bandPaint, bandRoom, TextUtils.TruncateAt.END);
        Paint.FontMetrics fm = bandPaint.getFontMetrics();
        c.drawText(bandText, 0, bandText.length(), band.left + 14,
                band.centerY() - (fm.ascent + fm.descent) / 2, bandPaint);

        // 道のりの線：左端がいまの地点、右端が次の地点。進んだぶんを太いティールで塗る
        float x0 = TRACK_SIDE, x1 = lay.w - TRACK_SIDE, y = lay.trackY;
        float xp = x0 + (x1 - x0) * Math.max(0, Math.min(1, t.progress));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(TEAL);
        p.setStrokeWidth(6);
        c.drawLine(x0, y, xp, y, p);
        p.setColor(INK);
        p.setStrokeWidth(2);
        c.drawLine(x0, y, x1, y, p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(x0, y, 4, p);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(x1, y, 4, p);
        p.setStyle(Paint.Style.STROKE);
        p.setColor(INK);
        c.drawCircle(x1, y, 4, p);

        // 線の下：左に地点の名前、右に次の地点と残りの距離
        TextPaint small = text(bold, 10.5f, GRAY);
        float toW = small.measureText(t.to);
        c.drawText(t.to, right - toW, lay.h - 8, small);
        CharSequence from = TextUtils.ellipsize(t.from, small,
                Math.max(0, right - toW - 10 - SIDE), TextUtils.TruncateAt.END);
        c.drawText(from, 0, from.length(), SIDE, lay.h - 8, small);
        return bmp;
    }

    /** 手描き数字を並べて描き、描き終えた右端の x を返す。 */
    private static float drawNumber(Canvas c, Resources res, Paint img, long value, float x) {
        String s = String.valueOf(value);
        for (int i = 0; i < s.length(); i++) {
            Bitmap digit = BitmapFactory.decodeResource(res, DIGITS[s.charAt(i) - '0']);
            float w = DIGIT_H * digit.getWidth() / digit.getHeight();
            c.drawBitmap(digit, null, new RectF(x, DIGIT_TOP, x + w, DIGIT_TOP + DIGIT_H), img);
            digit.recycle();
            x += w + DIGIT_GAP;
        }
        return x;
    }

    private static TextPaint text(Typeface face, float size, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(face);
        p.setTextSize(size);
        p.setColor(color);
        return p;
    }
}
