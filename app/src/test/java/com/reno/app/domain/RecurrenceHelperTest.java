package com.reno.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RecurrenceHelperTest {
    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    private static List<LocalDate> dates(String... s) {
        LocalDate[] out = new LocalDate[s.length];
        for (int i = 0; i < s.length; i++) out[i] = d(s[i]);
        return Arrays.asList(out);
    }

    @Test
    public void noRepeat_onlyStartDate() {
        assertEquals(dates("2026-11-13"), RecurrenceHelper.occurrenceDates(d("2026-11-13"),
                null, null, null, null, d("2026-11-01"), d("2026-12-31")));
        assertTrue(RecurrenceHelper.occurrenceDates(d("2026-11-13"),
                null, null, null, null, d("2026-11-14"), d("2026-12-31")).isEmpty());
    }

    /** Ví dụ của đặc tả 5.3: cách 3 ngày từ 13/11, bỏ 19/11. */
    @Test
    public void every3Days_withSkippedDay() {
        List<LocalDate> got = RecurrenceHelper.occurrenceDates(d("2026-11-13"),
                "FREQ=DAILY;INTERVAL=3", null, null, "2026-11-19",
                d("2026-11-13"), d("2026-11-26"));
        assertEquals(dates("2026-11-13", "2026-11-16", "2026-11-22", "2026-11-25"), got);
    }

    @Test
    public void daily_windowInTheFuture_isFastForwarded() {
        List<LocalDate> got = RecurrenceHelper.occurrenceDates(d("2020-01-01"),
                "FREQ=DAILY;INTERVAL=1", null, null, null, d("2026-11-13"), d("2026-11-15"));
        assertEquals(dates("2026-11-13", "2026-11-14", "2026-11-15"), got);
    }

    @Test
    public void interval_keepsPhaseAfterFastForward() {
        // 01/01, 08/01, 15/01 ... cách 7 ngày; cửa sổ 10/01–22/01 phải cho 15/01 và 22/01
        List<LocalDate> got = RecurrenceHelper.occurrenceDates(d("2026-01-01"),
                "FREQ=DAILY;INTERVAL=7", null, null, null, d("2026-01-10"), d("2026-01-22"));
        assertEquals(dates("2026-01-15", "2026-01-22"), got);
    }

    @Test
    public void repeatUntil_stopsTheRule_inclusive() {
        List<LocalDate> got = RecurrenceHelper.occurrenceDates(d("2026-11-13"),
                "FREQ=DAILY;INTERVAL=1", "2026-11-15", null, null,
                d("2026-11-01"), d("2026-12-31"));
        assertEquals(dates("2026-11-13", "2026-11-14", "2026-11-15"), got);
    }

    @Test
    public void custom_startPlusChosenDates() {
        List<LocalDate> got = RecurrenceHelper.occurrenceDates(d("2026-12-02"),
                null, null, "2026-12-20,2026-12-09", null, d("2026-12-01"), d("2026-12-31"));
        assertEquals(dates("2026-12-02", "2026-12-09", "2026-12-20"), got);
        // ngày bắt đầu nằm trong RDATE cũng không bị lặp hai lần
        got = RecurrenceHelper.occurrenceDates(d("2026-12-02"),
                null, null, "2026-12-02,2026-12-09", null, d("2026-12-01"), d("2026-12-31"));
        assertEquals(dates("2026-12-02", "2026-12-09"), got);
    }

    @Test
    public void singleDayWindow() {
        assertEquals(dates("2026-11-16"), RecurrenceHelper.occurrenceDates(d("2026-11-13"),
                "FREQ=DAILY;INTERVAL=3", null, null, "2026-11-19", d("2026-11-16"), d("2026-11-16")));
        assertEquals(Collections.emptyList(), RecurrenceHelper.occurrenceDates(d("2026-11-13"),
                "FREQ=DAILY;INTERVAL=3", null, null, "2026-11-19", d("2026-11-19"), d("2026-11-19")));
        assertEquals(Collections.emptyList(), RecurrenceHelper.occurrenceDates(d("2026-11-13"),
                "FREQ=DAILY;INTERVAL=3", null, null, "2026-11-19", d("2026-11-17"), d("2026-11-17")));
    }

    @Test
    public void infiniteRule_alwaysStopsAtWindowEnd() {
        List<LocalDate> got = RecurrenceHelper.occurrenceDates(d("2026-11-13"),
                "FREQ=DAILY;INTERVAL=1", null, null, null, d("2026-11-13"), d("2027-11-12"));
        assertEquals(365, got.size());
    }

    @Test
    public void invalidRule_fallsBackToStartOnly() {
        assertEquals(dates("2026-11-13"), RecurrenceHelper.occurrenceDates(d("2026-11-13"),
                "NOT A RULE", null, null, null, d("2026-11-01"), d("2026-12-31")));
    }

    @Test
    public void intervalOf_readsRule() {
        assertEquals(3, RecurrenceHelper.intervalOf("FREQ=DAILY;INTERVAL=3"));
        assertEquals(1, RecurrenceHelper.intervalOf("FREQ=DAILY"));
        assertEquals(1, RecurrenceHelper.intervalOf(null));
        assertEquals("FREQ=DAILY;INTERVAL=5", RecurrenceHelper.dailyRule(5));
    }
}
