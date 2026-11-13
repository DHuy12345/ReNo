package com.reno.app.ui.auth;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.reno.app.data.repository.UserRepository;
import com.reno.app.util.Event;

/** Trạng thái của màn Đăng nhập và Đăng ký. Băm mật khẩu chạy ở luồng nền trong UserRepository. */
public class AuthViewModel extends AndroidViewModel {
    private final UserRepository users;
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    /** Mã người dùng khi đăng nhập thành công; -1 khi sai username hoặc mật khẩu. */
    private final MutableLiveData<Event<Long>> loginResult = new MutableLiveData<>();
    private final MutableLiveData<Event<UserRepository.RegisterResult>> registerResult =
            new MutableLiveData<>();

    public AuthViewModel(@NonNull Application app) {
        super(app);
        users = UserRepository.get(app);
    }

    public LiveData<Boolean> loading() {
        return loading;
    }

    public LiveData<Event<Long>> loginResult() {
        return loginResult;
    }

    public LiveData<Event<UserRepository.RegisterResult>> registerResult() {
        return registerResult;
    }

    public void login(String username, String password) {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        loading.setValue(true);
        users.login(username, password, userId -> {
            loading.setValue(false);
            loginResult.setValue(new Event<>(userId == null ? -1L : userId));
        });
    }

    public void register(String contact, String username, String password, String confirm) {
        if (Boolean.TRUE.equals(loading.getValue())) return;
        loading.setValue(true);
        users.register(contact, username, password, confirm, result -> {
            loading.setValue(false);
            registerResult.setValue(new Event<>(result));
        });
    }
}
