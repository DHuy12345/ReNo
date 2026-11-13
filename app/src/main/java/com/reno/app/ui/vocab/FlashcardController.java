package com.reno.app.ui.vocab;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;

import androidx.annotation.Nullable;

import com.reno.app.R;
import com.reno.app.databinding.ViewFlashcardBinding;

/**
 * Điều khiển một thẻ flashcard: mặt trước là nghĩa tiếng Việt, bấm thẻ thì lật (xoay quanh
 * trục dọc 300 ms) ra từ tiếng Anh, bấm lần nữa để lật lại.
 */
public class FlashcardController {
    private static final long HALF_FLIP_MS = 150;

    private final ViewFlashcardBinding b;
    private String front = "";
    private String back = "";
    private boolean showingBack;
    @Nullable private Animator running;

    public FlashcardController(ViewFlashcardBinding binding) {
        b = binding;
        // Đẩy camera ra xa để hình không bị méo khi xoay.
        float density = b.card.getResources().getDisplayMetrics().density;
        b.card.setCameraDistance(8000 * density);
        b.card.setOnClickListener(v -> flip());
    }

    /** Chuyển từ mới: thẻ luôn hiện mặt trước. */
    public void show(String meaning, String word) {
        if (running != null) running.cancel();
        front = meaning;
        back = word;
        showingBack = false;
        b.card.setRotationY(0f);
        render();
    }

    public void setVisible(boolean visible) {
        b.card.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void render() {
        b.textCard.setText(showingBack ? back : front);
        b.textSide.setText(showingBack ? R.string.flashcard_side_word
                : R.string.flashcard_side_meaning);
    }

    /** Xoay rotationY 0→90°, đổi nội dung, rồi −90°→0°. */
    private void flip() {
        if (running != null && running.isRunning()) return;
        ObjectAnimator out = ObjectAnimator.ofFloat(b.card, View.ROTATION_Y, 0f, 90f);
        out.setDuration(HALF_FLIP_MS);
        out.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (cancelled) return;
                showingBack = !showingBack;
                render();
                ObjectAnimator in = ObjectAnimator.ofFloat(b.card, View.ROTATION_Y, -90f, 0f);
                in.setDuration(HALF_FLIP_MS);
                running = in;
                in.start();
            }
        });
        running = out;
        out.start();
    }
}
