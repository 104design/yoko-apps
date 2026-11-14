package app.yokozurashi.pomodoro;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.text.InputType;
import android.graphics.drawable.AnimationDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TimerState state;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView bannerText, modeLabel, guideLine;
    private LinearLayout dotsRow;
    private final ImageView[] dotViews = new ImageView[4];
    private LinearLayout timeRow;
    private String lastTimeStr = "";
    private Button tabPomodoro, tabTimer;
    private Button longBreakBtn;
    private final java.util.List<Button> presetBtns = new java.util.ArrayList<>();
    private TapeView tapeView;
    private PomoTapeView pomoTape;

    private String guideOverride = null;
    private long guideOverrideUntil = 0;
    private int colonIndex = 2;
    static volatile boolean foreground = false;
    private android.widget.FrameLayout charFrame;
    private boolean tapeActive = false;      // user is dragging/flinging the tape
    private long overrideDisplayMs = -1;     // digits shown while dragging (tape / digit drag)
    private final Runnable tapeWatchdog = () -> {
        if (tapeActive && tapeView != null) tapeView.forceSettle();
    };
    private android.widget.Switch autoSwitch;
    private ImageView charImage;
    private ImageView charImage2; // second character layer, used by the shift transition
    private ImageButton skipBtn, toggleBtn, resetBtn;
    private int charState = -1; // 0 still focus, 1 still break, 2 anim focus, 3 anim break
    private long bannerUntil = 0;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (state.running && state.remainingNow() <= 0) {
                long beforeEndAt = state.endAtMs;
                String msg = state.advancePhase();
                state.save(MainActivity.this);
                AlarmHelper.cancel(MainActivity.this);
                if (state.running) AlarmHelper.schedule(MainActivity.this, state.endAtMs);
                if (beforeEndAt != state.endAtMs) {
                    notifyFinishSound();
                    showBanner(msg);
                }
                TimerWidgetProvider.updateAll(MainActivity.this);
            }
            if (bannerText.getVisibility() == View.VISIBLE
                    && System.currentTimeMillis() > bannerUntil) {
                bannerText.setVisibility(View.INVISIBLE);
            }
            render();
            handler.postDelayed(this, 200);
        }
    };

    private void notifyFinishSound() {
        if ("timer_end".equals(state.lastEvent)) {
            SoundHelper.startTimerAlert(this);
            showTimerDoneDialog();
        } else {
            SoundHelper.playEvent(this, state.lastEvent);
        }
    }

    private boolean timerDialogShowing = false;

    /** Plain-timer finish: a dialog the user must explicitly close; the
     *  alert sound loops until then. */
    private void showTimerDoneDialog() {
        if (timerDialogShowing || isFinishing()) return;
        timerDialogShowing = true;
        new android.app.AlertDialog.Builder(this)
                .setTitle("⏰ 時間です！")
                .setMessage("タイマーが終了しました。")
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> {
                    SoundHelper.stopTimerAlert();
                    state.timerAlertPending = false;
                    state.save(MainActivity.this);
                    timerDialogShowing = false;
                })
                .show();
    }

    private void confirm(String title, String message, Runnable action) {
        new android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("はい", (d, w) -> action.run())
                .setNegativeButton("やめる", null)
                .show();
    }

    private void showBanner(String msg) {
        if (msg == null || msg.length() == 0) return;
        bannerText.setText(msg);
        bannerText.setVisibility(View.VISIBLE);
        bannerUntil = System.currentTimeMillis() + 6000;
    }

    private Button textButton(String text, float sizeSp) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(sizeSp);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        return r;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        state = TimerState.load(this);
        KillWatchService.start(this);
        NotifyHelper.ensureChannel(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(20), dp(24), dp(16));
        root.setBackgroundColor(Color.WHITE);

        // The banner keeps a fixed height and only fades in/out (INVISIBLE),
        // so the rest of the UI never shifts when a message appears.
        bannerText = new TextView(this);
        bannerText.setTextSize(14);
        bannerText.setPadding(dp(14), dp(10), dp(14), dp(10));
        bannerText.setGravity(Gravity.CENTER_VERTICAL);
        bannerText.setBackgroundColor(Color.rgb(245, 245, 247));
        bannerText.setVisibility(View.INVISIBLE);
        LinearLayout.LayoutParams bannerLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        bannerLp.setMargins(0, 0, 0, dp(12));
        root.addView(bannerText, bannerLp);

        LinearLayout tabs = row();
        tabPomodoro = textButton("ポモドーロ", 14);
        tabTimer = textButton("タイマー", 14);
        tabs.addView(tabPomodoro);
        tabs.addView(tabTimer);
        root.addView(tabs);

        modeLabel = new TextView(this);
        modeLabel.setTextSize(16);
        modeLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mlLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        mlLp.setMargins(0, dp(24), 0, 0);
        root.addView(modeLabel, mlLp);

        timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(92));
        timeLp.setMargins(0, dp(6), 0, dp(2));
        root.addView(timeRow, timeLp);

        dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < 4; i++) {
            ImageView iv = new ImageView(this);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(30), dp(30));
            dlp.setMargins(dp(3), dp(2), dp(3), 0);
            iv.setLayoutParams(dlp);
            dotViews[i] = iv;
            dotsRow.addView(iv);
        }
        root.addView(dotsRow);

        // one fixed-height guidance line; its text changes with state but
        // the layout never moves
        guideLine = new TextView(this);
        guideLine.setTextSize(12);
        guideLine.setGravity(Gravity.CENTER);
        guideLine.setTextColor(Color.GRAY);
        root.addView(guideLine, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));

        // character area: two overlapping layers in a frame, so the shift
        // transition can show yokozurashi and the coffee mug at once
        charFrame = new android.widget.FrameLayout(this);
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(dp(230), dp(230));
        frameLp.setMargins(0, dp(8), 0, dp(4));
        charImage = new ImageView(this);
        charImage.setImageResource(R.drawable.character_focus);
        charImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        charImage2 = new ImageView(this);
        charImage2.setScaleType(ImageView.ScaleType.FIT_CENTER);
        charImage2.setVisibility(View.GONE);
        android.widget.FrameLayout.LayoutParams match = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        charFrame.addView(charImage, match);
        charFrame.addView(charImage2, match);
        root.addView(charFrame, frameLp);

        // the time tape (timer mode only): one ruler under the character's
        // feet, used both to set the time and to show it flowing to 0
        tapeView = new TapeView(this);
        tapeView.setListener(new TapeView.Listener() {
            @Override public void onDragMinutes(int minutes) {
                tapeActive = true;
                overrideDisplayMs = minutes * 60L * 1000;
                renderDigits(TimerState.format(overrideDisplayMs));
                // watchdog: if the settle callback is ever lost (the view
                // was hidden mid-glide, etc.), force it shortly after the
                // last drag update so the display can never freeze
                handler.removeCallbacks(tapeWatchdog);
                handler.postDelayed(tapeWatchdog, 600);
            }
            @Override public void onSettledMinutes(int minutes) {
                tapeActive = false;
                overrideDisplayMs = -1;
                handler.removeCallbacks(tapeWatchdog);
                if (locked()) { syncInputs(); return; }
                applyTimerSetting(minutes * 60L * 1000);
            }
        });
        tapeView.setOnTouchListener((v, e) -> {
            if (locked() && e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                showGuide("動作中はずらせません。一時停止すると変えられます", 3000);
            }
            return false; // TapeView's own onTouchEvent does the work
        });
        // pulled up so the tape line sits right at the character's feet
        // (the character appears to walk on the tape)
        LinearLayout.LayoutParams tapeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(66));
        tapeLp.setMargins(0, dp(-92), 0, 0);
        root.addView(tapeView, tapeLp);
        // the pomodoro tape sits in the same slot (only one is visible)
        pomoTape = new PomoTapeView(this);
        pomoTape.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                showGuide("ポモドーロの帯はずらせません。SKIPで次へ進みます", 3000);
            }
            return true;
        });
        LinearLayout.LayoutParams pomoTapeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        pomoTapeLp.setMargins(0, dp(-98), 0, 0);
        root.addView(pomoTape, pomoTapeLp);
        // dragging the character sideways drags the tape too (timer mode)
        charFrame.setOnTouchListener((v, e) -> {
            if (!"timer".equals(state.kind)) return false;
            if (state.running) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    showGuide("動作中はずらせません。一時停止すると変えられます", 3000);
                }
                return false;
            }
            tapeView.onTouchEvent(e);
            return true;
        });
        // dragging the big digits sideways changes minutes / seconds
        timeRow.setOnTouchListener(new DigitDragListener());

        // icon controls: skip / play-pause / reset (user-drawn icons; no END)
        LinearLayout controls = row();
        skipBtn = iconButton(R.drawable.icon_skip);
        toggleBtn = iconButton(R.drawable.icon_play);
        resetBtn = iconButton(R.drawable.icon_reset);
        controls.addView(skipBtn);
        controls.addView(toggleBtn);
        controls.addView(resetBtn);
        root.addView(controls);

        // "long break" button: only visible at set completion, but its
        // space is always reserved so nothing else moves
        longBreakBtn = textButton("長い休憩 15分", 14);
        longBreakBtn.setVisibility(View.INVISIBLE);
        root.addView(longBreakBtn);

        // auto-advance on/off, pinned at the bottom of the screen (user request)
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout autoRow = row();
        TextView autoLabel = new TextView(this);
        autoLabel.setText("自動進行");
        autoLabel.setTextSize(14);
        autoSwitch = new android.widget.Switch(this);
        autoSwitch.setChecked(state.autoAdvance);
        autoSwitch.setOnCheckedChangeListener((btn, checked) -> {
            state.autoAdvance = checked;
            state.save(MainActivity.this);
        });
        autoRow.addView(autoLabel);
        autoRow.addView(autoSwitch);
        root.addView(autoRow);
        TextView autoNote = new TextView(this);
        autoNote.setText("オフにすると、フェーズが終わるたびに止まります");
        autoNote.setTextSize(11);
        autoNote.setGravity(Gravity.CENTER);
        autoNote.setTextColor(Color.GRAY);
        root.addView(autoNote);

        setContentView(root);

        tabPomodoro.setOnClickListener(v -> switchKind("pomodoro"));
        tabTimer.setOnClickListener(v -> switchKind("timer"));
        longBreakBtn.setOnClickListener(v -> {
            if (!"pomodoro".equals(state.kind) || state.running) return;
            // start the long break running (from set completion)
            state.mode = "long";
            state.cycles = 0;
            state.done = false;
            state.remainingMs = state.totalMs();
            state.start();
            AlarmHelper.schedule(this, state.endAtMs);
            KillWatchService.start(this);
            persist();
        });

        toggleBtn.setOnClickListener(v -> {
            if (state.running) {
                state.pause();
                AlarmHelper.cancel(this);
            } else {
                // if a tape glide never settled, apply its value first
                if (tapeActive && tapeView != null) tapeView.forceSettle();
                // timer with no time set does nothing
                if ("timer".equals(state.kind) && state.totalMs() <= 0) return;
                state.start();
                AlarmHelper.schedule(this, state.endAtMs);
                KillWatchService.start(this);
            }
            persist();
        });
        skipBtn.setOnClickListener(v -> {
            if (!"pomodoro".equals(state.kind) || state.done) return;
            String note = "focus".equals(state.mode)
                    ? "集中を終えたことになり、進捗ドットが1つ進みます。"
                    : "この休憩を終えて、次へ進みます。";
            confirm("スキップしますか？", note, () -> {
                boolean wasRunning = state.running;
                String msg = state.advancePhase();
                if (!state.done && !wasRunning) {
                    // skipped while stopped: show next phase stopped, don't auto-run
                    state.pause();
                    AlarmHelper.cancel(this);
                } else if (state.running) {
                    AlarmHelper.schedule(this, state.endAtMs);
                } else {
                    AlarmHelper.cancel(this);
                }
                showBanner(msg);
                persist();
            });
        });
        resetBtn.setOnClickListener(v -> {
            if ("timer".equals(state.kind)) {
                // two-stage reset (no dialog): 1st press cancels progress
                // back to the set time; pressed again at the set time, it
                // clears the setting itself back to 0
                if (state.running || state.remainingNow() < state.timerDurationMs) {
                    state.resetPhase();
                } else {
                    state.timerDurationMs = 0;
                    state.running = false; state.endAtMs = 0; state.done = false;
                    state.remainingMs = 0;
                }
                AlarmHelper.cancel(this);
                persist(); syncInputs();
                return;
            }
            if (state.done) {
                // at set completion, reset means "end the set"
                confirm("セットを終了しますか？",
                        "最初の集中に戻ります。進捗ドットもリセットされます。", () -> {
                    state.endAll();
                    AlarmHelper.cancel(this);
                    pomoTape.setElapsed(0);
                    persist();
                });
                return;
            }
            boolean atPhaseStart = !state.running
                    && state.remainingNow() >= state.totalMs();
            if (atPhaseStart) {
                // second-stage reset: end the whole set
                confirm("セットを終了しますか？",
                        "最初の集中に戻ります。進捗ドットもリセットされます。", () -> {
                    state.endAll();
                    AlarmHelper.cancel(this);
                    pomoTape.setElapsed(0);
                    persist();
                });
            } else {
                confirm("リセットしますか？",
                        "今のフェーズの最初に戻ります。進捗ドットはそのままです。", () -> {
                    state.resetPhase();
                    AlarmHelper.cancel(this);
                    persist();
                });
            }
        });

        syncInputs();
        render();
        maybeAskExactAlarm();
    }

    private void maybeAskExactAlarm() {
        if (Build.VERSION.SDK_INT < 31) return;
        android.app.AlarmManager am =
                (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
        if (am == null || am.canScheduleExactAlarms()) return;
        android.content.SharedPreferences p =
                getSharedPreferences(TimerState.PREF, MODE_PRIVATE);
        if (p.getBoolean("exactAlarmAsked", false)) return;
        p.edit().putBoolean("exactAlarmAsked", true).commit();
        new android.app.AlertDialog.Builder(this)
                .setTitle("正確な切り替わりのために")
                .setMessage("集中と休憩をぴったりの時刻で切り替えるには、「アラームとリマインダー」の許可が必要です。許可がないと、省電力で切り替わりが数分遅れて、ウィジェットがマイナス表示になることがあります。")
                .setPositiveButton("設定を開く", (d, w) -> {
                    try {
                        android.content.Intent i = new android.content.Intent(
                                android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                        i.setData(android.net.Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("あとで", null)
                .show();
    }

    private ImageButton iconButton(int res) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        b.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(64));
        lp.setMargins(dp(4), 0, dp(4), 0);
        b.setLayoutParams(lp);
        return b;
    }

    private int digitRes(char c) {
        switch (c) {
            case '0': return R.drawable.num_0;
            case '1': return R.drawable.num_1;
            case '2': return R.drawable.num_2;
            case '3': return R.drawable.num_3;
            case '4': return R.drawable.num_4;
            case '5': return R.drawable.num_5;
            case '6': return R.drawable.num_6;
            case '7': return R.drawable.num_7;
            case '8': return R.drawable.num_8;
            case '9': return R.drawable.num_9;
            default: return R.drawable.num_colon;
        }
    }

    /** Render the remaining time with the user-drawn digit images. */
    private void renderDigits(String text) {
        colonIndex = text.indexOf(':');
        if (text.equals(lastTimeStr) && timeRow.getChildCount() == text.length()) {
            return;
        }
        boolean rebuild = timeRow.getChildCount() != text.length();
        if (rebuild) {
            timeRow.removeAllViews();
            for (int i = 0; i < text.length(); i++) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int h = dp(84);
                int w = (int) (h * 300.0 / 420.0);
                iv.setLayoutParams(new LinearLayout.LayoutParams(w, h));
                timeRow.addView(iv);
            }
        }
        for (int i = 0; i < text.length(); i++) {
            ((ImageView) timeRow.getChildAt(i)).setImageResource(digitRes(text.charAt(i)));
        }
        lastTimeStr = text;
    }

    private String lastCharMode = null;
    private boolean charTransitioning = false;

    /** Character: frame animation while running, pause still while stopped.
     *  On a focus<->break switch, play the "yokozurashi shift" transition:
     *  break->focus: the coffee mug is shifted sideways off-screen and
     *  yokozurashi slides in. focus->break: yokozurashi shifts sideways
     *  off-screen and the coffee drops in from above. */
    private void renderChar(boolean isBreak, boolean running) {
        String modeKey = isBreak ? "break" : "focus";
        if (lastCharMode != null && !lastCharMode.equals(modeKey) && !charTransitioning) {
            startCharTransition(isBreak);
        }
        lastCharMode = modeKey;
        if (charTransitioning) return;
        applyChar(isBreak, running);
    }

    private void startCharTransition(final boolean toBreak) {
        if (!toBreak) {
            startShiftInTransition();
            return;
        }
        charTransitioning = true;
        // focus -> break: yokozurashi shifts sideways out of the screen,
        // then the coffee drops in from above (2x speed per user request)
        charImage.animate()
                .translationX(dp(440))
                .setDuration(190)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(() -> {
                    charState = -1;
                    applyChar(true, false);
                    charImage.setTranslationX(0);
                    charImage.setTranslationY(-dp(320));
                    charImage.animate()
                            .translationY(0)
                            .setDuration(280)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(1.4f))
                            .withEndAction(() -> {
                                charTransitioning = false;
                                charState = -1;
                                render();
                            })
                            .start();
                })
                .start();
    }

    /** break -> focus: yokozurashi slides in, touches the coffee mug for a
     *  moment, then pushes it sideways out of the screen (the "shift"). */
    private void startShiftInTransition() {
        charTransitioning = true;
        charImage2.setImageResource(R.drawable.focus_pause_1);
        charImage2.setVisibility(View.VISIBLE);
        charImage2.setTranslationY(0);
        charImage2.setTranslationX(-dp(440));
        // slide in until it just touches the mug
        charImage2.animate()
                .translationX(-dp(118))
                .setDuration(150)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    // push: both move right together, mug leaves, yokozurashi centers
                    charImage.animate()
                            .translationX(dp(440))
                            .setDuration(190)
                            .setInterpolator(new android.view.animation.AccelerateInterpolator())
                            .start();
                    charImage2.animate()
                            .translationX(0)
                            .setDuration(190)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator())
                            .withEndAction(() -> {
                                charImage2.setVisibility(View.GONE);
                                charImage.setTranslationX(0);
                                charTransitioning = false;
                                charState = -1;
                                render();
                            })
                            .start();
                })
                .start();
    }

    private void applyChar(boolean isBreak, boolean running) {
        // stopped states: "wait" art when the phase is fresh/prepared (or the
        // set is done), "pause" art when paused in the middle of a phase
        boolean fresh = state.done || state.remainingMs >= state.totalMs();
        int desired;
        if (running) desired = isBreak ? 3 : 2;
        else if (fresh) desired = isBreak ? 5 : 4;
        else desired = isBreak ? 1 : 0;
        if (desired != charState) {
            charState = desired;
            switch (desired) {
                case 2: charImage.setImageResource(R.drawable.anim_focus); break;
                case 3: charImage.setImageResource(R.drawable.anim_break); break;
                case 5: charImage.setImageResource(R.drawable.anim_wait_break); break;
                case 4: charImage.setImageResource(R.drawable.anim_wait_focus); break;
                case 1: charImage.setImageResource(R.drawable.anim_pause_break); break;
                default: charImage.setImageResource(R.drawable.anim_pause_focus); break;
            }
        }
        if (charImage.getDrawable() instanceof AnimationDrawable) {
            AnimationDrawable ad = (AnimationDrawable) charImage.getDrawable();
            if (!ad.isRunning()) ad.start();
        }
    }

    private long parseLong(String t, long def) {
        try { return Long.parseLong(t.trim()); } catch (Exception e) { return def; }
    }

    private boolean locked() {
        return "timer".equals(state.kind) && state.running;
    }

    private void switchKind(String kind) {
        if (state.kind.equals(kind)) return;
        tapeActive = false;
        overrideDisplayMs = -1;
        handler.removeCallbacks(tapeWatchdog);
        if (state.running) state.pause();
        AlarmHelper.cancel(this);
        state.kind = kind;
        state.done = false;
        state.remainingMs = state.totalMs();
        persist(); syncInputs();
    }

    private void setPomodoroMode(String mode) {
        state.mode = mode;
        state.running = false; state.endAtMs = 0; state.done = false;
        state.remainingMs = state.totalMs();
        AlarmHelper.cancel(this);
        persist();
    }

    private void persist() {
        state.save(this);
        TimerWidgetProvider.updateAll(this);
        render();
    }

    // ---------- time tape / digit drag (timer setting) ----------
    /** Apply a new timer setting (setting redefines both duration and
     *  remaining, as before; used while stopped or paused). */
    private void applyTimerSetting(long durationMs) {
        state.timerDurationMs = durationMs;
        state.running = false; state.endAtMs = 0; state.done = false;
        state.remainingMs = durationMs;
        AlarmHelper.cancel(this);
        persist();
    }

    /** Dragging the big hand-drawn digits sideways: minutes on the left
     *  group (clamp 0..999), seconds on the right group (wraps 0..59). */
    private class DigitDragListener implements View.OnTouchListener {
        private float downX;
        private float accum;
        private boolean seconds;
        private boolean active;

        @Override public boolean onTouch(View v, MotionEvent e) {
            if (!"timer".equals(state.kind)) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (locked()) {
                        showGuide("動作中はずらせません。一時停止すると変えられます", 3000);
                        return false;
                    }
                    View colon = timeRow.getChildCount() > colonIndex
                            ? timeRow.getChildAt(colonIndex) : null;
                    float colonCenter = colon != null
                            ? colon.getLeft() + colon.getWidth() / 2f : v.getWidth() / 2f;
                    seconds = e.getX() > colonCenter;
                    downX = e.getX();
                    accum = 0;
                    active = true;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!active) return false;
                    float dx = e.getX() - downX;
                    accum += dx;
                    downX = e.getX();
                    float stepPx = dp(40);
                    while (accum >= stepPx) { step(-1); accum -= stepPx; }
                    while (accum <= -stepPx) { step(1); accum += stepPx; }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    active = false;
                    return true;
            }
            return false;
        }

        private void step(int dir) {
            long totalSec = state.timerDurationMs / 1000;
            long min = totalSec / 60;
            long sec = totalSec % 60;
            if (seconds) {
                sec = (sec + dir + 60) % 60;
            } else {
                min = Math.max(0, Math.min(999, min + dir));
            }
            applyTimerSetting((min * 60 + sec) * 1000);
            slideInDigitGroup(seconds, dir);
        }
    }

    /** The dragged digit group slides in from the side, like the prototype. */
    private void slideInDigitGroup(boolean secondsGroup, int dir) {
        if (timeRow == null) return;
        int from = secondsGroup ? colonIndex + 1 : 0;
        int to = secondsGroup ? timeRow.getChildCount() : colonIndex;
        float digitW = dp(34);
        for (int i = from; i < to; i++) {
            View child = timeRow.getChildAt(i);
            if (child == null) continue;
            child.setTranslationX(dir > 0 ? digitW : -digitW);
            child.animate().translationX(0).setDuration(130).start();
        }
    }

    private void syncInputs() {
        if (tapeView == null || tapeActive) return;
        tapeView.setOffsetMinutes(state.remainingNow() / 60000f);
    }

    private void render() {
        if (timeRow == null) return;
        // the drag override is only valid mid-gesture in timer mode; if it
        // ever leaks past that, drop it so the digits can never freeze
        if (state.running || !"timer".equals(state.kind)) {
            overrideDisplayMs = -1;
            tapeActive = false;
            handler.removeCallbacks(tapeWatchdog);
        }
        long rem = overrideDisplayMs >= 0 ? overrideDisplayMs : state.remainingNow();
        renderDigits(TimerState.format(rem));

        boolean pomo = "pomodoro".equals(state.kind);
        String dotColor;
        String label;
        if (state.done) {
            label = pomo ? "1セット完了" : "時間です！";
            dotColor = "#F0B429";
        } else if (pomo) {
            if ("short".equals(state.mode)) { label = "休憩"; dotColor = "#59A96A"; }
            else if ("long".equals(state.mode)) { label = "長い休憩"; dotColor = "#59A96A"; }
            else { label = "集中"; dotColor = "#F0B429"; }
        } else {
            label = "タイマー";
            dotColor = "#5B8DEF";
        }
        modeLabel.setText("● " + label);
        modeLabel.setTextColor(Color.parseColor(dotColor));

        if (pomo) {
            dotsRow.setVisibility(View.VISIBLE);
            for (int i = 0; i < 4; i++) {
                dotViews[i].setImageResource(i < state.cycles
                        ? R.drawable.dot_on : R.drawable.dot_off);
            }
        } else {
            dotsRow.setVisibility(View.GONE);
        }

        // guidance line: transient override wins, then state-based text
        String guide;
        if (guideOverride != null && System.currentTimeMillis() < guideOverrideUntil) {
            guide = guideOverride;
        } else if (pomo && state.done) {
            guide = "1セット完了！おつかれさまでした";
        } else if (!pomo && !state.running && state.timerDurationMs <= 0) {
            guide = "帯か数字をずらして、時間を決めてください";
        } else {
            guide = "";
        }
        guideLine.setText(guide);

        toggleBtn.setImageResource(state.running ? R.drawable.icon_pause : R.drawable.icon_play);
        skipBtn.setVisibility(pomo ? View.VISIBLE : View.GONE);
        longBreakBtn.setVisibility(pomo && state.done && !state.running
                ? View.VISIBLE : View.INVISIBLE);

        // character: user-drawn frame animation (focus loop 0.5s, break loop 1.0s)
        boolean isBreak = pomo && !"focus".equals(state.mode);
        renderChar(isBreak, state.running);

        tabPomodoro.setAlpha(pomo ? 1f : 0.45f);
        tabTimer.setAlpha(pomo ? 0.45f : 1f);
        tabPomodoro.setTypeface(null, pomo ? Typeface.BOLD : Typeface.NORMAL);
        tabTimer.setTypeface(null, pomo ? Typeface.NORMAL : Typeface.BOLD);

        boolean lock = locked();
        if (tapeView != null) {
            tapeView.setVisibility(pomo ? View.GONE : View.VISIBLE);
            tapeView.setInteractive(!lock);
            if (!pomo && !tapeActive) {
                // while counting, the tape flows toward 0 in real time
                tapeView.setOffsetMinutes(state.remainingNow() / 60000f);
            }
        }
        if (pomoTape != null) {
            pomoTape.setVisibility(pomo ? View.VISIBLE : View.GONE);
            if (pomo) {
                boolean isLong = "long".equals(state.mode);
                pomoTape.setTimeline(isLong,
                        TimerState.pomodoroDurationMs("focus") / 60000f,
                        TimerState.pomodoroDurationMs("short") / 60000f);
                pomoTape.setMarkerColor(Color.parseColor(isBreak ? "#59A96A" : "#F0B429"));
                float elapsed = pomodoroElapsedMin();
                if (Math.abs(elapsed - pomoTape.getElapsed()) > 1.2f) {
                    // phase-scale jump (skip / reset / phase change): slide
                    pomoTape.animateElapsed(elapsed);
                } else {
                    pomoTape.setElapsed(elapsed);
                }
            }
        }
    }

    private float pomodoroElapsedMin() {
        return state.pomodoroElapsedMin();
    }

    private void showGuide(String text, long millis) {
        guideOverride = text;
        guideOverrideUntil = System.currentTimeMillis() + millis;
        if (guideLine != null) guideLine.setText(text);
        handler.postDelayed(this::render, millis + 50);
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        state = TimerState.load(this);
        handler.removeCallbacks(tick);
        handler.post(tick);
        if (state.timerAlertPending) {
            // the plain timer finished while the app was away; if the alert
            // loop is not running anymore (e.g. process restart), restart it
            if (!SoundHelper.isTimerAlertPlaying() && "timer".equals(state.kind)) {
                SoundHelper.startTimerAlert(this);
            }
            showTimerDoneDialog();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        foreground = false;
        handler.removeCallbacks(tick);
        if (charImage != null) {
            charImage.animate().cancel();
            charImage.setTranslationX(0);
            charImage.setTranslationY(0);
            if (charImage.getDrawable() instanceof AnimationDrawable) {
                ((AnimationDrawable) charImage.getDrawable()).stop();
            }
        }
        if (charImage2 != null) {
            charImage2.animate().cancel();
            charImage2.setTranslationX(0);
            charImage2.setTranslationY(0);
            charImage2.setVisibility(View.GONE);
        }
        charTransitioning = false;
        charState = -1;
        state.save(this);
    }
}
