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

    @Query("SELECT * FROM folders WHERE user_id = :userId AND type = :type")
    List<FolderEntity> listUnsorted(long userId, String type);

    /** Tìm thư mục trùng tên, không phân biệt hoa thường (kể cả chữ có dấu). */
    default FolderEntity findByName(long userId, String type, String name) {
        for (FolderEntity f : listUnsorted(userId, type)) {
            if (com.reno.app.util.TextKeys.sameIgnoreCase(f.name, name)) return f;
        }
        return null;
    }

    @Query("SELECT * FROM folders WHERE id = :id")
    FolderEntity findById(long id);
}
