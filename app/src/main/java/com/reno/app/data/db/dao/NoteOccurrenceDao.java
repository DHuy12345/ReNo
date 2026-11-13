package com.reno.app.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.reno.app.data.db.entity.NoteOccurrenceEntity;

import java.util.List;

@Dao
public interface NoteOccurrenceDao {
    /** UNIQUE (note_id, occ_date): ghi đè trạng thái cũ của cùng một ngày. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(NoteOccurrenceEntity occurrence);

    /** Bỏ tích: xóa dòng, lần xuất hiện quay về "chưa làm". */
    @Query("DELETE FROM note_occurrences WHERE note_id = :noteId AND occ_date = :date")
    void delete(long noteId, String date);

    @Query("SELECT * FROM note_occurrences WHERE note_id = :noteId AND occ_date = :date LIMIT 1")
    NoteOccurrenceEntity find(long noteId, String date);

    @Query("SELECT o.* FROM note_occurrences o JOIN notes n ON n.id = o.note_id "
            + "WHERE n.user_id = :userId AND o.occ_date BETWEEN :from AND :to")
    List<NoteOccurrenceEntity> listBetween(long userId, String from, String to);
}
