package com.reno.app.ui.common;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.DateValidatorPointForward;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.function.Consumer;

/** DatePicker và TimePicker của Material, quy đổi sang LocalDate / LocalTime. */
public final class Pickers {
    private Pickers() {
    }

    /**
     * @param min ngày nhỏ nhất được chọn; null = không giới hạn
     */
    public static void pickDate(FragmentManager fm, LocalDate initial, @Nullable LocalDate min,
                                Consumer<LocalDate> onPicked) {
        MaterialDatePicker.Builder<Long> builder = MaterialDatePicker.Builder.datePicker()
                .setSelection(toUtcMillis(initial));
        if (min != null) {
            builder.setCalendarConstraints(new CalendarConstraints.Builder()
                    .setStart(toUtcMillis(min))
                    .setValidator(DateValidatorPointForward.from(toUtcMillis(min)))
                    .build());
        }
        MaterialDatePicker<Long> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection != null) {
                // MaterialDatePicker trả về nửa đêm theo giờ UTC của ngày được chọn
                onPicked.accept(Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toLocalDate());
            }
        });
        picker.show(fm, "date_picker");
    }

    public static void pickTime(FragmentManager fm, LocalTime initial, Consumer<LocalTime> onPicked) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(initial.getHour())
                .setMinute(initial.getMinute())
                .build();
        picker.addOnPositiveButtonClickListener(v ->
                onPicked.accept(LocalTime.of(picker.getHour(), picker.getMinute())));
        picker.show(fm, "time_picker");
    }

    private static long toUtcMillis(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
}
