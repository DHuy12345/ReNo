package com.example.project_ck

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.project_ck.data.entity.Topic
import com.example.project_ck.ui.screens.auth.LoginScreen
import com.example.project_ck.ui.screens.auth.RegisterScreen
import com.example.project_ck.ui.screens.auth.UserGuideScreen
import com.example.project_ck.ui.screens.auth.UserInfoScreen
import com.example.project_ck.ui.screens.calendar.CalendarScreen
import com.example.project_ck.ui.screens.camera.CameraScreen
import com.example.project_ck.ui.screens.home.HomeScreen
import com.example.project_ck.ui.screens.home.PdfViewerScreen
import com.example.project_ck.ui.screens.main.MainContainer
import com.example.project_ck.ui.screens.notifications.NoteDetailScreen
import com.example.project_ck.ui.screens.notifications.NotificationScreen
import com.example.project_ck.ui.screens.stats.StatsScreen
import com.example.project_ck.ui.theme.ReNoTheme
import com.example.project_ck.viewmodel.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private val statsViewModel: StatsViewModel by viewModels()
    private val cameraViewModel: CameraViewModel by viewModels()
    private val calendarViewModel: CalendarViewModel by viewModels()
    private val notificationViewModel: NotificationViewModel by viewModels()

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        checkPermissions()

        setContent {
            ReNoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    @Composable
    fun AppNavigation() {
        val currentUserId by authViewModel.currentUserIdState.collectAsState()
        var currentScreen by remember { mutableStateOf("MAIN") } // LOGIN, REGISTER, USER_INFO, USER_GUIDE, PDF_VIEWER, NOTE_DETAIL, MAIN
        var currentTab by remember { mutableStateOf(0) }

        var selectedTopicForPdf by remember { mutableStateOf<Topic?>(null) }
        var selectedNoteIdForDetail by remember { mutableStateOf<Long?>(null) }
        var selectedNoteRefDate by remember { mutableStateOf("2026-11-13") }

        val fabClickState = remember { mutableStateOf(false) }
        val snackbarHostState = remember { SnackbarHostState() }

        // Handle Intent payload from notification
        LaunchedEffect(intent) {
            val navigateKind = intent?.getStringExtra("navigate_kind")
            val noteId = intent?.getLongExtra("note_id", -1L) ?: -1L
            val refDate = intent?.getStringExtra("ref_date")

            if (navigateKind == "NOTE" && noteId != -1L) {
                selectedNoteIdForDetail = noteId
                selectedNoteRefDate = refDate ?: ""
                currentScreen = "NOTE_DETAIL"
            } else if (navigateKind == "VOCAB") {
                currentTab = 4 // Notifications tab
            }
        }

        if (currentUserId == -1L) {
            when (currentScreen) {
                "REGISTER" -> RegisterScreen(
                    viewModel = authViewModel,
                    onRegisterSuccess = { currentScreen = "LOGIN" },
                    onBackToLogin = { currentScreen = "LOGIN" }
                )
                else -> LoginScreen(
                    viewModel = authViewModel,
                    onLoginSuccess = { currentScreen = "MAIN" },
                    onNavigateToRegister = { currentScreen = "REGISTER" }
                )
            }
        } else {
            when (currentScreen) {
                "USER_INFO" -> UserInfoScreen(
                    viewModel = authViewModel,
                    onBack = { currentScreen = "MAIN" }
                )
                "USER_GUIDE" -> UserGuideScreen(
                    onBack = { currentScreen = "MAIN" }
                )
                "PDF_VIEWER" -> {
                    selectedTopicForPdf?.let { topic ->
                        PdfViewerScreen(
                            topic = topic,
                            viewModel = homeViewModel,
                            onBack = { currentScreen = "MAIN" }
                        )
                    }
                }
                "NOTE_DETAIL" -> {
                    selectedNoteIdForDetail?.let { noteId ->
                        NoteDetailScreen(
                            noteId = noteId,
                            refDateStr = selectedNoteRefDate,
                            homeViewModel = homeViewModel,
                            onBack = { currentScreen = "MAIN" }
                        )
                    }
                }
                else -> {
                    MainContainer(
                        authViewModel = authViewModel,
                        notifViewModel = notificationViewModel,
                        onNavigateToUserInfo = { currentScreen = "USER_INFO" },
                        onNavigateToUserGuide = { currentScreen = "USER_GUIDE" },
                        onLogout = { currentScreen = "LOGIN" },
                        currentTab = currentTab,
                        onTabSelected = { currentTab = it },
                        onFabClick = { fabClickState.value = true }
                    ) { tab ->
                        when (tab) {
                            0 -> HomeScreen(
                                viewModel = homeViewModel,
                                onOpenTopic = { topic ->
                                    selectedTopicForPdf = topic
                                    currentScreen = "PDF_VIEWER"
                                },
                                showAddDialogState = fabClickState,
                                snackbarHostState = snackbarHostState
                            )
                            1 -> StatsScreen(viewModel = statsViewModel)
                            2 -> CameraScreen(
                                cameraViewModel = cameraViewModel,
                                homeViewModel = homeViewModel
                            )
                            3 -> CalendarScreen(
                                viewModel = calendarViewModel,
                                onOpenNoteDetail = { noteId ->
                                    selectedNoteIdForDetail = noteId
                                    selectedNoteRefDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                    currentScreen = "NOTE_DETAIL"
                                }
                            )
                            4 -> NotificationScreen(
                                viewModel = notificationViewModel,
                                onOpenNoteDetail = { noteId ->
                                    selectedNoteIdForDetail = noteId
                                    selectedNoteRefDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                                    currentScreen = "NOTE_DETAIL"
                                },
                                onOpenVocabReview = { _ ->
                                    currentTab = 0 // Home -> Vocab tab
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}