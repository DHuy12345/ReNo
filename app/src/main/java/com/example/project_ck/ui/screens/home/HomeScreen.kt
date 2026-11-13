package com.example.project_ck.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.project_ck.data.entity.Topic
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenTopic: (Topic) -> Unit,
    showAddDialogState: MutableState<Boolean>,
    snackbarHostState: SnackbarHostState
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Read, 1: Notes, 2: Idea, 3: Vocabulary

    val showAddReadFolderDialog = remember { mutableStateOf(false) }
    val showAddTopicDialog = remember { mutableStateOf(false) }
    val showAddNoteDialog = remember { mutableStateOf(false) }
    val showAddIdeaDialog = remember { mutableStateOf(false) }
    val showAddVocabFolderDialog = remember { mutableStateOf(false) }
    val showAddWordDialog = remember { mutableStateOf(false) }

    // When FAB is clicked in MainContainer, route to active tab dialog
    LaunchedEffect(showAddDialogState.value) {
        if (showAddDialogState.value) {
            when (selectedTab) {
                0 -> showAddTopicDialog.value = true
                1 -> showAddNoteDialog.value = true
                2 -> showAddIdeaDialog.value = true
                3 -> showAddWordDialog.value = true
            }
            showAddDialogState.value = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = PrimaryAccent
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Read", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Notes", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Idea", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Vocabulary", fontWeight = FontWeight.Bold) }
            )
        }

        when (selectedTab) {
            0 -> ReadTab(
                viewModel = viewModel,
                onOpenTopic = onOpenTopic,
                showAddFolderDialogState = showAddReadFolderDialog,
                showAddTopicDialogState = showAddTopicDialog
            )
            1 -> NotesTab(
                viewModel = viewModel,
                showAddNoteDialogState = showAddNoteDialog
            )
            2 -> IdeaTab(
                viewModel = viewModel,
                showAddIdeaDialogState = showAddIdeaDialog,
                snackbarHostState = snackbarHostState
            )
            3 -> VocabularyTab(
                viewModel = viewModel,
                showAddWordDialogState = showAddWordDialog,
                showAddFolderDialogState = showAddVocabFolderDialog
            )
        }
    }
}