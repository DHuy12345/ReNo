package com.reno.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.reno.app.data.db.entity.NoteEntity;

import java.util.List;

@Dao
public interface NoteDao {
    @Insert long insert(NoteEntity note);

    @Update void update(NoteEntity note);

    @Query("DELETE FROM notes WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM notes WHERE id = :id")
    NoteEntity findById(long id);

    @Query("SELECT * FROM notes WHERE user_id = :userId ORDER BY dtstart")
    List<NoteEntity> listByUser(long userId);

    @Query("SELECT DISTINCT color FROM notes WHERE user_id = :userId")
    List<String> usedColors(long userId);

    /** Phát lại mỗi khi ghi chú hoặc trạng thái của một ngày thay đổi. */
    @Query("SELECT (SELECT COUNT(*) FROM notes) + (SELECT COUNT(*) FROM note_occurrences)")
    LiveData<Integer> tick();
}
