package com.reno.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.reno.app.data.db.entity.AnnotationEntity;
import com.reno.app.data.model.TypeCount;

import java.util.List;

@Dao
public interface AnnotationDao {
    @Insert long insert(AnnotationEntity annotation);

    @Query("UPDATE annotations SET title = :title, detail = :detail, color = :color, "
            + "updated_at = :now WHERE id = :id")
    void update(long id, String title, String detail, String color, long now);

    @Query("DELETE FROM annotations WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM annotations WHERE id = :id")
    AnnotationEntity findById(long id);

    @Query("SELECT * FROM annotations WHERE topic_id = :topicId ORDER BY page_index, created_at")
    LiveData<List<AnnotationEntity>> observeByTopic(long topicId);

    @Query("SELECT type, COUNT(*) AS count FROM annotations WHERE topic_id = :topicId GROUP BY type")
    List<TypeCount> countByType(long topicId);
}
