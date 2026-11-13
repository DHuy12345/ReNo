package com.reno.app.reminder;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.reno.app.util.SessionManager;

import java.util.concurrent.TimeUnit;

/** Lưới an toàn: 12 giờ một lần gọi lại ReminderScheduler, phòng khi báo thức bị hệ thống xóa. */
public class RescheduleWorker extends Worker {
    private static final String UNIQUE_NAME = "reno_reschedule";

    public RescheduleWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context app = getApplicationContext();
        ReminderScheduler.get(app).rescheduleNow(SessionManager.currentUserId(app));
        return Result.success();
    }

    public static void ensureScheduled(Context ctx) {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(RescheduleWorker.class, 12, TimeUnit.HOURS).build();
        WorkManager.getInstance(ctx.getApplicationContext()).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
