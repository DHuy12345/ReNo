package com.example.project_ck.ui.screens.home

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_ck.data.entity.PdfAnnotation
import com.example.project_ck.data.entity.Topic
import com.example.project_ck.ui.theme.HighlightColors
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.ui.theme.PrimaryBackground
import com.example.project_ck.viewmodel.HomeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    topic: Topic,
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Ghi chú, 1: Thống kê, 2: Tóm tắt

    val annotations by viewModel.getAnnotationsByTopic(topic.id).collectAsState(initial = emptyList())
    var currentPage by remember { mutableStateOf(topic.last_page) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var totalPages by remember { mutableStateOf(topic.page_count) }

    var selectedTextForAnnotation by remember { mutableStateOf<String?>(null) }
    var showAnnotationTypeDialog by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf("NOTE") }

    // Render PDF Page
    LaunchedEffect(topic.file_path, currentPage) {
        withContext(Dispatchers.IO) {
            try {
                val file = File(topic.file_path)
                if (file.exists()) {
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    totalPages = renderer.pageCount
                    val pIndex = currentPage.coerceIn(0, totalPages - 1)
                    val page = renderer.openPage(pIndex)

                    val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    page.close()
                    renderer.close()
                    pfd.close()

                    withContext(Dispatchers.Main) {
                        pageBitmap = bitmap
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Save Last Opened Page
    DisposableEffect(currentPage) {
        onDispose {
            viewModel.updateTopic(topic.copy(last_page = currentPage, last_opened_at = System.currentTimeMillis()))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topic.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        ) {
            // Background Strip (72dp Tile)
            val bgResName = "bg_${topic.bg_index}_tile"
            val bgResId = remember(topic.bg_index) {
                context.resources.getIdentifier(bgResName, "drawable", context.packageName)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
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

            // 3 Tabs: Ghi chú, Thống kê, Tóm tắt
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryAccent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Ghi chú", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Thống kê", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Tóm tắt", fontWeight = FontWeight.Bold) }
                )
            }

            // Content
            when (selectedTab) {
                0 -> {
                    // PDF Reader view
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    ) {
                        // Annotation toolbar (5 buttons: ?, !, ★, Highlight, Notes)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            AnnotationTypeButton("?", "QUESTION", PrimaryAccent) { selectedType = "QUESTION"; showAnnotationTypeDialog = true }
                            AnnotationTypeButton("!", "IMPORTANT", Color(0xFFE07A5F)) { selectedType = "IMPORTANT"; showAnnotationTypeDialog = true }
                            AnnotationTypeButton("★", "STAR", Color(0xFFE9B949)) { selectedType = "STAR"; showAnnotationTypeDialog = true }
                            AnnotationTypeButton("Highlight", "HIGHLIGHT", Color(0xFFFFE58A)) { selectedType = "HIGHLIGHT"; showAnnotationTypeDialog = true }
                            AnnotationTypeButton("Notes", "NOTE", Color(0xFF5B8DEF)) { selectedType = "NOTE"; showAnnotationTypeDialog = true }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Rendered Page
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (pageBitmap != null) {
                                Image(
                                    bitmap = pageBitmap!!.asImageBitmap(),
                                    contentDescription = "PDF Page ${currentPage + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                CircularProgressIndicator()
                            }
                        }

                        // Page Control
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (currentPage > 0) currentPage-- },
                                enabled = currentPage > 0
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Prev Page")
                            }

                            Text(
                                text = "Trang ${currentPage + 1} / $totalPages",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            IconButton(
                                onClick = { if (currentPage < totalPages - 1) currentPage++ },
                                enabled = currentPage < totalPages - 1
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = "Next Page")
                            }
                        }
                    }
                }
                1 -> {
                    // My Annotations tab
                    MyAnnotationsTab(
                        annotations = annotations,
                        onAnnotationClick = { ann ->
                            currentPage = ann.page_index
                            selectedTab = 0
                        },
                        onDeleteAnnotation = { ann ->
                            viewModel.deleteAnnotation(ann)
                        }
                    )
                }
                2 -> {
                    // Summary tab
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Tính năng đang phát triển",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }

    // Annotation Dialog
    if (showAnnotationTypeDialog) {
        var annTitle by remember { mutableStateOf("") }
        var annDetail by remember { mutableStateOf("") }
        var selectedColorHex by remember { mutableStateOf("#FFE58A") }

        AlertDialog(
            onDismissRequest = { showAnnotationTypeDialog = false },
            title = { Text("Tạo đánh dấu ($selectedType)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = annTitle,
                        onValueChange = { annTitle = it },
                        label = { Text("Tên đánh dấu (Để trống được)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = annDetail,
                        onValueChange = { annDetail = it },
                        label = { Text("Chi tiết (Để trống được)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (selectedType == "HIGHLIGHT") {
                        Text("Màu highlight:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HighlightColors.forEach { color ->
                                val hex = "#" + Integer.toHexString(color.value.toInt()).substring(2).uppercase()
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(if (selectedColorHex == hex) 2.dp else 0.dp, PrimaryAccent, CircleShape)
                                        .clickable { selectedColorHex = hex }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newAnn = PdfAnnotation(
                            topic_id = topic.id,
                            type = selectedType,
                            page_index = currentPage,
                            rects = "[[0.1,0.1,0.8,0.05]]",
                            selected_text = selectedTextForAnnotation ?: "(Text)",
                            title = annTitle.ifBlank { null },
                            detail = annDetail.ifBlank { null },
                            color = if (selectedType == "HIGHLIGHT") selectedColorHex else null
                        )
                        viewModel.addAnnotation(newAnn)
                        showAnnotationTypeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAnnotationTypeDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun AnnotationTypeButton(label: String, type: String, color: Color, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun MyAnnotationsTab(
    annotations: List<PdfAnnotation>,
    onAnnotationClick: (PdfAnnotation) -> Unit,
    onDeleteAnnotation: (PdfAnnotation) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    val filteredList = if (selectedFilter == null) annotations else annotations.filter { it.type == selectedFilter }

    val counts = remember(annotations) {
        annotations.groupingBy { it.type }.eachCount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // 5 count cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("QUESTION" to "?", "IMPORTANT" to "!", "STAR" to "★", "HIGHLIGHT" to "HL", "NOTE" to "Notes").forEach { (type, label) ->
                val count = counts[type] ?: 0
                val isSelected = selectedFilter == type
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedFilter = if (isSelected) null else type
                        },
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) PrimaryAccent else MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else PrimaryAccent)
                        Text(count.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Chưa có đánh dấu nào", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList, key = { it.id }) { ann ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAnnotationClick(ann) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ann.title ?: "(Không có tên)",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!ann.detail.isNullOrBlank()) {
                                    Text(ann.detail!!, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                                Text("Trang ${ann.page_index + 1} - ${ann.selected_text}", fontSize = 12.sp, color = Color.Gray)
                                Text("Nhấn để di chuyển đến nơi được đánh dấu", fontSize = 11.sp, color = PrimaryAccent)
                            }

                            IconButton(onClick = { onDeleteAnnotation(ann) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    }
}