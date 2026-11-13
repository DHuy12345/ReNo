package com.reno.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputLayout;
import com.reno.app.data.repository.UserRepository;
import com.reno.app.databinding.ActivityRegisterBinding;
import com.reno.app.ui.common.BaseActivity;

/** Màn Đăng ký. Lỗi hiển thị ngay dưới ô nhập, không dùng Toast. */
public class RegisterActivity extends BaseActivity {
    public static final String EXTRA_USERNAME = "username";

    private ActivityRegisterBinding b;
    private AuthViewModel vm;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentRoot(b.getRoot());
        vm = new ViewModelProvider(this).get(AuthViewModel.class);

        b.btnRegister.setOnClickListener(v -> vm.register(
                text(b.editContact.getText()), text(b.editUsername.getText()),
                text(b.editPassword.getText()), text(b.editConfirm.getText())));
        b.btnLogin.setOnClickListener(v -> finish());

        vm.loading().observe(this, loading -> {
            b.btnRegister.setEnabled(!loading);
            b.progress.setVisibility(loading ? View.VISIBLE : View.INVISIBLE);
        });
        vm.registerResult().observe(this, event -> {
            UserRepository.RegisterResult r = event.consume();
            if (r == null) return;
            setError(b.tilContact, r.contactError);
            setError(b.tilUsername, r.usernameError);
            setError(b.tilPassword, r.passwordError);
            setError(b.tilConfirm, r.confirmError);
            if (r.isOk()) {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_USERNAME, r.username));
                finish();
            }
        });
    }

    private void setError(TextInputLayout til, @StringRes int error) {
        til.setError(error == 0 ? null : getString(error));
    }

    private static String text(@Nullable CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }
}
