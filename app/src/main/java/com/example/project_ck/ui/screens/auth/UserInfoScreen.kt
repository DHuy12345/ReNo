package com.example.project_ck.ui.screens.auth

import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.project_ck.R
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.ui.theme.PrimaryBackground
import com.example.project_ck.viewmodel.AuthViewModel
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserInfoScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val userState by viewModel.userState.collectAsState()
    val user = userState ?: return

    var username by remember(user) { mutableStateOf(user.username) }
    var contact by remember(user) { mutableStateOf(user.email ?: user.phone ?: "") }
    var reviewTime by remember(user) { mutableStateOf(user.review_time) }
    var avatarPath by remember(user) { mutableStateOf(user.avatar_path) }

    var showAvatarDialog by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val hasChanges = username != user.username ||
            contact != (user.email ?: user.phone ?: "") ||
            reviewTime != user.review_time ||
            avatarPath != user.avatar_path

    // Image Pickers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val destFile = File(context.filesDir, "avatars/${user.id}_${System.currentTimeMillis()}.jpg")
                destFile.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                avatarPath = destFile.absolutePath
            } catch (e: Exception) {
                errorMessage = "Không lưu được ảnh avatar"
            }
        }
    }

    val cameraPhotoUri = remember {
        val file = File(context.cacheDir, "temp_avatar_${System.currentTimeMillis()}.jpg")
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
            try {
                val destFile = File(context.filesDir, "avatars/${user.id}_${System.currentTimeMillis()}.jpg")
                destFile.parentFile?.mkdirs()
                context.contentResolver.openInputStream(cameraPhotoUri)?.use { input ->
                    FileOutputStream(destFile).use { output -> input.copyTo(output) }
                }
                avatarPath = destFile.absolutePath
            } catch (e: Exception) {
                errorMessage = "Không lưu được ảnh avatar"
            }
        }
    }

    fun handleBack() {
        if (hasChanges) {
            showDiscardConfirm = true
        } else {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thông tin người dùng", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBackground)
            )
        },
        containerColor = PrimaryBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .clickable { showAvatarDialog = true }
            ) {
                if (avatarPath != null && File(avatarPath!!).exists()) {
                    AsyncImage(
                        model = File(avatarPath!!),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.avatar_default),
                        contentDescription = "Default Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(PrimaryAccent, CircleShape)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Change Avatar", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Username
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Email or Phone
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it },
                label = { Text("Email / Số điện thoại") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Created Date (Read-only)
            val createdDateStr = remember(user.created_at) {
                Instant.ofEpochMilli(user.created_at)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
            }
            OutlinedTextField(
                value = createdDateStr,
                onValueChange = {},
                readOnly = true,
                label = { Text("Ngày tạo tài khoản (Chỉ xem)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Review Time Picker
            OutlinedTextField(
                value = reviewTime,
                onValueChange = {},
                readOnly = true,
                label = { Text("Giờ nhắc ôn tập mặc định") },
                trailingIcon = {
                    IconButton(onClick = { showTimePicker = true }) {
                        Icon(Icons.Default.Schedule, contentDescription = "Select Time")
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimePicker = true },
                shape = RoundedCornerShape(12.dp)
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { handleBack() },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Trở lại", fontSize = 16.sp)
                }

                Button(
                    onClick = {
                        isLoading = true
                        viewModel.updateUserProfile(username, contact, reviewTime, avatarPath) { success, msg ->
                            isLoading = false
                            if (success) {
                                onBack()
                            } else {
                                errorMessage = msg
                            }
                        }
                    },
                    enabled = !isLoading && hasChanges,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("Lưu", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Avatar Selection Dialog
    if (showAvatarDialog) {
        AlertDialog(
            onDismissRequest = { showAvatarDialog = false },
            title = { Text("Chọn ảnh đại diện", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showAvatarDialog = false
                            cameraLauncher.launch(cameraPhotoUri)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Chụp ảnh", fontSize = 16.sp, color = PrimaryAccent)
                    }
                    TextButton(
                        onClick = {
                            showAvatarDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Chọn từ thư viện", fontSize = 16.sp, color = PrimaryAccent)
                    }
                    TextButton(
                        onClick = {
                            showAvatarDialog = false
                            avatarPath = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Dùng ảnh mặc định", fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAvatarDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // TimePicker Dialog
    if (showTimePicker) {
        val parts = reviewTime.split(":")
        val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 20
        val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        DisposableEffect(Unit) {
            val dialog = TimePickerDialog(
                context,
                { _, hour, minute ->
                    reviewTime = String.format("%02d:%02d", hour, minute)
                    showTimePicker = false
                },
                initialHour,
                initialMinute,
                true
            )
            dialog.setOnCancelListener { showTimePicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }

    // Discard Changes Dialog
    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Hủy thay đổi?") },
            text = { Text("Bạn có thay đổi chưa lưu. Bỏ các thay đổi?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Bỏ thay đổi")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Tiếp tục sửa")
                }
            }
        )
    }
}