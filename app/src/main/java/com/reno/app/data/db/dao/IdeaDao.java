package com.reno.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.reno.app.data.db.entity.IdeaEntity;

import java.util.List;

@Dao
public interface IdeaDao {
    @Insert long insert(IdeaEntity idea);

    @Query("UPDATE ideas SET content = :content, updated_at = :now WHERE id = :id")
    void updateContent(long id, String content, long now);

    @Query("UPDATE ideas SET is_done = :isDone, done_at = :doneAt, updated_at = :now WHERE id = :id")
    void setDone(long id, int isDone, Long doneAt, long now);

    @Query("DELETE FROM ideas WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM ideas WHERE user_id = :userId AND is_done = :isDone "
            + "ORDER BY created_at DESC, id DESC")
    List<IdeaEntity> listNewestFirst(long userId, int isDone);

    @Query("SELECT * FROM ideas WHERE user_id = :userId AND is_done = :isDone "
            + "ORDER BY created_at ASC, id ASC")
    List<IdeaEntity> listOldestFirst(long userId, int isDone);

    @Query("SELECT COUNT(*) FROM ideas")
    LiveData<Integer> tick();
}
