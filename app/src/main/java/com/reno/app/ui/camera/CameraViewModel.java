package com.reno.app.ui.camera;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.reno.app.util.AppExecutors;

import java.io.IOException;
import java.io.InputStream;

/**
 * Nhận chữ (ML Kit Text Recognition v2, bản Latin) và dịch Anh → Việt (ML Kit Translation).
 * Cả hai chạy trên thiết bị, ở luồng nền của ML Kit. Kết quả OCR không được lưu: thoát màn là mất.
 */
public class CameraViewModel extends AndroidViewModel {
    private static final String TAG = "CameraViewModel";
    private static final int THUMB_MAX = 1600;

    public enum OcrState { IDLE, RECOGNIZING, DONE, EMPTY, ERROR }

    public enum TranslateState { NONE, NEED_MODEL, DOWNLOADING, TRANSLATING, DONE, ERROR }

    /** Trạng thái của khung dịch. */
    public static final class TranslationUi {
        public final TranslateState state;
        public final String source;
        @Nullable public final String result;

        TranslationUi(TranslateState state, String source, @Nullable String result) {
            this.state = state;
            this.source = source;
            this.result = result;
        }
    }

    private final MutableLiveData<OcrState> ocrState = new MutableLiveData<>(OcrState.IDLE);
    private final MutableLiveData<String> ocrText = new MutableLiveData<>("");
    private final MutableLiveData<Bitmap> image = new MutableLiveData<>();
    private final MutableLiveData<TranslationUi> translation =
            new MutableLiveData<>(new TranslationUi(TranslateState.NONE, "", null));

    /** Tạo khi cần lần đầu, để mở mục Máy ảnh không phải nạp mô hình ngay. */
    @Nullable private TextRecognizer recognizer;
    @Nullable private Translator translator;

    /** File tạm đang chờ app máy ảnh ghi vào. */
    @Nullable public Uri cameraUri;
    /** Tăng mỗi lần nhận ảnh mới, để bỏ qua kết quả của ảnh cũ về muộn. */
    private int generation;

    public CameraViewModel(@NonNull Application app) {
        super(app);
    }

    private TextRecognizer recognizer() {
        if (recognizer == null) {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        }
        return recognizer;
    }

    private Translator translator() {
        if (translator == null) {
            translator = Translation.getClient(new TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.VIETNAMESE)
                    .build());
        }
        return translator;
    }

    public LiveData<OcrState> ocrState() {
        return ocrState;
    }

    public LiveData<String> ocrText() {
        return ocrText;
    }

    public LiveData<Bitmap> image() {
        return image;
    }

    public LiveData<TranslationUi> translation() {
        return translation;
    }

    /** Quay về bảng chọn ảnh. */
    public void reset() {
        generation++;
        ocrState.setValue(OcrState.IDLE);
        ocrText.setValue("");
        image.setValue(null);
        clearTranslation();
    }

    public void clearTranslation() {
        translation.setValue(new TranslationUi(TranslateState.NONE, "", null));
    }

    /** Nhận chữ từ ảnh; InputImage.fromFilePath tự xử lý ảnh bị xoay. */
    public void recognize(Uri uri) {
        int current = ++generation;
        ocrState.setValue(OcrState.RECOGNIZING);
        ocrText.setValue("");
        image.setValue(null);
        clearTranslation();
        AppExecutors.heavy().execute(() -> {
            Bitmap thumb = decodeThumbnail(uri);
            if (current == generation) image.postValue(thumb);
        });
        try {
            InputImage input = InputImage.fromFilePath(getApplication(), uri);
            recognizer().process(input)
                    .addOnSuccessListener(text -> {
                        if (current != generation) return;
                        String value = text.getText().trim();
                        ocrText.setValue(value);
                        ocrState.setValue(value.isEmpty() ? OcrState.EMPTY : OcrState.DONE);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Nhận chữ thất bại", e);
                        if (current == generation) ocrState.setValue(OcrState.ERROR);
                    });
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Không đọc được ảnh", e);
            ocrState.setValue(OcrState.ERROR);
        }
    }

    @Nullable
    private Bitmap decodeThumbnail(Uri uri) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.Source source = ImageDecoder.createSource(
                        getApplication().getContentResolver(), uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    int largest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    int sample = Math.max(1, largest / THUMB_MAX);
                    decoder.setTargetSampleSize(sample);
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            try (InputStream in = getApplication().getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Không tạo được ảnh thu nhỏ", e);
            return null;
        }
    }

    /**
     * Dịch đoạn được bôi đen. Lần đầu, nếu chưa có gói ngôn ngữ thì chuyển sang NEED_MODEL để
     * màn hình hỏi người dùng trước khi tải.
     */
    public void translate(String selected) {
        String source = selected.trim();
        translation.setValue(new TranslationUi(TranslateState.TRANSLATING, source, null));
        TranslateRemoteModel model =
                new TranslateRemoteModel.Builder(TranslateLanguage.VIETNAMESE).build();
        RemoteModelManager.getInstance().isModelDownloaded(model)
                .addOnSuccessListener(downloaded -> {
                    if (!isCurrent(source)) return;
                    if (Boolean.TRUE.equals(downloaded)) {
                        runTranslate(source);
                    } else {
                        translation.setValue(
                                new TranslationUi(TranslateState.NEED_MODEL, source, null));
                    }
                })
                .addOnFailureListener(e -> fail(source, e));
    }

    /** Người dùng đồng ý tải gói dịch Anh – Việt (cần mạng), tải xong thì dịch luôn. */
    public void downloadModelAndTranslate() {
        TranslationUi ui = translation.getValue();
        if (ui == null || ui.source.isEmpty()) return;
        String source = ui.source;
        translation.setValue(new TranslationUi(TranslateState.DOWNLOADING, source, null));
        translator().downloadModelIfNeeded(new DownloadConditions.Builder().build())
                .addOnSuccessListener(unused -> {
                    if (isCurrent(source)) runTranslate(source);
                })
                .addOnFailureListener(e -> fail(source, e));
    }

    /** Thử lại sau khi lỗi mạng. */
    public void retry() {
        TranslationUi ui = translation.getValue();
        if (ui != null && !ui.source.isEmpty()) translate(ui.source);
    }

    private void runTranslate(String source) {
        translation.setValue(new TranslationUi(TranslateState.TRANSLATING, source, null));
        translator().translate(source)
                .addOnSuccessListener(result -> {
                    if (isCurrent(source)) {
                        translation.setValue(
                                new TranslationUi(TranslateState.DONE, source, result));
                    }
                })
                .addOnFailureListener(e -> fail(source, e));
    }

    private void fail(String source, Exception e) {
        Log.e(TAG, "Dịch thất bại", e);
        if (isCurrent(source)) {
            translation.setValue(new TranslationUi(TranslateState.ERROR, source, null));
        }
    }

    private boolean isCurrent(String source) {
        TranslationUi ui = translation.getValue();
        return ui != null && source.equals(ui.source);
    }

    @Override
    protected void onCleared() {
        if (recognizer != null) recognizer.close();
        if (translator != null) translator.close();
    }
}
