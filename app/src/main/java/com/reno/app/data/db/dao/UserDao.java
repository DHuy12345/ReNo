package com.reno.app.data.db.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.reno.app.data.db.entity.UserEntity;

import java.util.List;

@Dao
public interface UserDao {
    @Insert long insert(UserEntity user);

    @Update void update(UserEntity user);

    @Query("SELECT * FROM users WHERE id = :id")
    UserEntity findById(long id);

    @Query("SELECT * FROM users WHERE id = :id")
    LiveData<UserEntity> observeById(long id);

    /** Cột username khai báo COLLATE NOCASE nên phép "=" không phân biệt hoa thường. */
    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    UserEntity findByUsername(String username);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    UserEntity findByEmail(String email);

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    UserEntity findByPhone(String phone);

    @Query("SELECT avatar_path FROM users WHERE avatar_path IS NOT NULL")
    List<String> allAvatarPaths();
}
