package com.alain.dailyverse;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a restart, update or clock change: re-schedule and catch up if a morning was missed. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                VerseManager.catchUpIfMissed(app);
            } finally {
                Scheduler.schedule(app);
                result.finish();
            }
        }).start();
    }
}
