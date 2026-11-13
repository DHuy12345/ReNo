package com.reno.app.integration;

import com.reno.app.TestEnv;
import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.entity.UserEntity;
import com.reno.app.util.SessionManager;

/** Dữ liệu mẫu dùng chung. */
final class Fixtures {
    private Fixtures() {
    }

    /** Tạo nhanh một tài khoản (không băm mật khẩu) và đăng nhập vào đó. */
    static long loggedInUser(String username, String reviewTime) {
        UserEntity u = new UserEntity();
        u.username = username;
        u.email = username + "@reno.test";
        u.passwordHash = "pbkdf2$1$AA==$AA==";
        u.reviewTime = reviewTime;
        u.createdAt = System.currentTimeMillis();
        u.updatedAt = u.createdAt;
        long id = RenoDatabase.get(TestEnv.context()).userDao().insert(u);
        SessionManager.login(TestEnv.context(), id);
        return id;
    }
}
