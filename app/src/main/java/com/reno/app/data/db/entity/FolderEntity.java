package com.reno.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Bảng folders – thư mục dùng chung cho tab Read (READ) và Vocabulary (VOCAB). */
@Entity(tableName = "folders",
        foreignKeys = @ForeignKey(entity = UserEntity.class, parentColumns = "id",
                childColumns = "user_id", onDelete = ForeignKey.CASCADE),
        indices = {
                @Index(value = {"user_id", "type", "name"}, unique = true),
                @Index(value = {"user_id", "type"})})
public class FolderEntity {
    public static final String TYPE_READ = "READ";
    public static final String TYPE_VOCAB = "VOCAB";

    @PrimaryKey(autoGenerate = true) public long id;

    @ColumnInfo(name = "user_id") public long userId;

    @NonNull public String type = TYPE_READ;

    @NonNull @ColumnInfo(collate = ColumnInfo.NOCASE) public String name = "";

    @ColumnInfo(name = "created_at") public long createdAt;
}
