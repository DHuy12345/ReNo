package com.example.project_ck.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.project_ck.data.SessionManager
import com.example.project_ck.util.ReminderScheduler

class RescheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionManager = SessionManager(applicationContext)
        val userId = sessionManager.currentUserId
        if (userId != -1L) {
            ReminderScheduler.rescheduleAll(applicationContext, userId)
        }
        return Result.success()
    }
}