package app.yokozurashi.pomodoro;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Vibrator;

/**
 * Phase-end feedback. Each event has its own sound (user-supplied mp3s).
 * When the phone is in silent or vibrate mode, no sound is played and the
 * vibration is longer instead, so the event is still noticeable.
 */
public class SoundHelper {
    public static void playEvent(Context ctx, String event) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int ringer = am != null ? am.getRingerMode() : AudioManager.RINGER_MODE_NORMAL;
        boolean silent = ringer == AudioManager.RINGER_MODE_SILENT
                || ringer == AudioManager.RINGER_MODE_VIBRATE;
        if (silent) {
            vibrate(ctx,
                    new long[]{0, 600, 250, 600, 250, 600},
                    new int[]{0, 255, 0, 255, 0, 255});
            return;
        }
        vibrate(ctx,
                new long[]{0, 300, 150, 300},
                new int[]{0, 255, 0, 255});
        int res;
        if ("break_end".equals(event)) {
            res = R.raw.sound_break;
        } else if ("set_end".equals(event) || "timer_end".equals(event)) {
            res = R.raw.sound_set;
        } else {
            res = R.raw.sound_focus;
        }
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            android.content.res.AssetFileDescriptor afd =
                    ctx.getResources().openRawResourceFd(res);
            if (afd == null) { mp.release(); return; }
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setOnCompletionListener(p -> p.release());
            mp.setOnErrorListener((p, w, e) -> { p.release(); return true; });
            mp.prepare();
            mp.start();
        } catch (Exception ignored) {}
    }

    // ---- plain-timer finish alert: loops until the dialog is dismissed ----
    private static MediaPlayer loopPlayer;

    public static synchronized void startTimerAlert(Context ctx) {
        AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        int ringer = am != null ? am.getRingerMode() : AudioManager.RINGER_MODE_NORMAL;
        if (ringer == AudioManager.RINGER_MODE_SILENT
                || ringer == AudioManager.RINGER_MODE_VIBRATE) {
            vibrate(ctx,
                    new long[]{0, 600, 250, 600, 250, 600},
                    new int[]{0, 255, 0, 255, 0, 255});
            return;
        }
        vibrate(ctx,
                new long[]{0, 300, 150, 300},
                new int[]{0, 255, 0, 255});
        if (loopPlayer != null) return; // already looping
        try {
            MediaPlayer mp = new MediaPlayer();
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            android.content.res.AssetFileDescriptor afd =
                    ctx.getResources().openRawResourceFd(R.raw.sound_focus);
            if (afd == null) { mp.release(); return; }
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.setLooping(true);
            mp.prepare();
            mp.start();
            loopPlayer = mp;
        } catch (Exception ignored) {}
    }

    public static synchronized void stopTimerAlert() {
        if (loopPlayer != null) {
            try { loopPlayer.stop(); } catch (Exception ignored) {}
            try { loopPlayer.release(); } catch (Exception ignored) {}
            loopPlayer = null;
        }
    }

    public static synchronized boolean isTimerAlertPlaying() {
        return loopPlayer != null;
    }

    private static void vibrate(Context ctx, long[] timings, int[] amplitudes) {
        try {
            Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            // Explicit full-amplitude waveform: the strongest, most reliable
            // vibration the API offers (some OEM builds quietly ignore the
            // legacy pattern call or default-amplitude effects).
            try {
                v.vibrate(android.os.VibrationEffect.createWaveform(timings, amplitudes, -1));
            } catch (Exception e) {
                try {
                    v.vibrate(android.os.VibrationEffect.createWaveform(timings, -1));
                } catch (Exception e2) {
                    v.vibrate(timings, -1);
                }
            }
        } catch (Exception ignored) {}
    }
}
