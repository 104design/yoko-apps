package app.yokozurashi.news;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** 約30分ごとの定期更新。通信できるときだけ動く。再起動のあとも続く。 */
public class RefreshJob extends JobService {
    private static final int JOB_PERIODIC = 1;
    private static final int JOB_NOW = 2;
    private static final long PERIOD = 30 * 60 * 1000L;

    @Override
    public boolean onStartJob(final JobParameters params) {
        Refresher.runAsync(this, false, new Runnable() {
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

    /** 定期更新を予約する。すでに予約済みなら何もしない。 */
    public static void ensureScheduled(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null || js.getPendingJob(JOB_PERIODIC) != null) return;
        js.schedule(new JobInfo.Builder(JOB_PERIODIC, new ComponentName(ctx, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(PERIOD)
                .setPersisted(true)
                .build());
    }

    /** 通信できしだい、すぐ1回更新する。 */
    public static void runSoon(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        js.schedule(new JobInfo.Builder(JOB_NOW, new ComponentName(ctx, RefreshJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build());
    }

    public static void cancelAll(Context ctx) {
        JobScheduler js = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) {
            js.cancel(JOB_PERIODIC);
            js.cancel(JOB_NOW);
        }
    }
}
