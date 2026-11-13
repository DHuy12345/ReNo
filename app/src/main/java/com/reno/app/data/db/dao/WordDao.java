package com.reno.app.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.reno.app.data.db.entity.WordEntity;

import java.util.List;

@Dao
public interface WordDao {
    @Insert long insert(WordEntity word);

    @Query("UPDATE words SET word = :word, meaning = :meaning, folder_id = :folderId, "
            + "remind_time = :remindTime WHERE id = :id")
    void update(long id, String word, String meaning, long folderId, String remindTime);

    @Query("DELETE FROM words WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM words WHERE id = :id")
    WordEntity findById(long id);

    /** Cột word khai báo COLLATE NOCASE nên phép "=" không phân biệt hoa thường. */
    @Query("SELECT * FROM words WHERE folder_id = :folderId AND word = :word LIMIT 1")
    WordEntity findInFolder(long folderId, String word);

    @Query("SELECT * FROM words WHERE folder_id = :folderId ORDER BY created_at, id")
    List<WordEntity> listByFolder(long folderId);

    @Query("SELECT w.* FROM words w JOIN folders f ON f.id = w.folder_id "
            + "WHERE f.user_id = :userId ORDER BY w.created_at, w.id")
    List<WordEntity> listByUser(long userId);

    @Query("SELECT COUNT(*) FROM words WHERE folder_id = :folderId")
    int countInFolder(long folderId);
}
