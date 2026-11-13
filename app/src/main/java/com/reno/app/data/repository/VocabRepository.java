package com.reno.app.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;

import com.reno.app.R;
import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.dao.FolderDao;
import com.reno.app.data.db.dao.WordDao;
import com.reno.app.data.db.dao.WordReviewDao;
import com.reno.app.data.db.entity.FolderEntity;
import com.reno.app.data.db.entity.WordEntity;
import com.reno.app.data.db.entity.WordReviewEntity;
import com.reno.app.data.model.FolderGroup;
import com.reno.app.data.model.ReviewItem;
import com.reno.app.domain.ReviewPlanner;
import com.reno.app.reminder.ReminderScheduler;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.Callback;
import com.reno.app.util.DateUtils;
import com.reno.app.util.SessionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tab Vocabulary: thư mục từ, từ vựng, 5 lần ôn và xử lý Xong / Bỏ qua. */
public class VocabRepository {
    public static final int WORD_MAX = 100;
    public static final int MEANING_MAX = 300;
    private static volatile VocabRepository INSTANCE;

    private final Context app;
    private final RenoDatabase db;
    private final FolderDao folderDao;
    private final WordDao wordDao;
    private final WordReviewDao reviewDao;

    private VocabRepository(Context ctx) {
        app = ctx.getApplicationContext();
        db = RenoDatabase.get(app);
        folderDao = db.folderDao();
        wordDao = db.wordDao();
        reviewDao = db.wordReviewDao();
    }

    public static VocabRepository get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (VocabRepository.class) {
                if (INSTANCE == null) INSTANCE = new VocabRepository(ctx);
            }
        }
        return INSTANCE;
    }

    /** Dữ liệu của màn danh sách Vocabulary. */
    public static final class VocabData {
        /** Số từ còn PENDING có ngày ôn là hôm nay – thẻ "Hôm nay cần ôn". */
        public int dueToday;
        public final List<FolderGroup<WordEntity>> folders = new ArrayList<>();
    }

    /** Kết quả lưu từ: mỗi trường lỗi là mã chuỗi, 0 = không lỗi. */
    public static final class WordResult {
        @StringRes public int wordError;
        @StringRes public int meaningError;
        @StringRes public int folderError;
        /** Ngày ôn đầu tiên của từ vừa thêm. */
        @Nullable public LocalDate firstReview;

        public boolean isOk() {
            return wordError == 0 && meaningError == 0 && folderError == 0;
        }
    }

    /** Kết quả của hộp thoại Bỏ qua: error = 0 là thành công. */
    public static final class SkipResult {
        @StringRes public int error;
        /** Ngày giới hạn (lần ôn tiếp theo) để ghép vào thông báo lỗi. */
        @Nullable public LocalDate limit;
    }

    public LiveData<Integer> tick() {
        return reviewDao.tick();
    }

    // --------------------------------------------------------- Danh sách

    /** Gọi ở luồng nền. */
    public VocabData loadScreen(long userId) {
        VocabData data = new VocabData();
        data.dueToday = reviewDao.countPendingOnDate(userId, DateUtils.today().toString());
        Map<Long, FolderGroup<WordEntity>> byId = new LinkedHashMap<>();
        for (FolderEntity f : folderDao.list(userId, FolderEntity.TYPE_VOCAB)) {
            byId.put(f.id, new FolderGroup<>(f));
        }
        for (WordEntity w : wordDao.listByUser(userId)) {
            FolderGroup<WordEntity> g = byId.get(w.folderId);
            if (g != null) g.items.add(w);
        }
        data.folders.addAll(byId.values());
        return data;
    }

    public void listFolders(long userId, Callback<List<FolderEntity>> callback) {
        AppExecutors.diskIO().execute(() -> {
            List<FolderEntity> list = folderDao.list(userId, FolderEntity.TYPE_VOCAB);
            AppExecutors.mainThread().execute(() -> callback.onResult(list));
        });
    }

    /** @param callback nhận {mã lỗi (0 = thành công), mã thư mục mới} */
    public void createFolder(long userId, String name, Callback<long[]> callback) {
        AppExecutors.diskIO().execute(() -> {
            FolderStore.Result r = FolderStore.create(folderDao, userId, FolderEntity.TYPE_VOCAB, name);
            AppExecutors.mainThread().execute(() ->
                    callback.onResult(new long[]{r.error, r.folderId}));
        });
    }

    public void renameFolder(long folderId, String name, Callback<Integer> callback) {
        AppExecutors.diskIO().execute(() -> {
            int error = FolderStore.rename(folderDao, folderId, name).error;
            AppExecutors.mainThread().execute(() -> callback.onResult(error));
        });
    }

    /** Xóa thư mục xóa luôn mọi từ và lịch ôn của chúng (CASCADE). */
    public void deleteFolder(long userId, long folderId) {
        AppExecutors.diskIO().execute(() -> {
            folderDao.deleteById(folderId);
            ReminderScheduler.get(app).rescheduleNow(userId);
        });
    }

    // --------------------------------------------------------------- Từ

    public void findWord(long wordId, Callback<WordEntity> callback) {
        AppExecutors.diskIO().execute(() -> {
            WordEntity w = wordDao.findById(wordId);
            AppExecutors.mainThread().execute(() -> callback.onResult(w));
        });
    }

    /** Bộ thẻ flashcard của một thư mục, theo thời gian tạo. */
    public void listWords(long folderId, Callback<List<WordEntity>> callback) {
        AppExecutors.diskIO().execute(() -> {
            List<WordEntity> list = wordDao.listByFolder(folderId);
            AppExecutors.mainThread().execute(() -> callback.onResult(list));
        });
    }

    /**
     * Thêm từ (wordId = 0) hoặc sửa từ. Thêm mới tạo sẵn 5 lần ôn trong cùng một giao dịch;
     * sửa từ hoặc nghĩa không làm đổi lịch ôn.
     *
     * @param remindTime HH:mm nếu "Tự chọn", null nếu "Giờ mặc định"
     */
    public void saveWord(long userId, long wordId, String rawWord, String rawMeaning,
                         long folderId, @Nullable String remindTime,
                         Callback<WordResult> callback) {
        AppExecutors.diskIO().execute(() -> {
            WordResult result = saveWordSync(userId, wordId, rawWord, rawMeaning, folderId,
                    remindTime);
            AppExecutors.mainThread().execute(() -> callback.onResult(result));
        });
    }

    /** Gọi ở luồng nền. */
    public WordResult saveWordSync(long userId, long wordId, String rawWord, String rawMeaning,
                                   long folderId, @Nullable String remindTime) {
        WordResult result = new WordResult();
        String word = rawWord == null ? "" : rawWord.trim();
        String meaning = rawMeaning == null ? "" : rawMeaning.trim();
        if (word.isEmpty()) {
            result.wordError = R.string.error_word_empty;
        } else if (word.length() > WORD_MAX) {
            result.wordError = R.string.error_word_long;
        }
        if (meaning.isEmpty()) {
            result.meaningError = R.string.error_meaning_empty;
        } else if (meaning.length() > MEANING_MAX) {
            result.meaningError = R.string.error_meaning_long;
        }
        FolderEntity folder = folderId > 0 ? folderDao.findById(folderId) : null;
        if (folder == null || folder.userId != userId
                || !FolderEntity.TYPE_VOCAB.equals(folder.type)) {
            result.folderError = R.string.error_folder_required;
        }
        if (result.isOk()) {
            WordEntity same = wordDao.findInFolder(folderId, word);
            if (same != null && same.id != wordId) {
                result.wordError = R.string.error_word_duplicate;
            }
        }
        if (!result.isOk()) return result;

        if (wordId == 0) {
            LocalDate today = DateUtils.today();
            WordEntity w = new WordEntity();
            w.folderId = folderId;
            w.word = word;
            w.meaning = meaning;
            w.remindTime = remindTime;
            w.createdDate = today.toString();
            w.createdAt = System.currentTimeMillis();
            db.runInTransaction(() -> {
                long id = wordDao.insert(w);
                reviewDao.insertAll(ReviewPlanner.createReviews(id, today));
            });
            result.firstReview = ReviewPlanner.reviewDates(today).get(0);
        } else {
            wordDao.update(wordId, word, meaning, folderId, remindTime);
        }
        SessionManager.setLastVocabFolder(app, userId, folderId);
        ReminderScheduler.get(app).rescheduleNow(userId);
        return result;
    }

    public void deleteWord(long userId, long wordId) {
        AppExecutors.diskIO().execute(() -> {
            wordDao.deleteById(wordId);
            ReminderScheduler.get(app).rescheduleNow(userId);
        });
    }

    // ------------------------------------------------------------ Ôn tập

    /** Mọi lần ôn (mọi trạng thái) có ngày ôn trong [from, to]. Gọi ở luồng nền. */
    public List<ReviewItem> itemsBetween(long userId, LocalDate from, LocalDate to) {
        return reviewDao.itemsBetween(userId, from.toString(), to.toString());
    }

    /**
     * Nhóm ôn của một ngày. Một thông báo ứng với một mốc giờ: truyền {@code time} để chỉ lấy
     * các lần ôn có giờ nhắc thực tế đúng bằng mốc đó; null = cả ngày. Gọi ở luồng nền.
     */
    public List<ReviewItem> group(long userId, @NonNull String date, @Nullable String time) {
        List<ReviewItem> out = new ArrayList<>();
        for (ReviewItem it : reviewDao.itemsBetween(userId, date, date)) {
            if (time == null || time.equals(it.effectiveTime())) out.add(it);
        }
        return out;
    }

    /** Gọi ở luồng nền. */
    public List<ReviewItem> itemsByIds(long userId, List<Long> ids) {
        return ids.isEmpty() ? new ArrayList<>() : reviewDao.itemsByIds(userId, ids);
    }

    /** Xong: lần ôn → DONE, lưu done_at. */
    public void markDone(long userId, long reviewId, @Nullable Runnable done) {
        AppExecutors.diskIO().execute(() -> {
            reviewDao.markDone(reviewId, System.currentTimeMillis());
            ReminderScheduler.get(app).rescheduleNow(userId);
            if (done != null) AppExecutors.mainThread().execute(done);
        });
    }

    /**
     * Ngày giới hạn khi dời lịch: ngày mới phải TRƯỚC ngày này. Với một nhóm thì lấy ngày
     * sớm nhất trong nhóm. Gọi ở luồng nền.
     */
    @Nullable
    public LocalDate limitExclusive(List<Long> reviewIds) {
        LocalDate min = null;
        for (long id : reviewIds) {
            WordReviewEntity r = reviewDao.findById(id);
            if (r == null) continue;
            WordReviewEntity next = reviewDao.findStage(r.wordId, r.stage + 1);
            LocalDate limit = ReviewPlanner.limitExclusive(r.originalDueDate,
                    next == null ? null : next.dueDate);
            if (min == null || limit.isBefore(min)) min = limit;
        }
        return min;
    }

    /** Lựa chọn 1 – Dời sang lần ôn tiếp theo: SKIPPED, vào "Đã bỏ lỡ", không đặt lại lịch. */
    public SkipResult skipToNextSync(long userId, List<Long> reviewIds) {
        db.runInTransaction(() -> {
            for (long id : reviewIds) reviewDao.markSkipped(id);
        });
        ReminderScheduler.get(app).rescheduleNow(userId);
        return new SkipResult();
    }

    /**
     * Lựa chọn 2 – Nhắc vào thời điểm cụ thể: due_date = ngày chọn, remind_at = ngày giờ chọn,
     * vẫn PENDING. Phải sau hiện tại và trước ngày của lần ôn kế tiếp.
     */
    public SkipResult remindAtSync(long userId, List<Long> reviewIds, LocalDateTime when) {
        SkipResult result = new SkipResult();
        if (!when.isAfter(DateUtils.now())) {
            result.error = R.string.skip_error_past_time;
            return result;
        }
        LocalDate limit = limitExclusive(reviewIds);
        if (limit != null && !when.toLocalDate().isBefore(limit)) {
            result.error = R.string.skip_error_limit;
            result.limit = limit;
            return result;
        }
        String date = when.toLocalDate().toString();
        long at = DateUtils.toEpochMs(when.withSecond(0).withNano(0));
        db.runInTransaction(() -> {
            for (long id : reviewIds) reviewDao.reschedule(id, date, at);
        });
        ReminderScheduler.get(app).rescheduleNow(userId);
        return result;
    }

    /** Lựa chọn 3 – Lùi n ngày: due_date + n, vẫn PENDING, nhắc vào giờ nhắc của từ. */
    public SkipResult postponeSync(long userId, List<Long> reviewIds, int days) {
        SkipResult result = new SkipResult();
        if (days < 1) {
            result.error = R.string.skip_error_days;
            return result;
        }
        LocalDate today = DateUtils.today();
        LocalDate limit = limitExclusive(reviewIds);
        Map<Long, String> newDates = new LinkedHashMap<>();
        for (long id : reviewIds) {
            WordReviewEntity r = reviewDao.findById(id);
            if (r == null) continue;
            LocalDate newDate = LocalDate.parse(r.dueDate).plusDays(days);
            if (limit != null && !newDate.isBefore(limit)) {
                result.error = R.string.skip_error_limit;
                result.limit = limit;
                return result;
            }
            if (newDate.isBefore(today)) {
                result.error = R.string.skip_error_past_date;
                return result;
            }
            newDates.put(id, newDate.toString());
        }
        db.runInTransaction(() -> {
            for (Map.Entry<Long, String> e : newDates.entrySet()) {
                reviewDao.reschedule(e.getKey(), e.getValue(), null);
            }
        });
        ReminderScheduler.get(app).rescheduleNow(userId);
        return result;
    }
}
