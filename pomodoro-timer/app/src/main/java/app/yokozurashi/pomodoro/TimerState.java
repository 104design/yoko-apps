package app.yokozurashi.pomodoro;

import android.content.Context;
import android.content.SharedPreferences;

public class TimerState {
    public static final String PREF = "timer_state";
    public String kind;        // "pomodoro" or "timer"
    public String mode;        // pomodoro: focus/short/long
    public int cycles;         // completed focus sessions in the current 4-set
    public boolean running;
    public long endAtMs;
    public long remainingMs;
    public long timerDurationMs;
    public boolean done;       // timer finished, or whole pomodoro set finished
    public boolean autoAdvance = true; // pomodoro: start the next phase automatically
    public String lastEvent = "";      // focus_end / break_end / set_end / timer_end
    public boolean timerAlertPending = false; // plain timer finished; alert loops until dismissed

    public static long pomodoroDurationMs(String mode) {
        if ("short".equals(mode)) return 5L * 60 * 1000;
        if ("long".equals(mode)) return 15L * 60 * 1000;
        return 25L * 60 * 1000;
    }

    public long totalMs() {
        return "pomodoro".equals(kind) ? pomodoroDurationMs(mode) : timerDurationMs;
    }

    public long remainingNow() {
        if (done) return 0;
        if (running) return Math.max(0, endAtMs - System.currentTimeMillis());
        return remainingMs;
    }

    public static TimerState load(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        TimerState s = new TimerState();
        s.kind = p.getString("kind", "pomodoro");
        s.mode = p.getString("mode", "focus");
        s.cycles = p.getInt("cycles", 0);
        s.running = p.getBoolean("running", false);
        s.endAtMs = p.getLong("endAtMs", 0);
        s.remainingMs = p.getLong("remainingMs", 25L * 60 * 1000);
        s.timerDurationMs = p.getLong("timerDurationMs", 5L * 60 * 1000);
        s.done = p.getBoolean("done", false);
        s.autoAdvance = p.getBoolean("autoAdvance", true);
        s.timerAlertPending = p.getBoolean("timerAlertPending", false);
        return s;
    }

    public void save(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        p.edit()
            .putString("kind", kind)
            .putString("mode", mode)
            .putInt("cycles", cycles)
            .putBoolean("running", running)
            .putLong("endAtMs", endAtMs)
            .putLong("remainingMs", remainingMs)
            .putLong("timerDurationMs", timerDurationMs)
            .putBoolean("done", done)
            .putBoolean("autoAdvance", autoAdvance)
            .putBoolean("timerAlertPending", timerAlertPending)
            .commit();
    }

    public void start() {
        timerAlertPending = false;
        if (done) {
            // starting again after a finished set begins a NEW set: the red
            // tomatoes stay until this moment, then go back to green
            if ("pomodoro".equals(kind) && "focus".equals(mode) && cycles >= 4) cycles = 0;
            done = false;
            remainingMs = totalMs();
        }
        if (remainingMs <= 0) remainingMs = totalMs();
        running = true;
        endAtMs = System.currentTimeMillis() + remainingMs;
    }

    public void pause() {
        remainingMs = remainingNow();
        running = false;
        endAtMs = 0;
    }

    /** Reset just the current phase back to its full length, stopped. */
    public void resetPhase() {
        running = false;
        endAtMs = 0;
        done = false;
        remainingMs = totalMs();
    }

    /** End everything: back to a fresh focus of set 1 (pomodoro) or full timer. */
    public void endAll() {
        running = false;
        endAtMs = 0;
        done = false;
        timerAlertPending = false;
        if ("pomodoro".equals(kind)) { mode = "focus"; cycles = 0; }
        remainingMs = totalMs();
    }

    /**
     * The current phase just finished (or was skipped).
     * Pomodoro: auto-advance through the 4-set flow and auto-start the next phase.
     * Returns a Japanese transition message, or "" if nothing changed.
     */
    public String advancePhase() {
        if (!"pomodoro".equals(kind)) {
            running = false;
            endAtMs = 0;
            remainingMs = 0;
            done = true;
            lastEvent = "timer_end";
            timerAlertPending = true;
            return "⏰ 時間です！";
        }
        String msg;
        if ("focus".equals(mode)) {
            lastEvent = "focus_end";
            cycles = Math.min(4, cycles + 1);
            mode = "short";
            if (cycles >= 4) {
                msg = "集中時間が終了しました！🎉\n4セット目の集中が終わりました。最後の休憩です。";
            } else {
                msg = "集中時間が終了しました！🎉\nよくやりました！これで休憩時間です。";
            }
        } else if ("short".equals(mode)) {
            if (cycles >= 4) {
                // (focus+break) x4 done -> the whole set completes here and
                // stops. cycles stays 4 so the red tomatoes remain until a
                // new set starts (spec change 2026-11-13).
                lastEvent = "set_end";
                mode = "focus";
                running = false;
                endAtMs = 0;
                done = true;
                remainingMs = totalMs();
                return "1セット完了しました！🎉\nおつかれさまでした。";
            }
            lastEvent = "break_end";
            mode = "focus";
            msg = autoAdvance
                    ? "休憩が終わりました。\n次の集中をはじめます。"
                    : "休憩が終わりました。\n次の集中の準備ができました。";
        } else { // manual long break finished -> back to focus, stopped
            lastEvent = "break_end";
            mode = "focus";
            running = false;
            endAtMs = 0;
            done = false;
            remainingMs = totalMs();
            return "長い休憩が終わりました。";
        }
        // set up the next phase; auto-starts only when autoAdvance is on
        done = false;
        remainingMs = totalMs();
        if (autoAdvance) {
            running = true;
            endAtMs = System.currentTimeMillis() + remainingMs;
        } else {
            running = false;
            endAtMs = 0;
        }
        return msg;
    }

    /** Elapsed minutes within the current pomodoro set (or within the long
     *  break): the position of the pomodoro tape. Shared by app and widget. */
    public float pomodoroElapsedMin() {
        if ("long".equals(mode)) {
            return (totalMs() - remainingNow()) / 60000f;
        }
        long focus = pomodoroDurationMs("focus");
        long brk = pomodoroDurationMs("short");
        if (done) return (4 * (focus + brk)) / 60000f;
        // cycles counts completed focus phases; in a break it already
        // includes the focus that just ended
        int phaseIndex = "focus".equals(mode) ? cycles * 2 : cycles * 2 - 1;
        float elapsed = 0;
        for (int i = 0; i < phaseIndex; i++) {
            elapsed += (i % 2 == 0 ? focus : brk) / 60000f;
        }
        elapsed += (totalMs() - remainingNow()) / 60000f;
        return elapsed;
    }

    public static String format(long ms) {
        long totalSec = (long) Math.ceil(ms / 1000.0);
        long m = totalSec / 60;
        long sec = totalSec % 60;
        return String.format("%02d:%02d", m, sec);
    }
}
