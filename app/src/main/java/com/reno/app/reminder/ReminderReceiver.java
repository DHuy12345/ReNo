package com.reno.app.reminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.reno.app.data.db.entity.AppNotificationEntity;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.SessionManager;

/**
 * Nhận báo thức: kiểm tra lại CSDL ở luồng nền, lưu thông báo (bỏ qua nếu trùng dedupe_key)
 * rồi hiển thị thông báo hệ thống. Ghi chú đã xóa hay lần ôn đã Xong sẽ không bị nhắc.
 */
public class ReminderReceiver extends BroadcastReceiver {
    private static final String TAG = "ReminderReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ReminderScheduler.ACTION_REMIND.equals(intent.getAction())) return;
        PendingResult result = goAsync();
        Context app = context.getApplicationContext();
        AppExecutors.diskIO().execute(() -> {
            try {
                handle(app, intent);
            } catch (RuntimeException e) {
                Log.e(TAG, "Lỗi khi xử lý báo thức", e);
            } finally {
                result.finish();
            }
        });
    }

    private void handle(Context app, Intent intent) {
        long currentUser = SessionManager.currentUserId(app);
        long userId = intent.getLongExtra(ReminderScheduler.EXTRA_USER_ID, SessionManager.NO_USER);
        // Chỉ tài khoản đang đăng nhập được nhắc.
        if (currentUser == SessionManager.NO_USER || userId != currentUser) return;

        String kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND);
        if (AppNotificationEntity.KIND_NOTE.equals(kind)
                || AppNotificationEntity.KIND_VOCAB.equals(kind)) {
            NotificationRepository repo = NotificationRepository.get(app);
            AppNotificationEntity n = repo.buildIfStillValid(userId, kind,
                    intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, 0),
                    intent.getStringExtra(ReminderScheduler.EXTRA_DATE),
                    intent.getStringExtra(ReminderScheduler.EXTRA_TIME));
            // Vẫn ghi vào màn Thông báo kể cả khi người dùng tắt quyền thông báo.
            if (n != null && repo.insertIfNew(n)) {
                NotificationHelper.show(app, n);
            }
        }
        ReminderScheduler.get(app).rescheduleNow(userId);
    }
}
