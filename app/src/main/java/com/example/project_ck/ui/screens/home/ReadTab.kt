package com.example.project_ck.ui.screens.home

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PictureAsPdf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_ck.R
import com.example.project_ck.data.entity.Folder
import com.example.project_ck.data.entity.Topic
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.viewmodel.HomeViewModel

@Composable
fun ReadTab(
    viewModel: HomeViewModel,
    onOpenTopic: (Topic) -> Unit,
    showAddFolderDialogState: MutableState<Boolean>,
    showAddTopicDialogState: MutableState<Boolean>
) {
    val context = LocalContext.current
    val recentTopics by viewModel.recentTopics.collectAsState(initial = emptyList())
    val folders by viewModel.readFolders.collectAsState(initial = emptyList())

    val expandedFolderIds = remember { mutableStateMapOf<Long, Boolean>() }

    var folderForNewTopic by remember { mutableStateOf<Folder?>(null) }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    var newTopicName by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }

    var editingFolder by remember { mutableStateOf<Folder?>(null) }
    var deletingFolder by remember { mutableStateOf<Folder?>(null) }
    var deletingTopic by remember { mutableStateOf<Topic?>(null) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            selectedPdfUri = it
            val fileName = try {
                var name = ""
                context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) {
                        name = cursor.getString(nameIndex)
                    }
                }
                name.removeSuffix(".pdf").removeSuffix(".PDF")
            } catch (e: Exception) {
                "PDF Topic"
            }
            newTopicName = fileName
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- GẦN ĐÂY ---
        item {
            Text(
                text = "Gần đây",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (recentTopics.isEmpty()) {
                Text(
                    text = "Chưa có topic nào",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    for (i in 0 until 3) {
                        val topic = recentTopics.getOrNull(i)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        ) {
                            if (topic != null) {
                                RecentTopicCard(topic = topic, onClick = { onOpenTopic(topic) })
                            } else {
                                Spacer(modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }

        // --- DANH SÁCH ĐÃ TẠO ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Danh sách đã tạo",
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
                    text = "Chưa có thư mục nào. Bấm + để tạo thư mục.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        items(folders, key = { it.id }) { folder ->
            val isExpanded = expandedFolderIds[folder.id] ?: false
            val topicsFlow = remember(folder.id) { viewModel.getTopicsForFolder(folder.id) }
            val topics by topicsFlow.collectAsState(initial = emptyList())

            FolderItemRow(
                folder = folder,
                topicCount = topics.size,
                isExpanded = isExpanded,
                onToggleExpand = { expandedFolderIds[folder.id] = !isExpanded },
                onAddTopic = {
                    folderForNewTopic = folder
                    showAddTopicDialogState.value = true
                },
                onEditFolder = { editingFolder = folder },
                onDeleteFolder = { deletingFolder = folder }
            )

            if (isExpanded) {
                Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 8.dp)) {
                    if (topics.isEmpty()) {
                        Text(
                            text = "Chưa có file PDF nào. Nhấn + để thêm.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        topics.forEach { topic ->
                            TopicItemRow(
                                topic = topic,
                                onClick = { onOpenTopic(topic) },
                                onDelete = { deletingTopic = topic }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- ADD FOLDER DIALOG ---
    if (showAddFolderDialogState.value) {
        var errStr by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showAddFolderDialogState.value = false },
            title = { Text("Tạo thư mục Read") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it; errStr = null },
                        label = { Text("Tên thư mục") },
                        singleLine = true
                    )
                    errStr?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addReadFolder(newFolderName) { success, msg ->
                            if (success) {
                                newFolderName = ""
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

    // --- ADD TOPIC DIALOG ---
    if (showAddTopicDialogState.value && folderForNewTopic != null) {
        var errStr by remember { mutableStateOf<String?>(null) }
        var isUploading by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddTopicDialogState.value = false },
            title = { Text("Thêm PDF vào ${folderForNewTopic!!.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (selectedPdfUri != null) "Đã chọn file PDF" else "Chọn file PDF")
                    }

                    if (selectedPdfUri != null) {
                        OutlinedTextField(
                            value = newTopicName,
                            onValueChange = { newTopicName = it },
                            label = { Text("Tên topic") },
                            singleLine = true
                        )
                    }

                    errStr?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    if (isUploading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedPdfUri == null) {
                            errStr = "Vui lòng chọn file PDF"
                            return@Button
                        }
                        isUploading = true
                        viewModel.addTopicFromPdf(folderForNewTopic!!.id, selectedPdfUri!!, newTopicName) { success, msg ->
                            isUploading = false
                            if (success) {
                                selectedPdfUri = null
                                newTopicName = ""
                                showAddTopicDialogState.value = false
                            } else {
                                errStr = msg
                            }
                        }
                    },
                    enabled = !isUploading && selectedPdfUri != null,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Thêm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTopicDialogState.value = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // --- DELETE FOLDER CONFIRM DIALOG ---
    if (deletingFolder != null) {
        AlertDialog(
            onDismissRequest = { deletingFolder = null },
            title = { Text("Xóa thư mục?") },
            text = { Text("Sẽ xóa các topic và toàn bộ đánh dấu bên trong thư mục '${deletingFolder!!.name}'.") },
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

    // --- DELETE TOPIC CONFIRM DIALOG ---
    if (deletingTopic != null) {
        AlertDialog(
            onDismissRequest = { deletingTopic = null },
            title = { Text("Xóa topic?") },
            text = { Text("Bạn có chắc muốn xóa topic '${deletingTopic!!.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTopic(deletingTopic!!)
                        deletingTopic = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTopic = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun RecentTopicCard(topic: Topic, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val bgResName = "bg_${topic.bg_index}_tile"
            val context = LocalContext.current
            val bgResId = remember(topic.bg_index) {
                context.resources.getIdentifier(bgResName, "drawable", context.packageName)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(PrimaryAccent.copy(alpha = 0.1f))
            ) {
                if (bgResId != 0) {
                    Image(
                        painter = painterResource(id = bgResId),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = topic.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderItemRow(
    folder: Folder,
    topicCount: Int,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddTopic: () -> Unit,
    onEditFolder: () -> Unit,
    onDeleteFolder: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = onToggleExpand,
                onLongClick = { showMenu = true }
            ),
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
                Text("$topicCount topic", fontSize = 12.sp, color = Color.Gray)
            }

            IconButton(onClick = onAddTopic) {
                Icon(Icons.Default.Add, contentDescription = "Add Topic", tint = PrimaryAccent)
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Expand/Collapse"
            )

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Sửa tên") },
                    onClick = {
                        showMenu = false
                        onEditFolder()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Xóa thư mục", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onDeleteFolder()
                    }
                )
            }
        }
    }
}

@Composable
fun TopicItemRow(
    topic: Topic,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFB3261E))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = topic.name,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${topic.page_count} trang",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}