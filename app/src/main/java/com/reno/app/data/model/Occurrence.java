package com.reno.app.data.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.db.entity.NoteOccurrenceEntity;
import com.reno.app.util.DateUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Một lần xuất hiện của ghi chú tại một ngày, kèm trạng thái (null = chưa làm). */
public class Occurrence {
    @NonNull public final NoteEntity note;
    @NonNull public final LocalDate date;
    /** DONE, SKIPPED hoặc null (chưa có trạng thái). */
    @Nullable public final String status;

    public Occurrence(@NonNull NoteEntity note, @NonNull LocalDate date, @Nullable String status) {
        this.note = note;
        this.date = date;
        this.status = status;
    }

    public boolean isDone() {
        return NoteOccurrenceEntity.STATUS_DONE.equals(status);
    }

    public boolean isSkipped() {
        return NoteOccurrenceEntity.STATUS_SKIPPED.equals(status);
    }

    /** Giờ bắt đầu (giống nhau ở mọi lần lặp). */
    @NonNull
    public LocalTime startTime() {
        return DateUtils.toLocalDateTime(note.dtstart).toLocalTime();
    }

    @NonNull
    public LocalDateTime start() {
        return LocalDateTime.of(date, startTime());
    }

    @NonNull
    public LocalDateTime end() {
        return start().plusMinutes(note.durationMin);
    }
}
