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

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    UserEntity findByUsernameNocase(String username);

    @Query("SELECT * FROM users")
    List<UserEntity> listAll();

    /**
     * Tìm theo username, không phân biệt hoa thường. Cột khai báo COLLATE NOCASE (nhanh, đúng
     * với chữ ASCII); với chữ có dấu thì so sánh thêm bằng Java.
     */
    default UserEntity findByUsername(String username) {
        UserEntity u = findByUsernameNocase(username);
        if (u != null) return u;
        for (UserEntity candidate : listAll()) {
            if (com.reno.app.util.TextKeys.sameIgnoreCase(candidate.username, username)) {
                return candidate;
            }
        }
        return null;
    }

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    UserEntity findByEmail(String email);

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    UserEntity findByPhone(String phone);

    @Query("SELECT avatar_path FROM users WHERE avatar_path IS NOT NULL")
    List<String> allAvatarPaths();
}
