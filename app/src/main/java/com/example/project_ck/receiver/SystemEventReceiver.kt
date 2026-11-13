package com.example.project_ck.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.project_ck.data.SessionManager
import com.example.project_ck.util.ReminderScheduler

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED
        ) {
            val sessionManager = SessionManager(context)
            val userId = sessionManager.currentUserId
            if (userId != -1L) {
                ReminderScheduler.rescheduleAll(context, userId)
            }
        }
    }
}