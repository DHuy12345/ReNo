package com.reno.app.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.reno.app.R;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Định dạng và chuyển đổi ngày giờ. Dùng java.time và múi giờ của thiết bị.
 * Trong CSDL: ngày = yyyy-MM-dd, giờ = HH:mm, thời điểm = epoch ms.
 */
public final class DateUtils {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT);
    private static final DateTimeFormatter FULL_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    private DateUtils() {
    }

    public static ZoneId zone() {
        return ZoneId.systemDefault();
    }

    public static LocalDate today() {
        return LocalDate.now(zone());
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(zone());
    }

    public static LocalDateTime toLocalDateTime(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(zone()).toLocalDateTime();
    }

    public static LocalDate toLocalDate(long epochMs) {
        return toLocalDateTime(epochMs).toLocalDate();
    }

    public static long toEpochMs(LocalDateTime dateTime) {
        return dateTime.atZone(zone()).toInstant().toEpochMilli();
    }

    /** HH:mm */
    public static String formatTime(LocalTime time) {
        return time.format(TIME);
    }

    /** HH:mm → LocalTime; chuỗi sai trả về 20:00. */
    public static LocalTime parseTime(@Nullable String hhmm) {
        try {
            return LocalTime.parse(hhmm, TIME);
        } catch (RuntimeException e) {
            return LocalTime.of(20, 0);
        }
    }

    /** dd/MM */
    public static String formatDayMonth(LocalDate date) {
        return date.format(DAY_MONTH);
    }

    /** dd/MM/yyyy */
    public static String formatFullDate(LocalDate date) {
        return date.format(FULL_DATE);
    }

    /** "a,b,c" → [a, b, c]; null hoặc rỗng → danh sách rỗng. */
    @NonNull
    public static List<LocalDate> parseDateList(@Nullable String csv) {
        List<LocalDate> out = new ArrayList<>();
        if (csv == null || csv.trim().isEmpty()) return out;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (t.isEmpty()) continue;
            try {
                out.add(LocalDate.parse(t));
            } catch (RuntimeException ignored) {
                // bỏ qua giá trị hỏng thay vì làm sập màn hình
            }
        }
        return out;
    }

    /** [a, b, c] → "a,b,c"; danh sách rỗng → null (cột để NULL). */
    @Nullable
    public static String joinDateList(@Nullable List<LocalDate> dates) {
        if (dates == null || dates.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (LocalDate d : dates) {
            if (sb.length() > 0) sb.append(',');
            sb.append(d);
        }
        return sb.toString();
    }

    /** "Vừa xong", "5 phút trước", "2 giờ trước", "3 ngày trước" hoặc dd/MM/yyyy. */
    public static String relativeTime(Context ctx, long epochMs) {
        long diffMin = Math.max(0, (System.currentTimeMillis() - epochMs) / 60_000L);
        if (diffMin < 1) return ctx.getString(R.string.time_just_now);
        if (diffMin < 60) return ctx.getString(R.string.time_minutes_ago, (int) diffMin);
        long hours = diffMin / 60;
        if (hours < 24) return ctx.getString(R.string.time_hours_ago, (int) hours);
        long days = hours / 24;
        if (days < 7) return ctx.getString(R.string.time_days_ago, (int) days);
        return formatFullDate(toLocalDate(epochMs));
    }
}
