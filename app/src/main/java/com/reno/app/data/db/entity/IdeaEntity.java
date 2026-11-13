package com.reno.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Bảng ideas – ý tưởng. */
@Entity(tableName = "ideas",
        foreignKeys = @ForeignKey(entity = UserEntity.class, parentColumns = "id",
                childColumns = "user_id", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"user_id", "is_done", "created_at"})})
public class IdeaEntity {
    @PrimaryKey(autoGenerate = true) public long id;

    @ColumnInfo(name = "user_id") public long userId;

    @NonNull public String content = "";

    /** 0 = Chưa thực hiện, 1 = Đã thực hiện. */
    @ColumnInfo(name = "is_done", defaultValue = "0") public int isDone;

    @ColumnInfo(name = "done_at") public Long doneAt;

    @ColumnInfo(name = "created_at") public long createdAt;

    @ColumnInfo(name = "updated_at") public long updatedAt;
}
