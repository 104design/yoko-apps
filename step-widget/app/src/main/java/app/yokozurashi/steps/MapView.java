package app.yokozurashi.steps;

import android.animation.ValueAnimator;
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
 * 県の形の線は、手描きのように少し揺らして描く（wobble）。
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
    private static final float PINCH_GAIN = 2;    // ピンチの効き。2 なら指の動きの2倍の勢いで拡大・縮小する
    private static final long MOVE_MS = 350;      // 現在地ボタンで、開いたときの表示へ戻るまでの時間
    private static final float MIN_SPAN = 2.2f;   // はじめに見せる範囲の、いちばん狭い高さ（緯度の度）
    // 線の揺らし方（dp）。揺れの大きさ、揺れの波の長さ、点を打ち直す間隔
    private static final float WOBBLE_AMP = 1.3f, WOBBLE_LEN = 14, WOBBLE_STEP = 4;

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
    private final Path[] shapes = new Path[48]; // 揺らした県の形（画面の座標）。描くたびに作り直す
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
        for (int i = 0; i < shapes.length; i++) shapes[i] = new Path();
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
                // 指の動きより大きく効かせる（同じ動きで、拡大・縮小の度合いが PINCH_GAIN 乗になる）
                zoom((float) Math.pow(d.getScaleFactor(), PINCH_GAIN), d.getFocusX(), d.getFocusY());
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

    /**
     * 開いたときの表示：いまの場所をまん中に置き、ずらし終わった地点がぜんぶ入るまで引く。
     * 進むほど通った範囲が広がるので、だんだん引いていく。
     */
    public void fit() {
        if (getWidth() == 0) {
            fitted = false;
            return;
        }
        float[] home = home();
        scale = home[0];
        tx = home[1];
        ty = home[2];
        // clamp() はしない。いまの場所は日本の中にあるので、まん中に置けば日本が画面の外へ出ることはない
        fitted = true;
        invalidate();
    }

    private ValueAnimator moving;

    /** 現在地ボタン：開いたときの表示へ、なめらかに戻る。 */
    public void goHome() {
        if (getWidth() == 0) return;
        if (moving != null) moving.cancel();
        final float[] home = home();
        final float s0 = scale, x0 = tx, y0 = ty;
        // いまの場所が画面の上でまっすぐ動くよう、大きさは比で、位置は「いまの場所の画面の座標」でつなぐ
        final float px0 = nowX * s0 + x0, py0 = nowY * s0 + y0;
        final float px1 = nowX * home[0] + home[1], py1 = nowY * home[0] + home[2];
        moving = ValueAnimator.ofFloat(0, 1);
        moving.setDuration(MOVE_MS);
        moving.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (float) a.getAnimatedValue();
                scale = (float) (s0 * Math.pow(home[0] / s0, t));
                tx = px0 + (px1 - px0) * t - nowX * scale;
                ty = py0 + (py1 - py0) * t - nowY * scale;
                invalidate();
            }
        });
        moving.start();
        fitted = true;
    }

    /** 開いたときの表示の、大きさと位置 {scale, tx, ty}。 */
    private float[] home() {
        // いまの場所から、いちばん遠いずらし終わった地点までの距離（左右・上下それぞれ）
        float hx = 0, hy = 0;
        for (int i : visited) {
            hx = Math.max(hx, Math.abs(JapanMap.x(Route.LON[i]) - nowX));
            hy = Math.max(hy, Math.abs(JapanMap.y(Route.LAT[i]) - nowY));
        }
        float w = Math.max(hx * 2, MIN_SPAN * 0.7f), h = Math.max(hy * 2, MIN_SPAN);
        float availW = getWidth() - 48 * dp, availH = getHeight() - bottomInset - 64 * dp;
        float s = Math.max(minScale(), Math.min(maxScale(), Math.min(availW / w, availH / h)));
        return new float[]{s, getWidth() / 2f - nowX * s, (getHeight() - bottomInset) / 2f - nowY * s};
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
        if (moving != null && e.getActionMasked() == MotionEvent.ACTION_DOWN) moving.cancel();
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

        // 都道府県の形。画面の座標で、線を揺らして作り直す
        for (int code = 1; code <= 47; code++) buildShape(code);
        fill.setStyle(Paint.Style.FILL);
        for (int code = 1; code <= 47; code++) {
            fill.setColor(code == currentPref ? ACCENT : LAND);
            c.drawPath(shapes[code], fill);
        }
        line.setColor(0xFF3A3A3A);
        line.setStrokeWidth(0.8f * dp);
        for (int code = 1; code <= 47; code++) {
            if (code != currentPref) c.drawPath(shapes[code], line);
        }
        if (currentPref > 0) {
            line.setColor(INK);
            line.setStrokeWidth(1.6f * dp);
            c.drawPath(shapes[currentPref], line);
        }

        c.save();
        c.concat(m);
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

    /**
     * 県の形を、画面の座標で揺らして作る。
     * 点を WOBBLE_STEP ごとに打ち直し、「画面の上の位置で決まるずれ」でずらす。ずれは位置だけで決まるので、
     * となりの県と共有している境は同じ線になり、指で動かしても形は変わらない（拡大・縮小のあいだは揺れが変わる）。
     * 画面の外の輪は飛ばし、画面の外の辺は打ち直さない。
     */
    private void buildShape(int code) {
        Path path = shapes[code];
        path.reset();
        float[][] rings = map.rings[code];
        if (rings == null) return;
        float margin = 40 * dp, w = getWidth(), h = getHeight();
        float step = WOBBLE_STEP * dp;
        for (int r = 0; r < rings.length; r++) {
            float[] b = map.bounds[code][r];
            if (b[2] * scale + tx < -margin || b[0] * scale + tx > w + margin
                    || b[3] * scale + ty < -margin || b[1] * scale + ty > h + margin) continue;
            float[] p = rings[r];
            int n = p.length / 2;
            float ax = p[0] * scale, ay = p[1] * scale;
            wobbleTo(path, ax, ay, true);
            for (int i = 1; i < n; i++) {
                float bx = p[i * 2] * scale, by = p[i * 2 + 1] * scale;
                boolean visible = Math.max(ax, bx) + tx > -margin && Math.min(ax, bx) + tx < w + margin
                        && Math.max(ay, by) + ty > -margin && Math.min(ay, by) + ty < h + margin;
                int k = visible ? Math.max(1, (int) Math.ceil(Math.hypot(bx - ax, by - ay) / step)) : 1;
                for (int j = 1; j <= k; j++) {
                    float t = (float) j / k;
                    wobbleTo(path, ax + (bx - ax) * t, ay + (by - ay) * t, false);
                }
                ax = bx;
                ay = by;
            }
            path.close();
        }
    }

    /** (x, y)（地図の座標 × scale）を揺らして、画面の座標で path に足す。 */
    private void wobbleTo(Path path, float x, float y, boolean first) {
        float len = WOBBLE_LEN * dp, amp = WOBBLE_AMP * dp;
        float dx = amp * noise(x / len, y / len, 1), dy = amp * noise(x / len, y / len, 2);
        if (first) path.moveTo(x + dx + tx, y + dy + ty);
        else path.lineTo(x + dx + tx, y + dy + ty);
    }

    /** なめらかな -1〜1 のゆらぎ（格子の点ごとの乱数を、なめらかにつなぐ）。 */
    private static float noise(float x, float y, int seed) {
        int i = (int) Math.floor(x), j = (int) Math.floor(y);
        float fx = x - i, fy = y - j;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        float a = hash(i, j, seed) * (1 - fx) + hash(i + 1, j, seed) * fx;
        float b = hash(i, j + 1, seed) * (1 - fx) + hash(i + 1, j + 1, seed) * fx;
        return a * (1 - fy) + b * fy;
    }

    private static float hash(int i, int j, int seed) {
        int x = i * 374761393 + j * 668265263 + seed * 2147483647;
        x = (x ^ (x >>> 13)) * 1274126177;
        x ^= x >>> 16;
        return (x & 0xffff) / 32767.5f - 1;
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
