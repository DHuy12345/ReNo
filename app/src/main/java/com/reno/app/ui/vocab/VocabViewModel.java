package com.reno.app.ui.vocab;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.reno.app.data.repository.VocabRepository;
import com.reno.app.util.LiveQuery;
import com.reno.app.util.SessionManager;

import java.util.HashSet;
import java.util.Set;

/** Trạng thái của màn danh sách Vocabulary. */
public class VocabViewModel extends AndroidViewModel {
    public final long userId;
    public final VocabRepository repo;
    private final LiveData<VocabRepository.VocabData> data;
    /** Các thư mục đang mở. */
    public final Set<Long> expanded = new HashSet<>();

    public VocabViewModel(@NonNull Application app) {
        super(app);
        userId = SessionManager.currentUserId(app);
        repo = VocabRepository.get(app);
        data = LiveQuery.of(() -> repo.loadScreen(userId), repo.tick());
    }

    public LiveData<VocabRepository.VocabData> data() {
        return data;
    }
}
