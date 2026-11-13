package com.reno.app.reminder;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.reno.app.R;
import com.reno.app.data.db.entity.AppNotificationEntity;
import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.repository.NoteRepository;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.ui.notes.NoteDetailActivity;
import com.reno.app.ui.vocab.ReviewActivity;

/** Kênh "Nhắc nhở ReNo" và việc hiển thị thông báo hệ thống. */
public final class NotificationHelper {
    public static final String CHANNEL_REMINDER = "reno_reminders";

    private NotificationHelper() {
    }

    public static void createChannel(Context ctx) {
        NotificationChannel channel = new NotificationChannel(CHANNEL_REMINDER,
                ctx.getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(ctx.getString(R.string.channel_reminder_description));
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    public static boolean canPost(Context ctx) {
        boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        return granted && NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    /** Mã của thông báo hệ thống ứng với một dedupe_key. */
    public static int idOf(String dedupeKey) {
        return dedupeKey.hashCode();
    }

    public static void cancel(Context ctx, String dedupeKey) {
        NotificationManagerCompat.from(ctx).cancel(idOf(dedupeKey));
    }

    /** Hiện thông báo hệ thống cho một dòng app_notifications. Gọi ở luồng nền. */
    public static void show(Context ctx, AppNotificationEntity n) {
        if (!canPost(ctx)) return;
        int id = idOf(n.dedupeKey);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_REMINDER)
                .setContentTitle(n.title)
                .setContentText(n.body)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true);

        if (AppNotificationEntity.KIND_NOTE.equals(n.kind) && n.noteId != null) {
            // Thông báo ghi chú mang màu riêng của ghi chú.
            NoteEntity note = NoteRepository.get(ctx).findSync(n.noteId);
            int color = ContextCompat.getColor(ctx, R.color.moss);
            if (note != null) {
                try {
                    color = Color.parseColor(note.color);
                } catch (IllegalArgumentException ignored) {
                    // giữ màu mặc định
                }
            }
            Intent open = NoteDetailActivity.intent(ctx, n.noteId, n.refDate, n.dedupeKey)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            b.setSmallIcon(R.drawable.ic_note)
                    .setColor(color)
                    .setContentIntent(PendingIntent.getActivity(ctx, id, open, flags));
        } else {
            // Mọi thông báo ôn từ vựng dùng chung màu xanh rêu và biểu tượng sách,
            // kèm nút Bỏ qua áp dụng cho cả nhóm.
            String time = NotificationRepository.timeOfVocabKey(n.dedupeKey);
            Intent open = ReviewActivity.intentForGroup(ctx, n.refDate, time, n.dedupeKey)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Intent skip = SkipDialogActivity.intentForGroup(ctx, n.refDate, time, n.dedupeKey)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            b.setSmallIcon(R.drawable.ic_book)
                    .setColor(ContextCompat.getColor(ctx, R.color.moss))
                    .setContentIntent(PendingIntent.getActivity(ctx, id, open, flags))
                    .addAction(R.drawable.ic_skip, ctx.getString(R.string.skip),
                            PendingIntent.getActivity(ctx, id + 1, skip, flags));
        }
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build());
        } catch (SecurityException ignored) {
            // quyền vừa bị thu hồi: thông báo vẫn nằm trong màn Thông báo
        }
    }
}
