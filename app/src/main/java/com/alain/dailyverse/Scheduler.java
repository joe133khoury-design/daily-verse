package com.alain.dailyverse;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.Calendar;

/** Schedules the next morning alarm. Each alarm schedules the following one. */
public final class Scheduler {
    private Scheduler() {}

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, DailyReceiver.class);
        return PendingIntent.getBroadcast(c, 100, i,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
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
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
    }
}
