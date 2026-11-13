package com.reno.app.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.dao.NoteDao;
import com.reno.app.data.db.dao.NoteOccurrenceDao;
import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.db.entity.NoteOccurrenceEntity;
import com.reno.app.data.model.Occurrence;
import com.reno.app.domain.RecurrenceHelper;
import com.reno.app.reminder.ReminderScheduler;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.Callback;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ghi chú, quy luật lặp và trạng thái của từng lần xuất hiện. */
public class NoteRepository {
    /** Bảng 8 màu ghi chú (đặc tả 5.2). */
    public static final String[] NOTE_COLORS = {
            "#5B8DEF", "#E07A5F", "#81B29A", "#E9B949",
            "#9C89B8", "#F28482", "#4D908E", "#B07D62"};

    private static volatile NoteRepository INSTANCE;

    private final Context app;
    private final NoteDao noteDao;
    private final NoteOccurrenceDao occurrenceDao;

    private NoteRepository(Context ctx) {
        app = ctx.getApplicationContext();
        RenoDatabase db = RenoDatabase.get(app);
        noteDao = db.noteDao();
        occurrenceDao = db.noteOccurrenceDao();
    }

    public static NoteRepository get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (NoteRepository.class) {
                if (INSTANCE == null) INSTANCE = new NoteRepository(ctx);
            }
        }
        return INSTANCE;
    }

    public LiveData<Integer> tick() {
        return noteDao.tick();
    }

    /**
     * Hàm dùng chung cho tab Notes (1 ngày), Lịch (1 tuần), Thống kê và bộ đặt báo thức:
     * các lần xuất hiện của mọi ghi chú trong [from, to], đã ghép trạng thái từ note_occurrences.
     * Không tạo trước dòng nào trong CSDL. Gọi ở luồng nền.
     */
    @NonNull
    public List<Occurrence> occurrencesBetween(long userId, @NonNull LocalDate from,
                                               @NonNull LocalDate to) {
        List<NoteEntity> notes = noteDao.listByUser(userId);
        Map<String, String> status = statusMap(userId, from, to);
        List<Occurrence> out = new ArrayList<>();
        for (NoteEntity n : notes) out.addAll(occurrencesBetween(n, from, to, status));
        return out;
    }

    /** Các lần xuất hiện của một ghi chú trong [from, to]. */
    @NonNull
    public static List<Occurrence> occurrencesBetween(@NonNull NoteEntity note,
                                                      @NonNull LocalDate from,
                                                      @NonNull LocalDate to,
                                                      @NonNull Map<String, String> statusByKey) {
        List<Occurrence> out = new ArrayList<>();
        for (LocalDate d : RecurrenceHelper.occurrenceDates(note, from, to)) {
            // ngày không còn trong chuỗi thì trạng thái cũ của nó tự bị bỏ qua
            out.add(new Occurrence(note, d, statusByKey.get(key(note.id, d.toString()))));
        }
        return out;
    }

    /** Một lần xuất hiện cụ thể (dùng cho màn Chi tiết ghi chú). Gọi ở luồng nền. */
    @Nullable
    public Occurrence findOccurrence(long noteId, @NonNull LocalDate date) {
        NoteEntity note = noteDao.findById(noteId);
        if (note == null) return null;
        NoteOccurrenceEntity occ = occurrenceDao.find(noteId, date.toString());
        return new Occurrence(note, date, occ == null ? null : occ.status);
    }

    private Map<String, String> statusMap(long userId, LocalDate from, LocalDate to) {
        Map<String, String> map = new HashMap<>();
        for (NoteOccurrenceEntity o : occurrenceDao.listBetween(userId, from.toString(),
                to.toString())) {
            map.put(key(o.noteId, o.occDate), o.status);
        }
        return map;
    }

    private static String key(long noteId, String date) {
        return noteId + "|" + date;
    }

    /** Gọi ở luồng nền. */
    @Nullable
    public NoteEntity findSync(long noteId) {
        return noteDao.findById(noteId);
    }

    public void find(long noteId, Callback<NoteEntity> callback) {
        AppExecutors.diskIO().execute(() -> {
            NoteEntity n = noteDao.findById(noteId);
            AppExecutors.mainThread().execute(() -> callback.onResult(n));
        });
    }

    /** Gọi ở luồng nền. */
    public List<NoteEntity> listSync(long userId) {
        return noteDao.listByUser(userId);
    }

    /** Màu mặc định cho ghi chú mới: ngẫu nhiên, ưu tiên màu chưa dùng. */
    public void suggestColor(long userId, Callback<String> callback) {
        AppExecutors.diskIO().execute(() -> {
            List<String> used = noteDao.usedColors(userId);
            List<String> free = new ArrayList<>();
            for (String c : NOTE_COLORS) {
                boolean taken = false;
                for (String u : used) {
                    if (c.equalsIgnoreCase(u)) {
                        taken = true;
                        break;
                    }
                }
                if (!taken) free.add(c);
            }
            List<String> pool = free.isEmpty() ? java.util.Arrays.asList(NOTE_COLORS) : free;
            String color = pool.get((int) (Math.random() * pool.size()));
            AppExecutors.mainThread().execute(() -> callback.onResult(color));
        });
    }

    /** Thêm (id = 0) hoặc sửa ghi chú, rồi đặt lại báo thức. Sửa quy luật áp dụng cho cả chuỗi. */
    public void save(NoteEntity note, Runnable done) {
        AppExecutors.diskIO().execute(() -> {
            long now = System.currentTimeMillis();
            note.updatedAt = now;
            if (note.id == 0) {
                note.createdAt = now;
                note.id = noteDao.insert(note);
            } else {
                noteDao.update(note);
            }
            ReminderScheduler.get(app).rescheduleNow(note.userId);
            AppExecutors.mainThread().execute(done);
        });
    }

    /** Xóa ghi chú: mọi lần lặp và trạng thái mất theo, không còn nhắc. */
    public void delete(long noteId) {
        AppExecutors.diskIO().execute(() -> {
            NoteEntity n = noteDao.findById(noteId);
            if (n == null) return;
            noteDao.deleteById(noteId);
            ReminderScheduler.get(app).rescheduleNow(n.userId);
        });
    }

    /**
     * Đặt trạng thái cho một lần xuất hiện.
     *
     * @param status DONE, SKIPPED, hoặc null để bỏ tích (xóa dòng → chưa làm)
     */
    public void setStatus(long noteId, @NonNull LocalDate date, @Nullable String status) {
        setStatus(noteId, date, status, null);
    }

    public void setStatus(long noteId, @NonNull LocalDate date, @Nullable String status,
                          @Nullable Runnable done) {
        AppExecutors.diskIO().execute(() -> {
            setStatusSync(noteId, date, status);
            if (done != null) AppExecutors.mainThread().execute(done);
        });
    }

    /** Gọi ở luồng nền. */
    public void setStatusSync(long noteId, @NonNull LocalDate date, @Nullable String status) {
        NoteEntity n = noteDao.findById(noteId);
        if (n == null) return;
        if (status == null) {
            occurrenceDao.delete(noteId, date.toString());
        } else {
            NoteOccurrenceEntity o = new NoteOccurrenceEntity();
            o.noteId = noteId;
            o.occDate = date.toString();
            o.status = status;
            o.updatedAt = System.currentTimeMillis();
            occurrenceDao.upsert(o);
        }
        ReminderScheduler.get(app).rescheduleNow(n.userId);
    }
}
