package com.reno.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Bảng annotations – đánh dấu trong PDF, không ghi vào file PDF. */
@Entity(tableName = "annotations",
        foreignKeys = @ForeignKey(entity = TopicEntity.class, parentColumns = "id",
                childColumns = "topic_id", onDelete = ForeignKey.CASCADE),
        indices = {@Index(value = {"topic_id", "type"})})
public class AnnotationEntity {
    public static final String TYPE_QUESTION = "QUESTION";
    public static final String TYPE_IMPORTANT = "IMPORTANT";
    public static final String TYPE_STAR = "STAR";
    public static final String TYPE_HIGHLIGHT = "HIGHLIGHT";
    public static final String TYPE_NOTE = "NOTE";
    /** Thứ tự cố định của 5 nút / 5 ô đếm / 5 tab lọc. */
    public static final String[] TYPES = {
            TYPE_QUESTION, TYPE_IMPORTANT, TYPE_STAR, TYPE_HIGHLIGHT, TYPE_NOTE};

    @PrimaryKey(autoGenerate = true) public long id;

    @ColumnInfo(name = "topic_id") public long topicId;

    @NonNull public String type = TYPE_NOTE;

    /** Trang, đếm từ 0. */
    @ColumnInfo(name = "page_index") public int pageIndex;

    /** JSON [[x, y, rộng, cao], …] theo tỉ lệ 0–1 so với trang. */
    @NonNull public String rects = "[]";

    @NonNull @ColumnInfo(name = "selected_text") public String selectedText = "";

    public String title;

    public String detail;

    /** #RRGGBB, chỉ có với HIGHLIGHT. */
    public String color;

    @ColumnInfo(name = "created_at") public long createdAt;

    @ColumnInfo(name = "updated_at") public long updatedAt;
}
