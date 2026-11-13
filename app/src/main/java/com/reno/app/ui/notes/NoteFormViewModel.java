package com.reno.app.ui.notes;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.reno.app.data.db.entity.NoteEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Giữ nội dung đang nhập dở của màn thêm / sửa ghi chú. */
public class NoteFormViewModel extends ViewModel {
    public boolean initialized;
    /** Ghi chú đang sửa; null khi thêm mới. */
    @Nullable public NoteEntity editing;
    public LocalDate date;
    public LocalTime time;
    /** Ngày giờ bắt đầu lúc mở màn sửa – chỉ kiểm tra "không trước hiện tại" khi trường này đổi. */
    @Nullable public LocalDate originalDate;
    @Nullable public LocalTime originalTime;
    public String color;
    public String repeatType = NoteEntity.REPEAT_NONE;
    @Nullable public LocalDate repeatUntil;
    public final List<LocalDate> exdates = new ArrayList<>();
    public final List<LocalDate> rdates = new ArrayList<>();
}
