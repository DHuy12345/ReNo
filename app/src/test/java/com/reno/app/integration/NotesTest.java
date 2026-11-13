package com.reno.app.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.reno.app.TestEnv;
import com.reno.app.data.db.entity.AppNotificationEntity;
import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.db.entity.NoteOccurrenceEntity;
import com.reno.app.data.model.Occurrence;
import com.reno.app.data.repository.NoteRepository;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.util.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Ghi chú lặp, trạng thái từng ngày và kiểm tra lại khi báo thức chạy (TC12, TC14). */
@RunWith(RobolectricTestRunner.class)
public class NotesTest {
    private NoteRepository notes;
    private long userId;
    private final LocalDate today = DateUtils.today();

    @Before
    public void setUp() throws Exception {
        TestEnv.reset();
        notes = NoteRepository.get(TestEnv.context());
        userId = Fixtures.loggedInUser("noter", "20:00");
    }

    private NoteEntity every3DaysSkipping(LocalDate start, LocalDate skipped) throws Exception {
        NoteEntity n = new NoteEntity();
        n.userId = userId;
        n.title = "Ôn chương 5";
        n.color = "#5B8DEF";
        n.dtstart = DateUtils.toEpochMs(LocalDateTime.of(start, LocalTime.of(19, 0)));
        n.durationMin = 45;
        n.repeatType = NoteEntity.REPEAT_INTERVAL;
        n.rrule = "FREQ=DAILY;INTERVAL=3";
        n.exdate = skipped.toString();
        notes.save(n, () -> { });
        TestEnv.drain();
        return n;
    }

    @Test
    public void interval_withSkippedDay_showsRightDays() throws Exception {
        every3DaysSkipping(today, today.plusDays(6));
        List<Occurrence> list = notes.occurrencesBetween(userId, today, today.plusDays(12));
        assertEquals(4, list.size());
        assertEquals(today, list.get(0).date);
        assertEquals(today.plusDays(3), list.get(1).date);
        assertEquals(today.plusDays(9), list.get(2).date);
        assertEquals(today.plusDays(12), list.get(3).date);
        assertTrue(notes.occurrencesBetween(userId, today.plusDays(6), today.plusDays(6))
                .isEmpty());
    }

    @Test
    public void tickToday_doesNotTickNextOccurrence_andUntickRemovesRow() throws Exception {
        NoteEntity n = new NoteEntity();
        n.userId = userId;
        n.title = "Chạy bộ";
        n.color = "#81B29A";
        n.dtstart = DateUtils.toEpochMs(LocalDateTime.of(today, LocalTime.of(23, 59)));
        n.repeatType = NoteEntity.REPEAT_DAILY;
        n.rrule = "FREQ=DAILY;INTERVAL=1";
        notes.save(n, () -> { });
        TestEnv.drain();

        notes.setStatus(n.id, today, NoteOccurrenceEntity.STATUS_DONE);
        TestEnv.drain();
        List<Occurrence> two = notes.occurrencesBetween(userId, today, today.plusDays(1));
        assertTrue(two.get(0).isDone());
        assertNull(two.get(1).status);

        notes.setStatus(n.id, today, null);
        TestEnv.drain();
        assertNull(notes.findOccurrence(n.id, today).status);
    }

    @Test
    public void reminder_isNotBuiltForSkippedDay_doneOccurrence_orDeletedNote() throws Exception {
        NoteEntity n = every3DaysSkipping(today, today.plusDays(3));
        NotificationRepository repo = NotificationRepository.get(TestEnv.context());
        String kind = AppNotificationEntity.KIND_NOTE;

        AppNotificationEntity built = repo.buildIfStillValid(userId, kind, n.id,
                today.toString(), null);
        assertEquals("NOTE:" + n.id + ":" + today, built.dedupeKey);
        assertTrue(built.title.startsWith("Ghi chú: Ôn chương 5 – 19:00"));
        assertTrue(repo.insertIfNew(built));
        assertFalse("cùng dedupe_key không tạo hai thông báo", repo.insertIfNew(
                repo.buildIfStillValid(userId, kind, n.id, today.toString(), null)));

        assertNull("ngày bị bỏ qua không nhắc", repo.buildIfStillValid(userId, kind, n.id,
                today.plusDays(3).toString(), null));

        notes.setStatusSync(n.id, today, NoteOccurrenceEntity.STATUS_DONE);
        assertNull(repo.buildIfStillValid(userId, kind, n.id, today.toString(), null));

        notes.delete(n.id);
        TestEnv.drain();
        assertNull(repo.buildIfStillValid(userId, kind, n.id, today.plusDays(9).toString(),
                null));
        assertTrue("xóa ghi chú xóa luôn thông báo của nó", repo.list(userId).isEmpty());
    }
}
