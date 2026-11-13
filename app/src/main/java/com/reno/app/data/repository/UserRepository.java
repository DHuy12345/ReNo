package com.reno.app.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.LiveData;

import com.reno.app.R;
import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.dao.UserDao;
import com.reno.app.data.db.entity.UserEntity;
import com.reno.app.domain.PasswordHasher;
import com.reno.app.domain.Validators;
import com.reno.app.reminder.ReminderScheduler;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.Callback;
import com.reno.app.util.DateUtils;

import java.io.File;
import java.security.GeneralSecurityException;
import java.time.LocalTime;

/** Tài khoản cục bộ: đăng ký, đăng nhập, sửa thông tin, avatar. Mọi thao tác chạy ở luồng nền. */
public class UserRepository {
    private static final String TAG = "UserRepository";
    private static volatile UserRepository INSTANCE;

    private final Context app;
    private final UserDao userDao;

    private UserRepository(Context ctx) {
        app = ctx.getApplicationContext();
        userDao = RenoDatabase.get(app).userDao();
    }

    public static UserRepository get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (UserRepository.class) {
                if (INSTANCE == null) INSTANCE = new UserRepository(ctx);
            }
        }
        return INSTANCE;
    }

    /** Kết quả đăng ký: mỗi trường là mã chuỗi lỗi hiển thị dưới ô nhập, 0 = không lỗi. */
    public static final class RegisterResult {
        @StringRes public int contactError;
        @StringRes public int usernameError;
        @StringRes public int passwordError;
        @StringRes public int confirmError;
        /** Username đã lưu, để điền sẵn ở màn đăng nhập. */
        public String username;

        public boolean isOk() {
            return contactError == 0 && usernameError == 0 && passwordError == 0
                    && confirmError == 0;
        }
    }

    /** Kết quả lưu màn Thông tin người dùng. */
    public static final class ProfileResult {
        @StringRes public int usernameError;
        @StringRes public int emailError;
        @StringRes public int phoneError;

        public boolean isOk() {
            return usernameError == 0 && emailError == 0 && phoneError == 0;
        }
    }

    public LiveData<UserEntity> observeUser(long userId) {
        return userDao.observeById(userId);
    }

    /** Gọi ở luồng nền. */
    @Nullable
    public UserEntity findSync(long userId) {
        return userDao.findById(userId);
    }

    /** Kiểm tra theo bảng 3.1, băm mật khẩu rồi tạo tài khoản. */
    public void register(String contactRaw, String usernameRaw, String password, String confirm,
                         Callback<RegisterResult> callback) {
        AppExecutors.heavy().execute(() -> {
            RegisterResult result = new RegisterResult();
            Validators.Contact contact = Validators.parseContact(contactRaw);
            String username = Validators.normalizeUsername(usernameRaw);

            if (contact == null) {
                result.contactError = R.string.error_contact_invalid;
            } else if ((contact.email != null && userDao.findByEmail(contact.email) != null)
                    || (contact.phone != null && userDao.findByPhone(contact.phone) != null)) {
                result.contactError = R.string.error_contact_taken;
            }
            result.usernameError = usernameFormatError(username);
            if (result.usernameError == 0 && userDao.findByUsername(username) != null) {
                result.usernameError = R.string.error_username_taken;
            }
            result.passwordError = passwordError(password);
            if (confirm == null || !confirm.equals(password)) {
                result.confirmError = R.string.error_password_mismatch;
            }

            if (result.isOk()) {
                try {
                    long now = System.currentTimeMillis();
                    UserEntity user = new UserEntity();
                    user.username = username;
                    user.email = contact.email;
                    user.phone = contact.phone;
                    user.passwordHash = PasswordHasher.hash(password.toCharArray());
                    user.avatarPath = null;                       // dùng ảnh mặc định
                    user.reviewTime = DateUtils.formatTime(LocalTime.now());   // giờ phút hiện tại
                    user.createdAt = now;
                    user.updatedAt = now;
                    userDao.insert(user);
                    result.username = username;
                } catch (GeneralSecurityException e) {
                    Log.e(TAG, "Không băm được mật khẩu", e);
                    result.passwordError = R.string.error_generic;
                } catch (RuntimeException e) {
                    // UNIQUE bị vi phạm do hai lần bấm sát nhau
                    Log.e(TAG, "Không tạo được tài khoản", e);
                    result.usernameError = R.string.error_username_taken;
                }
            }
            AppExecutors.mainThread().execute(() -> callback.onResult(result));
        });
    }

    /**
     * Tìm tài khoản theo username (không phân biệt hoa thường) và so khớp mật khẩu đã băm.
     *
     * @param callback nhận mã người dùng, hoặc null nếu sai username hoặc mật khẩu
     */
    public void login(String usernameRaw, String password, Callback<Long> callback) {
        AppExecutors.heavy().execute(() -> {
            Long userId = null;
            try {
                UserEntity user = userDao.findByUsername(Validators.normalizeUsername(usernameRaw));
                if (user != null && password != null
                        && PasswordHasher.verify(password.toCharArray(), user.passwordHash)) {
                    userId = user.id;
                }
            } catch (GeneralSecurityException | RuntimeException e) {
                Log.e(TAG, "Lỗi khi đăng nhập", e);
            }
            Long finalUserId = userId;
            AppExecutors.mainThread().execute(() -> callback.onResult(finalUserId));
        });
    }

    /**
     * Lưu màn Thông tin người dùng. Giá trị mới phải qua kiểm tra bảng 3.1, kể cả kiểm tra trùng;
     * được lưu cả email lẫn số điện thoại nhưng không được xóa cả hai.
     *
     * @param newAvatarPath đường dẫn ảnh mới đã cắt; null = giữ nguyên
     * @param resetAvatar   true = dùng lại ảnh mặc định
     */
    public void updateProfile(long userId, String usernameRaw, String emailRaw, String phoneRaw,
                              @NonNull String reviewTime, @Nullable String newAvatarPath,
                              boolean resetAvatar, Callback<ProfileResult> callback) {
        AppExecutors.diskIO().execute(() -> {
            ProfileResult result = new ProfileResult();
            UserEntity user = userDao.findById(userId);
            if (user == null) {
                result.usernameError = R.string.error_generic;
                AppExecutors.mainThread().execute(() -> callback.onResult(result));
                return;
            }
            String username = Validators.normalizeUsername(usernameRaw);
            result.usernameError = usernameFormatError(username);
            if (result.usernameError == 0) {
                UserEntity other = userDao.findByUsername(username);
                if (other != null && other.id != userId) {
                    result.usernameError = R.string.error_username_taken;
                }
            }

            boolean emailEmpty = emailRaw == null || emailRaw.trim().isEmpty();
            boolean phoneEmpty = phoneRaw == null || phoneRaw.trim().isEmpty();
            String email = emailEmpty ? null : Validators.normalizeEmail(emailRaw);
            String phone = phoneEmpty ? null : Validators.normalizePhone(phoneRaw);
            if (emailEmpty && phoneEmpty) {
                result.emailError = R.string.error_contact_both_empty;
                result.phoneError = R.string.error_contact_both_empty;
            } else {
                if (!emailEmpty) {
                    if (email == null) {
                        result.emailError = R.string.error_email_invalid;
                    } else {
                        UserEntity other = userDao.findByEmail(email);
                        if (other != null && other.id != userId) {
                            result.emailError = R.string.error_contact_taken;
                        }
                    }
                }
                if (!phoneEmpty) {
                    if (phone == null) {
                        result.phoneError = R.string.error_phone_invalid;
                    } else {
                        UserEntity other = userDao.findByPhone(phone);
                        if (other != null && other.id != userId) {
                            result.phoneError = R.string.error_contact_taken;
                        }
                    }
                }
            }

            if (result.isOk()) {
                String oldAvatar = user.avatarPath;
                boolean reviewTimeChanged = !reviewTime.equals(user.reviewTime);
                user.username = username;
                user.email = email;
                user.phone = phone;
                user.reviewTime = reviewTime;
                if (resetAvatar) {
                    user.avatarPath = null;
                } else if (newAvatarPath != null) {
                    user.avatarPath = newAvatarPath;
                }
                user.updatedAt = System.currentTimeMillis();
                userDao.update(user);
                // Ảnh cũ bị xóa khi lưu ảnh mới (file không tự mất theo CSDL).
                if (oldAvatar != null && !oldAvatar.equals(user.avatarPath)) {
                    deleteQuietly(oldAvatar);
                }
                // Đổi giờ nhắc chung thì các từ dùng "Giờ mặc định" đổi theo.
                if (reviewTimeChanged) ReminderScheduler.get(app).rescheduleNow(userId);
            }
            AppExecutors.mainThread().execute(() -> callback.onResult(result));
        });
    }

    /** Thư mục chứa avatar đã cắt: filesDir/avatars. */
    public File avatarDir() {
        File dir = new File(app.getFilesDir(), "avatars");
        if (!dir.exists() && !dir.mkdirs()) Log.w(TAG, "Không tạo được thư mục avatars");
        return dir;
    }

    public static void deleteQuietly(@Nullable String path) {
        if (path == null) return;
        File f = new File(path);
        if (f.exists() && !f.delete()) Log.w(TAG, "Không xóa được file " + path);
    }

    @StringRes
    private static int usernameFormatError(String username) {
        if (username.contains("@")) return R.string.error_username_at;
        return Validators.isValidUsername(username) ? 0 : R.string.error_username_length;
    }

    @StringRes
    private static int passwordError(String password) {
        if (password == null || password.length() < Validators.PASSWORD_MIN) {
            return R.string.error_password_short;
        }
        return password.length() > Validators.PASSWORD_MAX ? R.string.error_password_long : 0;
    }
}
