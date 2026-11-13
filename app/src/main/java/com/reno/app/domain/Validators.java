package com.reno.app.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.PatternsCompat;

import java.util.Locale;
import java.util.regex.Pattern;

/** Quy tắc kiểm tra dữ liệu tài khoản (đặc tả mục 3.1). Lớp thuần Java, không phụ thuộc giao diện. */
public final class Validators {
    public static final int USERNAME_MIN = 3;
    public static final int USERNAME_MAX = 30;
    public static final int PASSWORD_MIN = 6;
    public static final int PASSWORD_MAX = 64;

    private static final Pattern VN_PHONE = Pattern.compile("0\\d{9}");

    private Validators() {
    }

    /** Kết quả chuẩn hóa ô Phone/email: đúng một trong hai trường khác null. */
    public static final class Contact {
        @Nullable public final String email;
        @Nullable public final String phone;

        Contact(@Nullable String email, @Nullable String phone) {
            this.email = email;
            this.phone = phone;
        }
    }

    /**
     * Có "@" thì kiểm tra theo mẫu email và chuyển về chữ thường; không có "@" thì phải là
     * số điện thoại Việt Nam 10 số bắt đầu bằng 0 ("+84" được đổi thành "0").
     *
     * @return null nếu không hợp lệ
     */
    @Nullable
    public static Contact parseContact(@Nullable String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        if (value.contains("@")) {
            String email = normalizeEmail(value);
            return email == null ? null : new Contact(email, null);
        }
        String phone = normalizePhone(value);
        return phone == null ? null : new Contact(null, phone);
    }

    /** @return email chữ thường, hoặc null nếu sai định dạng */
    @Nullable
    public static String normalizeEmail(@Nullable String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (!PatternsCompat.EMAIL_ADDRESS.matcher(value).matches()) return null;
        return value.toLowerCase(Locale.ROOT);
    }

    /** @return số dạng 0xxxxxxxxx, hoặc null nếu sai định dạng */
    @Nullable
    public static String normalizePhone(@Nullable String raw) {
        if (raw == null) return null;
        String value = raw.trim().replaceAll("[\\s.\\-]", "");
        if (value.startsWith("+84")) value = "0" + value.substring(3);
        return VN_PHONE.matcher(value).matches() ? value : null;
    }

    @NonNull
    public static String normalizeUsername(@Nullable String raw) {
        return raw == null ? "" : raw.trim();
    }

    public static boolean isValidUsername(@Nullable String raw) {
        String value = normalizeUsername(raw);
        return value.length() >= USERNAME_MIN && value.length() <= USERNAME_MAX
                && !value.contains("@");
    }

    public static boolean isValidPassword(@Nullable String password) {
        return password != null && password.length() >= PASSWORD_MIN
                && password.length() <= PASSWORD_MAX;
    }
}
