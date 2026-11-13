package com.reno.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Bảng topics – mỗi dòng là một file PDF đã chép vào filesDir/pdfs. */
@Entity(tableName = "topics",
        foreignKeys = @ForeignKey(entity = FolderEntity.class, parentColumns = "id",
                childColumns = "folder_id", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"folder_id"}), @Index(value = {"last_opened_at"})})
public class TopicEntity {
    @PrimaryKey(autoGenerate = true) public long id;

    @ColumnInfo(name = "folder_id") public long folderId;

    @NonNull public String name = "";

    /** Đường dẫn tuyệt đối tới file trong filesDir/pdfs. */
    @NonNull @ColumnInfo(name = "file_path") public String filePath = "";

    @ColumnInfo(name = "original_name") public String originalName;

    @ColumnInfo(name = "page_count") public int pageCount;

    /** Ảnh nền cố định 1–8, chọn ngẫu nhiên khi tạo, không bao giờ đổi. */
    @ColumnInfo(name = "bg_index") public int bgIndex;

    @ColumnInfo(name = "last_page", defaultValue = "0") public int lastPage;

    @ColumnInfo(name = "last_opened_at") public long lastOpenedAt;

    @ColumnInfo(name = "created_at") public long createdAt;
}
