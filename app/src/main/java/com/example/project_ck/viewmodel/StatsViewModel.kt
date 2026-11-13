package com.example.project_ck.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.project_ck.ReNoApplication
import com.example.project_ck.data.SessionManager
import com.example.project_ck.data.dao.WordReviewWithWord
import com.example.project_ck.data.repository.AppRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository((application as ReNoApplication).database)
    private val sessionManager = SessionManager(application)
    val userId: Long get() = sessionManager.currentUserId

    private val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    private val thirtyDaysAgoStr = LocalDate.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    private val sevenDaysLaterStr = LocalDate.now().plusDays(7).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    val missedReviews: Flow<List<WordReviewWithWord>> = repo.getMissedReviews(userId, todayStr, thirtyDaysAgoStr)
    val upcomingReviews: Flow<List<WordReviewWithWord>> = repo.getUpcomingReviews(userId, todayStr, sevenDaysLaterStr)
    val completedReviews: Flow<List<WordReviewWithWord>> = repo.getCompletedReviews(userId, thirtyDaysAgoStr)
}