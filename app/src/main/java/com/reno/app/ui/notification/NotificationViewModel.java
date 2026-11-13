package com.reno.app.ui.notification;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.reno.app.data.repository.NoteRepository;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.data.repository.VocabRepository;
import com.reno.app.util.LiveQuery;
import com.reno.app.util.SessionManager;

import java.util.List;

/** Danh sách thông báo; trạng thái Xong / Bỏ qua đọc lại mỗi khi ghi chú hoặc lịch ôn thay đổi. */
public class NotificationViewModel extends AndroidViewModel {
    public final long userId;
    public final NotificationRepository repo;
    private final MutableLiveData<Integer> refresh = new MutableLiveData<>(0);
    private final LiveData<List<NotificationRepository.Row>> rows;

    public NotificationViewModel(@NonNull Application app) {
        super(app);
        userId = SessionManager.currentUserId(app);
        repo = NotificationRepository.get(app);
        rows = LiveQuery.of(() -> repo.list(userId), repo.tick(), NoteRepository.get(app).tick(),
                VocabRepository.get(app).tick(), refresh);
    }

    public LiveData<List<NotificationRepository.Row>> rows() {
        return rows;
    }

    public void refresh() {
        Integer v = refresh.getValue();
        refresh.setValue(v == null ? 1 : v + 1);
    }
}
