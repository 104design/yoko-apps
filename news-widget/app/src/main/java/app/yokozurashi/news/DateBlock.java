package app.yokozurashi.news;

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

import java.util.Calendar;

/**
 * 日付と天気のブロックを1枚の画像に描く。
 * ウィジェットの文字部品は独自フォントを使えないので、手描き数字と
 * Zen Maru Gothic をここでまとめて描き、ImageView に渡す。
 * 寸法は SPEC.md の「仕様書 1: 画面」。単位は dp。
 */
public class DateBlock {
    public static final int WIDTH_DP = 160;
    public static final int HEIGHT_DP = 100;

    private static final int INK = 0xFF141414;
    private static final int MAGENTA = 0xFFC8387A;
    private static final int GRAY = 0xFF5A5A5A;

    private static final int[] DIGITS = {
            R.drawable.num_0, R.drawable.num_1, R.drawable.num_2, R.drawable.num_3,
            R.drawable.num_4, R.drawable.num_5, R.drawable.num_6, R.drawable.num_7,
            R.drawable.num_8, R.drawable.num_9};
    private static final String[] WEEKDAYS = {
            "日曜日", "月曜日", "火曜日", "水曜日", "木曜日", "金曜日", "土曜日"};

    private static final float DIGIT_TOP = 1;
    private static final float DIGIT_H = 32;
    private static final float DIGIT_GAP = 3;

    /**
     * @param temp  気温の表示（例「18°」）。取れていないときは「--°」
     * @param label 天気と地名（例「晴れ・渋谷区」）。なければ null
     */
    public static Bitmap render(Context ctx, Calendar now, String temp, String label) {
        Resources res = ctx.getResources();
        float d = res.getDisplayMetrics().density;
        Bitmap bmp = Bitmap.createBitmap(Math.round(WIDTH_DP * d), Math.round(HEIGHT_DP * d),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.scale(d, d); // 以降は dp で描く

        Typeface black = res.getFont(R.font.zen_maru_black);
        Typeface bold = res.getFont(R.font.zen_maru_bold);
        Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

        // 1行目：手描き数字の日付。「月」「日」はフォント
        TextPaint unit = text(black, 15, INK);
        float bottom = DIGIT_TOP + DIGIT_H;
        float x = 0;
        x = drawNumber(c, res, img, now.get(Calendar.MONTH) + 1, x);
        c.drawText("月", x, bottom - 2, unit);
        x += unit.measureText("月") + 4 + DIGIT_GAP;
        x = drawNumber(c, res, img, now.get(Calendar.DAY_OF_MONTH), x);
        c.drawText("日", x, bottom - 2, unit);

        // 2行目：曜日
        TextPaint week = text(bold, 13, MAGENTA);
        c.drawText(WEEKDAYS[now.get(Calendar.DAY_OF_WEEK) - 1], 0, bottom + 16, week);

        // 3行目：気温と、天気・地名
        TextPaint tempPaint = text(black, 22, INK);
        float tx = 0;
        c.drawText(temp, tx, bottom + 43, tempPaint);
        tx += tempPaint.measureText(temp) + 6;
        if (label != null && label.length() > 0) {
            TextPaint labelPaint = text(bold, 11, GRAY);
            CharSequence fit = TextUtils.ellipsize(label, labelPaint, WIDTH_DP - tx, TextUtils.TruncateAt.END);
            c.drawText(fit, 0, fit.length(), tx, bottom + 41, labelPaint);
        }
        return bmp;
    }

    /** 手描き数字を並べて描き、描き終えた右端の x を返す。 */
    private static float drawNumber(Canvas c, Resources res, Paint img, int value, float x) {
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
