package app.yokozurashi.steps;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 日本地図。いまいる都道府県を塗り、通った道すじと、ずらし終わった地点のよこずらしのピンを出す。
 * 指で動かす・広げる・ダブルタップで拡大できる。広く見ているときは、近いピンをまとめて数を添える。
 * ピンをタップすると Listener に知らせる。まとめたピンをタップすると、そこへ寄って拡大する。
 */
public class MapView extends View {
    public interface Listener {
        /** 地点（Route の番号）のピンがタップされた。 */
        void onPick(int index);

        /** ピンのないところがタップされた。 */
        void onPickNone();
    }

    private static final int INK = 0xFF141414;
    private static final int ACCENT = 0xFFEE7948;
    private static final int ACCENT_DARK = 0xFFC4501F;
    private static final int SEA = 0xFFF3F1EC;
    private static final int LAND = 0xFFFFFFFF;

    private static final float PIN_H = 17, PIN_PICKED_H = 26, PIN_NOW_H = 28; // ピンの高さ（dp）
    private static final float CLUSTER_CELL = 34; // この大きさ（dp）のます目に入るピンを1つにまとめる
    private static final float MIN_SPAN = 2.2f;   // はじめに見せる範囲の、いちばん狭い高さ（緯度の度）

    private final float dp;
    private final JapanMap map;
    private final Bitmap pin;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);

    // 地図の座標から画面（px）への変換：画面 = 地図 × scale + (tx, ty)
    private float scale = 1, tx, ty;
    private boolean fitted;
    private float bottomInset;

    private int currentPref;
    private float nowX, nowY;                // いまの場所（地図の座標）
    private int[] visited = new int[0];      // ずらし終わった地点（Route の番号）
    private final Path route = new Path();   // 通った道すじ（地図の座標）
    private int picked = -1;
    private Listener listener;

    /** 描いたピン（タップの当たり判定に使う）。 */
    private static class Cluster {
        float x, y;       // 画面の座標（ピンの足もと）
        int count, index; // まとめた数と、代表の地点
    }

    private final List<Cluster> clusters = new ArrayList<>();
    private final ScaleGestureDetector scaler;
    private final GestureDetector gestures;

    public MapView(Context ctx) {
        super(ctx);
        dp = ctx.getResources().getDisplayMetrics().density;
        map = JapanMap.load(ctx.getResources());
        Bitmap frame = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.yoko_frame_1);
        // コマの余白を落とす（tools/make_frames.py の絵で、よこずらしの本体がある範囲）
        int fw = frame.getWidth(), fh = frame.getHeight();
        pin = Bitmap.createBitmap(frame, 0, fh * 96 / 285, Math.min(fw, fw * 181 / 285), fh * 122 / 285);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        badge.setStyle(Paint.Style.FILL);
        Typeface black = ctx.getResources().getFont(R.font.zen_maru_black);
        badgeText.setTypeface(black);
        badgeText.setTextAlign(Paint.Align.CENTER);
        badgeText.setColor(INK);
        badgeText.setTextSize(8 * dp);

        scaler = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoom(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        gestures = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                tx -= dx;
                ty -= dy;
                clamp();
                invalidate();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                zoom(2, e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                tap(e.getX(), e.getY());
                return true;
            }
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** 下に重なるカードの高さ（px）。はじめに見せる範囲は、その上に収める。 */
    public void setBottomInset(float px) {
        bottomInset = px;
    }

    /**
     * 旅のようすを渡す。
     * @param index    いまいる地点（Route の番号）
     * @param progress その区間の進み具合（0〜1）
     * @param visited  ずらし終わった地点（Route の番号）
     * @param reached  道すじをどこまで通ったか（Route の番号。周回していれば最後まで）
     */
    public void setJourney(int index, float progress, String pref, int[] visited, int reached) {
        currentPref = JapanMap.code(pref);
        this.visited = visited;
        int next = Math.min(index + 1, Route.LAT.length - 1);
        float x0 = JapanMap.x(Route.LON[index]), y0 = JapanMap.y(Route.LAT[index]);
        float x1 = JapanMap.x(Route.LON[next]), y1 = JapanMap.y(Route.LAT[next]);
        nowX = x0 + (x1 - x0) * progress;
        nowY = y0 + (y1 - y0) * progress;
        route.reset();
        for (int i = 0; i <= reached && i < Route.LAT.length; i++) {
            float x = JapanMap.x(Route.LON[i]), y = JapanMap.y(Route.LAT[i]);
            if (i == 0) route.moveTo(x, y);
            else route.lineTo(x, y);
        }
        if (reached == index) route.lineTo(nowX, nowY);
        invalidate();
    }

    public void setPicked(int index) {
        picked = index;
        invalidate();
    }

    /** はじめに見せる範囲：ずらし終わった地点といまの場所がぜんぶ入る範囲。進むほど引いていく。 */
    public void fit() {
        if (getWidth() == 0) {
            fitted = false;
            return;
        }
        float minX = nowX, maxX = nowX, minY = nowY, maxY = nowY;
        for (int i : visited) {
            float x = JapanMap.x(Route.LON[i]), y = JapanMap.y(Route.LAT[i]);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        float w = Math.max(maxX - minX, MIN_SPAN * 0.7f), h = Math.max(maxY - minY, MIN_SPAN);
        float availW = getWidth() - 48 * dp, availH = getHeight() - bottomInset - 64 * dp;
        scale = Math.min(availW / w, availH / h);
        float cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        tx = getWidth() / 2f - cx * scale;
        ty = (getHeight() - bottomInset) / 2f - cy * scale;
        clamp();
        fitted = true;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!fitted) fit();
    }

    private void zoom(float f, float fx, float fy) {
        float next = Math.max(minScale(), Math.min(maxScale(), scale * f));
        f = next / scale;
        tx = fx - (fx - tx) * f;
        ty = fy - (fy - ty) * f;
        scale = next;
        clamp();
        invalidate();
    }

    /** 日本全体が収まる大きさ。これより小さくはしない。 */
    private float minScale() {
        return Math.min(getWidth() / 21f, getHeight() / 24f) * 0.9f;
    }

    /** 画面の幅に、経度で0.5度ぶんほどが入る大きさ。これより大きくはしない。 */
    private float maxScale() {
        return getWidth() / 0.4f;
    }

    /** 日本が画面の外へ行ってしまわないようにする。 */
    private void clamp() {
        if (getWidth() == 0) return;
        float left = JapanMap.x(122.5) * scale, right = JapanMap.x(146) * scale;
        float top = JapanMap.y(45.7) * scale, bottom = JapanMap.y(24) * scale;
        float margin = getWidth() / 2f;
        tx = Math.min(margin - left, Math.max(getWidth() - margin - right, tx));
        ty = Math.min(getHeight() / 2f - top, Math.max(getHeight() / 2f - bottom, ty));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        gestures.onTouchEvent(e);
        return true;
    }

    private void tap(float x, float y) {
        Cluster hit = null;
        float best = 22 * dp;
        for (Cluster c : clusters) {
            float d = (float) Math.hypot(c.x - x, c.y - PIN_H * dp / 2 - y);
            if (d < best) {
                best = d;
                hit = c;
            }
        }
        if (hit == null) {
            if (listener != null) listener.onPickNone();
        } else if (hit.count > 1) {
            zoom(2.5f, hit.x, hit.y); // まとめたピンは、そこへ寄って拡大する
        } else if (listener != null) {
            listener.onPick(hit.index);
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(SEA);
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate(tx, ty);

        // 都道府県の形。線の太さは、拡大しても画面の上で同じになるように割り戻す
        c.save();
        c.concat(m);
        fill.setStyle(Paint.Style.FILL);
        for (int code = 1; code <= 47; code++) {
            Path p = map.prefs[code];
            if (p == null) continue;
            fill.setColor(code == currentPref ? ACCENT : LAND);
            c.drawPath(p, fill);
        }
        line.setColor(0xFF3A3A3A);
        line.setStrokeWidth(0.8f * dp / scale);
        for (int code = 1; code <= 47; code++) {
            if (map.prefs[code] != null && code != currentPref) c.drawPath(map.prefs[code], line);
        }
        if (currentPref > 0 && map.prefs[currentPref] != null) {
            line.setColor(INK);
            line.setStrokeWidth(1.6f * dp / scale);
            c.drawPath(map.prefs[currentPref], line);
        }
        // 通った道すじ
        line.setColor(ACCENT_DARK);
        line.setStrokeWidth(1.4f * dp / scale);
        c.drawPath(route, line);
        c.restore();

        // ずらし終わった地点のピン。近いものはまとめる
        clusters.clear();
        Map<Long, Cluster> cells = new HashMap<>();
        float cell = CLUSTER_CELL * dp;
        for (int i : visited) {
            float x = JapanMap.x(Route.LON[i]) * scale + tx, y = JapanMap.y(Route.LAT[i]) * scale + ty;
            long key = ((long) Math.floor(x / cell) << 32) ^ ((long) Math.floor(y / cell) & 0xffffffffL);
            Cluster k = cells.get(key);
            if (k == null) {
                k = new Cluster();
                k.index = i;
                cells.put(key, k);
                clusters.add(k);
            }
            k.x += x;
            k.y += y;
            k.count++;
            if (i == picked) k.index = i;
        }
        for (Cluster k : clusters) {
            k.x /= k.count;
            k.y /= k.count;
            if (k.x < -cell || k.x > getWidth() + cell || k.y < -cell || k.y > getHeight() + cell) continue;
            boolean isPicked = k.count == 1 && k.index == picked;
            drawPin(c, k.x, k.y, isPicked ? PIN_PICKED_H : PIN_H);
            if (isPicked) {
                line.setColor(ACCENT_DARK);
                line.setStrokeWidth(1.6f * dp);
                c.drawCircle(k.x, k.y, 4 * dp, line);
            }
            if (k.count > 1) drawBadge(c, k.x, k.y, k.count);
        }

        // いまの場所
        drawPin(c, nowX * scale + tx, nowY * scale + ty, PIN_NOW_H);
    }

    private void drawPin(Canvas c, float x, float y, float hDp) {
        float h = hDp * dp, w = h * pin.getWidth() / pin.getHeight();
        c.drawBitmap(pin, null, new RectF(x - w / 2, y - h, x + w / 2, y), img);
    }

    private void drawBadge(Canvas c, float x, float y, int count) {
        float h = PIN_H * dp, w = h * pin.getWidth() / pin.getHeight();
        float bx = x + w / 2 - dp, by = y - h + dp, r = 7 * dp;
        badge.setColor(ACCENT);
        c.drawCircle(bx, by, r, badge);
        line.setColor(INK);
        line.setStrokeWidth(dp);
        c.drawCircle(bx, by, r, line);
        Paint.FontMetrics fm = badgeText.getFontMetrics();
        c.drawText(String.valueOf(count), bx, by - (fm.ascent + fm.descent) / 2, badgeText);
    }
}
