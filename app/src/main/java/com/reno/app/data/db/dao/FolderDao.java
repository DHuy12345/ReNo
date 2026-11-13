package com.reno.app.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.reno.app.data.db.entity.FolderEntity;

import java.util.List;

@Dao
public interface FolderDao {
    @Insert long insert(FolderEntity folder);

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    void rename(long id, String name);

    @Query("DELETE FROM folders WHERE id = :id")
    void deleteById(long id);

    @Query("SELECT * FROM folders WHERE user_id = :userId AND type = :type ORDER BY name COLLATE NOCASE")
    List<FolderEntity> list(long userId, String type);

    @Query("SELECT * FROM folders WHERE user_id = :userId AND type = :type AND name = :name LIMIT 1")
    FolderEntity findByName(long userId, String type, String name);

    @Query("SELECT * FROM folders WHERE id = :id")
    FolderEntity findById(long id);
}
