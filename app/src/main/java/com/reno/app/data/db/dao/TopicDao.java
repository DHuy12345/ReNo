package com.reno.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.reno.app.data.db.entity.TopicEntity;

import java.util.List;

@Dao
public interface TopicDao {
    @Insert long insert(TopicEntity topic);

    @Query("UPDATE topics SET name = :name WHERE id = :id")
    void rename(long id, String name);

    @Query("UPDATE topics SET folder_id = :folderId WHERE id = :id")
    void move(long id, long folderId);

    @Query("UPDATE topics SET last_opened_at = :now WHERE id = :id")
    void touch(long id, long now);

    @Query("UPDATE topics SET last_page = :page WHERE id = :id")
    void setLastPage(long id, int page);

    @Query("DELETE FROM topics WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM topics WHERE id = :id")
    TopicEntity findById(long id);

    @Query("SELECT t.* FROM topics t JOIN folders f ON f.id = t.folder_id "
            + "WHERE f.user_id = :userId ORDER BY t.name COLLATE NOCASE")
    List<TopicEntity> listByUser(long userId);

    @Query("SELECT t.* FROM topics t JOIN folders f ON f.id = t.folder_id "
            + "WHERE f.user_id = :userId ORDER BY t.last_opened_at DESC LIMIT :limit")
    List<TopicEntity> recent(long userId, int limit);

    @Query("SELECT file_path FROM topics WHERE folder_id = :folderId")
    List<String> filePathsInFolder(long folderId);

    @Query("SELECT COUNT(*) FROM topics WHERE folder_id = :folderId")
    int countInFolder(long folderId);

    /** Phát lại mỗi khi thư mục, topic hoặc đánh dấu thay đổi. */
    @Query("SELECT (SELECT COUNT(*) FROM folders) + (SELECT COUNT(*) FROM topics) "
            + "+ (SELECT COUNT(*) FROM annotations)")
    LiveData<Integer> tick();
}
