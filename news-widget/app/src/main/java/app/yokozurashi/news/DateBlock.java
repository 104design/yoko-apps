package app.yokozurashi.news;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
    public static final int MAX_WIDTH_DP = 160;
    public static final int MIN_WIDTH_DP = 110;
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
     * @param widthDp このブロックに使える幅。右のキャラに重ならないよう、ウィジェットの幅から決める
     * @param kind  天気アイコンの種類（Weather.SUN など）。Weather.NONE ならアイコンなし
     * @param temp  気温の表示（例「18°」）。取れていないときは「--°」
     * @param label 天気と地名（例「晴れ・渋谷区」）。なければ null
     */
    public static Bitmap render(Context ctx, Calendar now, int widthDp, int kind, String temp, String label) {
        Resources res = ctx.getResources();
        float d = res.getDisplayMetrics().density;
        widthDp = Math.max(MIN_WIDTH_DP, Math.min(MAX_WIDTH_DP, widthDp));
        Bitmap bmp = Bitmap.createBitmap(Math.round(widthDp * d), Math.round(HEIGHT_DP * d),
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

        // 2行目：曜日と、天気・地名
        TextPaint week = text(bold, 13, MAGENTA);
        String weekday = WEEKDAYS[now.get(Calendar.DAY_OF_WEEK) - 1];
        c.drawText(weekday, 0, bottom + 16, week);
        if (label != null && label.length() > 0) {
            float lx = week.measureText(weekday) + 8;
            TextPaint labelPaint = text(bold, 11, GRAY);
            CharSequence fit = TextUtils.ellipsize(label, labelPaint, widthDp - lx, TextUtils.TruncateAt.END);
            c.drawText(fit, 0, fit.length(), lx, bottom + 15.5f, labelPaint);
        }

        // 3行目：天気アイコンと気温
        TextPaint tempPaint = text(black, 22, INK);
        float tx = 0;
        if (kind != Weather.NONE) {
            c.save();
            c.translate(0, bottom + 21);
            drawIcon(c, kind);
            c.restore();
            tx = 24 + 6;
        }
        c.drawText(temp, tx, bottom + 41, tempPaint);
        return bmp;
    }

    /** 天気の線アイコンを、24×24 の枠の中に描く（仮のアイコン。手描きに差し替える余地を残す）。 */
    private static void drawIcon(Canvas c, int kind) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(INK);
        if (kind == Weather.SUN) {
            c.drawCircle(12, 12, 4.2f, p);
            for (int i = 0; i < 8; i++) {
                double a = Math.PI / 4 * i;
                float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                c.drawLine(12 + cos * 7.1f, 12 + sin * 7.1f, 12 + cos * 9.5f, 12 + sin * 9.5f, p);
            }
            return;
        }
        if (kind == Weather.FOG) {
            c.drawLine(4, 8, 20, 8, p);
            c.drawLine(4, 12.5f, 20, 12.5f, p);
            c.drawLine(4, 17, 20, 17, p);
            return;
        }
        // 雲。雨・雪・雷は雲を上に寄せて、下にしるしを描く
        float dy = kind == Weather.CLOUD ? 3 : 0;
        Path cloud = new Path();
        cloud.moveTo(7, 15 + dy);
        cloud.cubicTo(2.5f, 15 + dy, 2.5f, 9 + dy, 7, 9 + dy);
        cloud.cubicTo(7.5f, 4 + dy, 15, 3.5f + dy, 16, 8.5f + dy);
        cloud.cubicTo(21.5f, 8 + dy, 21.5f, 15 + dy, 17, 15 + dy);
        cloud.close();
        c.drawPath(cloud, p);
        if (kind == Weather.RAIN) {
            c.drawLine(8, 18.5f, 7, 21.5f, p);
            c.drawLine(12.5f, 18.5f, 11.5f, 21.5f, p);
            c.drawLine(17, 18.5f, 16, 21.5f, p);
        } else if (kind == Weather.SNOW) {
            p.setStyle(Paint.Style.FILL);
            c.drawCircle(8, 20, 1.3f, p);
            c.drawCircle(12.5f, 20, 1.3f, p);
            c.drawCircle(17, 20, 1.3f, p);
        } else if (kind == Weather.THUNDER) {
            Path bolt = new Path();
            bolt.moveTo(13, 17);
            bolt.lineTo(10.5f, 20);
            bolt.lineTo(13.5f, 20);
            bolt.lineTo(11.5f, 23);
            c.drawPath(bolt, p);
        }
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
