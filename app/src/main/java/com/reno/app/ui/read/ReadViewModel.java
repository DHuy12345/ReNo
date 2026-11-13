package com.reno.app.ui.read;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.reno.app.data.repository.ReadRepository;
import com.reno.app.util.LiveQuery;
import com.reno.app.util.SessionManager;

import java.util.HashSet;
import java.util.Set;

/** Trạng thái của màn danh sách Read. */
public class ReadViewModel extends AndroidViewModel {
    public final long userId;
    public final ReadRepository repo;
    private final LiveData<ReadRepository.ReadData> data;
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);

    /** Các thư mục đang mở. */
    public final Set<Long> expanded = new HashSet<>();
    /** Thư mục đã chọn cho topic sắp thêm, giữ lại trong lúc bộ chọn file đang mở. */
    public long pendingFolderId = -1;

    public ReadViewModel(@NonNull Application app) {
        super(app);
        userId = SessionManager.currentUserId(app);
        repo = ReadRepository.get(app);
        data = LiveQuery.of(() -> repo.loadScreen(userId), repo.tick());
    }

    public LiveData<ReadRepository.ReadData> data() {
        return data;
    }

    public LiveData<Boolean> busy() {
        return busy;
    }

    public void setBusy(boolean value) {
        busy.setValue(value);
    }
}
