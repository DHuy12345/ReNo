package com.reno.app.reminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.reno.app.data.db.entity.AppNotificationEntity;
import com.reno.app.data.db.entity.WordReviewEntity;
import com.reno.app.data.model.Occurrence;
import com.reno.app.data.model.ReviewItem;
import com.reno.app.data.repository.NoteRepository;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.data.repository.VocabRepository;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.DateUtils;
import com.reno.app.util.SessionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Đặt báo thức nhắc nhở. Chỉ đặt cho 48 giờ tới rồi tự lập lại mỗi ngày lúc 00:05: không bao giờ
 * phải đặt vô hạn báo thức cho ghi chú lặp vô hạn, và luôn khớp với dữ liệu mới nhất.
 * Chỉ tài khoản đang đăng nhập được đặt báo thức.
 */
public final class ReminderScheduler {
    private static final String TAG = "ReminderScheduler";
    static final String ACTION_REMIND = "com.reno.app.action.REMIND";
    static final String EXTRA_USER_ID = "user_id";
    static final String EXTRA_KIND = "kind";
    static final String EXTRA_NOTE_ID = "note_id";
    static final String EXTRA_DATE = "date";
    static final String EXTRA_TIME = "time";
    /** Mốc 00:05 mỗi ngày chỉ để lập lại lịch, không tạo thông báo. */
    static final String KIND_RESCHEDULE = "RESCHEDULE";

    private static final String PREFS = "reno_alarms";
    private static final String KEY_CODES = "alarm_codes";
    private static final int HORIZON_HOURS = 48;

    private static volatile ReminderScheduler INSTANCE;

    private final Context app;
    private final AlarmManager alarmManager;

    private ReminderScheduler(Context ctx) {
        app = ctx.getApplicationContext();
        alarmManager = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
    }

    public static ReminderScheduler get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (ReminderScheduler.class) {
                if (INSTANCE == null) INSTANCE = new ReminderScheduler(ctx);
            }
        }
        return INSTANCE;
    }

    /** Một mốc nhắc cần đặt báo thức. */
    private static final class Mark {
        final String kind;
        final long noteId;
        final String date;
        @Nullable final String time;
        final long triggerAt;

        Mark(String kind, long noteId, String date, @Nullable String time, long triggerAt) {
            this.kind = kind;
            this.noteId = noteId;
            this.date = date;
            this.time = time;
            this.triggerAt = triggerAt;
        }
    }

    /** Lập lại toàn bộ báo thức ở luồng nền. Gọi từ giao diện. */
    public void rescheduleAll(long userId) {
        AppExecutors.diskIO().execute(() -> rescheduleNow(userId));
    }

    /** Lập lại toàn bộ báo thức ngay tại luồng đang gọi (phải là luồng nền). */
    public synchronized void rescheduleNow(long userId) {
        cancelAllInternal();
        if (userId == SessionManager.NO_USER || userId != SessionManager.currentUserId(app)) return;
        try {
            Set<String> codes = new HashSet<>();
            for (Map.Entry<String, Mark> e : computeMarks(userId).entrySet()) {
                codes.add(String.valueOf(setAlarm(userId, e.getKey(), e.getValue())));
            }
            prefs().edit().putStringSet(KEY_CODES, codes).apply();
        } catch (RuntimeException e) {
            Log.e(TAG, "Không lập được lịch nhắc", e);
        }
    }

    /** Đăng xuất: hủy mọi báo thức đã đặt. */
    public synchronized void cancelAll() {
        cancelAllInternal();
    }

    /** Các mốc nhắc trong 48 giờ tới, theo dedupe_key (mỗi mốc một báo thức). */
    private Map<String, Mark> computeMarks(long userId) {
        Map<String, Mark> marks = new LinkedHashMap<>();
        LocalDateTime now = DateUtils.now();
        LocalDateTime horizon = now.plusHours(HORIZON_HOURS);
        LocalDate today = now.toLocalDate();

        // a. Ghi chú: mỗi lần xuất hiện chưa có trạng thái, vào giờ bắt đầu.
        for (Occurrence o : NoteRepository.get(app)
                .occurrencesBetween(userId, today, horizon.toLocalDate())) {
            if (o.status != null) continue;
            LocalDateTime at = o.start();
            if (!at.isAfter(now) || at.isAfter(horizon)) continue;
            String date = o.date.toString();
            marks.put(NotificationRepository.noteKey(o.note.id, date),
                    new Mark(AppNotificationEntity.KIND_NOTE, o.note.id, date, null,
                            DateUtils.toEpochMs(at)));
        }

        // b + c. Từ vựng: mỗi giờ nhắc khác nhau có lần ôn PENDING (giờ mặc định, giờ riêng
        // của từ, hoặc giờ đã hẹn lại remind_at). Mỗi mốc giờ đúng một báo thức.
        for (ReviewItem it : VocabRepository.get(app)
                .itemsBetween(userId, today, horizon.toLocalDate())) {
            if (!WordReviewEntity.STATUS_PENDING.equals(it.status)) continue;
            String time = it.effectiveTime();
            LocalDateTime at = LocalDateTime.of(LocalDate.parse(it.dueDate),
                    DateUtils.parseTime(time));
            if (!at.isAfter(now) || at.isAfter(horizon)) continue;
            String key = NotificationRepository.vocabKey(it.dueDate, time);
            if (!marks.containsKey(key)) {
                marks.put(key, new Mark(AppNotificationEntity.KIND_VOCAB, 0, it.dueDate, time,
                        DateUtils.toEpochMs(at)));
            }
        }

        // d. Mốc 00:05 ngày mai để lập lại.
        LocalDateTime next = LocalDateTime.of(today.plusDays(1), LocalTime.of(0, 5));
        marks.put(KIND_RESCHEDULE, new Mark(KIND_RESCHEDULE, 0, next.toLocalDate().toString(),
                null, DateUtils.toEpochMs(next)));
        return marks;
    }

    /** @return mã báo thức đã đặt (= dedupe_key.hashCode()) */
    private int setAlarm(long userId, String dedupeKey, Mark mark) {
        int code = dedupeKey.hashCode();
        Intent intent = baseIntent()
                .putExtra(EXTRA_USER_ID, userId)
                .putExtra(EXTRA_KIND, mark.kind)
                .putExtra(EXTRA_NOTE_ID, mark.noteId)
                .putExtra(EXTRA_DATE, mark.date)
                .putExtra(EXTRA_TIME, mark.time);
        PendingIntent pi = PendingIntent.getBroadcast(app, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (canScheduleExact()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        mark.triggerAt, pi);
                return code;
            } catch (SecurityException e) {
                Log.w(TAG, "Quyền báo thức chính xác vừa bị thu hồi", e);
            }
        }
        // Không có quyền báo thức chính xác: thông báo có thể đến trễ vài phút.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, mark.triggerAt, pi);
        return code;
    }

    public boolean canScheduleExact() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || alarmManager.canScheduleExactAlarms();
    }

    private void cancelAllInternal() {
        Set<String> codes = prefs().getStringSet(KEY_CODES, new HashSet<>());
        for (String c : codes) {
            try {
                PendingIntent pi = PendingIntent.getBroadcast(app, Integer.parseInt(c),
                        baseIntent(), PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
                if (pi != null) {
                    alarmManager.cancel(pi);
                    pi.cancel();
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Không hủy được báo thức " + c, e);
            }
        }
        prefs().edit().remove(KEY_CODES).apply();
    }

    private Intent baseIntent() {
        return new Intent(app, ReminderReceiver.class).setAction(ACTION_REMIND);
    }

    private SharedPreferences prefs() {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
