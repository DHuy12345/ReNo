package com.reno.app.util;

import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.Locale;

/**
 * So sánh chuỗi không phân biệt hoa thường cho mọi chữ, kể cả chữ có dấu tiếng Việt.
 * COLLATE NOCASE của SQLite chỉ bỏ qua hoa thường với chữ ASCII ("Huy" = "huy"), nhưng coi
 * "Đức" và "đức" là khác nhau, nên việc kiểm tra trùng tên được làm thêm ở đây.
 */
public final class TextKeys {
    private TextKeys() {
    }

    public static String key(@Nullable String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
    }

    public static boolean sameIgnoreCase(@Nullable String a, @Nullable String b) {
        return key(a).equals(key(b));
    }
}
