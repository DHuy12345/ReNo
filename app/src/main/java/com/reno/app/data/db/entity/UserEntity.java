package com.reno.app.data.db.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Bảng users – tài khoản cục bộ. Ràng buộc "email hoặc phone phải có" nằm trong DbConstraints. */
@Entity(tableName = "users",
        indices = {
                @Index(value = {"username"}, unique = true),
                @Index(value = {"email"}, unique = true),
                @Index(value = {"phone"}, unique = true)})
public class UserEntity {
    @PrimaryKey(autoGenerate = true) public long id;

    @NonNull @ColumnInfo(collate = ColumnInfo.NOCASE) public String username = "";

    public String email;   // chữ thường, có thể null

    public String phone;   // dạng 0xxxxxxxxx, có thể null

    @NonNull @ColumnInfo(name = "password_hash") public String passwordHash = "";

    @ColumnInfo(name = "avatar_path") public String avatarPath;   // null = ảnh mặc định

    @NonNull @ColumnInfo(name = "review_time") public String reviewTime = "20:00";   // HH:mm

    @ColumnInfo(name = "created_at") public long createdAt;

    @ColumnInfo(name = "updated_at") public long updatedAt;
}
