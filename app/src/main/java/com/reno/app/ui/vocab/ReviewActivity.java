package com.reno.app.ui.vocab;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.reno.app.R;
import com.reno.app.data.model.ReviewItem;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.databinding.ActivityReviewBinding;
import com.reno.app.reminder.NotificationHelper;
import com.reno.app.reminder.SkipDialogActivity;
import com.reno.app.ui.common.BaseActivity;
import com.reno.app.ui.common.DeepLink;

/**
 * Màn ôn tập dạng flashcard. Dưới mỗi thẻ có hai nút Bỏ qua và Xong áp dụng cho từ đang hiện;
 * xử lý hết thì hiện tổng kết và nút "Xong" để đóng màn.
 */
public class ReviewActivity extends BaseActivity {
    private static final String EXTRA_INCLUDE_SKIPPED = "include_skipped";

    private ActivityReviewBinding b;
    private ReviewViewModel vm;
    private FlashcardController card;
    private int shownIndex = -1;

    private final ActivityResultLauncher<Intent> skipLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) vm.onSkipped();
            });

    /** Mở từ một thông báo ôn tập: chỉ gồm các từ của mốc giờ đó. */
    public static Intent intentForGroup(Context ctx, String date, @Nullable String time,
                                        @Nullable String dedupeKey) {
        return DeepLink.put(new Intent(ctx, ReviewActivity.class), DeepLink.KIND_REVIEW, 0,
                date, time, dedupeKey);
    }

    /** Mở từ Thống kê, Lịch hoặc thẻ "Hôm nay cần ôn": các từ cần ôn của cả ngày. */
    public static Intent intentForDay(Context ctx, String date, boolean includeSkipped) {
        return intentForGroup(ctx, date, null, null)
                .putExtra(EXTRA_INCLUDE_SKIPPED, includeSkipped);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;
        String date = getIntent().getStringExtra(DeepLink.EXTRA_DATE);
        if (date == null) {
            finishToMain();
            return;
        }
        b = ActivityReviewBinding.inflate(getLayoutInflater());
        setContentRoot(b.getRoot());
        vm = new ViewModelProvider(this).get(ReviewViewModel.class);
        card = new FlashcardController(b.flashcard);

        b.toolbar.textTitle.setText(R.string.review_title);
        b.toolbar.btnBack.setOnClickListener(v -> finishToMain());
        b.btnClose.setOnClickListener(v -> finishToMain());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishToMain();
            }
        });

        // Mở từ thông báo: đánh dấu đã đọc và gỡ thông báo hệ thống.
        String key = getIntent().getStringExtra(DeepLink.EXTRA_KEY);
        if (key != null) {
            NotificationRepository.get(this).markReadByKey(userId, key);
            NotificationHelper.cancel(this, key);
        }

        b.btnDone.setOnClickListener(v -> vm.markDone());
        b.btnSkip.setOnClickListener(v -> {
            ReviewItem it = vm.current();
            if (it != null) {
                skipLauncher.launch(SkipDialogActivity.intentForReview(this, it.reviewId,
                        it.word));
            }
        });

        vm.version().observe(this, v -> bind());
        vm.load(date, getIntent().getStringExtra(DeepLink.EXTRA_TIME),
                getIntent().getBooleanExtra(EXTRA_INCLUDE_SKIPPED, false));
        bind();
    }

    private void bind() {
        b.loading.setVisibility(vm.loaded ? View.GONE : View.VISIBLE);
        boolean reviewing = vm.loaded && !vm.isFinished();
        b.sectionCard.setVisibility(reviewing ? View.VISIBLE : View.INVISIBLE);
        b.rowActions.setVisibility(reviewing ? View.VISIBLE : View.INVISIBLE);
        b.sectionSummary.setVisibility(vm.isFinished() ? View.VISIBLE : View.GONE);
        if (!vm.loaded) {
            b.sectionProgress.setVisibility(View.INVISIBLE);
            return;
        }

        int total = vm.items.size();
        b.sectionProgress.setVisibility(total == 0 ? View.INVISIBLE : View.VISIBLE);
        b.textProgress.setText(getString(R.string.review_progress, vm.processed, total));
        b.progress.setMax(Math.max(total, 1));
        b.progress.setProgressCompat(vm.processed, true);

        if (vm.isFinished()) {
            b.toolbar.textTrailing.setText("");
            if (total == 0) {
                b.textSummaryTitle.setText(R.string.review_title);
                b.textSummary.setText(R.string.review_nothing);
            } else {
                b.textSummaryTitle.setText(R.string.review_summary_title);
                b.textSummary.setText(getString(R.string.review_summary, vm.doneCount,
                        vm.skippedCount));
            }
            return;
        }
        ReviewItem it = vm.current();
        b.toolbar.textTrailing.setText(getString(R.string.flashcard_counter,
                vm.processed + 1, total));
        // Chỉ đặt lại thẻ khi sang từ mới, để không tự lật về mặt trước khi màn vẽ lại.
        if (it != null && shownIndex != vm.processed) {
            shownIndex = vm.processed;
            card.show(it.meaning, it.word);
        }
    }
}
