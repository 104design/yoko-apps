package app.yokozurashi.steps;

import android.content.Context;
import android.health.connect.AggregateRecordsRequest;
import android.health.connect.AggregateRecordsGroupedByPeriodResponse;
import android.health.connect.AggregateRecordsResponse;
import android.health.connect.LocalTimeRangeFilter;
import android.health.connect.HealthConnectException;
import android.health.connect.HealthConnectManager;
import android.health.connect.TimeInstantRangeFilter;
import android.health.connect.datatypes.StepsRecord;
import android.os.OutcomeReceiver;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.List;

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

    public interface DailyResult {
        /** steps[i] は from から i 日目の歩数。読めなかったときは null。メインスレッドで呼ばれる。 */
        void done(long[] steps);
    }

    /** from の日から to の日まで（両端を含む）の、日ごとの歩数。日の区切りは端末の時計の0時。 */
    public static void daily(Context ctx, final LocalDate from, LocalDate to, final DailyResult result) {
        final int days = (int) ChronoUnit.DAYS.between(from, to) + 1;
        if (days <= 0) {
            result.done(new long[0]);
            return;
        }
        try {
            HealthConnectManager manager = ctx.getSystemService(HealthConnectManager.class);
            if (manager == null) {
                result.done(null);
                return;
            }
            AggregateRecordsRequest<Long> request = new AggregateRecordsRequest.Builder<Long>(
                    new LocalTimeRangeFilter.Builder()
                            .setStartTime(from.atStartOfDay())
                            .setEndTime(to.plusDays(1).atStartOfDay())
                            .build())
                    .addAggregationType(StepsRecord.STEPS_COUNT_TOTAL)
                    .build();
            manager.aggregateGroupByPeriod(request, Period.ofDays(1), ctx.getMainExecutor(),
                    new OutcomeReceiver<List<AggregateRecordsGroupedByPeriodResponse<Long>>,
                            HealthConnectException>() {
                        @Override
                        public void onResult(List<AggregateRecordsGroupedByPeriodResponse<Long>> list) {
                            long[] steps = new long[days];
                            for (AggregateRecordsGroupedByPeriodResponse<Long> r : list) {
                                int i = (int) ChronoUnit.DAYS.between(from, r.getStartTime().toLocalDate());
                                Long v = r.get(StepsRecord.STEPS_COUNT_TOTAL);
                                if (i >= 0 && i < days && v != null) steps[i] += v;
                            }
                            result.done(steps);
                        }

                        @Override
                        public void onError(HealthConnectException e) {
                            result.done(null);
                        }
                    });
        } catch (RuntimeException e) {
            result.done(null);
        }
    }
}
