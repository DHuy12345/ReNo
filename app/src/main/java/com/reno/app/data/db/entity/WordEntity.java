package com.reno.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Bảng words – từ vựng trong một thư mục loại VOCAB. */
@Entity(tableName = "words",
        foreignKeys = @ForeignKey(entity = FolderEntity.class, parentColumns = "id",
                childColumns = "folder_id", onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(value = {"folder_id", "word"}, unique = true),
                @Index(value = {"folder_id"})})
public class WordEntity {
    @PrimaryKey(autoGenerate = true) public long id;

    @ColumnInfo(name = "folder_id") public long folderId;

    @NonNull @ColumnInfo(collate = ColumnInfo.NOCASE) public String word = "";

    @NonNull public String meaning = "";

    /** Giờ nhắc riêng HH:mm; null = dùng giờ mặc định users.review_time. */
    @ColumnInfo(name = "remind_time") public String remindTime;

    /** yyyy-MM-dd – gốc để tính lịch ôn. */
    @NonNull @ColumnInfo(name = "created_date") public String createdDate = "";

    /** Thứ tự flashcard. */
    @ColumnInfo(name = "created_at") public long createdAt;
}
