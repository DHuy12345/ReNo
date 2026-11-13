package com.reno.app.ui.notes;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.reno.app.data.model.Occurrence;
import com.reno.app.data.repository.NoteRepository;
import com.reno.app.util.DateUtils;
import com.reno.app.util.LiveQuery;
import com.reno.app.util.SessionManager;

import java.time.LocalDate;
import java.util.List;

/** Tab Notes: các lần xuất hiện của ghi chú rơi vào ngày đang xem (mặc định hôm nay). */
public class NotesViewModel extends AndroidViewModel {
    public final long userId;
    public final NoteRepository repo;
    private final MutableLiveData<LocalDate> date = new MutableLiveData<>(DateUtils.today());
    private final LiveData<List<Occurrence>> occurrences;

    public NotesViewModel(@NonNull Application app) {
        super(app);
        userId = SessionManager.currentUserId(app);
        repo = NoteRepository.get(app);
        occurrences = LiveQuery.of(this::load, repo.tick(), date);
    }

    private List<Occurrence> load() {
        LocalDate day = date.getValue() == null ? DateUtils.today() : date.getValue();
        List<Occurrence> list = repo.occurrencesBetween(userId, day, day);
        // Chưa tích xếp trước, theo giờ bắt đầu; đã tích xếp xuống cuối.
        list.sort((a, b) -> {
            if (a.isDone() != b.isDone()) return a.isDone() ? 1 : -1;
            int byTime = a.startTime().compareTo(b.startTime());
            return byTime != 0 ? byTime : Long.compare(a.note.id, b.note.id);
        });
        return list;
    }

    public LiveData<LocalDate> date() {
        return date;
    }

    public LocalDate currentDate() {
        return date.getValue() == null ? DateUtils.today() : date.getValue();
    }

    public void setDate(LocalDate day) {
        date.setValue(day);
    }

    public LiveData<List<Occurrence>> occurrences() {
        return occurrences;
    }
}
