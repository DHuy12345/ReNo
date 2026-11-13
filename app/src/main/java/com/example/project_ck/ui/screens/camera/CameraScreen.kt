package com.example.project_ck.ui.screens.camera

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.project_ck.data.entity.Folder
import com.example.project_ck.data.entity.Word
import com.example.project_ck.ui.screens.home.AddEditWordDialog
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.viewmodel.CameraViewModel
import com.example.project_ck.viewmodel.HomeViewModel
import java.io.File

@Composable
fun CameraScreen(
    cameraViewModel: CameraViewModel,
    homeViewModel: HomeViewModel
) {
    val context = LocalContext.current
    val isProcessing by cameraViewModel.isProcessing.collectAsState()
    val recognizedText by cameraViewModel.recognizedText.collectAsState()
    val translatedText by cameraViewModel.translatedText.collectAsState()
    val isDownloadingModel by cameraViewModel.isDownloadingModel.collectAsState()

    val vocabFolders by homeViewModel.vocabFolders.collectAsState(initial = emptyList())

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTextToTranslate by remember { mutableStateOf("") }

    var prefilledWord by remember { mutableStateOf<String?>(null) }
    var prefilledMeaning by remember { mutableStateOf<String?>(null) }
    var showAddWordDialog by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            cameraViewModel.processImageUri(it) { err -> errorMessage = err }
        }
    }

    val cameraPhotoUri = remember {
        val file = File(context.cacheDir, "ocr_temp_${System.currentTimeMillis()}.jpg")
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            cameraViewModel.processImageUri(cameraPhotoUri) { err -> errorMessage = err }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Máy ảnh (OCR & Dịch)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { cameraLauncher.launch(cameraPhotoUri) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Chụp ảnh")
            }

            OutlinedButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Chọn ảnh")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isProcessing || isDownloadingModel) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PrimaryAccent)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(if (isDownloadingModel) "Đang tải gói dịch Anh – Việt..." else "Đang nhận diện chữ...", color = Color.Gray)
                }
            }
        } else if (recognizedText != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text("Văn bản nhận diện (Bôi đen để dịch):", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = recognizedText!!,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = selectedTextToTranslate,
                        onValueChange = { selectedTextToTranslate = it },
                        label = { Text("Nhập hoặc chọn đoạn chữ để dịch") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            IconButton(onClick = {
                                if (selectedTextToTranslate.isNotBlank()) {
                                    cameraViewModel.translateSelectedText(
                                        selectedText = selectedTextToTranslate,
                                        onResult = { _ -> },
                                        onError = { err -> errorMessage = err }
                                    )
                                }
                            }) {
                                Icon(Icons.Default.Translate, contentDescription = "Translate", tint = PrimaryAccent)
                            }
                        }
                    )
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Chụp hoặc chọn ảnh để nhận diện văn bản tiếng Anh", color = Color.Gray, fontSize = 14.sp)
            }
        }

        // Translation result popup
        if (translatedText != null && selectedTextToTranslate.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${selectedTextToTranslate.trim()} → ${translatedText!!.trim()}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PrimaryAccent)
                        Text("Nguồn dịch: Google ML Kit Translation", fontSize = 11.sp, color = Color.Gray)
                    }

                    IconButton(onClick = {
                        prefilledWord = selectedTextToTranslate.trim()
                        prefilledMeaning = translatedText!!.trim()
                        showAddWordDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Word", tint = PrimaryAccent)
                    }
                }
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }

    if (showAddWordDialog && prefilledWord != null) {
        val dummyWord = remember { Word(folder_id = -1L, word = prefilledWord!!, meaning = prefilledMeaning!!, created_date = "") }
        AddEditWordDialog(
            word = dummyWord,
            folders = vocabFolders,
            onDismiss = { showAddWordDialog = false },
            onSave = { folderId, w, m, time ->
                homeViewModel.addWord(folderId, w, m, time) { _, _ -> }
                showAddWordDialog = false
            }
        )
    }
}