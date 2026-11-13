package com.reno.app.ui.common;

import android.content.Context;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reno.app.R;
import com.reno.app.databinding.DialogInputBinding;

/** Các hộp thoại dùng chung: xác nhận và nhập một dòng chữ. */
public final class Dialogs {
    private Dialogs() {
    }

    /** Điều khiển hộp thoại nhập: báo lỗi ngay dưới ô nhập hoặc đóng khi đã lưu xong. */
    public interface InputHandle {
        void setError(@Nullable String message);

        void dismiss();
    }

    public interface InputListener {
        void onSubmit(String text, InputHandle handle);
    }

    /** Hộp thoại xác nhận; mọi thao tác xóa đều đi qua đây và nêu rõ cái gì sẽ mất theo. */
    public static void confirm(Context ctx, CharSequence title, @Nullable CharSequence message,
                               @StringRes int positive, @StringRes int negative, Runnable onYes) {
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(negative, null)
                .setPositiveButton(positive, (d, w) -> onYes.run())
                .show();
    }

    public static void confirmDelete(Context ctx, CharSequence title,
                                     @Nullable CharSequence message, Runnable onYes) {
        confirm(ctx, title, message, R.string.delete, R.string.cancel, onYes);
    }

    /**
     * Hộp thoại nhập chữ. Bấm Lưu không tự đóng: {@code listener} kiểm tra rồi gọi
     * {@link InputHandle#setError} hoặc {@link InputHandle#dismiss}.
     */
    public static void input(Context ctx, CharSequence title, CharSequence hint,
                             @Nullable String initial, int maxLength, boolean multiLine,
                             InputListener listener) {
        input(ctx, title, hint, initial, maxLength, multiLine, listener, null);
    }

    /**
     * @param onCancel chạy khi hộp thoại đóng mà chưa lưu (bấm Hủy, bấm ra ngoài, nút Back)
     */
    public static void input(Context ctx, CharSequence title, CharSequence hint,
                             @Nullable String initial, int maxLength, boolean multiLine,
                             InputListener listener, @Nullable Runnable onCancel) {
        final boolean[] saved = {false};
        DialogInputBinding b = DialogInputBinding.inflate(LayoutInflater.from(ctx));
        b.til.setHint(hint);
        b.til.setCounterEnabled(maxLength > 200);
        b.til.setCounterMaxLength(maxLength);
        b.edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        if (multiLine) {
            b.edit.setSingleLine(false);
            b.edit.setMinLines(3);
            b.edit.setMaxLines(8);
        }
        if (initial != null) {
            b.edit.setText(initial);
            b.edit.setSelection(b.edit.length());
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle(title)
                .setView(b.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, null)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(d -> {
            b.edit.requestFocus();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                b.til.setError(null);
                String text = b.edit.getText() == null ? "" : b.edit.getText().toString();
                listener.onSubmit(text, new InputHandle() {
                    @Override
                    public void setError(@Nullable String message) {
                        b.til.setError(message);
                    }

                    @Override
                    public void dismiss() {
                        saved[0] = true;
                        dialog.dismiss();
                    }
                });
            });
        });
        dialog.setOnDismissListener(d -> {
            if (!saved[0] && onCancel != null) onCancel.run();
        });
        dialog.show();
    }
}
