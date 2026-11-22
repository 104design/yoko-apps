package app.yokozurashi.steps;

import android.app.Activity;
import android.os.Bundle;

/**
 * ウィジェットの更新マークを押したときに開く、見えない画面。
 * ヘルスコネクトは、画面に出ているアプリにしか歩数を渡さない（裏で読む許可のない端末では）。
 * そこで、透明な画面を一瞬だけ開いて歩数を読み、ウィジェットを描き直してすぐ閉じる。
 * 読み終わるか、StepReader が待つのをあきらめたら閉じる。
 */
public class ReloadActivity extends Activity {
    private boolean started;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 画面に出てから読む。onCreate の時点では、まだ画面に出ているとみなされないことがある
        if (started) return;
        started = true;
        StepReader.read(this, new Runnable() {
            @Override
            public void run() {
                finish();
                overridePendingTransition(0, 0);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        // ほかの画面に切り替わったら、読み終わっていなくても閉じる（読み取りは裏で最後まで進む）
        if (!isFinishing()) {
            finish();
            overridePendingTransition(0, 0);
        }
    }
}
