package com.reno.app.ui.camera;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.reno.app.R;
import com.reno.app.databinding.FragmentCameraBinding;
import com.reno.app.ui.vocab.WordFormActivity;

import java.io.File;

/** Mục Máy ảnh: chụp hoặc chọn ảnh → nhận chữ → bôi đen → Dịch → "+" thêm vào từ vựng. */
public class CameraFragment extends Fragment {
    private static final String TAG = "CameraFragment";
    private static final int MENU_TRANSLATE = 1001;
    private static final int MAX_SELECTION = 200;

    private FragmentCameraBinding b;
    private CameraViewModel vm;
    @Nullable private Bitmap currentImage;

    /** App máy ảnh hệ thống ghi vào file tạm trong cacheDir; không cần quyền CAMERA. */
    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), ok -> {
                if (Boolean.TRUE.equals(ok) && vm.cameraUri != null) vm.recognize(vm.cameraUri);
            });

    /** Photo Picker, không cần quyền bộ nhớ. */
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) vm.recognize(uri);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentCameraBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(CameraViewModel.class);

        b.btnTake.setOnClickListener(v -> takePhoto());
        b.btnPick.setOnClickListener(v -> pickImage.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));
        b.btnOther.setOnClickListener(v -> vm.reset());
        b.imgThumb.setOnClickListener(v -> showFullImage());
        b.btnRetry.setOnClickListener(v -> vm.retry());
        b.btnAddWord.setOnClickListener(v -> {
            CameraViewModel.TranslationUi ui = vm.translation().getValue();
            if (ui != null && ui.result != null) {
                // Mở màn Thêm từ vựng, điền sẵn từ và nghĩa.
                startActivity(WordFormActivity.intentForNew(requireContext(), ui.source,
                        ui.result));
            }
        });
        // Bôi đen → menu có thêm mục Dịch.
        b.textOcr.setCustomSelectionActionModeCallback(new TranslateCallback());

        vm.ocrState().observe(getViewLifecycleOwner(), this::bindOcrState);
        vm.ocrText().observe(getViewLifecycleOwner(), text -> b.textOcr.setText(text));
        vm.image().observe(getViewLifecycleOwner(), bitmap -> {
            currentImage = bitmap;
            b.imgThumb.setImageBitmap(bitmap);
        });
        vm.translation().observe(getViewLifecycleOwner(), this::bindTranslation);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void takePhoto() {
        File dir = new File(requireContext().getCacheDir(), "camera");
        if (!dir.exists() && !dir.mkdirs()) Log.w(TAG, "Không tạo được thư mục camera");
        File file = new File(dir, "ocr_" + System.currentTimeMillis() + ".jpg");
        vm.cameraUri = FileProvider.getUriForFile(requireContext(),
                requireContext().getPackageName() + ".fileprovider", file);
        try {
            takePicture.launch(vm.cameraUri);
        } catch (ActivityNotFoundException e) {
            Snackbar.make(b.getRoot(), R.string.camera_unavailable, Snackbar.LENGTH_LONG).show();
        }
    }

    private void bindOcrState(CameraViewModel.OcrState state) {
        boolean idle = state == CameraViewModel.OcrState.IDLE;
        b.sectionChoose.setVisibility(idle ? View.VISIBLE : View.GONE);
        b.sectionResult.setVisibility(idle ? View.GONE : View.VISIBLE);
        b.rowRecognizing.setVisibility(state == CameraViewModel.OcrState.RECOGNIZING
                ? View.VISIBLE : View.GONE);
        boolean done = state == CameraViewModel.OcrState.DONE;
        b.textOcr.setVisibility(done ? View.VISIBLE : View.GONE);
        b.textHint.setVisibility(done ? View.VISIBLE : View.GONE);
        if (state == CameraViewModel.OcrState.EMPTY) {
            b.textMessage.setText(R.string.camera_no_text);
            b.textMessage.setVisibility(View.VISIBLE);
        } else if (state == CameraViewModel.OcrState.ERROR) {
            b.textMessage.setText(R.string.camera_ocr_failed);
            b.textMessage.setVisibility(View.VISIBLE);
        } else {
            b.textMessage.setVisibility(View.GONE);
        }
    }

    private void bindTranslation(CameraViewModel.TranslationUi ui) {
        CameraViewModel.TranslateState s = ui.state;
        b.cardTranslation.setVisibility(s == CameraViewModel.TranslateState.NONE
                ? View.GONE : View.VISIBLE);
        boolean busy = s == CameraViewModel.TranslateState.TRANSLATING
                || s == CameraViewModel.TranslateState.DOWNLOADING;
        b.progressTranslation.setVisibility(busy ? View.VISIBLE : View.GONE);
        b.btnAddWord.setVisibility(s == CameraViewModel.TranslateState.DONE
                ? View.VISIBLE : View.GONE);
        b.btnRetry.setVisibility(s == CameraViewModel.TranslateState.ERROR
                ? View.VISIBLE : View.GONE);
        switch (s) {
            case DONE:
                b.textTranslation.setText(getString(R.string.camera_translation_pair,
                        ui.source, ui.result));
                break;
            case DOWNLOADING:
                b.textTranslation.setText(R.string.camera_model_downloading);
                break;
            case ERROR:
                b.textTranslation.setText(R.string.camera_translate_failed);
                break;
            case NEED_MODEL:
                b.textTranslation.setText(ui.source);
                askDownloadModel();
                break;
            case TRANSLATING:
                b.textTranslation.setText(getString(R.string.camera_translation_pair,
                        ui.source, getString(R.string.camera_translating)));
                break;
            default:
                break;
        }
    }

    /** Lần dịch đầu tiên: hỏi trước khi tải gói ngôn ngữ. */
    private void askDownloadModel() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.camera_model_title)
                .setMessage(R.string.camera_model_message)
                .setNegativeButton(R.string.cancel, (d, w) -> vm.clearTranslation())
                .setPositiveButton(R.string.camera_model_download,
                        (d, w) -> vm.downloadModelAndTranslate())
                .setOnCancelListener(d -> vm.clearTranslation())
                .show();
    }

    /** Bấm ảnh thu nhỏ để phóng to. */
    private void showFullImage() {
        if (currentImage == null) return;
        Dialog dialog = new Dialog(requireContext(),
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView view = new ImageView(requireContext());
        view.setImageBitmap(currentImage);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setContentDescription(getString(R.string.close));
        view.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(view);
        dialog.show();
    }

    /** Thêm mục "Dịch" vào menu bôi đen của văn bản nhận được. */
    private final class TranslateCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            menu.add(Menu.NONE, MENU_TRANSLATE, 0, R.string.camera_translate)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);   // các nút đánh dấu / Dịch phải luôn hiện
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            if (item.getItemId() != MENU_TRANSLATE) return false;
            int start = Math.max(0, Math.min(b.textOcr.getSelectionStart(),
                    b.textOcr.getSelectionEnd()));
            int end = Math.max(b.textOcr.getSelectionStart(), b.textOcr.getSelectionEnd());
            String selected = end > start
                    ? b.textOcr.getText().subSequence(start, end).toString().trim() : "";
            mode.finish();
            if (selected.isEmpty()) return true;
            if (selected.length() > MAX_SELECTION) {
                Snackbar.make(b.getRoot(), R.string.camera_translate_too_long,
                        Snackbar.LENGTH_LONG).show();
            } else {
                vm.translate(selected.replaceAll("\\s+", " "));
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
    }
}
