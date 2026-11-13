package com.reno.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Bảng notes – ghi chú và quy luật lặp theo chuẩn iCalendar (RRULE, RDATE, EXDATE). */
@Entity(tableName = "notes",
        foreignKeys = @ForeignKey(entity = UserEntity.class, parentColumns = "id",
                childColumns = "user_id", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"user_id"})})
public class NoteEntity {
    public static final String REPEAT_NONE = "NONE";
    public static final String REPEAT_DAILY = "DAILY";
    public static final String REPEAT_INTERVAL = "INTERVAL";
    public static final String REPEAT_CUSTOM = "CUSTOM";

    @PrimaryKey(autoGenerate = true) public long id;

    @ColumnInfo(name = "user_id") public long userId;

    @NonNull public String title = "";

    /** #RRGGBB – màu của ghi chú trên Lịch và trên thông báo. */
    @NonNull public String color = "#5B8DEF";

    /** Ngày giờ bắt đầu của lần đầu tiên (epoch ms). */
    public long dtstart;

    @ColumnInfo(name = "duration_min", defaultValue = "30") public int durationMin = 30;

    @NonNull @ColumnInfo(name = "repeat_type") public String repeatType = REPEAT_NONE;

    /** Ví dụ FREQ=DAILY;INTERVAL=3. Không ghi UNTIL ở đây. */
    public String rrule;

    /** yyyy-MM-dd; null = lặp vô hạn. */
    @ColumnInfo(name = "repeat_until") public String repeatUntil;

    /** Các ngày tự chọn, yyyy-MM-dd cách nhau dấu phẩy. */
    public String rdate;

    /** Các ngày bỏ qua, yyyy-MM-dd cách nhau dấu phẩy. */
    public String exdate;

    @ColumnInfo(name = "created_at") public long createdAt;

    @ColumnInfo(name = "updated_at") public long updatedAt;

    public boolean isRepeating() {
        return !REPEAT_NONE.equals(repeatType);
    }
}
