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
 * ウィジェットの絵（地名、累計の距離、きょうの歩数、名物の絵、帯の枠、道のりの線）を1枚の画像に描く。
 * ウィジェットの文字部品は独自フォントを使えないので、手描き数字と Zen Maru Gothic をここでまとめて描く。
 * 寸法は SPEC.md の「仕様書 1: 画面」。基準は 250×172dp で、単位は dp。
 * tools/preview.py が同じ配置で見本の画像を作る。配置を変えたら両方を直す。
 */
public class Panel {
    public static final float BASE_W = 250, BASE_H = 172;

    private static final int INK = 0xFF141414;
    private static final int GRAY = 0xFF5A5A5A;
    /** よこめぐりのアクセントカラー。帯と、進んだぶんの線に使う。 */
    private static final int ACCENT = 0xFFEE7948;
    /** アクセントを白地の文字に使うときの、濃いめの色。 */
    private static final int ACCENT_DARK = 0xFFC4501F;
    /** アクセントの薄い色。絵がまだない都道府県の、仮の丸に使う。 */
    private static final int ACCENT_PALE = 0xFFFCE4D9;

    private static final int[] DIGITS = {
            R.drawable.num_0, R.drawable.num_1, R.drawable.num_2, R.drawable.num_3,
            R.drawable.num_4, R.drawable.num_5, R.drawable.num_6, R.drawable.num_7,
            R.drawable.num_8, R.drawable.num_9};

    private static final float SIDE = 16;        // 文字の左右の余白
    private static final float DIGIT_TOP = 31, DIGIT_H = 32, DIGIT_GAP = 3;
    private static final float PICTURE = 50;     // 右上の絵（正方形）。下に都道府県の名前を添える
    private static final float PICTURE_TOP = 9, PICTURE_RIGHT = 20;
    private static final float PREF_BASE = 13;   // 絵の下端から、都道府県の名前の文字の下端まで
    private static final float BAND_SIDE = 10, BAND_TOP = 88, BAND_H = 30;
    private static final float BAND_TEXT_SIDE = 12; // 帯の中の、文字の左右の余白
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

        /** 帯の位置と大きさ（実寸のdp）。帯のタップを受ける枠を、ここに重ねる。 */
        public float bandLeft() {
            return BAND_SIDE * scale;
        }

        public float bandTopDp() {
            return bandTop * scale;
        }

        public float bandWidth() {
            return (w - BAND_SIDE * 2) * scale;
        }

        public float bandHeight() {
            return BAND_H * scale;
        }

        /** 帯の中で、文字に使える幅（実寸のdp）。左右の余白は widget_steps.xml の band_text と同じにする。 */
        public float bandTextRoom() {
            return bandWidth() - BAND_TEXT_SIDE * 2;
        }
    }

    /** 描く内容。 */
    public static class Content {
        public String place;   // 1行目（「那覇」）
        public String pref;    // 都道府県（絵の下に書く）
        public String picture;     // この地点の名物の絵の名前（meibutsu_NN_M）
        public String prefPicture; // この都道府県の形の絵の名前（pref_NN）
        public long distanceM; // 累計の距離
        public long today;     // きょうの歩数
        public String time;    // 最後に歩数を読めた時刻（「12:34」）。なければ null
        public String from;    // 線の左端の地名
        public String to;      // 線の右端の説明（「読谷まで あと12.3km」）
        public float progress; // 区間の進み具合
    }

    /**
     * 出す絵を選ぶ。絵は2段になっていて、下の段が都道府県の形の絵、上の段がその地点の名物の絵。
     * 名物の絵があればそれを、なければ都道府県の形の絵を使う。どちらもなければ 0。
     * 絵は res/drawable-nodpi にファイルを置くだけで増やせる（名前は SPEC.md の「名物の絵」）。
     */
    public static int pictureRes(Context ctx, String picture, String prefPicture) {
        Resources res = ctx.getResources();
        int id = res.getIdentifier(picture, "drawable", ctx.getPackageName());
        return id != 0 ? id : res.getIdentifier(prefPicture, "drawable", ctx.getPackageName());
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
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 右上：名物の絵
        RectF pic = new RectF(lay.w - PICTURE_RIGHT - PICTURE, PICTURE_TOP,
                lay.w - PICTURE_RIGHT, PICTURE_TOP + PICTURE);
        int picRes = pictureRes(ctx, t.picture, t.prefPicture);
        Bitmap picture = picRes == 0 ? null : BitmapFactory.decodeResource(res, picRes);
        if (picture != null) {
            // 縦横の比を保ったまま、枠の中央に収める
            float fit = Math.min(pic.width() / picture.getWidth(), pic.height() / picture.getHeight());
            float pw = picture.getWidth() * fit, ph = picture.getHeight() * fit;
            c.drawBitmap(picture, null, new RectF(pic.centerX() - pw / 2, pic.centerY() - ph / 2,
                    pic.centerX() + pw / 2, pic.centerY() + ph / 2), img);
            picture.recycle();
        } else {
            // 絵がまだない。薄い丸を置いておく
            p.setStyle(Paint.Style.FILL);
            p.setColor(ACCENT_PALE);
            c.drawCircle(pic.centerX(), pic.centerY(), PICTURE / 2 - 2, p);
        }
        // 絵の下に、都道府県の名前。標章と名前をいっしょに覚えられるように、いつも添える
        TextPaint prefPaint = text(bold, 10.5f, ACCENT_DARK);
        c.drawText(t.pref, pic.centerX() - prefPaint.measureText(t.pref) / 2, pic.bottom + PREF_BASE, prefPaint);
        float leftRoom = pic.left - 8 - SIDE; // 絵の左にある、文字のための幅

        // 1行目：いまの場所
        TextPaint placePaint = text(bold, 13, ACCENT_DARK);
        CharSequence place = TextUtils.ellipsize(t.place, placePaint, leftRoom, TextUtils.TruncateAt.END);
        c.drawText(place, 0, place.length(), SIDE, 25, placePaint);

        // 2行目：累計の距離。km の整数部は手描き数字、小数と単位はフォント。幅に入らなければ全体を縮める
        String whole = String.valueOf(t.distanceM / 1000);
        String tail = "." + (t.distanceM % 1000 / 100) + "km";
        TextPaint unit = text(black, 15, INK);
        float need = numberWidth(res, whole) + unit.measureText(tail);
        float bottom = DIGIT_TOP + DIGIT_H;
        c.save();
        if (need > leftRoom) c.scale(leftRoom / need, leftRoom / need, SIDE, bottom);
        float x = drawNumber(c, res, img, whole, SIDE);
        c.drawText(tail, x, bottom - 2, unit);
        c.restore();

        // 3行目：きょうの歩数と、最後に読めた時刻
        TextPaint label = text(bold, 10, GRAY);
        TextPaint steps = text(black, 13, INK);
        String todayText = String.format("%,d歩", t.today);
        float sx = SIDE;
        c.drawText("きょう", sx, bottom + 17, label);
        sx += label.measureText("きょう") + 4;
        c.drawText(todayText, sx, bottom + 17, steps);
        sx += steps.measureText(todayText) + 7;
        if (t.time != null && sx + label.measureText(t.time) <= SIDE + leftRoom) {
            c.drawText(t.time, sx, bottom + 17, label);
        }

        // 帯：いまずらしているもの
        RectF band = new RectF(BAND_SIDE, lay.bandTop, lay.w - BAND_SIDE, lay.bandTop + BAND_H);
        p.setStyle(Paint.Style.FILL);
        p.setColor(ACCENT);
        c.drawRoundRect(band, 14, 14, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(INK);
        c.drawRoundRect(band, 14, 14, p);
        // 帯の中の文は、ここでは描かない。流れる文字の部品（Belt）を上に重ねる

        // 道のりの線：左端がいまの地点、右端が次の地点。進んだぶんを太いアクセントカラーで塗る
        float x0 = TRACK_SIDE, x1 = lay.w - TRACK_SIDE, y = lay.trackY;
        float xp = x0 + (x1 - x0) * Math.max(0, Math.min(1, t.progress));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(ACCENT);
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

    /** 手描き数字を並べたときの幅。 */
    private static float numberWidth(Resources res, String s) {
        BitmapFactory.Options size = new BitmapFactory.Options();
        size.inJustDecodeBounds = true;
        float w = 0;
        for (int i = 0; i < s.length(); i++) {
            BitmapFactory.decodeResource(res, DIGITS[s.charAt(i) - '0'], size);
            w += DIGIT_H * size.outWidth / size.outHeight + DIGIT_GAP;
        }
        return w;
    }

    /** 手描き数字を並べて描き、描き終えた右端の x を返す。 */
    private static float drawNumber(Canvas c, Resources res, Paint img, String s, float x) {
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
