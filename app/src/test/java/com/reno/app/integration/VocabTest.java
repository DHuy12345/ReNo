package com.reno.app.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlarmManager;
import android.content.Context;

import com.reno.app.R;
import com.reno.app.TestEnv;
import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.entity.AppNotificationEntity;
import com.reno.app.data.db.entity.FolderEntity;
import com.reno.app.data.db.entity.WordReviewEntity;
import com.reno.app.data.model.ReviewItem;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.data.repository.VocabRepository;
import com.reno.app.reminder.ReminderScheduler;
import com.reno.app.util.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAlarmManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Từ vựng, lịch ôn 5 lần, gom thông báo theo giờ nhắc và hộp thoại Bỏ qua (TC16–TC19, TC23). */
@RunWith(RobolectricTestRunner.class)
public class VocabTest {
    private RenoDatabase db;
    private VocabRepository vocab;
    private long userId;
    private long folderId;

    @Before
    public void setUp() throws Exception {
        TestEnv.reset();
        db = RenoDatabase.get(TestEnv.context());
        vocab = VocabRepository.get(TestEnv.context());
        userId = Fixtures.loggedInUser("learner", "23:58");
        long[] created = new long[2];
        vocab.createFolder(userId, "IELTS", r -> System.arraycopy(r, 0, created, 0, 2));
        TestEnv.drain();
        assertEquals(0, created[0]);
        folderId = created[1];
    }

    private void addWords(int count, String remindTime) {
        for (int i = 0; i < count; i++) {
            VocabRepository.WordResult r = vocab.saveWordSync(userId, 0, "word" + i + remindTime,
                    "nghĩa " + i, folderId, remindTime);
            assertTrue(r.isOk());
        }
    }

    /** Dời ngày ôn của mọi từ về hôm nay để kiểm tra phần thông báo. */
    private void moveAllStage1ToToday() {
        db.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE word_reviews SET due_date = ?, original_due_date = ? WHERE stage = 1",
                new Object[]{DateUtils.today().toString(), DateUtils.today().toString()});
    }

    @Test
    public void addWord_createsFiveReviews_andBlocksDuplicateInFolder() {
        VocabRepository.WordResult r = vocab.saveWordSync(userId, 0, " Resilient ",
                "kiên cường", folderId, null);
        assertTrue(r.isOk());
        assertEquals(DateUtils.today().plusDays(1), r.firstReview);
        long wordId = db.wordDao().listByFolder(folderId).get(0).id;
        List<WordReviewEntity> reviews = db.wordReviewDao().listByWord(wordId);
        assertEquals(5, reviews.size());
        assertEquals(DateUtils.today().plusMonths(3).toString(), reviews.get(4).dueDate);

        VocabRepository.WordResult dup = vocab.saveWordSync(userId, 0, "RESILIENT", "x",
                folderId, null);
        assertEquals(R.string.error_word_duplicate, dup.wordError);
    }

    @Test
    public void thirtyWordsSameTime_giveOneNotificationPerTime() {
        addWords(30, null);          // giờ mặc định 23:58
        addWords(2, "23:57");        // giờ tự chọn
        moveAllStage1ToToday();

        NotificationRepository repo = NotificationRepository.get(TestEnv.context());
        String today = DateUtils.today().toString();
        AppNotificationEntity def = repo.buildIfStillValid(userId,
                AppNotificationEntity.KIND_VOCAB, 0, today, "23:58");
        assertNotNull(def);
        assertEquals("Có 30 từ cần ôn tập", def.title);
        assertEquals("VOCAB:" + today + "T23:58", def.dedupeKey);
        AppNotificationEntity custom = repo.buildIfStillValid(userId,
                AppNotificationEntity.KIND_VOCAB, 0, today, "23:57");
        assertEquals("Có 2 từ cần ôn tập", custom.title);

        // Bộ lập lịch: mỗi mốc giờ đúng một báo thức (+ mốc 00:05 ngày mai).
        ReminderScheduler.get(TestEnv.context()).rescheduleNow(userId);
        AlarmManager am = (AlarmManager) TestEnv.context().getSystemService(Context.ALARM_SERVICE);
        ShadowAlarmManager shadow = shadowOf(am);
        List<ShadowAlarmManager.ScheduledAlarm> alarms = shadow.getScheduledAlarms();
        // 23:57 và 23:58 hôm nay (nếu chưa qua), 23:57 / 23:58 ngày mai không có, + 00:05
        int expected = 1;
        LocalDateTime now = DateUtils.now();
        if (now.toLocalTime().isBefore(java.time.LocalTime.of(23, 57))) expected += 2;
        else if (now.toLocalTime().isBefore(java.time.LocalTime.of(23, 58))) expected += 1;
        assertEquals(expected, alarms.size());

        // Đăng xuất: hủy hết báo thức.
        com.reno.app.util.SessionManager.logout(TestEnv.context());
        ReminderScheduler.get(TestEnv.context()).cancelAll();
        assertTrue(shadow.getScheduledAlarms().isEmpty());
        assertNull("tài khoản đã đăng xuất không được đặt lại báo thức", rescheduleAndPeek());
    }

    private ShadowAlarmManager.ScheduledAlarm rescheduleAndPeek() {
        ReminderScheduler.get(TestEnv.context()).rescheduleNow(userId);
        AlarmManager am = (AlarmManager) TestEnv.context().getSystemService(Context.ALARM_SERVICE);
        return shadowOf(am).peekNextScheduledAlarm();
    }

    @Test
    public void skip_postponeBeyondNextReview_isBlocked() {
        addWords(1, null);
        long wordId = db.wordDao().listByFolder(folderId).get(0).id;
        WordReviewEntity stage1 = db.wordReviewDao().findStage(wordId, 1);   // ngày tạo + 1
        List<Long> ids = Collections.singletonList(stage1.id);

        // Lần 2 ở ngày tạo + 3: lùi 2 ngày (→ +3) bị chặn, lùi 1 ngày (→ +2) được.
        VocabRepository.SkipResult blocked = vocab.postponeSync(userId, ids, 2);
        assertEquals(R.string.skip_error_limit, blocked.error);
        assertEquals(DateUtils.today().plusDays(3), blocked.limit);
        assertEquals(0, vocab.postponeSync(userId, ids, 1).error);
        WordReviewEntity moved = db.wordReviewDao().findById(stage1.id);
        assertEquals(DateUtils.today().plusDays(2).toString(), moved.dueDate);
        assertEquals(DateUtils.today().plusDays(1).toString(), moved.originalDueDate);

        // Nhắc vào thời điểm cụ thể: phải trước lần ôn kế tiếp.
        LocalDateTime tooLate = DateUtils.today().plusDays(3).atTime(9, 0);
        assertEquals(R.string.skip_error_limit, vocab.remindAtSync(userId, ids, tooLate).error);
        LocalDateTime ok = DateUtils.today().plusDays(1).atTime(9, 0);
        assertEquals(0, vocab.remindAtSync(userId, ids, ok).error);
        assertNotNull(db.wordReviewDao().findById(stage1.id).remindAt);

        // Dời sang lần ôn tiếp theo: SKIPPED, không đổi lịch các lần sau.
        assertEquals(0, vocab.skipToNextSync(userId, ids).error);
        assertEquals(WordReviewEntity.STATUS_SKIPPED, db.wordReviewDao().findById(stage1.id).status);
        assertEquals(DateUtils.today().plusDays(3).toString(),
                db.wordReviewDao().findStage(wordId, 2).dueDate);
    }

    @Test
    public void missedReviews_areCountedAfterTheDay() {
        addWords(3, null);
        db.getOpenHelper().getWritableDatabase().execSQL(
                "UPDATE word_reviews SET due_date = ? WHERE stage = 1",
                new Object[]{DateUtils.today().minusDays(2).toString()});
        LocalDate past = DateUtils.today().minusDays(2);
        List<ReviewItem> items = vocab.itemsBetween(userId, past, past);
        assertEquals(3, items.size());
        List<Long> ids = new ArrayList<>();
        for (ReviewItem it : items) ids.add(it.reviewId);
        vocab.markDone(userId, ids.get(0), null);
        try {
            TestEnv.drain();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        int pending = 0;
        for (ReviewItem it : vocab.itemsBetween(userId, past, past)) {
            if (WordReviewEntity.STATUS_PENDING.equals(it.status)) pending++;
        }
        assertEquals(2, pending);
        assertEquals(1, db.folderDao().list(userId, FolderEntity.TYPE_VOCAB).size());
    }
}
