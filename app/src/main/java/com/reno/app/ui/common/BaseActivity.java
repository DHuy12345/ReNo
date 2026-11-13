package com.reno.app.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.reno.app.ui.auth.LoginActivity;
import com.reno.app.ui.main.MainActivity;
import com.reno.app.util.SessionManager;

/** Lớp cha của mọi Activity: vẽ tràn viền (bắt buộc từ Android 15) và kiểm tra phiên đăng nhập. */
public abstract class BaseActivity extends AppCompatActivity {
    protected long userId = SessionManager.NO_USER;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
    }

    /** Đặt giao diện và chừa chỗ cho thanh trạng thái, thanh điều hướng, bàn phím. */
    protected void setContentRoot(View root) {
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * Màn chỉ dành cho người đã đăng nhập. Chưa đăng nhập thì chuyển sang màn Đăng nhập
     * (mang theo đích đến nếu màn này được mở từ thông báo) và đóng màn hiện tại.
     *
     * @return false nếu đã chuyển hướng; khi đó onCreate phải dừng ngay
     */
    protected boolean ensureLoggedIn() {
        userId = SessionManager.currentUserId(this);
        if (userId != SessionManager.NO_USER) return true;
        Intent login = new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        DeepLink.copy(getIntent(), login);
        startActivity(login);
        finish();
        return false;
    }

    /** Màn mở từ thông báo khi app chưa chạy: đóng lại thì về màn chính thay vì thoát app. */
    protected void finishToMain() {
        if (isTaskRoot()) {
            startActivity(new Intent(this, MainActivity.class));
        }
        finish();
    }
}
