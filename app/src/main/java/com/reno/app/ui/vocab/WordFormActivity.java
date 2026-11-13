package com.reno.app.ui.vocab;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.reno.app.R;
import com.reno.app.data.db.entity.FolderEntity;
import com.reno.app.data.db.entity.UserEntity;
import com.reno.app.data.db.entity.WordEntity;
import com.reno.app.data.repository.UserRepository;
import com.reno.app.data.repository.VocabRepository;
import com.reno.app.databinding.ActivityWordFormBinding;
import com.reno.app.domain.ReviewPlanner;
import com.reno.app.ui.common.BaseActivity;
import com.reno.app.ui.common.Dialogs;
import com.reno.app.ui.common.Pickers;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.DateUtils;
import com.reno.app.util.SessionManager;

import java.time.LocalDate;
import java.util.List;

/**
 * Màn thêm / sửa từ vựng. Khi mở từ khung dịch của màn Máy ảnh, từ và nghĩa được điền sẵn;
 * người dùng chỉ cần chọn thư mục và Lưu.
 */
public class WordFormActivity extends BaseActivity {
    private static final String EXTRA_WORD_ID = "word_id";
    private static final String EXTRA_WORD = "word";
    private static final String EXTRA_MEANING = "meaning";
    private static final int FOLDER_NAME_MAX = 100;

    private ActivityWordFormBinding b;
    private WordFormViewModel vm;
    private VocabRepository repo;

    public static Intent intentForNew(Context ctx, @Nullable String word,
                                      @Nullable String meaning) {
        return new Intent(ctx, WordFormActivity.class)
                .putExtra(EXTRA_WORD, word)
                .putExtra(EXTRA_MEANING, meaning);
    }

    public static Intent intentForEdit(Context ctx, long wordId) {
        return new Intent(ctx, WordFormActivity.class).putExtra(EXTRA_WORD_ID, wordId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;
        b = ActivityWordFormBinding.inflate(getLayoutInflater());
        setContentRoot(b.getRoot());
        vm = new ViewModelProvider(this).get(WordFormViewModel.class);
        repo = VocabRepository.get(this);

        vm.wordId = getIntent().getLongExtra(EXTRA_WORD_ID, 0);
        b.toolbar.textTitle.setText(vm.wordId == 0 ? R.string.vocab_add_word
                : R.string.vocab_edit_word);
        b.toolbar.btnBack.setOnClickListener(v -> finish());
        b.btnFolder.setOnClickListener(v -> chooseFolder());
        b.btnSave.setOnClickListener(v -> save());
        b.groupRemind.setOnCheckedChangeListener((group, checkedId) -> {
            if (!vm.initialized) return;
            vm.customTime = checkedId == R.id.radio_custom;
            if (vm.customTime && vm.time == null) vm.time = DateUtils.parseTime(vm.defaultTime);
            bindState();
        });
        b.btnTime.setOnClickListener(v -> Pickers.pickTime(getSupportFragmentManager(),
                vm.time == null ? DateUtils.parseTime(vm.defaultTime) : vm.time, time -> {
                    vm.time = time;
                    bindState();
                }));

        if (vm.initialized) {
            bindState();
        } else {
            load(savedInstanceState == null);
        }
    }

    /** Nạp thư mục, giờ nhắc chung và (khi sửa) từ đang sửa ở luồng nền. */
    private void load(boolean fillTexts) {
        b.btnSave.setEnabled(false);
        long lastFolder = SessionManager.lastVocabFolder(this, userId);
        AppExecutors.diskIO().execute(() -> {
            UserEntity user = UserRepository.get(this).findSync(userId);
            runOnUiThread(() -> {
                if (user != null) vm.defaultTime = user.reviewTime;
                repo.listFolders(userId, folders -> {
                    vm.folders.clear();
                    vm.folders.addAll(folders);
                    if (vm.wordId == 0) {
                        // Mặc định là thư mục dùng lần trước.
                        for (FolderEntity f : folders) {
                            if (f.id == lastFolder) vm.folderId = f.id;
                        }
                        if (fillTexts) {
                            b.editWord.setText(getIntent().getStringExtra(EXTRA_WORD));
                            b.editMeaning.setText(getIntent().getStringExtra(EXTRA_MEANING));
                        }
                        finishLoading();
                    } else {
                        repo.findWord(vm.wordId, word -> {
                            if (word == null) {
                                Toast.makeText(this, R.string.word_missing,
                                        Toast.LENGTH_SHORT).show();
                                finish();
                                return;
                            }
                            fillFromWord(word, fillTexts);
                            finishLoading();
                        });
                    }
                });
            });
        });
    }

    private void fillFromWord(WordEntity word, boolean fillTexts) {
        vm.folderId = word.folderId;
        vm.customTime = word.remindTime != null;
        vm.time = word.remindTime == null ? null : DateUtils.parseTime(word.remindTime);
        if (fillTexts) {
            b.editWord.setText(word.word);
            b.editMeaning.setText(word.meaning);
        }
    }

    private void finishLoading() {
        vm.initialized = true;
        b.btnSave.setEnabled(true);
        bindState();
    }

    private void bindState() {
        b.radioDefault.setText(getString(R.string.vocab_remind_default, vm.defaultTime));
        int radio = vm.customTime ? R.id.radio_custom : R.id.radio_default;
        if (b.groupRemind.getCheckedRadioButtonId() != radio) b.groupRemind.check(radio);
        b.btnTime.setVisibility(vm.customTime ? View.VISIBLE : View.GONE);
        if (vm.time != null) b.btnTime.setText(DateUtils.formatTime(vm.time));

        FolderEntity folder = currentFolder();
        b.btnFolder.setText(folder == null ? getString(R.string.vocab_folder_choose)
                : folder.name);

        // Thêm mới: hiện trước 5 ngày ôn sẽ được tạo. Sửa từ không làm đổi lịch ôn.
        if (vm.wordId == 0) {
            StringBuilder sb = new StringBuilder();
            for (LocalDate d : ReviewPlanner.reviewDates(DateUtils.today())) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(DateUtils.formatDayMonth(d));
            }
            b.textSchedule.setText(getString(R.string.vocab_schedule_preview, sb.toString()));
            b.textSchedule.setVisibility(View.VISIBLE);
        } else {
            b.textSchedule.setVisibility(View.GONE);
        }
    }

    @Nullable
    private FolderEntity currentFolder() {
        for (FolderEntity f : vm.folders) {
            if (f.id == vm.folderId) return f;
        }
        return null;
    }

    /** Danh sách thư mục, mục cuối là "+ Thư mục mới". */
    private void chooseFolder() {
        List<FolderEntity> folders = vm.folders;
        CharSequence[] names = new CharSequence[folders.size() + 1];
        int checked = -1;
        for (int i = 0; i < folders.size(); i++) {
            names[i] = folders.get(i).name;
            if (folders.get(i).id == vm.folderId) checked = i;
        }
        names[folders.size()] = getString(R.string.vocab_folder_new);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.vocab_folder_choose)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    d.dismiss();
                    if (which < folders.size()) {
                        vm.folderId = folders.get(which).id;
                        b.textFolderError.setVisibility(View.GONE);
                        bindState();
                    } else {
                        createFolder();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void createFolder() {
        Dialogs.input(this, getString(R.string.folder_create),
                getString(R.string.folder_name_hint), null, FOLDER_NAME_MAX, false,
                (text, handle) -> repo.createFolder(userId, text, result -> {
                    if (result[0] != 0) {
                        handle.setError(getString((int) result[0]));
                        return;
                    }
                    handle.dismiss();
                    repo.listFolders(userId, folders -> {
                        vm.folders.clear();
                        vm.folders.addAll(folders);
                        vm.folderId = result[1];
                        b.textFolderError.setVisibility(View.GONE);
                        bindState();
                    });
                }));
    }

    private void save() {
        if (!vm.initialized) return;
        String remindTime = vm.customTime && vm.time != null
                ? DateUtils.formatTime(vm.time) : null;
        b.btnSave.setEnabled(false);
        repo.saveWord(userId, vm.wordId, text(b.editWord.getText()),
                text(b.editMeaning.getText()), vm.folderId, remindTime, result -> {
                    b.btnSave.setEnabled(true);
                    b.tilWord.setError(result.wordError == 0 ? null
                            : getString(result.wordError));
                    b.tilMeaning.setError(result.meaningError == 0 ? null
                            : getString(result.meaningError));
                    b.textFolderError.setVisibility(result.folderError == 0
                            ? View.GONE : View.VISIBLE);
                    if (result.folderError != 0) b.textFolderError.setText(result.folderError);
                    if (!result.isOk()) return;
                    String message = result.firstReview == null
                            ? getString(R.string.vocab_saved)
                            : getString(R.string.vocab_added,
                            DateUtils.formatDayMonth(result.firstReview));
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private static String text(@Nullable CharSequence cs) {
        return cs == null ? "" : cs.toString();
    }
}
