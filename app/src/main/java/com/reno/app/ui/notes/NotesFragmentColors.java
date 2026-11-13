package com.reno.app.ui.notes;

import android.graphics.Color;

/** Đọc mã màu #RRGGBB của ghi chú; mã hỏng thì dùng màu xám. */
public final class NotesFragmentColors {
    private NotesFragmentColors() {
    }

    public static int parse(String color) {
        try {
            return Color.parseColor(color);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Color.GRAY;
        }
    }
}
