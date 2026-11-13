package com.reno.app.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;

import com.reno.app.util.DateUtils;

import java.time.LocalTime;

/** Một lần ôn kèm thông tin của từ. Kết quả của các truy vấn JOIN word_reviews – words – users. */
public class ReviewItem {
    @ColumnInfo(name = "review_id") public long reviewId;
    @ColumnInfo(name = "word_id") public long wordId;
    public int stage;
    @NonNull @ColumnInfo(name = "original_due_date") public String originalDueDate = "";
    @NonNull @ColumnInfo(name = "due_date") public String dueDate = "";
    @ColumnInfo(name = "remind_at") public Long remindAt;
    @NonNull public String status = "";
    @ColumnInfo(name = "done_at") public Long doneAt;
    @NonNull public String word = "";
    @NonNull public String meaning = "";
    /** Giờ nhắc của từ: giờ riêng hoặc giờ mặc định của tài khoản (HH:mm). */
    @NonNull @ColumnInfo(name = "word_time") public String wordTime = "";

    /** Giờ nhắc thực tế của lần ôn này: giờ đã hẹn lại (remind_at) nếu có, không thì giờ của từ. */
    @NonNull
    public String effectiveTime() {
        if (remindAt != null) {
            LocalTime t = DateUtils.toLocalDateTime(remindAt).toLocalTime();
            return DateUtils.formatTime(t);
        }
        return wordTime;
    }
}
