package com.reno.app.ui;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Looper;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.reno.app.R;
import com.reno.app.TestEnv;
import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.entity.AnnotationEntity;
import com.reno.app.data.db.entity.AppNotificationEntity;
import com.reno.app.data.db.entity.FolderEntity;
import com.reno.app.data.db.entity.IdeaEntity;
import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.db.entity.NoteOccurrenceEntity;
import com.reno.app.data.db.entity.TopicEntity;
import com.reno.app.data.db.entity.UserEntity;
import com.reno.app.data.db.entity.WordEntity;
import com.reno.app.data.repository.NotificationRepository;
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
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chụp ảnh các màn hình chính với dữ liệu mẫu (khung điện thoại 390 × 844 dp như bản thiết kế).
 * Chỉ chạy khi bật -PrenoScreenshots=true; ảnh lưu ở app/build/screenshots.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-port-xhdpi")
public class ScreenshotTest {
    private RenoDatabase db;
    private long userId;
    private long noteId;
    private long topicId;
    private long vocabFolderId;
    private long firstWordId;
    private final List<ActivityController<?>> launched = new ArrayList<>();
    private final LocalDate today = DateUtils.today();

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("reno.screenshots"));
        TestEnv.reset();
        db = RenoDatabase.get(TestEnv.context());
        seed();
    }

    @After
    public void tearDown() throws Exception {
        for (ActivityController<?> c : launched) {
            try {
                c.pause().stop().destroy();
            } catch (RuntimeException ignored) {
                // đã đóng
            }
        }
        if (db != null) {
            TestEnv.settle();
            TestEnv.flushSharedPreferences();
        }
    }

    private long ms(LocalDate d, int h, int m) {
        return DateUtils.toEpochMs(LocalDateTime.of(d, LocalTime.of(h, m)));
    }

    private void seed() throws Exception {
        long now = System.currentTimeMillis();
        UserEntity u = new UserEntity();
        u.username = "Huy";
        u.email = "huy@reno.vn";
        u.phone = "0912345678";
        u.passwordHash = "pbkdf2$1$AA==$AA==";
        u.reviewTime = "20:30";
        u.createdAt = ms(today.minusDays(40), 9, 0);
        u.updatedAt = now;
        userId = db.userDao().insert(u);
        SessionManager.login(TestEnv.context(), userId);

        // ---- Read
        String[][] readFolders = {{"Lập trình di động", "Bài giảng tuần 1 – Activity và Intent",
                "Room và SQLite", "Thông báo và AlarmManager"},
                {"Tiếng Anh chuyên ngành", "Reading – Cloud computing"}};
        int bg = 1;
        for (String[] f : readFolders) {
            FolderEntity folder = new FolderEntity();
            folder.userId = userId;
            folder.type = FolderEntity.TYPE_READ;
            folder.name = f[0];
            folder.createdAt = now;
            long fid = db.folderDao().insert(folder);
            for (int i = 1; i < f.length; i++) {
                TopicEntity t = new TopicEntity();
                t.folderId = fid;
                t.name = f[i];
                t.filePath = "/x.pdf";
                t.pageCount = 10 + i * 7;
                t.bgIndex = bg++;
                t.lastOpenedAt = now - bg * 3_600_000L;
                t.createdAt = t.lastOpenedAt;
                long id = db.topicDao().insert(t);
                if (topicId == 0) topicId = id;
            }
        }
        String[][] anns = {{"QUESTION", "Vì sao cần ViewModel?", "Dữ liệu còn khi xoay màn?",
                "ViewModel giữ dữ liệu của màn hình khi Activity bị tạo lại"},
                {"IMPORTANT", "Vòng đời Activity", "", "onCreate → onStart → onResume"},
                {"HIGHLIGHT", "", "", "Intent tường minh chỉ rõ lớp Activity cần mở"},
                {"HIGHLIGHT", "Định nghĩa", "Ôn kỹ trước kiểm tra", "Fragment là một phần giao diện có vòng đời riêng"},
                {"NOTE", "Hỏi thầy", "Phần này chưa hiểu rõ", "PendingIntent.FLAG_IMMUTABLE"}};
        int page = 0;
        for (String[] a : anns) {
            AnnotationEntity e = new AnnotationEntity();
            e.topicId = topicId;
            e.type = a[0];
            e.title = a[1].isEmpty() ? null : a[1];
            e.detail = a[2].isEmpty() ? null : a[2];
            e.selectedText = a[3];
            e.pageIndex = page++;
            e.rects = "[[0.1,0.2,0.6,0.02]]";
            e.color = "HIGHLIGHT".equals(a[0]) ? "#FFE58A" : null;
            db.annotationDao().insert(e);
        }

        // ---- Notes (thời điểm bắt đầu ở quá khứ chỉ có trong dữ liệu mẫu)
        Object[][] notes = {
                {"Ôn chương 5 – Room", "#5B8DEF", 7, 30, 60, NoteEntity.REPEAT_INTERVAL, "FREQ=DAILY;INTERVAL=3"},
                {"Chạy bộ buổi sáng", "#81B29A", 6, 0, 30, NoteEntity.REPEAT_DAILY, "FREQ=DAILY;INTERVAL=1"},
                {"Họp nhóm đồ án", "#E07A5F", 9, 0, 90, NoteEntity.REPEAT_NONE, null},
                {"Nộp báo cáo tuần", "#E9B949", 14, 0, 45, NoteEntity.REPEAT_NONE, null},
                {"Đọc tài liệu PDF.js", "#9C89B8", 9, 30, 60, NoteEntity.REPEAT_NONE, null},
                {"Luyện nghe IELTS", "#4D908E", 20, 0, 40, NoteEntity.REPEAT_DAILY, "FREQ=DAILY;INTERVAL=1"}};
        for (Object[] n : notes) {
            NoteEntity e = new NoteEntity();
            e.userId = userId;
            e.title = (String) n[0];
            e.color = (String) n[1];
            e.dtstart = ms(today.minusDays(3), (int) n[2], (int) n[3]);
            if (NoteEntity.REPEAT_NONE.equals(n[5])) e.dtstart = ms(today, (int) n[2], (int) n[3]);
            e.durationMin = (int) n[4];
            e.repeatType = (String) n[5];
            e.rrule = (String) n[6];
            e.createdAt = now;
            e.updatedAt = now;
            long id = db.noteDao().insert(e);
            if (noteId == 0) noteId = id;
        }
        markNote(2, today, NoteOccurrenceEntity.STATUS_DONE);        // chạy bộ hôm nay: xong
        markNote(2, today.minusDays(1), NoteOccurrenceEntity.STATUS_DONE);
        markNote(6, today.minusDays(2), NoteOccurrenceEntity.STATUS_SKIPPED);

        // ---- Idea
        String[] ideas = {"Làm widget màn hình chính hiển thị việc cần làm hôm nay",
                "Thêm chế độ tối cho ReNo",
                "Tóm tắt PDF bằng mô hình ngôn ngữ nhỏ chạy trên máy",
                "Xuất từ vựng ra file CSV để in"};
        for (int i = 0; i < ideas.length; i++) {
            IdeaEntity idea = new IdeaEntity();
            idea.userId = userId;
            idea.content = ideas[i];
            idea.isDone = i == 3 ? 1 : 0;
            idea.createdAt = now - i * 86_400_000L * 2;
            idea.updatedAt = now;
            db.ideaDao().insert(idea);
        }

        // ---- Vocabulary
        VocabRepository vocab = VocabRepository.get(TestEnv.context());
        long[] created = new long[2];
        vocab.createFolder(userId, "IELTS Reading", r -> System.arraycopy(r, 0, created, 0, 2));
        TestEnv.drain();
        vocabFolderId = created[1];
        vocab.createFolder(userId, "Công nghệ thông tin", r -> { });
        TestEnv.drain();
        String[][] words = {{"resilient", "kiên cường, mau phục hồi"}, {"thrive", "phát triển mạnh"},
                {"meticulous", "tỉ mỉ, kỹ lưỡng"}, {"ubiquitous", "có mặt khắp nơi"},
                {"feasible", "khả thi"}, {"mitigate", "giảm nhẹ"}};
        for (String[] w : words) {
            vocab.saveWordSync(userId, 0, w[0], w[1], vocabFolderId, null);
        }
        long itFolder = db.folderDao().findByName(userId, FolderEntity.TYPE_VOCAB,
                "Công nghệ thông tin").id;
        vocab.saveWordSync(userId, 0, "latency", "độ trễ", itFolder, "06:30");
        vocab.saveWordSync(userId, 0, "throughput", "thông lượng", itFolder, "06:30");
        vocab.saveWordSync(userId, 0, "bandwidth", "băng thông", itFolder, null);
        List<WordEntity> all = db.wordDao().listByUser(userId);
        firstWordId = all.get(0).id;
        for (int i = 0; i < all.size(); i++) {
            WordEntity w = all.get(i);
            long stage1 = db.wordReviewDao().findStage(w.id, 1).id;
            if (i < 6) {
                db.wordReviewDao().reschedule(stage1, today.toString(), null);
            } else if (i < 8) {
                db.wordReviewDao().reschedule(stage1, today.plusDays(1).toString(), null);
            } else {
                db.wordReviewDao().reschedule(stage1, today.minusDays(2).toString(), null);
            }
            if (i >= 6) continue;
        }
        db.wordReviewDao().markDone(db.wordReviewDao().findStage(all.get(4).id, 1).id, now);

        // ---- Thông báo
        addNotification(AppNotificationEntity.KIND_VOCAB, null, today.toString(),
                NotificationRepository.vocabKey(today.toString(), "20:30"),
                "Có 5 từ cần ôn tập", "resilient, thrive, meticulous, ubiquitous, …. Bấm để ôn bằng flashcard.",
                now - 10 * 60_000L, 0);
        addNotification(AppNotificationEntity.KIND_NOTE, noteId + 2, today.toString(),
                NotificationRepository.noteKey(noteId + 2, today.toString()),
                "Ghi chú: Họp nhóm đồ án – 09:00", DateUtils.formatFullDate(today) + " · 09:00 – 10:30",
                now - 2 * 3_600_000L, 0);
        addNotification(AppNotificationEntity.KIND_NOTE, noteId + 1, today.toString(),
                NotificationRepository.noteKey(noteId + 1, today.toString()),
                "Ghi chú: Chạy bộ buổi sáng – 06:00", DateUtils.formatFullDate(today) + " · 06:00 – 06:30",
                now - 5 * 3_600_000L, 1);
        TestEnv.drain();
    }

    private void markNote(long id, LocalDate date, String status) {
        NoteOccurrenceEntity o = new NoteOccurrenceEntity();
        o.noteId = id;
        o.occDate = date.toString();
        o.status = status;
        o.updatedAt = System.currentTimeMillis();
        db.noteOccurrenceDao().upsert(o);
    }

    private void addNotification(String kind, @Nullable Long noteId, String refDate, String key,
                                 String title, String body, long createdAt, int read) {
        AppNotificationEntity n = new AppNotificationEntity();
        n.userId = userId;
        n.kind = kind;
        n.noteId = noteId;
        n.refDate = refDate;
        n.dedupeKey = key;
        n.title = title;
        n.body = body;
        n.createdAt = createdAt;
        n.isRead = read;
        db.appNotificationDao().insertIfNew(n);
    }

    // ------------------------------------------------------------------ chụp

    private <T extends Activity> T open(Class<T> type, Intent intent) throws Exception {
        ActivityController<T> c = Robolectric.buildActivity(type, intent).setup();
        launched.add(c);
        TestEnv.settle();
        return c.get();
    }

    private void shot(Activity activity, String name) throws Exception {
        TestEnv.settle();
        shadowOf(Looper.getMainLooper()).idle();
        View root = activity.getWindow().getDecorView();
        int w = root.getWidth();
        int h = root.getHeight();
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        root.draw(canvas);
        // Hộp thoại (DatePicker, DialogFragment…) nằm ở cửa sổ riêng: vẽ chồng lên.
        if (activity instanceof FragmentActivity) {
            for (androidx.fragment.app.Fragment f
                    : ((FragmentActivity) activity).getSupportFragmentManager().getFragments()) {
                if (f instanceof DialogFragment && ((DialogFragment) f).getDialog() != null) {
                    View dv = ((DialogFragment) f).getDialog().getWindow().getDecorView();
                    canvas.drawColor(0x66000000);
                    canvas.save();
                    canvas.translate((w - dv.getWidth()) / 2f, (h - dv.getHeight()) / 2f);
                    dv.draw(canvas);
                    canvas.restore();
                }
            }
        }
        File dir = new File(System.getProperty("reno.screenshotDir", "build/screenshots"));
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir " + dir);
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    private Intent intent(Class<?> type) {
        return new Intent(TestEnv.context(), type);
    }

    @Test
    public void accountScreens() throws Exception {
        SessionManager.logout(TestEnv.context());
        shot(open(LoginActivity.class, intent(LoginActivity.class)), "01_login");
        RegisterActivity register = open(RegisterActivity.class, intent(RegisterActivity.class));
        ((android.widget.EditText) register.findViewById(R.id.edit_contact)).setText("huy@reno");
        ((android.widget.EditText) register.findViewById(R.id.edit_username)).setText("Huy");
        ((android.widget.EditText) register.findViewById(R.id.edit_password)).setText("12345678");
        ((android.widget.EditText) register.findViewById(R.id.edit_confirm)).setText("1234");
        register.findViewById(R.id.btn_register).performClick();
        TestEnv.waitUntil(() -> register.<com.google.android.material.textfield.TextInputLayout>
                findViewById(R.id.til_confirm).getError() != null);
        shot(register, "02_register_errors");
        SessionManager.login(TestEnv.context(), userId);
        shot(open(ProfileActivity.class, intent(ProfileActivity.class)), "03_profile");
        shot(open(GuideActivity.class, intent(GuideActivity.class)), "04_guide");
    }

    @Test
    public void mainSections() throws Exception {
        MainActivity main = open(MainActivity.class, intent(MainActivity.class));
        ViewPager2 pager = main.findViewById(R.id.pager);
        String[] homeTabs = {"05_home_read", "06_home_notes", "07_home_idea", "08_home_vocabulary"};
        for (int i = 0; i < homeTabs.length; i++) {
            pager.setCurrentItem(i, false);
            TestEnv.settle();
            shot(main, homeTabs[i]);
        }
        BottomNavigationView nav = main.findViewById(R.id.bottom_nav);
        int[] items = {R.id.nav_stats, R.id.nav_camera, R.id.nav_calendar, R.id.nav_notifications};
        String[] names = {"09_stats", "10_camera", "11_calendar", "12_notifications"};
        for (int i = 0; i < items.length; i++) {
            nav.setSelectedItemId(items[i]);
            TestEnv.settle();
            shot(main, names[i]);
        }
    }

    @Test
    public void deepScreens() throws Exception {
        TopicActivity topic = open(TopicActivity.class, TopicActivity.intent(TestEnv.context(),
                topicId));
        com.google.android.material.tabs.TabLayout tabs = topic.findViewById(R.id.tabs);
        tabs.getTabAt(1).select();
        com.google.android.material.tabs.TabLayout filter = topic.findViewById(R.id.tabs_filter);
        filter.getTabAt(3).select();
        shot(topic, "13_topic_stats");
        shot(open(NoteFormActivity.class, NoteFormActivity.intentForEdit(TestEnv.context(),
                noteId)), "14_note_form");
        shot(open(NoteDetailActivity.class, NoteDetailActivity.intent(TestEnv.context(),
                noteId + 2, today.toString(), null)), "15_note_detail");
        shot(open(WordFormActivity.class, WordFormActivity.intentForNew(TestEnv.context(),
                "sustainable", "bền vững")), "16_word_form");
        shot(open(FlashcardActivity.class, FlashcardActivity.intentForFolder(TestEnv.context(),
                vocabFolderId, firstWordId)), "17_flashcard");
        shot(open(ReviewActivity.class, ReviewActivity.intentForDay(TestEnv.context(),
                today.toString(), false)), "18_review");
        shot(open(SkipDialogActivity.class, SkipDialogActivity.intentForGroup(TestEnv.context(),
                today.toString(), null, null)), "19_skip_dialog");
    }
}
