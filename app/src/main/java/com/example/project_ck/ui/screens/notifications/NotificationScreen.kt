package com.example.project_ck.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_ck.data.entity.AppNotification
import com.example.project_ck.data.entity.Note
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.viewmodel.HomeViewModel
import com.example.project_ck.viewmodel.NotificationViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotificationScreen(
    viewModel: NotificationViewModel,
    onOpenNoteDetail: (Long) -> Unit,
    onOpenVocabReview: (String) -> Unit
) {
    val notifications by viewModel.notifications.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Thông báo",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (notifications.isEmpty()) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text("Chưa có thông báo nào", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    NotificationRow(
                        item = item,
                        onClick = {
                            viewModel.markAsRead(item.id)
                            if (item.kind == "NOTE" && item.note_id != null) {
                                onOpenNoteDetail(item.note_id)
                            } else if (item.kind == "VOCAB") {
                                onOpenVocabReview(item.ref_date)
                            }
                        },
                        onDelete = { viewModel.deleteNotification(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationRow(
    item: AppNotification,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isUnread = item.is_read == 0
    val timeAgoStr = remember(item.created_at) {
        val diffMin = (System.currentTimeMillis() - item.created_at) / (1000 * 60)
        when {
            diffMin < 60 -> "$diffMin phút trước"
            diffMin < 24 * 60 -> "${diffMin / 60} giờ trước"
            else -> Instant.ofEpochMilli(item.created_at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
        ),
        elevation = CardDefaults.cardElevation(if (isUnread) 3.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (item.kind == "VOCAB") PrimaryAccent.copy(alpha = 0.2f) else Color(0xFF5B8DEF).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.kind == "VOCAB") Icons.Default.Book else Icons.Default.EventNote,
                    contentDescription = null,
                    tint = if (item.kind == "VOCAB") PrimaryAccent else Color(0xFF5B8DEF)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isUnread) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(PrimaryAccent)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = item.title,
                        fontSize = 15.sp,
                        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(item.body, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(timeAgoStr, fontSize = 11.sp, color = Color.Gray)
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
            }
        }
    }
}

// NOTE DETAIL SCREEN (Dùng chung cho Notification, Stats, Calendar SRS Section 7.4)
@Composable
fun NoteDetailScreen(
    noteId: Long,
    refDateStr: String,
    homeViewModel: HomeViewModel,
    onBack: () -> Unit
) {
    var note by remember { mutableStateOf<Note?>(null) }
    var occurrenceStatus by remember { mutableStateOf("PENDING") }

    LaunchedEffect(noteId) {
        note = homeViewModel.getNoteById(noteId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết ghi chú", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            if (note == null) {
                CircularProgressIndicator()
            } else {
                val n = note!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(n.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryAccent)
                        Text("Ngày: $refDateStr", fontSize = 14.sp)
                        Text("Thời lượng: ${n.duration_min} phút", fontSize = 14.sp)
                        Text("Lặp lại: ${n.repeat_type}", fontSize = 14.sp)
                        Text("Trạng thái: $occurrenceStatus", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    homeViewModel.setNoteOccurrenceStatus(n.id, refDateStr, "SKIPPED")
                                    occurrenceStatus = "SKIPPED"
                                    onBack()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Bỏ qua")
                            }

                            Button(
                                onClick = {
                                    homeViewModel.setNoteOccurrenceStatus(n.id, refDateStr, "DONE")
                                    occurrenceStatus = "DONE"
                                    onBack()
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
            }
        }
    }
}