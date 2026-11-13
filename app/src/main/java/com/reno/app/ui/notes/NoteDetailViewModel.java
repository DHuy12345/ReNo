package com.reno.app.ui.notes;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.reno.app.data.model.Occurrence;
import com.reno.app.data.repository.NoteRepository;
import com.reno.app.util.LiveQuery;

import java.time.LocalDate;
import java.util.Optional;

/** Một lần xuất hiện của ghi chú, tự tải lại khi ghi chú hoặc trạng thái thay đổi. */
public class NoteDetailViewModel extends AndroidViewModel {
    public final NoteRepository repo;
    private LiveData<Optional<Occurrence>> occurrence;

    public NoteDetailViewModel(@NonNull Application app) {
        super(app);
        repo = NoteRepository.get(app);
    }

    public LiveData<Optional<Occurrence>> occurrence(long noteId, LocalDate date) {
        if (occurrence == null) {
            occurrence = LiveQuery.of(
                    () -> Optional.ofNullable(repo.findOccurrence(noteId, date)), repo.tick());
        }
        return occurrence;
    }
}
