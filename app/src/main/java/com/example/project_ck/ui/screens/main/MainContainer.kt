package com.example.project_ck.ui.screens.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.project_ck.R
import com.example.project_ck.data.entity.User
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.ui.theme.PrimaryBackground
import com.example.project_ck.viewmodel.AuthViewModel
import com.example.project_ck.viewmodel.NotificationViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer(
    authViewModel: AuthViewModel,
    notifViewModel: NotificationViewModel,
    onNavigateToUserInfo: () -> Unit,
    onNavigateToUserGuide: () -> Unit,
    onLogout: () -> Unit,
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    onFabClick: () -> Unit,
    content: @Composable (Int) -> Unit
) {
    val userState by authViewModel.userState.collectAsState()
    val unreadCount by notifViewModel.unreadCount.collectAsState(initial = 0)
    val user = userState

    var showMenu by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNavigateToUserInfo() }
                    ) {
                        val avatarPath = user?.avatar_path
                        if (avatarPath != null && File(avatarPath).exists()) {
                            AsyncImage(
                                model = File(avatarPath),
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.avatar_default),
                                contentDescription = "Default Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = user?.username ?: "ReNo",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Thông tin người dùng") },
                            onClick = {
                                showMenu = false
                                onNavigateToUserInfo()
                            },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Hướng dẫn sử dụng") },
                            onClick = {
                                showMenu = false
                                onNavigateToUserGuide()
                            },
                            leadingIcon = { Icon(Icons.Default.HelpOutline, contentDescription = null) }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Đăng xuất", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showLogoutDialog = true
                            },
                            leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBackground)
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = PrimaryAccent
            ) {
                // 0: Nhà
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { onTabSelected(0) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Nhà") },
                    label = { Text("Nhà") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryAccent, indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                )
                // 1: Thống kê
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { onTabSelected(1) },
                    icon = { Icon(Icons.Default.BarChart, contentDescription = "Thống kê") },
                    label = { Text("Thống kê") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryAccent, indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                )
                // 2: Máy ảnh
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { onTabSelected(2) },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Máy ảnh") },
                    label = { Text("Máy ảnh") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryAccent, indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                )
                // 3: Lịch
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { onTabSelected(3) },
                    icon = { Icon(Icons.Default.CalendarToday, contentDescription = "Lịch") },
                    label = { Text("Lịch") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryAccent, indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                )
                // 4: Thông báo
                NavigationBarItem(
                    selected = currentTab == 4,
                    onClick = { onTabSelected(4) },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text(unreadCount.toString(), color = Color.White)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Thông báo")
                        }
                    },
                    label = { Text("Thông báo") },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryAccent, indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                )
            }
        },
        floatingActionButton = {
            if (currentTab == 0) {
                FloatingActionButton(
                    onClick = onFabClick,
                    containerColor = PrimaryAccent,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        containerColor = PrimaryBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            content(currentTab)
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Đăng xuất khỏi ReNo?") },
            text = { Text("Bạn có chắc muốn đăng xuất khỏi ReNo?") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        authViewModel.logout()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Có (Đăng xuất)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Không")
                }
            }
        )
    }
}