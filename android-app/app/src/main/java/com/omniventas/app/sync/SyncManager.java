package com.omniventas.app.sync;

import android.content.Context;
import android.util.Log;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class SyncManager {
    private static final String TAG = "SyncManager";
    private static final int SYNC_INTERVAL_MINUTES = 15;

    public static void scheduleSync(Context context) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        PeriodicWorkRequest syncWork = new PeriodicWorkRequest.Builder(
            SyncWorker.class,
            SYNC_INTERVAL_MINUTES,
            TimeUnit.MINUTES
        )
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_work",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncWork
        );

        Log.d(TAG, "Sincronización programada cada " + SYNC_INTERVAL_MINUTES + " minutos");
    }

    public static void syncNow(Context context) {
        Constraints constraints = new Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build();

        OneTimeWorkRequest syncNow = new OneTimeWorkRequest.Builder(SyncWorker.class)
            .setConstraints(constraints)
            .build();

        WorkManager.getInstance(context).enqueue(syncNow);
        Log.d(TAG, "Sincronización manual solicitada");
    }
}
