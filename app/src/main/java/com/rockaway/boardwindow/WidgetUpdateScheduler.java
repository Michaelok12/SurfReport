package com.rockaway.boardwindow;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public final class WidgetUpdateScheduler {
    private static final String PERIODIC_NAME = "rockaway_widget_periodic";
    private static final String IMMEDIATE_NAME = "rockaway_widget_immediate";

    private WidgetUpdateScheduler() {}

    private static Constraints networkConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }

    public static void ensurePeriodic(Context context) {
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                WidgetUpdateWorker.class, 30, TimeUnit.MINUTES)
                .setConstraints(networkConstraints())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }

    public static void requestImmediate(Context context) {
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(WidgetUpdateWorker.class)
                .setConstraints(networkConstraints())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_NAME, ExistingWorkPolicy.REPLACE, request);
    }

    public static void cancelPeriodic(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_NAME);
    }
}
