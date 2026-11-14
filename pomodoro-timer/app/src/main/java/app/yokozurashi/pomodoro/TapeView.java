package app.yokozurashi.pomodoro;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.OverScroller;

/**
 * The "time tape": one horizontal ruler under yokozurashi's feet.
 * 0 minutes at the left end, up to 999 minutes; scale is fixed at
 * 1 minute = 18dp in both setting and counting. The character always
 * stands at the center; the tick at the center marker is the time.
 * Numbers are drawn with the user's hand-drawn digit art.
 * Drag/fling to set (snaps to whole minutes); while the timer runs the
 * activity drives the offset from the remaining time so the tape flows
 * toward 0 in real time.
 */
public class TapeView extends View {
    public interface Listener {
        void onDragMinutes(int minutes);
        void onSettledMinutes(int minutes);
    }

    private static final int MAX_MIN = 999;
    private static final int[] PRESETS = {1, 3, 5, 10, 15, 30, 60};

    private final float density;
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Bitmap[] digits = new Bitmap[10];

    private float offsetMin = 0f; // minutes at the center marker (float while flowing/dragging)
    private float virtualMin = 0f; // unbounded drag position; offsetMin is its rubber-banded form
    private boolean interactive = true;
    private Listener listener;

    private final OverScroller scroller;
    private VelocityTracker velocityTracker;
    private float lastX;
    private boolean dragging = false;
    private boolean moved = false;
    private float downX;
    private long downTime;

    public TapeView(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        scroller = new OverScroller(ctx);
        tickPaint.setColor(Color.rgb(120, 120, 120));
        tickPaint.setStrokeWidth(dp(1.2f));
        linePaint.setColor(Color.rgb(60, 60, 60));
        linePaint.setStrokeWidth(dp(1.6f));
        markerPaint.setColor(Color.parseColor("#5B8DEF"));
        markerPaint.setStyle(Paint.Style.FILL);
        int[] ids = {R.drawable.num_0, R.drawable.num_1, R.drawable.num_2, R.drawable.num_3,
                R.drawable.num_4, R.drawable.num_5, R.drawable.num_6, R.drawable.num_7,
                R.drawable.num_8, R.drawable.num_9};
        for (int i = 0; i < 10; i++) {
            digits[i] = BitmapFactory.decodeResource(getResources(), ids[i]);
        }
    }

    public void setListener(Listener l) { listener = l; }
    public void setInteractive(boolean b) { interactive = b; }

    private float dp(float v) { return v * density; }
    private float minutePx() { return dp(18); }

    /** Programmatic position (minutes, may be fractional while counting). */
    public void setOffsetMinutes(float min) {
        offsetMin = clamp(min);
        virtualMin = offsetMin;
        invalidate();
    }

    /** Rubber band: past either end, the tape follows at 1/4 rate. */
    private float rubber(float v) {
        if (v < 0) return v * 0.25f;
        if (v > MAX_MIN) return MAX_MIN + (v - MAX_MIN) * 0.25f;
        return v;
    }

    public float getOffsetMinutes() { return offsetMin; }

    private float clamp(float v) { return Math.max(0, Math.min(MAX_MIN, v)); }

    private boolean isPreset(int m) {
        for (int p : PRESETS) if (p == m) return true;
        return false;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // safety net: if a scroll animation ended while this view was not
        // drawn (e.g. mode switched mid-glide), deliver the settle here so
        // the activity never waits forever for the value
        if (animating && !dragging && scroller.isFinished()) {
            animating = false;
            int target = Math.round(clamp(virtualMin));
            if (Math.abs(offsetMin - target) > 0.001f) {
                animateTo(target);
            } else if (listener != null) {
                listener.onSettledMinutes(target);
            }
        }
        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float baseY = dp(10);           // the tape line sits at yokozurashi's feet
        float pxPerMin = minutePx();

        // tape line
        canvas.drawLine(0, baseY, w, baseY, linePaint);

        int startMin = Math.max(0, (int) Math.floor(offsetMin - cx / pxPerMin) - 1);
        int endMin = Math.min(MAX_MIN, (int) Math.ceil(offsetMin + (w - cx) / pxPerMin) + 1);
        for (int m = startMin; m <= endMin; m++) {
            float x = cx + (m - offsetMin) * pxPerMin;
            boolean major = m % 5 == 0;
            boolean preset = isPreset(m);
            float tickH = preset ? dp(15) : major ? dp(12) : dp(7);
            float savedWidth = tickPaint.getStrokeWidth();
            if (m == 0) tickPaint.setStrokeWidth(dp(3)); // 0 is a thick tick
            canvas.drawLine(x, baseY, x, baseY + tickH, tickPaint);
            tickPaint.setStrokeWidth(savedWidth);
            if (major || preset) {
                float numH = preset ? dp(21) : dp(15);
                drawNumber(canvas, m, x, baseY + dp(17), numH);
                if (preset) {
                    // pseudo-bold: draw again with a slight offset (presets are markers)
                    drawNumber(canvas, m, x + dp(0.7f), baseY + dp(17), numH);
                }
            }
        }

        // the tape fades out toward both screen edges
        android.graphics.LinearGradient fadeL = new android.graphics.LinearGradient(
                0, 0, dp(36), 0, Color.WHITE, Color.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP);
        android.graphics.Paint fadePaint = new android.graphics.Paint();
        fadePaint.setShader(fadeL);
        canvas.drawRect(0, 0, dp(36), h, fadePaint);
        android.graphics.LinearGradient fadeR = new android.graphics.LinearGradient(
                w, 0, w - dp(36), 0, Color.WHITE, Color.TRANSPARENT,
                android.graphics.Shader.TileMode.CLAMP);
        fadePaint.setShader(fadeR);
        canvas.drawRect(w - dp(36), 0, w, h, fadePaint);

        // center marker (fixed): a small triangle BELOW the tape, apex up
        // (same placement as the prototype)
        float mx = cx;
        Path tri = new Path();
        tri.moveTo(mx, dp(54));
        tri.lineTo(mx - dp(6), dp(62));
        tri.lineTo(mx + dp(6), dp(62));
        tri.close();
        canvas.drawPath(tri, markerPaint);
    }

    /** Settle immediately without animation (watchdog / mode switch). */
    public void forceSettle() {
        if (dragging) return;
        scroller.forceFinished(true);
        animating = false;
        int target = Math.round(clamp(virtualMin));
        offsetMin = target;
        virtualMin = target;
        invalidate();
        if (listener != null) listener.onSettledMinutes(target);
    }

    /** Draw a non-negative integer centered at x, top at y, with the given height. */
    private void drawNumber(Canvas canvas, int value, float cx, float top, float height) {
        String s = String.valueOf(value);
        float digitW = height * (300f / 420f);
        float totalW = digitW * s.length();
        float x = cx - totalW / 2f;
        for (int i = 0; i < s.length(); i++) {
            Bitmap b = digits[s.charAt(i) - '0'];
            if (b != null) {
                android.graphics.RectF dst = new android.graphics.RectF(
                        x + i * digitW, top, x + (i + 1) * digitW, top + height);
                canvas.drawBitmap(b, null, dst, null);
            }
        }
    }

    // ---- touch: drag / fling / tap (only when interactive) ----
    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!interactive) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                virtualMin = clamp(offsetMin);
                animating = false;
                dragging = true;
                moved = false;
                downX = lastX = e.getX();
                downTime = System.currentTimeMillis();
                if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
                else velocityTracker.clear();
                velocityTracker.addMovement(e);
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!dragging) return false;
                velocityTracker.addMovement(e);
                float dx = e.getX() - lastX;
                lastX = e.getX();
                if (Math.abs(e.getX() - downX) > dp(4)) moved = true;
                virtualMin -= dx / minutePx();
                offsetMin = rubber(virtualMin);
                invalidate();
                if (listener != null) listener.onDragMinutes(Math.round(clamp(virtualMin)));
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) return false;
                dragging = false;
                velocityTracker.addMovement(e);
                velocityTracker.computeCurrentVelocity(1000);
                float vx = velocityTracker.getXVelocity();
                if (!moved && System.currentTimeMillis() - downTime < 300) {
                    // tap: glide to the tapped minute
                    float tapped = offsetMin + (e.getX() - getWidth() / 2f) / minutePx();
                    animateTo(Math.round(clamp(tapped)));
                } else if (Math.abs(vx) > dp(60)) {
                    // fling: convert px/s velocity into scroller over offset space
                    int start = Math.round(offsetMin * minutePx());
                    scroller.fling(start, 0, -(int) vx, 0, 0, (int) (MAX_MIN * minutePx()), 0, 0);
                    animating = true;
                    invalidate();
                } else {
                    animateTo(Math.round(clamp(virtualMin)));
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    private boolean animating = false;

    private void animateTo(int minutes) {
        int start = Math.round(offsetMin * minutePx());
        int target = Math.round(minutes * minutePx());
        scroller.startScroll(start, 0, target - start, 0, 260);
        animating = true;
        invalidate();
    }

    @Override public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            offsetMin = scroller.getCurrX() / minutePx();
            virtualMin = offsetMin;
            invalidate();
            if (listener != null) listener.onDragMinutes(Math.round(clamp(offsetMin)));
        } else if (animating && !dragging) {
            // fling/animation ended: snap to a whole minute and report once
            animating = false;
            int target = Math.round(offsetMin);
            if (Math.abs(offsetMin - target) > 0.001f) {
                animateTo(target);
            } else if (listener != null) {
                listener.onSettledMinutes(target);
            }
        }
    }

    /** External drag forwarding (e.g. dragging the character itself). */
    public void externalDrag(float dxPx) {
        if (!interactive) return;
        scroller.forceFinished(true);
        virtualMin -= dxPx / minutePx();
        offsetMin = rubber(virtualMin);
        invalidate();
        if (listener != null) listener.onDragMinutes(Math.round(clamp(virtualMin)));
    }

    public void externalDragEnd() {
        if (!interactive) return;
        animateTo(Math.round(clamp(virtualMin)));
    }
}
