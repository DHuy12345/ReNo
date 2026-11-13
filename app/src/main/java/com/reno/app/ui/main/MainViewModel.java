package com.reno.app.ui.main;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.reno.app.data.db.entity.UserEntity;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.data.repository.UserRepository;
import com.reno.app.util.SessionManager;

/** Dữ liệu của khung màn chính: tài khoản đang đăng nhập và số thông báo chưa đọc. */
public class MainViewModel extends AndroidViewModel {
    private final LiveData<UserEntity> user;
    private final LiveData<Integer> unreadCount;

    public MainViewModel(@NonNull Application app) {
        super(app);
        long userId = SessionManager.currentUserId(app);
        user = UserRepository.get(app).observeUser(userId);
        unreadCount = NotificationRepository.get(app).observeUnreadCount(userId);
    }

    public LiveData<UserEntity> user() {
        return user;
    }

    public LiveData<Integer> unreadCount() {
        return unreadCount;
    }
}
