package com.reno.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Bảng app_notifications – thông báo đã gửi, hiển thị ở màn Thông báo. */
@Entity(tableName = "app_notifications",
        foreignKeys = {
                @ForeignKey(entity = UserEntity.class, parentColumns = "id",
                        childColumns = "user_id", onDelete = ForeignKey.CASCADE),
                @ForeignKey(entity = NoteEntity.class, parentColumns = "id",
                        childColumns = "note_id", onDelete = ForeignKey.CASCADE)},
        indices = {
                @Index(value = {"user_id", "dedupe_key"}, unique = true),
                @Index(value = {"user_id", "created_at"}),
                @Index(value = {"note_id"})})
public class AppNotificationEntity {
    public static final String KIND_NOTE = "NOTE";
    public static final String KIND_VOCAB = "VOCAB";

    @PrimaryKey(autoGenerate = true) public long id;

    @ColumnInfo(name = "user_id") public long userId;

    @NonNull public String kind = KIND_NOTE;

    /** Có khi kind = NOTE. */
    @ColumnInfo(name = "note_id") public Long noteId;

    /** Ngày của lần xuất hiện / nhóm ôn, yyyy-MM-dd. */
    @NonNull @ColumnInfo(name = "ref_date") public String refDate = "";

    /** NOTE:12:2026-11-13 hoặc VOCAB:2026-11-13T20:30 – mỗi mốc một thông báo. */
    @NonNull @ColumnInfo(name = "dedupe_key") public String dedupeKey = "";

    @NonNull public String title = "";

    @NonNull public String body = "";

    @ColumnInfo(name = "is_read", defaultValue = "0") public int isRead;

    @ColumnInfo(name = "created_at") public long createdAt;
}
