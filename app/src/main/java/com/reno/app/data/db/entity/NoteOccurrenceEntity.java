package com.reno.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Bảng note_occurrences – trạng thái của từng ngày; không có dòng nghĩa là chưa làm. */
@Entity(tableName = "note_occurrences",
        foreignKeys = @ForeignKey(entity = NoteEntity.class, parentColumns = "id",
                childColumns = "note_id", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"note_id", "occ_date"}, unique = true)})
public class NoteOccurrenceEntity {
    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_SKIPPED = "SKIPPED";

    @PrimaryKey(autoGenerate = true) public long id;

    @ColumnInfo(name = "note_id") public long noteId;

    /** yyyy-MM-dd */
    @NonNull @ColumnInfo(name = "occ_date") public String occDate = "";

    @NonNull public String status = STATUS_DONE;

    @ColumnInfo(name = "updated_at") public long updatedAt;
}
