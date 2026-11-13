package com.reno.app.ui.common;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.reno.app.reminder.SkipDialogActivity;
import com.reno.app.ui.notes.NoteDetailActivity;
import com.reno.app.ui.vocab.ReviewActivity;

/**
 * Đích đến của một thông báo, mang qua màn Đăng nhập khi người dùng chưa đăng nhập.
 * Chỉ truyền dữ liệu (loại, mã ghi chú, ngày, giờ); Intent thật do app tự dựng lại,
 * nên app khác không thể lợi dụng màn Đăng nhập để mở màn tùy ý.
 */
public final class DeepLink {
    public static final String EXTRA_KIND = "dl_kind";
    public static final String EXTRA_NOTE_ID = "dl_note_id";
    public static final String EXTRA_DATE = "dl_date";
    public static final String EXTRA_TIME = "dl_time";
    public static final String EXTRA_KEY = "dl_key";

    public static final String KIND_NOTE = "NOTE";
    public static final String KIND_REVIEW = "REVIEW";
    public static final String KIND_SKIP = "SKIP";

    private DeepLink() {
    }

    public static Intent put(Intent intent, String kind, long noteId, @Nullable String date,
                             @Nullable String time, @Nullable String dedupeKey) {
        return intent.putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_NOTE_ID, noteId)
                .putExtra(EXTRA_DATE, date)
                .putExtra(EXTRA_TIME, time)
                .putExtra(EXTRA_KEY, dedupeKey);
    }

    public static void copy(@Nullable Intent from, Intent to) {
        if (from == null || from.getStringExtra(EXTRA_KIND) == null) return;
        put(to, from.getStringExtra(EXTRA_KIND), from.getLongExtra(EXTRA_NOTE_ID, 0),
                from.getStringExtra(EXTRA_DATE), from.getStringExtra(EXTRA_TIME),
                from.getStringExtra(EXTRA_KEY));
    }

    /** Dựng lại Intent của đích đến; null nếu không có hoặc thiếu dữ liệu. */
    @Nullable
    public static Intent target(Context ctx, @Nullable Intent holder) {
        if (holder == null) return null;
        String kind = holder.getStringExtra(EXTRA_KIND);
        String date = holder.getStringExtra(EXTRA_DATE);
        if (kind == null || date == null) return null;
        String time = holder.getStringExtra(EXTRA_TIME);
        String key = holder.getStringExtra(EXTRA_KEY);
        switch (kind) {
            case KIND_NOTE:
                return NoteDetailActivity.intent(ctx, holder.getLongExtra(EXTRA_NOTE_ID, 0),
                        date, key);
            case KIND_REVIEW:
                return ReviewActivity.intentForGroup(ctx, date, time, key);
            case KIND_SKIP:
                return SkipDialogActivity.intentForGroup(ctx, date, time, key);
            default:
                return null;
        }
    }
}
