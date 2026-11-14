package com.alain.dailyverse;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/**
 * Two ways to make sure the verse changes every morning:
 *  1. An exact alarm at the chosen time (works even when the phone is asleep).
 *  2. A backup job about every hour that catches up if the alarm was blocked.
 */
public final class Scheduler {
    private Scheduler() {}

    private static final int JOB_ID = 4711;

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, DailyReceiver.class);
        return PendingIntent.getBroadcast(c, 100, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        scheduleAlarm(c);
        scheduleBackupJob(c);
    }

    static void scheduleAlarm(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(c);
        am.cancel(pi);

        SharedPreferences p = VerseManager.prefs(c);
        if (!p.getBoolean(VerseManager.K_ENABLED, true)) return;

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, p.getInt(VerseManager.K_HOUR, 6));
        cal.set(Calendar.MINUTE, p.getInt(VerseManager.K_MINUTE, 0));
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        long t = cal.getTimeInMillis();
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi);
        }
    }

    static void scheduleBackupJob(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!VerseManager.prefs(c).getBoolean(VerseManager.K_ENABLED, true)) {
            js.cancel(JOB_ID);
            return;
        }
        if (js.getPendingJob(JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, CatchUpJob.class))
            .setPeriodic(60 * 60 * 1000L)
            .setPersisted(true)
            .build();
        js.schedule(job);
    }
}
