package com.alain.dailyverse;

import android.app.job.JobParameters;
import android.app.job.JobService;

/**
 * Backup check that Android runs about every hour. If the morning alarm was blocked
 * (battery saver, phone cleaner...), it changes the verse as soon as it gets a chance.
 */
public class CatchUpJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                VerseManager.catchUpIfMissed(getApplicationContext());
                Scheduler.scheduleAlarm(getApplicationContext());
            } finally {
                jobFinished(params, false);
            }
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
