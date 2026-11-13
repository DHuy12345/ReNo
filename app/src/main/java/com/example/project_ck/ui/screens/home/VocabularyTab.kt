package com.example.project_ck.ui.screens.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_ck.data.dao.WordReviewWithWord
import com.example.project_ck.data.entity.Folder
import com.example.project_ck.data.entity.Word
import com.example.project_ck.data.entity.WordReview
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.util.SpacedRepetitionUtils
import com.example.project_ck.viewmodel.HomeViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun VocabularyTab(
    viewModel: HomeViewModel,
    showAddWordDialogState: MutableState<Boolean>,
    showAddFolderDialogState: MutableState<Boolean>
) {
    val context = LocalContext.current
    val folders by viewModel.vocabFolders.collectAsState(initial = emptyList())
    val expandedFolderIds = remember { mutableStateMapOf<Long, Boolean>() }

    val todayStr = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
    val todayReviews by viewModel.getReviewsForDate(todayStr).collectAsState(initial = emptyList())
    val pendingTodayReviews = remember(todayReviews) { todayReviews.filter { it.review.status == "PENDING" } }

    var selectedFlashcardWords by remember { mutableStateOf<List<Word>?>(null) }
    var flashcardStartIndex by remember { mutableStateOf(0) }
    var activeReviewSession by remember { mutableStateOf<List<WordReviewWithWord>?>(null) }

    var editingWord by remember { mutableStateOf<Word?>(null) }
    var deletingWord by remember { mutableStateOf<Word?>(null) }
    var deletingFolder by remember { mutableStateOf<Folder?>(null) }

    if (activeReviewSession != null) {
        ReviewSessionScreen(
            reviews = activeReviewSession!!,
            viewModel = viewModel,
            onClose = { activeReviewSession = null }
        )
        return
    }

    if (selectedFlashcardWords != null) {
        FlashcardScreen(
            words = selectedFlashcardWords!!,
            initialIndex = flashcardStartIndex,
            onClose = { selectedFlashcardWords = null }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner: Today's Vocab Reviews
        if (pendingTodayReviews.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeReviewSession = pendingTodayReviews },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryAccent),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Hôm nay cần ôn: ${pendingTodayReviews.size} từ",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Bấm để bắt đầu ôn tập ngay",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }

        // Header: Thư mục từ vựng
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Danh sách thư mục",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(onClick = { showAddFolderDialogState.value = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Folder", tint = PrimaryAccent)
                }
            }
        }

        if (folders.isEmpty()) {
            item {
                Text(
                    text = "Chưa có thư mục từ vựng nào. Bấm + để tạo.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }

        items(folders, key = { it.id }) { folder ->
            val isExpanded = expandedFolderIds[folder.id] ?: false
            val wordsFlow = remember(folder.id) { viewModel.getWordsForFolder(folder.id) }
            val words by wordsFlow.collectAsState(initial = emptyList())

            VocabFolderRow(
                folder = folder,
                wordCount = words.size,
                isExpanded = isExpanded,
                onToggleExpand = { expandedFolderIds[folder.id] = !isExpanded },
                onAddWord = { showAddWordDialogState.value = true },
                onDeleteFolder = { deletingFolder = folder }
            )

            if (isExpanded) {
                Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp)) {
                    if (words.isEmpty()) {
                        Text(
                            text = "Thư mục trống. Nhấn + để thêm từ vựng.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        words.forEachIndexed { index, word ->
                            WordRow(
                                word = word,
                                onClick = {
                                    selectedFlashcardWords = words
                                    flashcardStartIndex = index
                                },
                                onEdit = { editingWord = word },
                                onDelete = { deletingWord = word }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add Folder Dialog
    if (showAddFolderDialogState.value) {
        var folderName by remember { mutableStateOf("") }
        var errStr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { showAddFolderDialogState.value = false },
            title = { Text("Tạo thư mục Từ vựng") },
            text = {
                Column {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it; errStr = null },
                        label = { Text("Tên thư mục") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    errStr?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addVocabFolder(folderName) { success, msg ->
                            if (success) {
                                folderName = ""
                                showAddFolderDialogState.value = false
                            } else {
                                errStr = msg
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Tạo")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialogState.value = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Add Word Dialog
    if (showAddWordDialogState.value || editingWord != null) {
        AddEditWordDialog(
            word = editingWord,
            folders = folders,
            onDismiss = {
                showAddWordDialogState.value = false
                editingWord = null
            },
            onSave = { folderId, wordText, meaningText, remindTime ->
                if (editingWord != null) {
                    viewModel.updateWord(editingWord!!.copy(folder_id = folderId, word = wordText, meaning = meaningText, remind_time = remindTime))
                } else {
                    viewModel.addWord(folderId, wordText, meaningText, remindTime) { _, _ -> }
                }
                showAddWordDialogState.value = false
                editingWord = null
            }
        )
    }

    // Delete Folder Confirm Dialog
    if (deletingFolder != null) {
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Xóa thư mục từ vựng?") },
            text = { Text("Sẽ xóa mọi từ vựng và lịch ôn tập của thư mục '${deletingFolder!!.name}'.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteFolder(deletingFolder!!)
                        deletingFolder = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingFolder = null }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Delete Word Confirm Dialog
    if (deletingWord != null) {
        AlertDialog(
            onDismissRequest = { deletingWord = null },
            title = { Text("Xóa từ vựng?") },
            text = { Text("Bạn có chắc muốn xóa từ '${deletingWord!!.word}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteWord(deletingWord!!)
                        deletingWord = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingWord = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun VocabFolderRow(
    folder: Folder,
    wordCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddWord: () -> Unit,
    onDeleteFolder: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = PrimaryAccent)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("$wordCount từ vựng", fontSize = 12.sp, color = Color.Gray)
            }

            IconButton(onClick = onDeleteFolder) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand/Collapse"
            )
        }
    }
}

@Composable
fun WordRow(
    word: Word,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(word.word, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PrimaryAccent)
            Text(word.meaning, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray, modifier = Modifier.size(20.dp))
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray, modifier = Modifier.size(20.dp))
        }
    }
}

// FLASHCARD SCREEN (3D Flip Animation SRS Section 6.3)
@Composable
fun FlashcardScreen(
    words: List<Word>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(initialIndex.coerceIn(0, words.size - 1)) }
    var isFlipped by remember { mutableStateOf(false) }

    val currentWord = words[currentIndex]
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "FlipAnimation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${currentIndex + 1} / ${words.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Flashcard
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .clickable { isFlipped = !isFlipped },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (rotation <= 90f) {
                        // Front: Vietnamese Meaning
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Mặt trước (Nghĩa tiếng Việt)", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = currentWord.meaning,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        // Back: English Word (graphicsLayer mirror fix)
                        Column(
                            modifier = Modifier.graphicsLayer { rotationY = 180f },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Mặt sau (Từ tiếng Anh)", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = currentWord.word,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryAccent,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Navigation Arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentIndex > 0) {
                            currentIndex--
                            isFlipped = false
                        }
                    },
                    enabled = currentIndex > 0
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Prev",
                        tint = if (currentIndex > 0) PrimaryAccent else Color.LightGray,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text("Chạm vào thẻ để lật", fontSize = 13.sp, color = Color.Gray)

                IconButton(
                    onClick = {
                        if (currentIndex < words.size - 1) {
                            currentIndex++
                            isFlipped = false
                        }
                    },
                    enabled = currentIndex < words.size - 1
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Next",
                        tint = if (currentIndex < words.size - 1) PrimaryAccent else Color.LightGray,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}

// REVIEW SESSION SCREEN (SRS Section 6.4)
@Composable
fun ReviewSessionScreen(
    reviews: List<WordReviewWithWord>,
    viewModel: HomeViewModel,
    onClose: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }
    var processedCount by remember { mutableStateOf(0) }

    var showingSkipDialogForReview by remember { mutableStateOf<WordReviewWithWord?>(null) }
    val total = reviews.size

    if (currentIndex >= total) {
        // Summary screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryAccent, modifier = Modifier.size(72.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Hoàn thành phiên ôn tập!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Đã xử lý $processedCount / $total từ", fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Xong")
                }
            }
        }
        return
    }

    val currentReviewItem = reviews[currentIndex]
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "FlipAnimation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đã xử lý $processedCount / $total", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LinearProgressIndicator(
                progress = processedCount.toFloat() / total.toFloat(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = PrimaryAccent
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .graphicsLayer {
                        rotationY = rotation
                        cameraDistance = 12f * density
                    }
                    .clickable { isFlipped = !isFlipped },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (rotation <= 90f) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Mặt trước (Nghĩa tiếng Việt)", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = currentReviewItem.word.meaning,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.graphicsLayer { rotationY = 180f },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Mặt sau (Từ tiếng Anh)", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = currentReviewItem.word.word,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryAccent,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action buttons: Bỏ qua & Xong
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = { showingSkipDialogForReview = currentReviewItem },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Bỏ qua")
                }

                Button(
                    onClick = {
                        viewModel.updateReviewStatus(currentReviewItem.review, "DONE")
                        processedCount++
                        currentIndex++
                        isFlipped = false
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Xong", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showingSkipDialogForReview != null) {
        SkipOptionsDialog(
            reviewWithWord = showingSkipDialogForReview!!,
            viewModel = viewModel,
            onDismiss = { showingSkipDialogForReview = null },
            onCompleted = {
                showingSkipDialogForReview = null
                processedCount++
                currentIndex++
                isFlipped = false
            }
        )
    }
}

// 3-OPTION SKIP DIALOG (SRS Section 6.4)
@Composable
fun SkipOptionsDialog(
    reviewWithWord: WordReviewWithWord,
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    val review = reviewWithWord.review
    val maxDate = remember { SpacedRepetitionUtils.getMaxPostponeDate(review.stage, review.original_due_date) }

    var selectedOption by remember { mutableStateOf(1) } // 1: Next stage, 2: Specific date/time, 3: Postpone n days
    var postponeDays by remember { mutableStateOf(1) }
    var schedDate by remember { mutableStateOf(LocalDate.now()) }
    var schedTime by remember { mutableStateOf(LocalTime.now().plusMinutes(30)) }

    var errStr by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tùy chọn Bỏ qua", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Từ: ${reviewWithWord.word.word}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Hạn chót phải trước: ${maxDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}", fontSize = 12.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(4.dp))

                // Option 1
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOption = 1 }
                ) {
                    RadioButton(selected = selectedOption == 1, onClick = { selectedOption = 1 })
                    Text("Dời sang lần ôn tiếp theo", fontSize = 14.sp)
                }

                // Option 2
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOption = 2 }
                ) {
                    RadioButton(selected = selectedOption == 2, onClick = { selectedOption = 2 })
                    Text("Nhắc vào thời điểm cụ thể", fontSize = 14.sp)
                }

                if (selectedOption == 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.weight(1f)) {
                            Text(schedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                        }
                    }
                }

                // Option 3
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedOption = 3 }
                ) {
                    RadioButton(selected = selectedOption == 3, onClick = { selectedOption = 3 })
                    Text("Lùi n ngày", fontSize = 14.sp)
                }

                if (selectedOption == 3) {
                    OutlinedTextField(
                        value = postponeDays.toString(),
                        onValueChange = { postponeDays = (it.toIntOrNull() ?: 1).coerceAtLeast(1) },
                        label = { Text("Số ngày lùi (n ≥ 1)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                errStr?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedOption) {
                        1 -> {
                            viewModel.snoozeReviewToNextStage(review)
                            onCompleted()
                        }
                        2 -> {
                            if (schedDate.isAfter(maxDate)) {
                                errStr = "Phải chọn trước ngày ${maxDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
                                return@Button
                            }
                            val dateStr = schedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                            val epochMs = schedDate.atTime(schedTime).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            viewModel.scheduleReviewToDate(review, dateStr, epochMs)
                            onCompleted()
                        }
                        3 -> {
                            val targetDate = LocalDate.now().plusDays(postponeDays.toLong())
                            if (targetDate.isAfter(maxDate)) {
                                errStr = "Phải chọn trước ngày ${maxDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
                                return@Button
                            }
                            viewModel.postponeReviewNDays(review, postponeDays)
                            onCompleted()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
            ) {
                Text("Xác nhận")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )

    if (showDatePicker) {
        DisposableEffect(Unit) {
            val dialog = DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    schedDate = LocalDate.of(year, month + 1, dayOfMonth)
                    showDatePicker = false
                },
                schedDate.year,
                schedDate.monthValue - 1,
                schedDate.dayOfMonth
            )
            dialog.setOnCancelListener { showDatePicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
}

@Composable
fun AddEditWordDialog(
    word: Word?,
    folders: List<Folder>,
    onDismiss: () -> Unit,
    onSave: (folderId: Long, wordText: String, meaningText: String, remindTime: String?) -> Unit
) {
    var wordText by remember { mutableStateOf(word?.word ?: "") }
    var meaningText by remember { mutableStateOf(word?.meaning ?: "") }
    var selectedFolderId by remember { mutableStateOf(word?.folder_id ?: folders.firstOrNull()?.id ?: -1L) }
    var isCustomTime by remember { mutableStateOf(word?.remind_time != null) }
    var customTime by remember { mutableStateOf(word?.remind_time ?: "08:00") }

    var errStr by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (word != null) "Sửa từ vựng" else "Thêm từ vựng mới") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = wordText,
                    onValueChange = { if (it.length <= 100) wordText = it; errStr = null },
                    label = { Text("Từ vựng tiếng Anh (Tối đa 100 ký tự)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = meaningText,
                    onValueChange = { if (it.length <= 300) meaningText = it; errStr = null },
                    label = { Text("Nghĩa tiếng Việt (Tối đa 300 ký tự)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Thư mục:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                folders.forEach { f ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedFolderId = f.id }
                    ) {
                        RadioButton(selected = selectedFolderId == f.id, onClick = { selectedFolderId = f.id })
                        Text(f.name, fontSize = 14.sp)
                    }
                }

                Text("Giờ nhắc ôn:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCustomTime = false }
                ) {
                    RadioButton(selected = !isCustomTime, onClick = { isCustomTime = false })
                    Text("Giờ mặc định (Theo tài khoản)", fontSize = 14.sp)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCustomTime = true }
                ) {
                    RadioButton(selected = isCustomTime, onClick = { isCustomTime = true })
                    Text("Tự chọn", fontSize = 14.sp)
                }

                if (isCustomTime) {
                    OutlinedTextField(
                        value = customTime,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Giờ phút nhắc ôn") },
                        trailingIcon = {
                            IconButton(onClick = { showTimePicker = true }) {
                                Icon(Icons.Default.Schedule, contentDescription = "Time")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePicker = true }
                    )
                }

                errStr?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (wordText.isBlank() || meaningText.isBlank()) {
                        errStr = "Vui lòng nhập từ và nghĩa"
                        return@Button
                    }
                    if (selectedFolderId == -1L) {
                        errStr = "Vui lòng chọn hoặc tạo thư mục"
                        return@Button
                    }
                    val remindTime = if (isCustomTime) customTime else null
                    onSave(selectedFolderId, wordText.trim(), meaningText.trim(), remindTime)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )

    if (showTimePicker) {
        val parts = customTime.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0

        DisposableEffect(Unit) {
            val dialog = TimePickerDialog(
                context,
                { _, hour, minute ->
                    customTime = String.format("%02d:%02d", hour, minute)
                    showTimePicker = false
                },
                h,
                m,
                true
            )
            dialog.setOnCancelListener { showTimePicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
}