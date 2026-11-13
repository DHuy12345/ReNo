package com.reno.app.ui.calendar;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.db.entity.WordReviewEntity;
import com.reno.app.data.model.Occurrence;
import com.reno.app.data.model.ReviewItem;
import com.reno.app.data.repository.NoteRepository;
import com.reno.app.data.repository.VocabRepository;
import com.reno.app.util.DateUtils;
import com.reno.app.util.LiveQuery;
import com.reno.app.util.SessionManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Lịch: chấm của lịch tháng, khối ghi chú và vạch ôn từ vựng của tuần đang chọn. */
public class CalendarViewModel extends AndroidViewModel {
    public static final class Data {
        public LocalDate weekStart;
        public LocalDate selected;
        /** Ngày có ghi chú (chấm xám) và ngày có từ cần ôn (chấm xanh rêu) quanh tháng đang xem. */
        public final Set<LocalDate> noteDays = new HashSet<>();
        public final Set<LocalDate> vocabDays = new HashSet<>();
        public final List<Occurrence> weekNotes = new ArrayList<>();
        public final List<TimetableView.VocabMark> weekVocab = new ArrayList<>();
        /** Mọi ghi chú của tài khoản – cho bảng My calendars. */
        public final List<NoteEntity> allNotes = new ArrayList<>();
        public Set<String> hidden = new HashSet<>();
    }

    public final long userId;
    private final NoteRepository notes;
    private final VocabRepository vocab;
    private final MutableLiveData<LocalDate> selected = new MutableLiveData<>(DateUtils.today());
    private final MutableLiveData<YearMonth> month = new MutableLiveData<>(YearMonth.now());
    private final MutableLiveData<Integer> hiddenVersion = new MutableLiveData<>(0);
    private final LiveData<Data> data;
    public boolean collapsed;

    public CalendarViewModel(@NonNull Application app) {
        super(app);
        userId = SessionManager.currentUserId(app);
        notes = NoteRepository.get(app);
        vocab = VocabRepository.get(app);
        data = LiveQuery.of(this::load, notes.tick(), vocab.tick(), selected, month,
                hiddenVersion);
    }

    public LiveData<Data> data() {
        return data;
    }

    public LocalDate selectedDate() {
        return selected.getValue() == null ? DateUtils.today() : selected.getValue();
    }

    public YearMonth visibleMonth() {
        return month.getValue() == null ? YearMonth.now() : month.getValue();
    }

    public void select(LocalDate date) {
        if (!date.equals(selected.getValue())) selected.setValue(date);
    }

    public void setMonth(YearMonth ym) {
        if (!ym.equals(month.getValue())) month.setValue(ym);
    }

    public void setHidden(String key, boolean hidden) {
        Set<String> set = SessionManager.hiddenCalendars(getApplication(), userId);
        if (hidden) {
            set.add(key);
        } else {
            set.remove(key);
        }
        SessionManager.setHiddenCalendars(getApplication(), userId, set);
        Integer v = hiddenVersion.getValue();
        hiddenVersion.setValue(v == null ? 1 : v + 1);
    }

    public static LocalDate weekStartOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private Data load() {
        Data out = new Data();
        out.selected = selectedDate();
        out.weekStart = weekStartOf(out.selected);
        LocalDate weekEnd = out.weekStart.plusDays(6);
        YearMonth ym = visibleMonth();
        LocalDate from = ym.atDay(1).minusDays(7);
        LocalDate to = ym.atEndOfMonth().plusDays(7);
        if (out.weekStart.isBefore(from)) from = out.weekStart;
        if (weekEnd.isAfter(to)) to = weekEnd;

        out.hidden = SessionManager.hiddenCalendars(getApplication(), userId);
        out.allNotes.addAll(notes.listSync(userId));
        for (Occurrence o : notes.occurrencesBetween(userId, from, to)) {
            out.noteDays.add(o.date);
            boolean inWeek = !o.date.isBefore(out.weekStart) && !o.date.isAfter(weekEnd);
            if (inWeek && !out.hidden.contains(String.valueOf(o.note.id))) out.weekNotes.add(o);
        }

        // Từ vựng: một vạch tại mỗi giờ nhắc có từ cần ôn; nhóm đã hẹn lại thì ở giờ đã hẹn.
        Map<String, Integer> counts = new TreeMap<>();
        for (ReviewItem it : vocab.itemsBetween(userId, from, to)) {
            if (!WordReviewEntity.STATUS_PENDING.equals(it.status)) continue;
            LocalDate due = LocalDate.parse(it.dueDate);
            out.vocabDays.add(due);
            if (due.isBefore(out.weekStart) || due.isAfter(weekEnd)) continue;
            String key = it.dueDate + "|" + it.effectiveTime();
            Integer c = counts.get(key);
            counts.put(key, c == null ? 1 : c + 1);
        }
        if (!out.hidden.contains(SessionManager.CALENDAR_VOCAB)) {
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                String[] parts = e.getKey().split("\\|");
                out.weekVocab.add(new TimetableView.VocabMark(LocalDate.parse(parts[0]),
                        DateUtils.parseTime(parts[1]), e.getValue()));
            }
        }
        return out;
    }

    /** Giờ "HH:mm" của một vạch – dùng khi mở màn ôn tập đúng mốc giờ. */
    public static String timeKey(LocalTime time) {
        return DateUtils.formatTime(time);
    }
}
