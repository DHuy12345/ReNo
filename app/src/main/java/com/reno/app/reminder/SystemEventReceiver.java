package com.reno.app.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.reno.app.util.AppExecutors;
import com.reno.app.util.SessionManager;

/**
 * Báo thức bị mất khi khởi động lại máy và lệch khi đổi giờ / múi giờ: nhận các sự kiện đó
 * (và sự kiện cập nhật app) rồi lập lại toàn bộ lịch nhắc.
 */
public class SystemEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        Context app = context.getApplicationContext();
        PendingResult result = goAsync();
        AppExecutors.diskIO().execute(() -> {
            try {
                ReminderScheduler.get(app).rescheduleNow(SessionManager.currentUserId(app));
                RescheduleWorker.ensureScheduled(app);
            } finally {
                result.finish();
            }
        });
    }
}
