package app.yokozurashi.steps;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

/**
 * 歩数センサー（TYPE_STEP_COUNTER）を1回だけ読む。
 * センサーは端末が数えつづけているので、常駐せず、読みたいときに値を1つ受け取ってすぐやめる。
 */
public class StepReader {
    public static final int OK = 0, NO_SENSOR = 1, NO_PERMISSION = 2;

    private static final long TIMEOUT = 4000; // 値が来ないときにあきらめるまで（ms）

    public static int status(Context ctx) {
        if (sensor(ctx) == null) return NO_SENSOR;
        if (Build.VERSION.SDK_INT >= 29 && ctx.checkSelfPermission(
                Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            return NO_PERMISSION;
        }
        return OK;
    }

    private static Sensor sensor(Context ctx) {
        SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        return sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
    }

    /**
     * 歩数を読んで帳簿に足し、ウィジェットを描き直してから done を呼ぶ。
     * 読めなかったとき（許可がない、値が来ない）も、描き直しと done は行う。done はメインスレッドで呼ばれる。
     */
    public static void read(Context context, final Runnable done) {
        final Context ctx = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable finish = new Runnable() {
            @Override
            public void run() {
                StepWidgetProvider.updateAll(ctx);
                if (done != null) done.run();
            }
        };
        if (status(ctx) != OK) {
            handler.post(finish);
            return;
        }
        final SensorManager sm = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        final boolean[] finished = {false};
        final SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (finished[0]) return;
                finished[0] = true;
                sm.unregisterListener(this);
                handler.removeCallbacksAndMessages(null);
                StepStore.apply(ctx, (long) event.values[0]);
                finish.run();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };
        boolean registered = sm.registerListener(listener, sensor(ctx),
                SensorManager.SENSOR_DELAY_NORMAL, handler);
        if (!registered) {
            handler.post(finish);
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (finished[0]) return;
                finished[0] = true;
                sm.unregisterListener(listener);
                finish.run();
            }
        }, TIMEOUT);
    }
}
