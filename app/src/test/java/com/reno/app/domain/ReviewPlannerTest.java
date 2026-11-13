package com.reno.app.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.reno.app.data.db.entity.WordReviewEntity;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

public class ReviewPlannerTest {
    /** Ví dụ của đặc tả 6.4: từ tạo ngày 13/11/2026. */
    @Test
    public void fiveReviews_fromCreatedDate() {
        List<WordReviewEntity> list = ReviewPlanner.createReviews(7, LocalDate.parse("2026-11-13"));
        assertEquals(5, list.size());
        String[] expected = {"2026-11-14", "2026-11-16", "2026-11-20", "2026-12-13", "2027-02-13"};
        for (int i = 0; i < 5; i++) {
            WordReviewEntity r = list.get(i);
            assertEquals(7, r.wordId);
            assertEquals(i + 1, r.stage);
            assertEquals(expected[i], r.originalDueDate);
            assertEquals(expected[i], r.dueDate);
            assertEquals("PENDING", r.status);
        }
    }

    @Test
    public void postpone_mustBeBeforeNextReview() {
        LocalDate today = LocalDate.parse("2026-11-14");
        LocalDate limit = ReviewPlanner.limitExclusive("2026-11-14", "2026-11-16");
        assertTrue(ReviewPlanner.isValidPostpone(LocalDate.parse("2026-11-14"), today, limit));
        assertTrue(ReviewPlanner.isValidPostpone(LocalDate.parse("2026-11-15"), today, limit));
        assertFalse(ReviewPlanner.isValidPostpone(LocalDate.parse("2026-11-16"), today, limit));
        assertFalse(ReviewPlanner.isValidPostpone(LocalDate.parse("2026-11-13"), today, limit));
    }

    @Test
    public void lastStage_limitIs30DaysFromOriginal() {
        LocalDate today = LocalDate.parse("2027-02-13");
        LocalDate limit = ReviewPlanner.limitExclusive("2027-02-13", null);
        assertTrue(ReviewPlanner.isValidPostpone(LocalDate.parse("2027-03-15"), today, limit));
        assertFalse(ReviewPlanner.isValidPostpone(LocalDate.parse("2027-03-16"), today, limit));
    }
}
