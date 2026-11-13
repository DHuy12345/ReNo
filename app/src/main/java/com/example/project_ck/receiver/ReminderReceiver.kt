package com.example.project_ck.receiver

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.example.project_ck.MainActivity
import com.example.project_ck.R
import com.example.project_ck.ReNoApplication
import com.example.project_ck.data.db.AppDatabase
import com.example.project_ck.data.entity.AppNotification
import com.example.project_ck.util.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val dedupeKey = intent.getStringExtra(ReminderScheduler.EXTRA_DEDUPE_KEY) ?: return
        val kind = intent.getStringExtra(ReminderScheduler.EXTRA_KIND) ?: return
        val userId = intent.getLongExtra(ReminderScheduler.EXTRA_USER_ID, -1L)

        if (kind == "RESCHEDULE") {
            ReminderScheduler.rescheduleAll(context, userId)
            return
        }

        val noteId = intent.getLongExtra(ReminderScheduler.EXTRA_NOTE_ID, -1L).takeIf { it != -1L }
        val refDate = intent.getStringExtra(ReminderScheduler.EXTRA_REF_DATE) ?: ""
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "Nhắc nhở ReNo"
        val body = intent.getStringExtra(ReminderScheduler.EXTRA_BODY) ?: ""

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)

                // Insert into app_notifications table
                val appNotif = AppNotification(
                    user_id = userId,
                    kind = kind,
                    note_id = noteId,
                    ref_date = refDate,
                    dedupe_key = dedupeKey,
                    title = title,
                    body = body,
                    is_read = 0,
                    created_at = System.currentTimeMillis()
                )
                db.appNotificationDao().insertNotification(appNotif)

                // Build System Notification
                val contentIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("navigate_kind", kind)
                    putExtra("note_id", noteId ?: -1L)
                    putExtra("ref_date", refDate)
                }
                val contentPendingIntent = PendingIntent.getActivity(
                    context,
                    dedupeKey.hashCode(),
                    contentIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, ReNoApplication.CHANNEL_ID)
                    .setSmallIcon(R.drawable.logo_reno)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setContentIntent(contentPendingIntent)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)

                if (kind == "VOCAB") {
                    builder.setColor(Color.parseColor("#2E6F40"))
                    builder.setColorized(true)
                }

                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(dedupeKey.hashCode(), builder.build())

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}