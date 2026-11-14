package app.yokozurashi.pomodoro;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * The pomodoro tape: one 2-hour set as a single road. The future is to
 * the RIGHT (unlike the timer's ruler): upcoming breaks and tomatoes
 * approach from the right, finished time flows away to the left.
 * Focus segments are solid lines, breaks are dotted. A tomato sits on
 * the line at the end of each focus segment; it turns red once passed.
 * Same fixed scale as the timer tape: 1 minute = 18dp. Not draggable.
 * During a long break, the tape is a single 15-minute dotted segment.
 */
public class PomoTapeView extends View {
    private final float density;
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap tomatoOn, tomatoOff;

    // timeline in minutes
    private float[] segEnds = new float[0];   // cumulative end of each segment
    private boolean[] segBreak = new boolean[0];
    private float totalMin = 0;
    private float[] tomatoAt = new float[0];  // elapsed positions of tomatoes
    private float elapsedMin = 0;             // displayed position (may animate)
    private int markerColor = Color.parseColor("#F0B429");

    private ValueAnimator animator;

    public PomoTapeView(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        tickPaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeWidth(dp(3));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        markerPaint.setStyle(Paint.Style.FILL);
        tomatoOn = BitmapFactory.decodeResource(getResources(), R.drawable.dot_on);
        tomatoOff = BitmapFactory.decodeResource(getResources(), R.drawable.dot_off);
        setTimeline(false, 25, 5);
    }

    private float dp(float v) { return v * density; }
    private float minutePx() { return dp(18); }

    /** Build the segment list. longBreak=true gives the single 15-min dotted tape.
     *  Durations are float minutes so shortened test builds still work. */
    public void setTimeline(boolean longBreak, float focusMin, float breakMin) {
        if (longBreak) {
            segEnds = new float[]{15};
            segBreak = new boolean[]{true};
            totalMin = 15;
            tomatoAt = new float[0];
        } else {
            segEnds = new float[8];
            segBreak = new boolean[8];
            tomatoAt = new float[4];
            float acc = 0;
            for (int i = 0; i < 4; i++) {
                acc += focusMin;
                segEnds[i * 2] = acc;
                segBreak[i * 2] = false;
                tomatoAt[i] = acc;
                acc += breakMin;
                segEnds[i * 2 + 1] = acc;
                segBreak[i * 2 + 1] = true;
            }
            totalMin = acc;
        }
        invalidate();
    }

    public void setMarkerColor(int color) {
        markerColor = color;
        invalidate();
    }

    /** Jump straight to an elapsed position. */
    public void setElapsed(float elapsed) {
        if (animator != null) animator.cancel();
        elapsedMin = elapsed;
        invalidate();
    }

    /** Slide to an elapsed position (skip / reset / phase change). */
    public void animateElapsed(float target) {
        if (animator != null) animator.cancel();
        final float from = elapsedMin;
        if (Math.abs(target - from) < 0.01f) { elapsedMin = target; invalidate(); return; }
        animator = ValueAnimator.ofFloat(from, target);
        animator.setDuration(320);
        animator.addUpdateListener(a -> {
            elapsedMin = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public float getElapsed() { return elapsedMin; }

    private int segmentAt(float minute) {
        for (int i = 0; i < segEnds.length; i++) {
            if (minute < segEnds[i]) return i;
        }
        return segEnds.length - 1;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        float cx = w / 2f;
        float baseY = dp(16);
        float px = minutePx();

        // segments: solid (focus) / dotted (break)
        for (int s = 0; s < segEnds.length; s++) {
            float start = s == 0 ? 0 : segEnds[s - 1];
            float end = segEnds[s];
            float x1 = cx + (start - elapsedMin) * px;
            float x2 = cx + (end - elapsedMin) * px;
            if (x2 < 0 || x1 > w) continue;
            linePaint.setColor(TapeView.INK);
            if (!segBreak[s]) {
                canvas.drawLine(Math.max(0, x1), baseY, Math.min(w, x2), baseY, linePaint);
            } else {
                float dotStep = dp(7);
                float dotR = dp(1.8f);
                for (float x = x1; x <= x2; x += dotStep) {
                    if (x < -dotR || x > w + dotR) continue;
                    canvas.drawCircle(x, baseY, dotR, linePaint);
                }
            }
        }

        // ticks: every minute short, every 5 minutes long, segment bounds thick
        int fromMin = Math.max(0, (int) Math.floor(elapsedMin - cx / px) - 1);
        int toMin = Math.min((int) totalMin, (int) Math.ceil(elapsedMin + (w - cx) / px) + 1);
        for (int m = fromMin; m <= toMin; m++) {
            float x = cx + (m - elapsedMin) * px;
            boolean bound = false;
            for (float e : segEnds) if (Math.abs(e - m) < 0.001f) bound = true;
            boolean major = m % 5 == 0;
            // same weights as the prototype: thin gray minutes, dark
            // 5-minute ticks, thick segment bounds
            float tickH = bound ? dp(14) : major ? dp(13) : dp(7);
            tickPaint.setColor((bound || major) ? TapeView.INK : TapeView.MUTE);
            tickPaint.setStrokeWidth(bound ? dp(4) : major ? dp(2.5f) : dp(1.5f));
            canvas.drawLine(x, baseY, x, baseY + tickH, tickPaint);
        }

        // tomatoes sitting on the line at each focus end
        float tSize = dp(17);
        for (int i = 0; i < tomatoAt.length; i++) {
            float x = cx + (tomatoAt[i] - elapsedMin) * px;
            if (x < -tSize || x > w + tSize) continue;
            Bitmap b = elapsedMin >= tomatoAt[i] ? tomatoOn : tomatoOff;
            if (b != null) {
                android.graphics.RectF dst = new android.graphics.RectF(
                        x - tSize / 2f, baseY - tSize + dp(3), x + tSize / 2f, baseY + dp(3));
                canvas.drawBitmap(b, null, dst, null);
            }
        }

        // center marker BELOW the tape, apex up, colored by phase kind
        markerPaint.setColor(markerColor);
        Path tri = new Path();
        tri.moveTo(cx, baseY + dp(24));
        tri.lineTo(cx - dp(6), baseY + dp(32));
        tri.lineTo(cx + dp(6), baseY + dp(32));
        tri.close();
        canvas.drawPath(tri, markerPaint);

        // fade both edges like the timer tape
        android.graphics.Paint fadePaint = new android.graphics.Paint();
        fadePaint.setShader(new android.graphics.LinearGradient(
                0, 0, dp(36), 0, Color.WHITE, Color.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, dp(36), getHeight(), fadePaint);
        fadePaint.setShader(new android.graphics.LinearGradient(
                w, 0, w - dp(36), 0, Color.WHITE, Color.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP));
        canvas.drawRect(w - dp(36), 0, w, getHeight(), fadePaint);
    }
}
