package com.reno.app.ui.main;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reno.app.R;
import com.reno.app.data.repository.UserRepository;
import com.reno.app.databinding.ActivityMainBinding;
import com.reno.app.reminder.ReminderScheduler;
import com.reno.app.ui.auth.LoginActivity;
import com.reno.app.ui.calendar.CalendarFragment;
import com.reno.app.ui.camera.CameraFragment;
import com.reno.app.ui.common.AvatarLoader;
import com.reno.app.ui.common.BaseActivity;
import com.reno.app.ui.common.Dialogs;
import com.reno.app.ui.notification.NotificationFragment;
import com.reno.app.ui.profile.ProfileActivity;
import com.reno.app.ui.stats.StatsFragment;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.SessionManager;

/**
 * Màn chính sau khi đăng nhập: thanh trên cùng (avatar + username, nút "…"), nội dung của mục
 * đang chọn, và thanh điều hướng dưới gồm 5 mục Nhà, Thống kê, Máy ảnh, Lịch, Thông báo.
 */
public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity";
    private ActivityMainBinding b;
    private boolean loggingOut;

    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> explainExactAlarmIfNeeded());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentRoot(b.getRoot());

        MainViewModel vm = new ViewModelProvider(this).get(MainViewModel.class);
        vm.user().observe(this, user -> {
            if (user == null) {
                // current_user_id có thể không còn hợp lệ: kiểm tra lại CSDL rồi mới đăng xuất.
                if (!loggingOut) logoutIfAccountMissing();
                return;
            }
            b.textUsername.setText(user.username);
            AvatarLoader.load(b.imgAvatar, user.avatarPath);
        });
        vm.unreadCount().observe(this, count -> {
            BadgeDrawable badge = b.bottomNav.getOrCreateBadge(R.id.nav_notifications);
            badge.setBackgroundColor(ContextCompat.getColor(this, R.color.moss));
            badge.setBadgeTextColor(ContextCompat.getColor(this, R.color.white));
            int n = count == null ? 0 : count;
            badge.setVisible(n > 0);
            if (n > 0) badge.setNumber(n);
        });

        b.btnMore.setOnClickListener(v -> showMoreMenu());
        b.bottomNav.setOnItemSelectedListener(item -> {
            showSection(item.getItemId());
            return true;
        });
        if (savedInstanceState == null) showSection(R.id.nav_home);

        askNotificationPermissionOnce();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Mở app: lập lại lịch nhắc để khớp với dữ liệu và quyền mới nhất.
        if (userId != SessionManager.NO_USER) ReminderScheduler.get(this).rescheduleAll(userId);
    }

    private void showSection(int itemId) {
        Fragment current = getSupportFragmentManager().findFragmentById(R.id.container);
        String tag = String.valueOf(itemId);
        if (current != null && tag.equals(current.getTag())) return;
        Fragment fragment;
        if (itemId == R.id.nav_stats) {
            fragment = new StatsFragment();
        } else if (itemId == R.id.nav_camera) {
            fragment = new CameraFragment();
        } else if (itemId == R.id.nav_calendar) {
            fragment = new CalendarFragment();
        } else if (itemId == R.id.nav_notifications) {
            fragment = new NotificationFragment();
        } else {
            fragment = new HomeFragment();
        }
        getSupportFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.container, fragment, tag)
                .commit();
    }

    private void showMoreMenu() {
        PopupMenu menu = new PopupMenu(this, b.btnMore);
        menu.inflate(R.menu.main_more);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
            } else if (id == R.id.menu_guide) {
                startActivity(new Intent(this, GuideActivity.class));
            } else if (id == R.id.menu_logout) {
                Dialogs.confirm(this, getString(R.string.logout),
                        getString(R.string.logout_confirm), R.string.yes, R.string.no,
                        this::logout);
            }
            return true;
        });
        menu.show();
    }

    private void logoutIfAccountMissing() {
        AppExecutors.diskIO().execute(() -> {
            boolean missing = UserRepository.get(this).findSync(userId) == null;
            runOnUiThread(() -> {
                if (missing && !loggingOut && !isFinishing()) logout();
            });
        });
    }

    /** Hủy mọi báo thức đã đặt, xóa current_user_id, về màn đăng nhập và xóa ngăn xếp màn hình. */
    private void logout() {
        loggingOut = true;
        SessionManager.logout(this);
        AppExecutors.diskIO().execute(() -> ReminderScheduler.get(this).cancelAll());
        NotificationManagerCompat.from(this).cancelAll();
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    /** Android 13 trở lên: xin quyền thông báo ngay sau lần đăng nhập đầu. */
    private void askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !SessionManager.askedNotificationPermission(this, userId)
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            SessionManager.setAskedNotificationPermission(this, userId);
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            explainExactAlarmIfNeeded();
        }
    }

    /** Android 12 trở lên: giải thích một lần và mở cài đặt "Báo thức và lời nhắc". */
    private void explainExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || ReminderScheduler.get(this).canScheduleExact()
                || SessionManager.explainedExactAlarm(this, userId)) {
            return;
        }
        SessionManager.setExplainedExactAlarm(this, userId);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permission_exact_alarm_title)
                .setMessage(R.string.permission_exact_alarm_message)
                .setNegativeButton(R.string.permission_later, null)
                .setPositiveButton(R.string.permission_open_settings, (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                Uri.parse("package:" + getPackageName())));
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Không mở được cài đặt báo thức", e);
                    }
                })
                .show();
    }
}
