package com.reno.app.ui.profile;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.reno.app.R;
import com.reno.app.data.db.entity.UserEntity;
import com.reno.app.data.repository.UserRepository;
import com.reno.app.databinding.ActivityProfileBinding;
import com.reno.app.ui.common.AvatarLoader;
import com.reno.app.ui.common.BaseActivity;
import com.reno.app.ui.common.Dialogs;
import com.reno.app.ui.common.Pickers;
import com.reno.app.util.DateUtils;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.Objects;

/** Màn Thông tin người dùng: avatar, username, email / số điện thoại, giờ nhắc ôn tập. */
public class ProfileActivity extends BaseActivity {
    private static final String TAG = "ProfileActivity";
    private static final int AVATAR_SIZE = 512;
    private static final int AVATAR_QUALITY = 85;

    private ActivityProfileBinding b;
    private ProfileViewModel vm;
    private UserEntity loaded;

    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && vm.cameraFilePath != null) {
                    startCrop(Uri.fromFile(new File(vm.cameraFilePath)));
                }
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) startCrop(uri);
            });

    private final ActivityResultLauncher<Intent> cropLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (result.getResultCode() == RESULT_OK && data != null) {
                    Uri output = UCrop.getOutput(data);
                    if (output != null && output.getPath() != null) {
                        vm.setPendingAvatar(output.getPath());
                        AvatarLoader.load(b.imgAvatar, output.getPath());
                        return;
                    }
                }
                if (result.getResultCode() == UCrop.RESULT_ERROR) {
                    Snackbar.make(b.getRoot(), R.string.avatar_error, Snackbar.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;
        b = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentRoot(b.getRoot());
        vm = new ViewModelProvider(this).get(ProfileViewModel.class);

        b.toolbar.textTitle.setText(R.string.profile_title);
        b.toolbar.btnBack.setOnClickListener(v -> confirmLeave());
        b.btnReturn.setOnClickListener(v -> confirmLeave());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                confirmLeave();
            }
        });

        b.imgAvatar.setOnClickListener(v -> showAvatarOptions());
        b.btnAvatar.setOnClickListener(v -> showAvatarOptions());
        b.rowReviewTime.setOnClickListener(v -> {
            if (vm.reviewTime == null) return;
            Pickers.pickTime(getSupportFragmentManager(), DateUtils.parseTime(vm.reviewTime),
                    time -> {
                        vm.reviewTime = DateUtils.formatTime(time);
                        b.textReviewTime.setText(vm.reviewTime);
                    });
        });
        b.btnSave.setOnClickListener(v -> vm.save(text(b.editUsername.getText()),
                text(b.editEmail.getText()), text(b.editPhone.getText())));

        vm.user().observe(this, user -> {
            if (user == null) {
                finish();
                return;
            }
            loaded = user;
            if (!vm.formFilled) {
                // chỉ điền lần đầu để không ghi đè nội dung đang sửa dở
                vm.formFilled = true;
                vm.reviewTime = user.reviewTime;
                b.editUsername.setText(user.username);
                b.editEmail.setText(user.email);
                b.editPhone.setText(user.phone);
            }
            b.textReviewTime.setText(vm.reviewTime);
            b.textCreated.setText(DateUtils.formatFullDate(DateUtils.toLocalDate(user.createdAt)));
            AvatarLoader.load(b.imgAvatar, vm.resetAvatar ? null
                    : vm.pendingAvatarPath != null ? vm.pendingAvatarPath : user.avatarPath);
        });
        vm.saveResult().observe(this, event -> {
            UserRepository.ProfileResult r = event.consume();
            if (r == null) return;
            setError(b.tilUsername, r.usernameError);
            setError(b.tilEmail, r.emailError);
            setError(b.tilPhone, r.phoneError);
            if (r.isOk()) {
                android.widget.Toast.makeText(this, R.string.profile_saved,
                        android.widget.Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /** Bảng chọn: Chụp ảnh, Chọn từ thư viện, Dùng ảnh mặc định. */
    private void showAvatarOptions() {
        CharSequence[] items = {getString(R.string.avatar_take_photo),
                getString(R.string.avatar_pick_gallery), getString(R.string.avatar_use_default)};
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.avatar_change)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        takePhoto();
                    } else if (which == 1) {
                        pickImage.launch(new PickVisualMediaRequest.Builder()
                                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build());
                    } else {
                        vm.useDefaultAvatar();
                        AvatarLoader.load(b.imgAvatar, null);
                    }
                })
                .show();
    }

    /** App máy ảnh hệ thống ghi vào file tạm trong cacheDir, chia sẻ qua FileProvider. */
    private void takePhoto() {
        File dir = new File(getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) Log.w(TAG, "Không tạo được thư mục camera");
        File file = new File(dir, "avatar_" + System.currentTimeMillis() + ".jpg");
        vm.cameraFilePath = file.getAbsolutePath();
        try {
            takePicture.launch(FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file));
        } catch (ActivityNotFoundException e) {
            Snackbar.make(b.getRoot(), R.string.camera_unavailable, Snackbar.LENGTH_LONG).show();
        }
    }

    /** Mở uCrop khung tròn tỉ lệ 1:1; kết quả 512×512, JPEG 85%, lưu vào filesDir/avatars. */
    private void startCrop(Uri source) {
        File target = new File(UserRepository.get(this).avatarDir(),
                userId + "_" + System.currentTimeMillis() + ".jpg");
        UCrop.Options options = new UCrop.Options();
        options.setCircleDimmedLayer(true);
        options.setShowCropFrame(false);
        options.setShowCropGrid(false);
        options.setHideBottomControls(true);
        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
        options.setCompressionQuality(AVATAR_QUALITY);
        options.setToolbarTitle(getString(R.string.avatar_crop_title));
        options.setToolbarColor(ContextCompat.getColor(this, R.color.bg_main));
        options.setToolbarWidgetColor(ContextCompat.getColor(this, R.color.text_primary));
        options.setActiveControlsWidgetColor(ContextCompat.getColor(this, R.color.moss));
        options.setStatusBarLight(true);
        Intent intent = UCrop.of(source, Uri.fromFile(target))
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(AVATAR_SIZE, AVATAR_SIZE)
                .withOptions(options)
                .getIntent(this);
        cropLauncher.launch(intent);
    }

    private boolean hasChanges() {
        if (loaded == null) return false;
        return vm.pendingAvatarPath != null
                || (vm.resetAvatar && loaded.avatarPath != null)
                || !Objects.equals(vm.reviewTime, loaded.reviewTime)
                || !text(b.editUsername.getText()).trim().equals(loaded.username)
                || !text(b.editEmail.getText()).trim().equals(nullToEmpty(loaded.email))
                || !text(b.editPhone.getText()).trim().equals(nullToEmpty(loaded.phone));
    }

    /** Bấm Trở lại hoặc ← khi có thay đổi chưa lưu: hỏi "Bỏ các thay đổi?". */
    private void confirmLeave() {
        if (!hasChanges()) {
            finish();
            return;
        }
        Dialogs.confirm(this, getString(R.string.profile_discard_title),
                getString(R.string.profile_discard_message), R.string.profile_discard,
                R.string.profile_keep_editing, () -> {
                    vm.discardPendingAvatar();
                    finish();
                });
    }

    private void setError(TextInputLayout til, @StringRes int error) {
        til.setError(error == 0 ? null : getString(error));
    }

    private static String text(@Nullable CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }

    private static String nullToEmpty(@Nullable String s) {
        return s == null ? "" : s;
    }
}
