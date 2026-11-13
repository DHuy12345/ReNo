package com.reno.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.reno.app.data.db.entity.AppNotificationEntity;

import java.util.List;

@Dao
public interface AppNotificationDao {
    /** Trả về -1 nếu đã có dòng cùng (user_id, dedupe_key): báo thức chạy hai lần không tạo hai thông báo. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertIfNew(AppNotificationEntity notification);

    @Query("SELECT * FROM app_notifications WHERE user_id = :userId ORDER BY created_at DESC, id DESC")
    List<AppNotificationEntity> list(long userId);

    @Query("SELECT * FROM app_notifications WHERE id = :id")
    AppNotificationEntity findById(long id);

    @Query("UPDATE app_notifications SET is_read = 1 WHERE id = :id")
    void markRead(long id);

    @Query("UPDATE app_notifications SET is_read = 1 WHERE user_id = :userId AND dedupe_key = :key")
    void markReadByKey(long userId, String key);

    @Query("DELETE FROM app_notifications WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT COUNT(*) FROM app_notifications WHERE user_id = :userId AND is_read = 0")
    LiveData<Integer> observeUnreadCount(long userId);

    @Query("SELECT COUNT(*) FROM app_notifications")
    LiveData<Integer> tick();
}
