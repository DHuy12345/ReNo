package com.reno.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;
import com.reno.app.R;
import com.reno.app.databinding.ActivityLoginBinding;
import com.reno.app.reminder.ReminderScheduler;
import com.reno.app.ui.common.BaseActivity;
import com.reno.app.ui.common.DeepLink;
import com.reno.app.ui.main.MainActivity;
import com.reno.app.util.SessionManager;

/** Màn Đăng nhập – cũng là màn khởi động: đã có phiên đăng nhập thì vào thẳng màn chính. */
public class LoginActivity extends BaseActivity {
    private ActivityLoginBinding b;
    private AuthViewModel vm;

    private final ActivityResultLauncher<Intent> registerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                // Đăng ký xong: báo thành công và điền sẵn username.
                String username = result.getData().getStringExtra(RegisterActivity.EXTRA_USERNAME);
                b.editUsername.setText(username);
                b.editPassword.setText("");
                b.editPassword.requestFocus();
                Snackbar.make(b.getRoot(), R.string.register_success, Snackbar.LENGTH_LONG).show();
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (SessionManager.isLoggedIn(this)) {
            openMain();
            return;
        }
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentRoot(b.getRoot());
        vm = new ViewModelProvider(this).get(AuthViewModel.class);

        b.btnLogin.setOnClickListener(v -> submit());
        b.editPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        b.btnRegister.setOnClickListener(v ->
                registerLauncher.launch(new Intent(this, RegisterActivity.class)));

        vm.loading().observe(this, loading -> {
            b.btnLogin.setEnabled(!loading);
            b.progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        });
        vm.loginResult().observe(this, event -> {
            Long id = event.consume();
            if (id == null) return;
            if (id < 0) {
                // Báo chung, không nói rõ sai ô nào.
                b.tilPassword.setError(getString(R.string.error_login_failed));
            } else {
                SessionManager.login(this, id);
                ReminderScheduler.get(this).rescheduleAll(id);
                openMain();
            }
        });
    }

    private void submit() {
        b.tilPassword.setError(null);
        String username = text(b.editUsername.getText());
        String password = text(b.editPassword.getText());
        if (username.trim().isEmpty() || password.isEmpty()) {
            b.tilPassword.setError(getString(R.string.error_login_failed));
            return;
        }
        vm.login(username, password);
    }

    /** Mở màn chính và xóa ngăn xếp; nếu đến đây từ một thông báo thì mở tiếp đúng màn đó. */
    private void openMain() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Intent target = DeepLink.target(this, getIntent());
        if (target != null) startActivity(target);
        finish();
    }

    private static String text(@Nullable CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }
}
