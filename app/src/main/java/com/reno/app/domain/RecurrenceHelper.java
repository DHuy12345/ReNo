package com.reno.app.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.util.DateUtils;

import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.InstanceIterator;
import org.dmfs.rfc5545.RecurrenceSet;
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.dmfs.rfc5545.recurrenceset.Difference;
import org.dmfs.rfc5545.recurrenceset.FastForwarded;
import org.dmfs.rfc5545.recurrenceset.Merged;
import org.dmfs.rfc5545.recurrenceset.OfList;
import org.dmfs.rfc5545.recurrenceset.OfRule;
import org.dmfs.rfc5545.recurrenceset.Preceding;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sinh các ngày xuất hiện của một ghi chú từ quy luật iCalendar (RRULE, RDATE, EXDATE)
 * bằng RecurrenceSet của lib-recur.
 *
 * <p>Tập ngày = ngày bắt đầu + các ngày sinh ra từ RRULE (tới repeat_until) + RDATE, trừ EXDATE.
 * Mọi lần duyệt đều bị chặn bởi {@code to}: không bao giờ duyệt không giới hạn.
 */
public final class RecurrenceHelper {
    private static final Logger LOG = Logger.getLogger("RecurrenceHelper");

    private RecurrenceHelper() {
    }

    /** RRULE của kiểu "Mỗi ngày" (n = 1) và "Cách n ngày" (n = 2–65). */
    @NonNull
    public static String dailyRule(int interval) {
        return "FREQ=DAILY;INTERVAL=" + Math.max(1, interval);
    }

    /** Đọc INTERVAL từ chuỗi RRULE; không có thì là 1. */
    public static int intervalOf(@Nullable String rrule) {
        if (rrule == null) return 1;
        for (String part : rrule.split(";")) {
            if (part.startsWith("INTERVAL=")) {
                try {
                    return Integer.parseInt(part.substring("INTERVAL=".length()).trim());
                } catch (NumberFormatException ignored) {
                    return 1;
                }
            }
        }
        return 1;
    }

    /** Các ngày ghi chú xuất hiện trong khoảng [from, to], tăng dần. */
    @NonNull
    public static List<LocalDate> occurrenceDates(@NonNull NoteEntity n,
                                                  @NonNull LocalDate from, @NonNull LocalDate to) {
        LocalDate start = DateUtils.toLocalDate(n.dtstart);
        return occurrenceDates(start, n.rrule, n.repeatUntil, n.rdate, n.exdate, from, to);
    }

    @NonNull
    public static List<LocalDate> occurrenceDates(@NonNull LocalDate start, @Nullable String rrule,
                                                  @Nullable String repeatUntil,
                                                  @Nullable String rdate, @Nullable String exdate,
                                                  @NonNull LocalDate from, @NonNull LocalDate to) {
        List<LocalDate> result = new ArrayList<>();
        if (to.isBefore(from)) return result;

        // Ngày bắt đầu + RRULE; RRULE dừng ở repeat_until (cột riêng, không ghi UNTIL trong chuỗi).
        RecurrenceSet ruleSet = new OfList(toDateTime(start));
        if (rrule != null && !rrule.trim().isEmpty()) {
            try {
                ruleSet = new OfRule(new RecurrenceRule(rrule.trim()), toDateTime(start));
            } catch (InvalidRecurrenceRuleException e) {
                LOG.log(Level.WARNING, "RRULE không hợp lệ: " + rrule, e);
            }
        }
        LocalDate until = parseOrNull(repeatUntil);
        if (until != null) {
            ruleSet = new Preceding(toDateTime(until.plusDays(1)), ruleSet);
        }

        List<RecurrenceSet> included = new ArrayList<>();
        included.add(ruleSet);
        List<LocalDate> extra = DateUtils.parseDateList(rdate);
        if (!extra.isEmpty()) included.add(new OfList(toDateTimes(extra)));
        RecurrenceSet set = new Merged(included);

        List<LocalDate> excluded = DateUtils.parseDateList(exdate);
        if (!excluded.isEmpty()) set = new Difference(set, new OfList(toDateTimes(excluded)));

        // Chỉ lấy [from, to]: bỏ qua phần trước from, dừng ngay khi vượt to.
        set = new Preceding(toDateTime(to.plusDays(1)), new FastForwarded(toDateTime(from), set));
        InstanceIterator it = set.iterator();
        while (it.hasNext()) {
            LocalDate d = toLocalDate(it.next());
            if (d.isAfter(to)) break;          // luôn có điểm dừng
            if (!d.isBefore(from)) result.add(d);
        }
        return result;
    }

    /** Ghi chú có xuất hiện vào đúng ngày này không. */
    public static boolean occursOn(@NonNull NoteEntity n, @NonNull LocalDate date) {
        return !occurrenceDates(n, date, date).isEmpty();
    }

    @Nullable
    private static LocalDate parseOrNull(@Nullable String date) {
        if (date == null || date.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(date.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** DateTime "cả ngày" của lib-recur; tháng đếm từ 0. */
    private static DateTime toDateTime(LocalDate d) {
        return new DateTime(d.getYear(), d.getMonthValue() - 1, d.getDayOfMonth());
    }

    private static DateTime[] toDateTimes(List<LocalDate> dates) {
        DateTime[] out = new DateTime[dates.size()];
        for (int i = 0; i < out.length; i++) out[i] = toDateTime(dates.get(i));
        return out;
    }

    private static LocalDate toLocalDate(DateTime dt) {
        return LocalDate.of(dt.getYear(), dt.getMonth() + 1, dt.getDayOfMonth());
    }
}
