package app.yokozurashi.pomodoro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Draws the short tape under the widget character into a bitmap.
 * A widget cannot host the app's custom tape views, so this repeats their
 * look at the same scale (1 minute = 18dp):
 *  - pomodoro: the road. Future to the right, focus solid, break dotted,
 *    a tomato on the line at each focus end (red once passed).
 *  - timer: the ruler. 0 at the left, flows toward 0 while counting.
 * No numbers here: the big digits above already show the time.
 * The bitmap is wider than any widget and shown centered and uncropped
 * (scaleType=center), so its middle is always the character's position.
 */
final class WidgetTape {
    static final int WIDTH_DP = 220;
    static final int HEIGHT_DP = 36;
    private static final float BASE_DP = 16;   // the line; tomatoes sit above it
    private static final float VISIBLE_HALF_DP = 100; // the tape fades out beyond this

    private static Bitmap tomatoOn, tomatoOff;

    private WidgetTape() {}

    static Bitmap render(Context ctx, TimerState s) {
        final float d = ctx.getResources().getDisplayMetrics().density;
        int w = Math.round(WIDTH_DP * d), h = Math.round(HEIGHT_DP * d);
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float cx = w / 2f, baseY = BASE_DP * d, px = 18 * d;

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(TapeView.INK);
        line.setStrokeWidth(3 * d);
        line.setStrokeCap(Paint.Cap.ROUND);
        Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
        tick.setStrokeCap(Paint.Cap.ROUND);

        if ("pomodoro".equals(s.kind)) {
            drawPomodoro(ctx, c, s, w, cx, baseY, px, d, line, tick);
        } else {
            drawTimer(c, s, w, cx, baseY, px, d, line, tick);
        }

        // fade out toward both sides (keeps only what is already drawn)
        Paint fade = new Paint();
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        float half = VISIBLE_HALF_DP * d, soft = 30 * d;
        fade.setShader(new LinearGradient(cx - half, 0, cx + half, 0,
                new int[]{Color.TRANSPARENT, Color.BLACK, Color.BLACK, Color.TRANSPARENT},
                new float[]{0f, soft / (2 * half), 1f - soft / (2 * half), 1f},
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, fade);
        return bmp;
    }

    private static void drawTick(Canvas c, Paint tick, float x, float baseY, float d,
                                 boolean thick, boolean major) {
        tick.setColor((thick || major) ? TapeView.INK : TapeView.MUTE);
        tick.setStrokeWidth((thick ? 4f : major ? 2.5f : 1.5f) * d);
        c.drawLine(x, baseY, x, baseY + (thick ? 14 : major ? 13 : 7) * d, tick);
    }

    private static void drawTimer(Canvas c, TimerState s, int w, float cx, float baseY,
                                  float px, float d, Paint line, Paint tick) {
        float pos = s.remainingNow() / 60000f;
        float left = Math.max(0, cx - pos * px);
        float right = Math.min(w, cx + (999 - pos) * px);
        if (right > left) c.drawLine(left, baseY, right, baseY, line);
        int from = Math.max(0, (int) Math.floor(pos - cx / px));
        int to = Math.min(999, (int) Math.ceil(pos + cx / px));
        for (int m = from; m <= to; m++) {
            drawTick(c, tick, cx + (m - pos) * px, baseY, d, m == 0, m % 5 == 0);
        }
    }

    private static void drawPomodoro(Context ctx, Canvas c, TimerState s, int w, float cx,
                                     float baseY, float px, float d, Paint line, Paint tick) {
        float focusMin = TimerState.pomodoroDurationMs("focus") / 60000f;
        float breakMin = TimerState.pomodoroDurationMs("short") / 60000f;
        boolean isLong = "long".equals(s.mode);
        float[] ends;        // cumulative end of each segment, in minutes
        boolean[] isBreak;
        if (isLong) {
            ends = new float[]{TimerState.pomodoroDurationMs("long") / 60000f};
            isBreak = new boolean[]{true};
        } else {
            ends = new float[8];
            isBreak = new boolean[8];
            float acc = 0;
            for (int i = 0; i < 4; i++) {
                acc += focusMin; ends[i * 2] = acc; isBreak[i * 2] = false;
                acc += breakMin; ends[i * 2 + 1] = acc; isBreak[i * 2 + 1] = true;
            }
        }
        float pos = s.pomodoroElapsedMin();
        float total = ends[ends.length - 1];

        for (int i = 0; i < ends.length; i++) {
            float start = i == 0 ? 0 : ends[i - 1];
            float x1 = cx + (start - pos) * px, x2 = cx + (ends[i] - pos) * px;
            if (x2 < 0 || x1 > w) continue;
            if (!isBreak[i]) {
                c.drawLine(Math.max(0, x1), baseY, Math.min(w, x2), baseY, line);
            } else {
                for (float x = x1; x <= x2; x += 7 * d) {
                    if (x >= 0 && x <= w) c.drawCircle(x, baseY, 1.8f * d, line);
                }
            }
        }

        int from = Math.max(0, (int) Math.floor(pos - cx / px));
        int to = Math.min((int) total, (int) Math.ceil(pos + cx / px));
        for (int m = from; m <= to; m++) {
            boolean bound = m == 0;
            for (float e : ends) if (Math.abs(e - m) < 0.001f) bound = true;
            drawTick(c, tick, cx + (m - pos) * px, baseY, d, bound, m % 5 == 0);
        }

        if (isLong) return;
        if (tomatoOn == null) {
            tomatoOn = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.dot_on);
            tomatoOff = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.dot_off);
        }
        Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
        float size = 15 * d;
        for (int i = 0; i < 4; i++) {
            float at = ends[i * 2];
            float x = cx + (at - pos) * px;
            if (x < -size || x > w + size) continue;
            Bitmap b = pos >= at ? tomatoOn : tomatoOff;
            if (b == null) continue;
            c.drawBitmap(b, null, new RectF(x - size / 2f, baseY - size + 3 * d,
                    x + size / 2f, baseY + 3 * d), bmpPaint);
        }
    }
}
