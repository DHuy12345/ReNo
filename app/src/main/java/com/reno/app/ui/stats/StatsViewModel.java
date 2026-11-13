package com.reno.app.ui.stats;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.reno.app.data.db.entity.WordReviewEntity;
import com.reno.app.data.model.Occurrence;
import com.reno.app.data.model.ReviewItem;
import com.reno.app.data.repository.NoteRepository;
import com.reno.app.data.repository.VocabRepository;
import com.reno.app.util.DateUtils;
import com.reno.app.util.LiveQuery;
import com.reno.app.util.SessionManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Thống kê: Đã bỏ lỡ (30 ngày gần nhất), Sắp tới (7 ngày tới), Đã hoàn thành (30 ngày gần
 * nhất). Mọi mục được tính bằng truy vấn mỗi lần mở màn, không cần tác vụ chạy nền.
 */
public class StatsViewModel extends AndroidViewModel {
    public static final int SECTION_MISSED = 0;
    public static final int SECTION_UPCOMING = 1;
    public static final int SECTION_DONE = 2;
    private static final int PAST_DAYS = 30;
    private static final int FUTURE_DAYS = 7;

    /** Một dòng: một lần xuất hiện của ghi chú, hoặc một nhóm ôn từ vựng trong một ngày. */
    public static final class Row {
        public int section;
        public boolean isNote;
        public LocalDate date;
        public Occurrence occurrence;                         // khi isNote
        public final List<Long> reviewIds = new ArrayList<>();   // khi là nhóm từ vựng
    }

    public static final class Data {
        public final List<List<Row>> sections = new ArrayList<>();

        Data() {
            for (int i = 0; i < 3; i++) sections.add(new ArrayList<>());
        }
    }

    private final long userId;
    private final NoteRepository notes;
    private final VocabRepository vocab;
    private final MutableLiveData<Integer> refresh = new MutableLiveData<>(0);
    private final LiveData<Data> data;
    /** Các mục đang mở; mặc định chỉ mở Đã bỏ lỡ. */
    public final Set<Integer> expanded = new HashSet<>();

    public StatsViewModel(@NonNull Application app) {
        super(app);
        userId = SessionManager.currentUserId(app);
        notes = NoteRepository.get(app);
        vocab = VocabRepository.get(app);
        expanded.add(SECTION_MISSED);
        data = LiveQuery.of(this::load, notes.tick(), vocab.tick(), refresh);
    }

    public LiveData<Data> data() {
        return data;
    }

    /** Mở lại màn: tính lại vì "hôm nay" có thể đã đổi. */
    public void refresh() {
        Integer v = refresh.getValue();
        refresh.setValue(v == null ? 1 : v + 1);
    }

    private Data load() {
        Data out = new Data();
        LocalDate today = DateUtils.today();
        LocalDate from = today.minusDays(PAST_DAYS);
        LocalDate to = today.plusDays(FUTURE_DAYS);

        // ---- Ghi chú
        for (Occurrence o : notes.occurrencesBetween(userId, from, to)) {
            boolean past = o.date.isBefore(today);
            int section;
            if (o.isDone()) {
                if (o.date.isAfter(today)) continue;
                section = SECTION_DONE;
            } else if (o.isSkipped() || past) {
                // ngày đã qua mà chưa DONE, hoặc đã bấm Bỏ qua
                section = SECTION_MISSED;
            } else {
                // từ hôm nay đến 7 ngày tới, chưa có trạng thái
                section = SECTION_UPCOMING;
            }
            Row row = new Row();
            row.section = section;
            row.isNote = true;
            row.date = o.date;
            row.occurrence = o;
            out.sections.get(section).add(row);
        }

        // ---- Từ vựng: gom theo ngày để danh sách không dài hàng trăm dòng
        List<Map<LocalDate, Row>> groups = new ArrayList<>();
        for (int i = 0; i < 3; i++) groups.add(new TreeMap<>());
        for (ReviewItem it : vocab.itemsBetween(userId, from, to)) {
            LocalDate due = LocalDate.parse(it.dueDate);
            int section;
            if (WordReviewEntity.STATUS_DONE.equals(it.status)) {
                if (due.isAfter(today)) continue;
                section = SECTION_DONE;
            } else if (WordReviewEntity.STATUS_SKIPPED.equals(it.status)
                    || due.isBefore(today)) {
                // ngày ôn đã qua mà còn PENDING, hoặc SKIPPED
                if (due.isAfter(today)) continue;
                section = SECTION_MISSED;
            } else {
                section = SECTION_UPCOMING;
            }
            Row row = groups.get(section).get(due);
            if (row == null) {
                row = new Row();
                row.section = section;
                row.date = due;
                groups.get(section).put(due, row);
            }
            row.reviewIds.add(it.reviewId);
        }
        for (int i = 0; i < 3; i++) out.sections.get(i).addAll(groups.get(i).values());

        // Đã bỏ lỡ và Đã hoàn thành: mới nhất trước; Sắp tới: gần nhất trước.
        out.sections.get(SECTION_MISSED).sort((a, b) -> compare(b, a));
        out.sections.get(SECTION_DONE).sort((a, b) -> compare(b, a));
        out.sections.get(SECTION_UPCOMING).sort(StatsViewModel::compare);
        return out;
    }

    private static int compare(Row a, Row b) {
        int byDate = a.date.compareTo(b.date);
        if (byDate != 0) return byDate;
        if (a.isNote && b.isNote) {
            return a.occurrence.startTime().compareTo(b.occurrence.startTime());
        }
        return Boolean.compare(b.isNote, a.isNote);
    }
}
