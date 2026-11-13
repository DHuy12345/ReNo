package com.reno.app.ui.vocab;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.reno.app.R;
import com.reno.app.data.db.entity.WordEntity;
import com.reno.app.data.model.ReviewItem;
import com.reno.app.data.repository.VocabRepository;
import com.reno.app.databinding.ActivityFlashcardBinding;
import com.reno.app.ui.common.BaseActivity;
import com.reno.app.util.AppExecutors;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn flashcard: xem lần lượt các từ của một thư mục (theo thời gian tạo), bắt đầu từ từ được
 * bấm; hoặc xem lại một nhóm từ đã ôn xong.
 */
public class FlashcardActivity extends BaseActivity {
    private static final String EXTRA_FOLDER_ID = "folder_id";
    private static final String EXTRA_WORD_ID = "word_id";
    private static final String EXTRA_REVIEW_IDS = "review_ids";

    /** Giữ bộ thẻ và vị trí đang xem. */
    public static class State extends ViewModel {
        boolean loaded;
        final List<String[]> cards = new ArrayList<>();   // {nghĩa, từ}
        int index;
    }

    private ActivityFlashcardBinding b;
    private State state;
    private FlashcardController card;

    public static Intent intentForFolder(Context ctx, long folderId, long startWordId) {
        return new Intent(ctx, FlashcardActivity.class)
                .putExtra(EXTRA_FOLDER_ID, folderId)
                .putExtra(EXTRA_WORD_ID, startWordId);
    }

    public static Intent intentForReviews(Context ctx, long[] reviewIds) {
        return new Intent(ctx, FlashcardActivity.class).putExtra(EXTRA_REVIEW_IDS, reviewIds);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;
        b = ActivityFlashcardBinding.inflate(getLayoutInflater());
        setContentRoot(b.getRoot());
        state = new ViewModelProvider(this).get(State.class);
        card = new FlashcardController(b.flashcard);

        b.toolbar.textTitle.setText(R.string.flashcard_title);
        b.toolbar.btnBack.setOnClickListener(v -> finish());
        b.btnPrev.setOnClickListener(v -> move(-1));
        b.btnNext.setOnClickListener(v -> move(1));

        if (state.loaded) {
            bind();
        } else {
            load();
        }
    }

    private void load() {
        VocabRepository repo = VocabRepository.get(this);
        long[] reviewIds = getIntent().getLongArrayExtra(EXTRA_REVIEW_IDS);
        if (reviewIds != null) {
            List<Long> ids = new ArrayList<>();
            for (long id : reviewIds) ids.add(id);
            AppExecutors.diskIO().execute(() -> {
                List<ReviewItem> items = repo.itemsByIds(userId, ids);
                runOnUiThread(() -> {
                    for (ReviewItem it : items) {
                        state.cards.add(new String[]{it.meaning, it.word});
                    }
                    state.loaded = true;
                    bind();
                });
            });
            return;
        }
        long startWordId = getIntent().getLongExtra(EXTRA_WORD_ID, -1);
        repo.listWords(getIntent().getLongExtra(EXTRA_FOLDER_ID, -1), words -> {
            for (int i = 0; i < words.size(); i++) {
                WordEntity w = words.get(i);
                state.cards.add(new String[]{w.meaning, w.word});
                if (w.id == startWordId) state.index = i;
            }
            state.loaded = true;
            bind();
        });
    }

    private void move(int delta) {
        int next = state.index + delta;
        if (next < 0 || next >= state.cards.size()) return;   // không quay vòng
        state.index = next;
        bind();
    }

    private void bind() {
        int total = state.cards.size();
        boolean empty = total == 0;
        card.setVisible(!empty);
        b.rowArrows.setVisibility(empty ? View.GONE : View.VISIBLE);
        b.textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            b.toolbar.textTrailing.setText("");
            return;
        }
        String[] current = state.cards.get(state.index);
        card.show(current[0], current[1]);
        b.toolbar.textTrailing.setText(getString(R.string.flashcard_counter,
                state.index + 1, total));
        // Ở từ đầu thì mũi tên trái tối đi, ở từ cuối thì mũi tên phải cũng vậy.
        setArrowEnabled(b.btnPrev, state.index > 0);
        setArrowEnabled(b.btnNext, state.index < total - 1);
    }

    private static void setArrowEnabled(View arrow, boolean enabled) {
        arrow.setEnabled(enabled);
        arrow.setAlpha(enabled ? 1f : 0.38f);
    }
}
