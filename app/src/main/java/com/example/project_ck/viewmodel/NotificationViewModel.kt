package com.example.project_ck.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.project_ck.ReNoApplication
import com.example.project_ck.data.SessionManager
import com.example.project_ck.data.entity.AppNotification
import com.example.project_ck.data.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository((application as ReNoApplication).database)
    private val sessionManager = SessionManager(application)
    val userId: Long get() = sessionManager.currentUserId

    val notifications: Flow<List<AppNotification>> = repo.getNotificationsByUser(userId)
    val unreadCount: Flow<Int> = repo.getUnreadNotificationCount(userId)

    fun markAsRead(id: Long) {
        viewModelScope.launch { repo.markNotificationAsRead(id) }
    }

    fun deleteNotification(id: Long) {
        viewModelScope.launch { repo.deleteNotification(id) }
    }
}