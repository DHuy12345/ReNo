package com.reno.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.reno.app.data.db.entity.WordReviewEntity;
import com.reno.app.data.model.ReviewItem;

import java.util.List;

@Dao
public interface WordReviewDao {
    String ITEM_COLUMNS = "r.id AS review_id, r.word_id, r.stage, r.original_due_date, "
            + "r.due_date, r.remind_at, r.status, r.done_at, w.word, w.meaning, "
            + "COALESCE(w.remind_time, u.review_time) AS word_time";
    String ITEM_JOIN = " FROM word_reviews r "
            + "JOIN words w ON w.id = r.word_id "
            + "JOIN folders f ON f.id = w.folder_id "
            + "JOIN users u ON u.id = f.user_id ";

    @Insert void insertAll(List<WordReviewEntity> reviews);

    @Query("SELECT * FROM word_reviews WHERE id = :id")
    WordReviewEntity findById(long id);

    @Query("SELECT * FROM word_reviews WHERE word_id = :wordId AND stage = :stage LIMIT 1")
    WordReviewEntity findStage(long wordId, int stage);

    @Query("SELECT * FROM word_reviews WHERE word_id = :wordId ORDER BY stage")
    List<WordReviewEntity> listByWord(long wordId);

    /** Mọi lần ôn (mọi trạng thái) có ngày ôn trong khoảng, theo thứ tự tạo từ. */
    @Query("SELECT " + ITEM_COLUMNS + ITEM_JOIN
            + "WHERE f.user_id = :userId AND r.due_date BETWEEN :from AND :to "
            + "ORDER BY r.due_date, w.created_at, w.id")
    List<ReviewItem> itemsBetween(long userId, String from, String to);

    @Query("SELECT " + ITEM_COLUMNS + ITEM_JOIN
            + "WHERE f.user_id = :userId AND r.id IN (:ids) ORDER BY w.created_at, w.id")
    List<ReviewItem> itemsByIds(long userId, List<Long> ids);

    /** Số từ cần ôn của một ngày tại một giờ nhắc – dùng khi gửi thông báo. */
    @Query("SELECT COUNT(*)" + ITEM_JOIN
            + "WHERE f.user_id = :userId AND r.due_date = :date AND r.status = 'PENDING' "
            + "AND r.remind_at IS NULL AND COALESCE(w.remind_time, u.review_time) = :time")
    int countPending(long userId, String date, String time);

    /** Số từ cần ôn hôm nay (mọi giờ nhắc). */
    @Query("SELECT COUNT(*)" + ITEM_JOIN
            + "WHERE f.user_id = :userId AND r.due_date = :date AND r.status = 'PENDING'")
    int countPendingOnDate(long userId, String date);

    @Query("UPDATE word_reviews SET status = 'DONE', done_at = :now WHERE id = :id")
    void markDone(long id, long now);

    /** Lựa chọn 1 – dời sang lần ôn tiếp theo: tính là đã bỏ lỡ, không đặt lại lịch. */
    @Query("UPDATE word_reviews SET status = 'SKIPPED', remind_at = NULL, done_at = NULL WHERE id = :id")
    void markSkipped(long id);

    /** Lựa chọn 2 và 3 – dời ngày ôn, vẫn PENDING. */
    @Query("UPDATE word_reviews SET status = 'PENDING', due_date = :dueDate, remind_at = :remindAt, "
            + "done_at = NULL WHERE id = :id")
    void reschedule(long id, String dueDate, Long remindAt);

    /** Phát lại mỗi khi từ, lịch ôn, thư mục hoặc giờ nhắc mặc định thay đổi. */
    @Query("SELECT (SELECT COUNT(*) FROM folders) + (SELECT COUNT(*) FROM words) "
            + "+ (SELECT COUNT(*) FROM word_reviews) + (SELECT COUNT(*) FROM users)")
    LiveData<Integer> tick();
}
