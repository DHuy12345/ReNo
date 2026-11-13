package com.reno.app.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.reno.app.R;
import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.dao.AppNotificationDao;
import com.reno.app.data.db.entity.AppNotificationEntity;
import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.db.entity.WordReviewEntity;
import com.reno.app.data.model.Occurrence;
import com.reno.app.data.model.ReviewItem;
import com.reno.app.domain.RecurrenceHelper;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.DateUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Thông báo đã gửi (bảng app_notifications) và trạng thái xử lý của chúng. */
public class NotificationRepository {
    public static final int STATUS_PENDING = 0;
    public static final int STATUS_DONE = 1;
    public static final int STATUS_SKIPPED = 2;

    private static volatile NotificationRepository INSTANCE;

    private final Context app;
    private final AppNotificationDao dao;

    private NotificationRepository(Context ctx) {
        app = ctx.getApplicationContext();
        dao = RenoDatabase.get(app).appNotificationDao();
    }

    public static NotificationRepository get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (NotificationRepository.class) {
                if (INSTANCE == null) INSTANCE = new NotificationRepository(ctx);
            }
        }
        return INSTANCE;
    }

    /** Một dòng của màn Thông báo. */
    public static final class Row {
        public AppNotificationEntity notification;
        /** STATUS_PENDING / STATUS_DONE / STATUS_SKIPPED – đọc từ note_occurrences hoặc word_reviews. */
        public int status;
        /** Màu của ghi chú (kind = NOTE). */
        @Nullable public String noteColor;
        /** Mốc giờ HH:mm của nhóm ôn (kind = VOCAB). */
        @Nullable public String time;
        /** Số từ còn PENDING của nhóm (kind = VOCAB). */
        public int pendingCount;
    }

    public static String noteKey(long noteId, String date) {
        return AppNotificationEntity.KIND_NOTE + ":" + noteId + ":" + date;
    }

    /** Mỗi mốc giờ một thông báo: VOCAB:2026-11-13T20:30. */
    public static String vocabKey(String date, String time) {
        return AppNotificationEntity.KIND_VOCAB + ":" + date + "T" + time;
    }

    /** Lấy mốc giờ HH:mm từ dedupe_key của thông báo ôn tập. */
    @Nullable
    public static String timeOfVocabKey(@NonNull String dedupeKey) {
        int t = dedupeKey.lastIndexOf('T');
        return t < 0 || t == dedupeKey.length() - 1 ? null : dedupeKey.substring(t + 1);
    }

    public LiveData<Integer> tick() {
        return dao.tick();
    }

    public LiveData<Integer> observeUnreadCount(long userId) {
        return dao.observeUnreadCount(userId);
    }

    /**
     * Kiểm tra lại CSDL khi báo thức chạy: ghi chú còn tồn tại và lần xuất hiện chưa DONE,
     * hoặc còn ít nhất một lần ôn PENDING. Không còn thì trả về null (không báo gì).
     * Gọi ở luồng nền.
     */
    @Nullable
    public AppNotificationEntity buildIfStillValid(long userId, String kind, long noteId,
                                                   String date, @Nullable String time) {
        AppNotificationEntity n = new AppNotificationEntity();
        n.userId = userId;
        n.kind = kind;
        n.refDate = date;
        n.createdAt = System.currentTimeMillis();
        if (AppNotificationEntity.KIND_NOTE.equals(kind)) {
            NoteEntity note = NoteRepository.get(app).findSync(noteId);
            if (note == null || note.userId != userId) return null;
            LocalDate day = LocalDate.parse(date);
            if (!RecurrenceHelper.occursOn(note, day)) return null;
            Occurrence occ = NoteRepository.get(app).findOccurrence(noteId, day);
            if (occ == null || occ.isDone()) return null;
            n.noteId = noteId;
            n.dedupeKey = noteKey(noteId, date);
            n.title = app.getString(R.string.notif_note_title, note.title,
                    DateUtils.formatTime(occ.startTime()));
            n.body = app.getString(R.string.notif_note_body, DateUtils.formatFullDate(day),
                    DateUtils.formatTime(occ.startTime()),
                    DateUtils.formatTime(occ.end().toLocalTime()));
            return n;
        }
        if (time == null) return null;
        List<String> words = new ArrayList<>();
        for (ReviewItem it : VocabRepository.get(app).group(userId, date, time)) {
            if (WordReviewEntity.STATUS_PENDING.equals(it.status)) words.add(it.word);
        }
        if (words.isEmpty()) return null;
        n.dedupeKey = vocabKey(date, time);
        n.title = app.getString(R.string.notif_vocab_title, words.size());
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < words.size() && i < 4; i++) {
            if (i > 0) preview.append(", ");
            preview.append(words.get(i));
        }
        if (words.size() > 4) preview.append(", …");
        n.body = app.getString(R.string.notif_vocab_body, preview.toString());
        return n;
    }

    /** @return true nếu là thông báo mới; false nếu đã có dòng cùng dedupe_key */
    public boolean insertIfNew(AppNotificationEntity n) {
        return dao.insertIfNew(n) != -1L;
    }

    /** Danh sách thông báo, mới nhất trên cùng, kèm trạng thái xử lý. Gọi ở luồng nền. */
    public List<Row> list(long userId) {
        List<Row> rows = new ArrayList<>();
        NoteRepository notes = NoteRepository.get(app);
        VocabRepository vocab = VocabRepository.get(app);
        for (AppNotificationEntity n : dao.list(userId)) {
            Row row = new Row();
            row.notification = n;
            if (AppNotificationEntity.KIND_NOTE.equals(n.kind) && n.noteId != null) {
                Occurrence occ = notes.findOccurrence(n.noteId, LocalDate.parse(n.refDate));
                if (occ != null) {
                    row.noteColor = occ.note.color;
                    row.status = occ.isDone() ? STATUS_DONE
                            : occ.isSkipped() ? STATUS_SKIPPED : STATUS_PENDING;
                }
            } else {
                row.time = timeOfVocabKey(n.dedupeKey);
                List<ReviewItem> group = vocab.group(userId, n.refDate, row.time);
                boolean allDone = !group.isEmpty();
                for (ReviewItem it : group) {
                    if (WordReviewEntity.STATUS_PENDING.equals(it.status)) row.pendingCount++;
                    if (!WordReviewEntity.STATUS_DONE.equals(it.status)) allDone = false;
                }
                // Không còn từ PENDING: mọi từ đều Xong → Xong; còn lại (bỏ lỡ hoặc đã dời) → Bỏ qua.
                row.status = row.pendingCount > 0 ? STATUS_PENDING
                        : allDone ? STATUS_DONE : STATUS_SKIPPED;
            }
            rows.add(row);
        }
        return rows;
    }

    public void markRead(long id) {
        AppExecutors.diskIO().execute(() -> dao.markRead(id));
    }

    /** Người dùng mở từ thông báo hệ thống: đánh dấu đã đọc theo dedupe_key. */
    public void markReadByKey(long userId, @Nullable String dedupeKey) {
        if (dedupeKey == null) return;
        AppExecutors.diskIO().execute(() -> dao.markReadByKey(userId, dedupeKey));
    }

    /** Chỉ xóa thông báo; không xóa ghi chú hay từ vựng và không đổi trạng thái của chúng. */
    public void delete(long id) {
        AppExecutors.diskIO().execute(() -> dao.deleteById(id));
    }
}
