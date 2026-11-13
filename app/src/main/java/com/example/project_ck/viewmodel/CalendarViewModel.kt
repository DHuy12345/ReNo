package com.example.project_ck.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.project_ck.ReNoApplication
import com.example.project_ck.data.SessionManager
import com.example.project_ck.data.entity.Note
import com.example.project_ck.data.dao.WordReviewWithWord
import com.example.project_ck.data.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository((application as ReNoApplication).database)
    private val sessionManager = SessionManager(application)
    val userId: Long get() = sessionManager.currentUserId

    val notes: Flow<List<Note>> = repo.getNotesByUser(userId)

    fun getReviewsForDate(dateStr: String): Flow<List<WordReviewWithWord>> =
        repo.getReviewsForUserByDate(userId, dateStr)
}