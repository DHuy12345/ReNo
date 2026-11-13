package com.example.project_ck.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_ck.data.entity.Idea
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.viewmodel.HomeViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaTab(
    viewModel: HomeViewModel,
    showAddIdeaDialogState: MutableState<Boolean>,
    snackbarHostState: SnackbarHostState
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Chưa thực hiện, 1: Đã thực hiện
    val isAscending by viewModel.ideaSortAscending.collectAsState()

    val pendingIdeas by viewModel.pendingIdeas.collectAsState(initial = emptyList())
    val doneIdeas by viewModel.doneIdeas.collectAsState(initial = emptyList())

    val ideas = if (selectedTab == 0) pendingIdeas else doneIdeas
    var showSortMenu by remember { mutableStateOf(false) }

    var editingIdea by remember { mutableStateOf<Idea?>(null) }
    var deletingIdea by remember { mutableStateOf<Idea?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // Tab row & Sort button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryAccent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Chưa thực hiện (${pendingIdeas.size})", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Đã thực hiện (${doneIdeas.size})", fontWeight = FontWeight.Bold) }
                )
            }

            Box {
                IconButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Default.Sort, contentDescription = "Sort")
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Mới nhất trước") },
                        onClick = {
                            showSortMenu = false
                            viewModel.setIdeaSortAscending(false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Cũ nhất trước") },
                        onClick = {
                            showSortMenu = false
                            viewModel.setIdeaSortAscending(true)
                        }
                    )
                }
            }
        }

        if (ideas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedTab == 0) "Chưa có ý tưởng nào. Bấm + để thêm." else "Chưa có ý tưởng nào đã hoàn thành.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ideas, key = { it.id }) { idea ->
                    IdeaRow(
                        idea = idea,
                        onToggleDone = {
                            viewModel.toggleIdeaStatus(idea)
                            val targetTab = if (idea.is_done == 0) "Đã thực hiện" else "Chưa thực hiện"
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Đã chuyển sang $targetTab",
                                    actionLabel = "Hoàn tác",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.toggleIdeaStatus(idea)
                                }
                            }
                        },
                        onClick = { editingIdea = idea },
                        onDelete = { deletingIdea = idea }
                    )
                }
            }
        }
    }

    // Add / Edit Idea Dialog
    if (showAddIdeaDialogState.value || editingIdea != null) {
        var contentText by remember { mutableStateOf(editingIdea?.content ?: "") }
        var errStr by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showAddIdeaDialogState.value = false
                editingIdea = null
            },
            title = { Text(if (editingIdea != null) "Sửa ý tưởng" else "Thêm ý tưởng mới") },
            text = {
                Column {
                    OutlinedTextField(
                        value = contentText,
                        onValueChange = { if (it.length <= 1000) contentText = it; errStr = null },
                        label = { Text("Nội dung ý tưởng (Tối đa 1000 ký tự)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                    errStr?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contentText.isBlank()) {
                            errStr = "Nội dung không được để trống"
                            return@Button
                        }
                        if (editingIdea != null) {
                            viewModel.updateIdea(editingIdea!!.copy(content = contentText.trim(), updated_at = System.currentTimeMillis()))
                        } else {
                            viewModel.addIdea(contentText.trim()) { _, _ -> }
                        }
                        showAddIdeaDialogState.value = false
                        editingIdea = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                ) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddIdeaDialogState.value = false
                    editingIdea = null
                }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Delete Confirm Dialog
    if (deletingIdea != null) {
        AlertDialog(
            onDismissRequest = { deletingIdea = null },
            title = { Text("Xóa ý tưởng?") },
            text = { Text("Bạn có chắc muốn xóa ý tưởng này?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteIdea(deletingIdea!!)
                        deletingIdea = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingIdea = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
fun IdeaRow(
    idea: Idea,
    onToggleDone: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val createdStr = remember(idea.created_at) {
        Instant.ofEpochMilli(idea.created_at)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = idea.is_done == 1,
                onCheckedChange = { onToggleDone() },
                colors = CheckboxDefaults.colors(checkedColor = PrimaryAccent)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = idea.content,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = createdStr,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
            }
        }
    }
}