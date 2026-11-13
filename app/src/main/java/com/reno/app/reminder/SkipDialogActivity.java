package com.reno.app.reminder;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.reno.app.R;
import com.reno.app.data.db.entity.WordReviewEntity;
import com.reno.app.data.model.ReviewItem;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.data.repository.VocabRepository;
import com.reno.app.databinding.ActivitySkipDialogBinding;
import com.reno.app.ui.auth.LoginActivity;
import com.reno.app.ui.common.DeepLink;
import com.reno.app.ui.common.Pickers;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.DateUtils;
import com.reno.app.util.SessionManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Hộp thoại Bỏ qua với 3 lựa chọn, dùng chung cho cả nhóm (nút Bỏ qua trên thông báo) và từng
 * từ (trong màn ôn tập). Thông báo hệ thống không tự hiện hộp thoại được, nên đây là một
 * Activity nhỏ dạng hộp thoại.
 */
public class SkipDialogActivity extends AppCompatActivity {
    private static final String EXTRA_REVIEW_IDS = "review_ids";
    private static final String EXTRA_WORD = "word";

    /** Trạng thái của hộp thoại, giữ qua các lần tạo lại. */
    public static class State extends ViewModel {
        boolean loaded;
        final List<Long> reviewIds = new ArrayList<>();
        @Nullable LocalDate limit;
        LocalDate date;
        LocalTime time;
    }

    private ActivitySkipDialogBinding b;
    private State state;
    private long userId;

    /** Bỏ qua cả nhóm: mọi từ còn PENDING của một thông báo (một mốc giờ). */
    public static Intent intentForGroup(Context ctx, String date, @Nullable String time,
                                        @Nullable String dedupeKey) {
        return DeepLink.put(new Intent(ctx, SkipDialogActivity.class), DeepLink.KIND_SKIP, 0,
                date, time, dedupeKey);
    }

    /** Bỏ qua một từ trong màn ôn tập. */
    public static Intent intentForReview(Context ctx, long reviewId, String word) {
        return new Intent(ctx, SkipDialogActivity.class)
                .putExtra(EXTRA_REVIEW_IDS, new long[]{reviewId})
                .putExtra(EXTRA_WORD, word);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        userId = SessionManager.currentUserId(this);
        if (userId == SessionManager.NO_USER) {
            // Chưa đăng nhập thì đăng nhập trước, rồi quay lại đúng hộp thoại này.
            Intent login = new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            DeepLink.copy(getIntent(), login);
            startActivity(login);
            finish();
            return;
        }
        b = ActivitySkipDialogBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setFinishOnTouchOutside(true);
        state = new ViewModelProvider(this).get(State.class);

        b.btnCancel.setOnClickListener(v -> finish());
        b.btnConfirm.setOnClickListener(v -> confirm());
        b.groupOptions.setOnCheckedChangeListener((group, id) -> bindOptions());
        b.btnDate.setOnClickListener(v -> Pickers.pickDate(getSupportFragmentManager(),
                state.date, DateUtils.today(), date -> {
                    state.date = date;
                    bindOptions();
                }));
        b.btnTime.setOnClickListener(v -> Pickers.pickTime(getSupportFragmentManager(),
                state.time, time -> {
                    state.time = time;
                    bindOptions();
                }));

        if (state.loaded) {
            onLoaded();
        } else {
            load();
        }
    }

    private void load() {
        long[] ids = getIntent().getLongArrayExtra(EXTRA_REVIEW_IDS);
        String date = getIntent().getStringExtra(DeepLink.EXTRA_DATE);
        String time = getIntent().getStringExtra(DeepLink.EXTRA_TIME);
        VocabRepository repo = VocabRepository.get(this);
        AppExecutors.diskIO().execute(() -> {
            List<Long> resolved = new ArrayList<>();
            if (ids != null) {
                for (long id : ids) resolved.add(id);
            } else if (date != null) {
                for (ReviewItem it : repo.group(userId, date, time)) {
                    if (WordReviewEntity.STATUS_PENDING.equals(it.status)) {
                        resolved.add(it.reviewId);
                    }
                }
            }
            LocalDate limit = repo.limitExclusive(resolved);
            runOnUiThread(() -> {
                state.reviewIds.addAll(resolved);
                state.limit = limit;
                // Mặc định: hôm nay, một giờ nữa (không vượt quá cuối ngày).
                LocalDateTime now = DateUtils.now();
                state.date = now.toLocalDate();
                state.time = now.getHour() >= 23 ? LocalTime.of(23, 59)
                        : now.toLocalTime().plusHours(1).withSecond(0).withNano(0);
                state.loaded = true;
                onLoaded();
            });
        });
    }

    private void onLoaded() {
        if (state.reviewIds.isEmpty()) {
            Toast.makeText(this, R.string.skip_nothing, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String word = getIntent().getStringExtra(EXTRA_WORD);
        b.textTitle.setText(word != null ? getString(R.string.skip_title_single, word)
                : getString(R.string.skip_title_group, state.reviewIds.size()));
        if (state.limit != null) {
            b.textLimit.setText(getString(R.string.skip_limit_info,
                    DateUtils.formatDayMonth(state.limit)));
        }
        b.btnConfirm.setEnabled(true);
        bindOptions();
    }

    private void bindOptions() {
        int id = b.groupOptions.getCheckedRadioButtonId();
        b.textNextHint.setVisibility(id == R.id.radio_next ? View.VISIBLE : View.GONE);
        b.rowRemind.setVisibility(id == R.id.radio_remind ? View.VISIBLE : View.GONE);
        b.tilDays.setVisibility(id == R.id.radio_postpone ? View.VISIBLE : View.GONE);
        b.textError.setVisibility(View.GONE);
        if (state.date != null) b.btnDate.setText(DateUtils.formatFullDate(state.date));
        if (state.time != null) b.btnTime.setText(DateUtils.formatTime(state.time));
    }

    private void confirm() {
        if (!state.loaded) return;
        int option = b.groupOptions.getCheckedRadioButtonId();
        int days;
        try {
            days = Integer.parseInt(String.valueOf(b.editDays.getText()).trim());
        } catch (NumberFormatException e) {
            days = 0;
        }
        int finalDays = days;
        LocalDateTime when = LocalDateTime.of(state.date, state.time);
        List<Long> ids = new ArrayList<>(state.reviewIds);
        VocabRepository repo = VocabRepository.get(this);
        b.btnConfirm.setEnabled(false);
        AppExecutors.diskIO().execute(() -> {
            VocabRepository.SkipResult result;
            if (option == R.id.radio_remind) {
                result = repo.remindAtSync(userId, ids, when);
            } else if (option == R.id.radio_postpone) {
                result = repo.postponeSync(userId, ids, finalDays);
            } else {
                result = repo.skipToNextSync(userId, ids);
            }
            runOnUiThread(() -> onResult(result));
        });
    }

    private void onResult(VocabRepository.SkipResult result) {
        b.btnConfirm.setEnabled(true);
        if (result.error != 0) {
            // Vượt giới hạn: "Phải chọn trước ngày dd/MM (lần ôn tiếp theo)".
            b.textError.setText(result.limit != null
                    ? getString(result.error, DateUtils.formatDayMonth(result.limit))
                    : getString(result.error));
            b.textError.setVisibility(View.VISIBLE);
            return;
        }
        String key = getIntent().getStringExtra(DeepLink.EXTRA_KEY);
        if (key != null) {
            NotificationRepository.get(this).markReadByKey(userId, key);
            NotificationHelper.cancel(this, key);
        }
        Toast.makeText(this, R.string.skip_done, Toast.LENGTH_SHORT).show();
        setResult(RESULT_OK);
        finish();
    }
}
