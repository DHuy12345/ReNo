package com.reno.app;

import android.app.Application;

import com.reno.app.reminder.NotificationHelper;
import com.reno.app.reminder.ReminderScheduler;
import com.reno.app.reminder.RescheduleWorker;
import com.reno.app.util.SessionManager;

public class RenoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NotificationHelper.createChannel(this);
        // Mở app: lập lại lịch nhắc cho tài khoản đang đăng nhập và bật tác vụ định kỳ 12 giờ.
        ReminderScheduler.get(this).rescheduleAll(SessionManager.currentUserId(this));
        try {
            RescheduleWorker.ensureScheduled(this);
        } catch (IllegalStateException ignored) {
            // WorkManager chưa khởi tạo (chỉ gặp trong môi trường kiểm thử)
        }
    }
}
