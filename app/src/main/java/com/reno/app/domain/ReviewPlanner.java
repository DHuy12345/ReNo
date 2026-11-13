package com.reno.app.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.reno.app.data.db.entity.WordReviewEntity;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Lịch ôn ngắt quãng cố định 5 lần tính từ ngày tạo từ: +1 ngày, +3 ngày, +7 ngày,
 * +1 tháng, +3 tháng. Ôn sớm, ôn muộn hay bỏ qua một lần không làm đổi các lần sau.
 */
public final class ReviewPlanner {
    private static final Period[] STEPS = {
            Period.ofDays(1), Period.ofDays(3), Period.ofDays(7),
            Period.ofMonths(1), Period.ofMonths(3)};

    public static final int STAGE_COUNT = STEPS.length;
    /** Lần ôn thứ 5 không còn lần sau: được dời tối đa 30 ngày kể từ ngày ôn gốc. */
    public static final int LAST_STAGE_LIMIT_DAYS = 30;

    private ReviewPlanner() {
    }

    /** 5 ngày ôn của một từ tạo vào {@code createdDate}. */
    @NonNull
    public static List<LocalDate> reviewDates(@NonNull LocalDate createdDate) {
        List<LocalDate> list = new ArrayList<>();
        for (Period step : STEPS) list.add(createdDate.plus(step));
        return list;
    }

    @NonNull
    public static List<WordReviewEntity> createReviews(long wordId, @NonNull LocalDate createdDate) {
        List<WordReviewEntity> list = new ArrayList<>();
        List<LocalDate> dates = reviewDates(createdDate);
        for (int i = 0; i < dates.size(); i++) {
            WordReviewEntity r = new WordReviewEntity();
            r.wordId = wordId;
            r.stage = i + 1;
            r.originalDueDate = dates.get(i).toString();   // yyyy-MM-dd
            r.dueDate = r.originalDueDate;
            r.status = WordReviewEntity.STATUS_PENDING;
            list.add(r);
        }
        return list;
    }

    /**
     * Ngày giới hạn khi dời một lần ôn: ngày mới phải TRƯỚC ngày này.
     * Còn lần ôn kế tiếp thì đó là ngày ôn của lần kế tiếp; với lần thứ 5 là
     * ngày ôn gốc + 30 ngày + 1 (tức được chọn tới hết ngày thứ 30).
     */
    @NonNull
    public static LocalDate limitExclusive(@NonNull String originalDueDate,
                                           @Nullable String nextDueDate) {
        if (nextDueDate != null) return LocalDate.parse(nextDueDate);
        return LocalDate.parse(originalDueDate).plusDays(LAST_STAGE_LIMIT_DAYS + 1);
    }

    /** Ngày mới không được ở quá khứ và phải trước ngày giới hạn. */
    public static boolean isValidPostpone(@NonNull LocalDate newDate, @NonNull LocalDate today,
                                          @NonNull LocalDate limitExclusive) {
        return !newDate.isBefore(today) && newDate.isBefore(limitExclusive);
    }
}
