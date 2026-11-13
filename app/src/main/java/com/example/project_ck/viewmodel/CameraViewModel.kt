package com.example.project_ck.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _recognizedText = MutableStateFlow<String?>(null)
    val recognizedText: StateFlow<String?> = _recognizedText.asStateFlow()

    private val _translatedText = MutableStateFlow<String?>(null)
    val translatedText: StateFlow<String?> = _translatedText.asStateFlow()

    private val _isDownloadingModel = MutableStateFlow(false)
    val isDownloadingModel: StateFlow<Boolean> = _isDownloadingModel.asStateFlow()

    fun processImageUri(uri: Uri, onError: (String) -> Unit) {
        val context = getApplication<Application>()
        _isProcessing.value = true
        _recognizedText.value = null
        _translatedText.value = null

        try {
            val image = InputImage.fromFilePath(context, uri)
            recognizer.process(image)
                .addOnSuccessListener { text ->
                    _isProcessing.value = false
                    if (text.text.isBlank()) {
                        onError("Không tìm thấy chữ trong ảnh")
                    } else {
                        _recognizedText.value = text.text
                    }
                }
                .addOnFailureListener { e ->
                    _isProcessing.value = false
                    onError(e.message ?: "Lỗi nhận diện văn bản")
                }
        } catch (e: Exception) {
            _isProcessing.value = false
            onError(e.message ?: "Không nạp được ảnh")
        }
    }

    fun processImageBitmap(bitmap: Bitmap, onError: (String) -> Unit) {
        _isProcessing.value = true
        _recognizedText.value = null
        _translatedText.value = null

        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { text ->
                _isProcessing.value = false
                if (text.text.isBlank()) {
                    onError("Không tìm thấy chữ trong ảnh")
                } else {
                    _recognizedText.value = text.text
                }
            }
            .addOnFailureListener { e ->
                _isProcessing.value = false
                onError(e.message ?: "Lỗi nhận diện văn bản")
            }
    }

    fun translateSelectedText(selectedText: String, onResult: (String?) -> Unit, onError: (String) -> Unit) {
        if (selectedText.length > 200) {
            onError("Hãy chọn đoạn ngắn hơn (tối đa 200 ký tự)")
            return
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.VIETNAMESE)
            .build()
        val englishVietnameseTranslator = Translation.getClient(options)

        val conditions = DownloadConditions.Builder().build()
        _isDownloadingModel.value = true

        englishVietnameseTranslator.downloadModelIfNeeded(conditions)
            .addOnSuccessListener {
                _isDownloadingModel.value = false
                englishVietnameseTranslator.translate(selectedText)
                    .addOnSuccessListener { translated ->
                        _translatedText.value = translated
                        onResult(translated)
                    }
                    .addOnFailureListener { e ->
                        onError(e.message ?: "Dịch thất bại")
                    }
            }
            .addOnFailureListener { e ->
                _isDownloadingModel.value = false
                onError("Cần tải gói dịch Anh – Việt, cần kết nối mạng. (${e.message})")
            }
    }
}