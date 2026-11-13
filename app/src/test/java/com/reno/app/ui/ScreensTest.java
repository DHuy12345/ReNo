package com.reno.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.reno.app.R;
import com.reno.app.TestEnv;
import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.entity.FolderEntity;
import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.db.entity.TopicEntity;
import com.reno.app.data.db.entity.UserEntity;
import com.reno.app.data.db.entity.WordEntity;
import com.reno.app.data.repository.NoteRepository;
import com.reno.app.data.repository.VocabRepository;
import com.reno.app.reminder.SkipDialogActivity;
import com.reno.app.ui.auth.LoginActivity;
import com.reno.app.ui.auth.RegisterActivity;
import com.reno.app.ui.main.GuideActivity;
import com.reno.app.ui.main.MainActivity;
import com.reno.app.ui.notes.NoteDetailActivity;
import com.reno.app.ui.notes.NoteFormActivity;
import com.reno.app.ui.profile.ProfileActivity;
import com.reno.app.ui.read.TopicActivity;
import com.reno.app.ui.vocab.FlashcardActivity;
import com.reno.app.ui.vocab.ReviewActivity;
import com.reno.app.ui.vocab.WordFormActivity;
import com.reno.app.util.DateUtils;
import com.reno.app.util.SessionManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalTime;

/** Mở thử mọi màn hình với dữ liệu mẫu: không màn nào được crash khi tạo hoặc khi vẽ lại. */
@RunWith(RobolectricTestRunner.class)
public class ScreensTest {
    private RenoDatabase db;
    private long userId;
    private long noteId;
    private long wordId;
    private long vocabFolderId;
    private long topicId;

    @Before
    public void setUp() throws Exception {
        TestEnv.reset();
        db = RenoDatabase.get(TestEnv.context());
        UserEntity u = new UserEntity();
        u.username = "huy";
        u.email = "huy@reno.test";
        u.passwordHash = "pbkdf2$1$AA==$AA==";
        u.reviewTime = "20:00";
        u.createdAt = System.currentTimeMillis();
        userId = db.userDao().insert(u);
        SessionManager.login(TestEnv.context(), userId);

        NoteEntity n = new NoteEntity();
        n.userId = userId;
        n.title = "Ôn chương 5";
        n.color = "#E07A5F";
        n.dtstart = DateUtils.toEpochMs(LocalDateTime.of(DateUtils.today(), LocalTime.of(9, 0)));
        n.durationMin = 90;
        n.repeatType = NoteEntity.REPEAT_INTERVAL;
        n.rrule = "FREQ=DAILY;INTERVAL=3";
        noteId = db.noteDao().insert(n);

        long[] folder = new long[2];
        VocabRepository.get(TestEnv.context()).createFolder(userId, "IELTS",
                r -> System.arraycopy(r, 0, folder, 0, 2));
        TestEnv.drain();
        vocabFolderId = folder[1];
        VocabRepository.get(TestEnv.context()).saveWordSync(userId, 0, "resilient",
                "kiên cường", vocabFolderId, null);
        VocabRepository.get(TestEnv.context()).saveWordSync(userId, 0, "thrive", "phát triển",
                vocabFolderId, "06:30");
        wordId = db.wordDao().listByFolder(vocabFolderId).get(0).id;
        for (WordEntity w : db.wordDao().listByFolder(vocabFolderId)) {
            db.wordReviewDao().reschedule(db.wordReviewDao().findStage(w.id, 1).id,
                    DateUtils.today().toString(), null);
        }

        FolderEntity rf = new FolderEntity();
        rf.userId = userId;
        rf.type = FolderEntity.TYPE_READ;
        rf.name = "Giáo trình";
        long readFolder = db.folderDao().insert(rf);
        TopicEntity t = new TopicEntity();
        t.folderId = readFolder;
        t.name = "Lập trình di động – chương 1";
        t.filePath = TestEnv.context().getFilesDir() + "/pdfs/x.pdf";
        t.pageCount = 12;
        t.bgIndex = 3;
        t.lastOpenedAt = 1;
        t.createdAt = 1;
        topicId = db.topicDao().insert(t);
        TestEnv.drain();
    }

    private final List<ActivityController<?>> launched = new ArrayList<>();

    /** Hủy mọi Activity đã mở để LiveData của chúng không còn chạy sang kiểm thử sau. */
    @After
    public void tearDown() throws Exception {
        for (ActivityController<?> c : launched) {
            try {
                c.pause().stop().destroy();
            } catch (RuntimeException ignored) {
                // Activity đã tự đóng (finish) trước đó
            }
        }
        TestEnv.settle();
        TestEnv.flushSharedPreferences();
    }

    private <T extends Activity> T launch(Class<T> type, Intent intent) throws Exception {
        ActivityController<T> controller = Robolectric.buildActivity(type, intent).setup();
        launched.add(controller);
        TestEnv.settle();
        controller.get().getWindow().getDecorView().getRootView()
                .measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        return controller.get();
    }

    private Intent intent(Class<?> type) {
        return new Intent(TestEnv.context(), type);
    }

    @Test
    public void login_whenLoggedOut_showsForm_andRegisterOpens() throws Exception {
        SessionManager.logout(TestEnv.context());
        LoginActivity login = launch(LoginActivity.class, intent(LoginActivity.class));
        assertFalse(login.isFinishing());
        launch(RegisterActivity.class, intent(RegisterActivity.class));
    }

    @Test
    public void login_whenLoggedIn_goesStraightToMain() throws Exception {
        LoginActivity login = launch(LoginActivity.class, intent(LoginActivity.class));
        assertTrue(login.isFinishing());
        Intent next = shadowOf(login).getNextStartedActivity();
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
    }

    @Test
    public void main_allFiveSections() throws Exception {
        MainActivity main = launch(MainActivity.class, intent(MainActivity.class));
        TextView username = main.findViewById(R.id.text_username);
        try {
            TestEnv.waitUntil(() -> "huy".contentEquals(username.getText()));
        } catch (AssertionError e) {
            throw new AssertionError("username='" + username.getText() + "' finishing="
                    + main.isFinishing() + " session=" + SessionManager.currentUserId(TestEnv.context())
                    + " user=" + db.userDao().findById(userId));
        }
        BottomNavigationView nav = main.findViewById(R.id.bottom_nav);
        int[] items = {R.id.nav_stats, R.id.nav_camera, R.id.nav_calendar, R.id.nav_notifications,
                R.id.nav_home};
        for (int item : items) {
            nav.setSelectedItemId(item);
            TestEnv.settle();
            assertNotNull(main.getSupportFragmentManager().findFragmentById(R.id.container));
        }
        // 4 tab con của Nhà
        androidx.viewpager2.widget.ViewPager2 pager = main.findViewById(R.id.pager);
        for (int i = 0; i < 4; i++) {
            pager.setCurrentItem(i, false);
            TestEnv.settle();
        }
    }

    @Test
    public void deepScreens_open() throws Exception {
        launch(GuideActivity.class, intent(GuideActivity.class));
        launch(ProfileActivity.class, intent(ProfileActivity.class));
        launch(TopicActivity.class, TopicActivity.intent(TestEnv.context(), topicId));
        launch(NoteFormActivity.class, NoteFormActivity.intentForNew(TestEnv.context(),
                DateUtils.today()));
        launch(NoteFormActivity.class, NoteFormActivity.intentForEdit(TestEnv.context(), noteId));
        NoteDetailActivity detail = launch(NoteDetailActivity.class, NoteDetailActivity.intent(
                TestEnv.context(), noteId, DateUtils.today().toString(), null));
        assertFalse(detail.isFinishing());
        launch(WordFormActivity.class, WordFormActivity.intentForNew(TestEnv.context(),
                "hello", "xin chào"));
        launch(WordFormActivity.class, WordFormActivity.intentForEdit(TestEnv.context(), wordId));
        launch(FlashcardActivity.class, FlashcardActivity.intentForFolder(TestEnv.context(),
                vocabFolderId, wordId));
        ReviewActivity review = launch(ReviewActivity.class, ReviewActivity.intentForDay(
                TestEnv.context(), DateUtils.today().toString(), false));
        TextView progress = review.findViewById(R.id.text_progress);
        try {
            TestEnv.waitUntil(() -> "Đã xử lý 0 / 2".contentEquals(progress.getText()));
        } catch (AssertionError e) {
            throw new AssertionError("progress='" + progress.getText() + "' items="
                    + VocabRepository.get(TestEnv.context()).group(userId,
                    DateUtils.today().toString(), null).size() + " finishing=" + review.isFinishing());
        }
        review.findViewById(R.id.btn_done).performClick();
        TestEnv.waitUntil(() -> "Đã xử lý 1 / 2".contentEquals(progress.getText()));
        launch(SkipDialogActivity.class, SkipDialogActivity.intentForGroup(TestEnv.context(),
                DateUtils.today().toString(), null, null));
    }

    @Test
    public void notificationDeepLink_whenLoggedOut_goesThroughLogin() throws Exception {
        SessionManager.logout(TestEnv.context());
        NoteDetailActivity detail = launch(NoteDetailActivity.class, NoteDetailActivity.intent(
                TestEnv.context(), noteId, DateUtils.today().toString(), "NOTE:1:x"));
        assertTrue(detail.isFinishing());
        Intent next = shadowOf(detail).getNextStartedActivity();
        assertEquals(LoginActivity.class.getName(), next.getComponent().getClassName());
        assertEquals("NOTE", next.getStringExtra("dl_kind"));
    }

    @Test
    public void noteDetail_doneButtonMarksOccurrence() throws Exception {
        NoteDetailActivity detail = launch(NoteDetailActivity.class, NoteDetailActivity.intent(
                TestEnv.context(), noteId, DateUtils.today().toString(), null));
        detail.findViewById(R.id.btn_done).performClick();
        TestEnv.settle();
        assertTrue(NoteRepository.get(TestEnv.context())
                .findOccurrence(noteId, DateUtils.today()).isDone());
        TextView status = detail.findViewById(R.id.text_status);
        TestEnv.waitUntil(() -> "Xong".contentEquals(status.getText()));
    }
}
