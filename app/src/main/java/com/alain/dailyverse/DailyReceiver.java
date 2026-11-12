package com.alain.dailyverse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Runs every morning: picks the next verse and puts it on the lock screen. */
public class DailyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                VerseManager.advanceAndApply(app);
            } finally {
                Scheduler.schedule(app);
                result.finish();
            }
        }).start();
    }
}
