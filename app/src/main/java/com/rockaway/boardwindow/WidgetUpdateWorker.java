package com.rockaway.boardwindow;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class WidgetUpdateWorker extends Worker {
    public WidgetUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            ForecastRepository.ForecastSnapshot snapshot = ForecastRepository.refresh(getApplicationContext());
            ForecastRepository.saveSnapshot(getApplicationContext(), snapshot);
            BoardWindowWidgetProvider.updateAllWidgets(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            BoardWindowWidgetProvider.updateAllWidgets(getApplicationContext());
            return ForecastRepository.loadSnapshot(getApplicationContext()) == null ? Result.retry() : Result.success();
        }
    }
}
