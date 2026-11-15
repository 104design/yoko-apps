package app.yokozurashi.steps;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** 約15分ごとにヘルスコネクトから歩数を読む。再起動のあとも続く。 */
public class StepJob extends JobService {
    private static final int JOB_PERIODIC = 1;
    private static final long PERIOD = 15 * 60 * 1000L; // JobScheduler で指定できる最短

    @Override
    public boolean onStartJob(final JobParameters params) {
        StepReader.read(this, new Runnable() {
            @Override
            public void run() {
                jobFinished(params, false);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

    /** 定期の読み取りを予約する。すでに予約済みなら何もしない。 */
    public static void ensureScheduled(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null || js.getPendingJob(JOB_PERIODIC) != null) return;
        js.schedule(new JobInfo.Builder(JOB_PERIODIC, new ComponentName(ctx, StepJob.class))
                .setPeriodic(PERIOD)
                .setPersisted(true)
                .build());
    }

    public static void cancel(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) js.cancel(JOB_PERIODIC);
    }
}
