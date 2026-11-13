package com.reno.app.ui.vocab;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.reno.app.data.db.entity.WordReviewEntity;
import com.reno.app.data.model.ReviewItem;
import com.reno.app.data.repository.VocabRepository;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.SessionManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn ôn tập: bộ thẻ là các từ cần ôn của một thông báo (một mốc giờ) hoặc của cả một ngày.
 * Bộ thẻ được chụp lại lúc mở màn; mỗi từ được xử lý đúng một lần (Xong hoặc Bỏ qua).
 */
public class ReviewViewModel extends AndroidViewModel {
    public final long userId;
    public final VocabRepository repo;
    private final MutableLiveData<Integer> version = new MutableLiveData<>();

    public boolean loaded;
    public final List<ReviewItem> items = new ArrayList<>();
    /** Số từ đã xử lý = vị trí của thẻ đang hiện. */
    public int processed;
    public int doneCount;
    public int skippedCount;

    public ReviewViewModel(@NonNull Application app) {
        super(app);
        userId = SessionManager.currentUserId(app);
        repo = VocabRepository.get(app);
    }

    /** Phát mỗi khi trạng thái đổi để màn hình vẽ lại. */
    public LiveData<Integer> version() {
        return version;
    }

    /**
     * @param time           mốc giờ HH:mm của thông báo; null = cả ngày
     * @param includeSkipped true khi ôn bù từ mục "Đã bỏ lỡ" (lấy cả các lần đã SKIPPED)
     */
    public void load(String date, @Nullable String time, boolean includeSkipped) {
        if (loaded) return;
        AppExecutors.diskIO().execute(() -> {
            List<ReviewItem> list = new ArrayList<>();
            for (ReviewItem it : repo.group(userId, date, time)) {
                if (WordReviewEntity.STATUS_PENDING.equals(it.status)
                        || (includeSkipped && WordReviewEntity.STATUS_SKIPPED.equals(it.status))) {
                    list.add(it);
                }
            }
            AppExecutors.mainThread().execute(() -> {
                items.addAll(list);
                loaded = true;
                bump();
            });
        });
    }

    @Nullable
    public ReviewItem current() {
        return processed < items.size() ? items.get(processed) : null;
    }

    public boolean isFinished() {
        return loaded && processed >= items.size();
    }

    /** Xong: lần ôn của từ → DONE, lưu done_at, tự chuyển sang thẻ tiếp theo. */
    public void markDone() {
        ReviewItem it = current();
        if (it == null) return;
        repo.markDone(userId, it.reviewId, null);
        doneCount++;
        processed++;
        bump();
    }

    /** Hộp thoại Bỏ qua đã áp dụng xong cho từ đang hiện: chuyển sang thẻ tiếp theo. */
    public void onSkipped() {
        if (current() == null) return;
        skippedCount++;
        processed++;
        bump();
    }

    private void bump() {
        Integer v = version.getValue();
        version.setValue(v == null ? 1 : v + 1);
    }
}
