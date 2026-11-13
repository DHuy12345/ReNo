package com.reno.app.ui.profile;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.reno.app.data.db.entity.UserEntity;
import com.reno.app.data.repository.UserRepository;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.Event;
import com.reno.app.util.SessionManager;

/** Giữ các thay đổi chưa lưu của màn Thông tin người dùng (giờ nhắc, ảnh vừa cắt). */
public class ProfileViewModel extends AndroidViewModel {
    private final UserRepository users;
    private final long userId;
    private final MutableLiveData<UserEntity> user = new MutableLiveData<>();
    private final MutableLiveData<Event<UserRepository.ProfileResult>> saveResult =
            new MutableLiveData<>();

    /** Giờ nhắc đang chọn (HH:mm); null = chưa nạp xong. */
    @Nullable public String reviewTime;
    /** Ảnh đã cắt nhưng chưa bấm Lưu. */
    @Nullable public String pendingAvatarPath;
    /** Người dùng chọn "Dùng ảnh mặc định". */
    public boolean resetAvatar;
    /** File tạm đang chờ app máy ảnh ghi vào. */
    @Nullable public String cameraFilePath;
    public boolean formFilled;

    public ProfileViewModel(@NonNull Application app) {
        super(app);
        users = UserRepository.get(app);
        userId = SessionManager.currentUserId(app);
        AppExecutors.diskIO().execute(() -> user.postValue(users.findSync(userId)));
    }

    public LiveData<UserEntity> user() {
        return user;
    }

    public LiveData<Event<UserRepository.ProfileResult>> saveResult() {
        return saveResult;
    }

    public void setPendingAvatar(@Nullable String path) {
        discardPendingAvatar();
        pendingAvatarPath = path;
        resetAvatar = false;
    }

    public void useDefaultAvatar() {
        discardPendingAvatar();
        resetAvatar = true;
    }

    /** Bỏ thay đổi: xóa file ảnh đã cắt nhưng chưa lưu. */
    public void discardPendingAvatar() {
        String path = pendingAvatarPath;
        pendingAvatarPath = null;
        if (path != null) {
            AppExecutors.diskIO().execute(() -> UserRepository.deleteQuietly(path));
        }
    }

    public void save(String username, String email, String phone) {
        if (reviewTime == null) return;
        users.updateProfile(userId, username, email, phone, reviewTime, pendingAvatarPath,
                resetAvatar, result -> {
                    if (result.isOk()) pendingAvatarPath = null;   // file đã thuộc về tài khoản
                    saveResult.setValue(new Event<>(result));
                });
    }
}
