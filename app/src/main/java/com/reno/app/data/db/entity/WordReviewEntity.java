package com.reno.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Bảng word_reviews – 5 lần ôn của mỗi từ. */
@Entity(tableName = "word_reviews",
        foreignKeys = @ForeignKey(entity = WordEntity.class, parentColumns = "id",
                childColumns = "word_id", onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(value = {"word_id", "stage"}, unique = true),
                @Index(value = {"due_date", "status"}),
                @Index(value = {"word_id"})})
public class WordReviewEntity {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_SKIPPED = "SKIPPED";

    @PrimaryKey(autoGenerate = true) public long id;

    @ColumnInfo(name = "word_id") public long wordId;

    /** Lần ôn thứ mấy, 1–5. */
    public int stage;

    /** Ngày ôn tính từ ngày tạo, không bao giờ đổi. */
    @NonNull @ColumnInfo(name = "original_due_date") public String originalDueDate = "";

    /** Ngày ôn hiện tại, đổi khi lùi lịch. */
    @NonNull @ColumnInfo(name = "due_date") public String dueDate = "";

    /** Giờ hẹn lại cụ thể (epoch ms); null = nhắc theo giờ nhắc của từ. */
    @ColumnInfo(name = "remind_at") public Long remindAt;

    @NonNull @ColumnInfo(defaultValue = "PENDING") public String status = STATUS_PENDING;

    @ColumnInfo(name = "done_at") public Long doneAt;
}
