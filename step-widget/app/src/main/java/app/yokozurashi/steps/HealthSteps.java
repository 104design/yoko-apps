package app.yokozurashi.steps;

import android.content.Context;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.StepsRecord;
import android.os.OutcomeReceiver;

import java.time.Instant;

/**
 * ヘルスコネクトに、ある期間の歩数の合計をたずねる。
 * Android 14 以上の本体に入っているヘルスコネクトを使う。それより古い端末では、このクラスを呼ばない。
 * 合計はヘルスコネクトが出すので、複数のアプリや時計が同じ歩みを書きこんでいても二重には数えない。
 */
public class HealthSteps {
    public interface Result {
        /** steps は歩数の合計。読めなかったときは -1。メインスレッドで呼ばれる。 */
        void done(long steps);
    }

    public static void count(Context ctx, long fromMillis, long toMillis, final Result result) {
        if (toMillis <= fromMillis) {
            result.done(0);
            return;
        }
        try {
            HealthConnectManager manager = ctx.getSystemService(HealthConnectManager.class);
            if (manager == null) {
                result.done(-1);
                return;
            }
            AggregateRecordsRequest<Long> request = new AggregateRecordsRequest.Builder<Long>(
                    new TimeInstantRangeFilter.Builder()
                            .setStartTime(Instant.ofEpochMilli(fromMillis))
                            .setEndTime(Instant.ofEpochMilli(toMillis))
                            .build())
                    .addAggregationType(StepsRecord.STEPS_COUNT_TOTAL)
                    .build();
            manager.aggregate(request, ctx.getMainExecutor(),
                    new OutcomeReceiver<AggregateRecordsResponse<Long>, HealthConnectException>() {
                        @Override
                        public void onResult(AggregateRecordsResponse<Long> response) {
                            Long steps = response.get(StepsRecord.STEPS_COUNT_TOTAL);
                            result.done(steps == null ? 0 : steps);
                        }

                        @Override
                        public void onError(HealthConnectException e) {
                            result.done(-1);
                        }
                    });
        } catch (RuntimeException e) {
            // 許可がない、裏からは読めない、など
            result.done(-1);
        }
    }
}
