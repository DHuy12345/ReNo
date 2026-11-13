package com.example.project_ck.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.project_ck.data.db.AppDatabase
import com.example.project_ck.receiver.ReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ReminderScheduler {
    const val EXTRA_DEDUPE_KEY = "dedupe_key"
    const val EXTRA_KIND = "kind"
    const val EXTRA_NOTE_ID = "note_id"
    const val EXTRA_REF_DATE = "ref_date"
    const val EXTRA_TITLE = "title"
    const val EXTRA_BODY = "body"
    const val EXTRA_USER_ID = "user_id"

    fun rescheduleAll(context: Context, userId: Long) {
        if (userId == -1L) return

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(context)
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val now = LocalDateTime.now()
            val windowEnd = now.plusHours(48)
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val maxDueDateStr = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

            // 1. NOTES
            val notes = db.noteDao().getNotesByUserSync(userId)
            val noteOccurrences = db.noteOccurrenceDao().getOccurrencesByUserSync(userId)
                .associateBy { "${it.note_id}:${it.occ_date}" }

            for (note in notes) {
                val dates = RecurrenceUtils.occurrencesBetween(note, today, windowEnd.toLocalDate())
                val startTime = try {
                    LocalTime.ofNanoOfDay(note.dtstart % (24 * 60 * 60 * 1000) * 1_000_000)
                } catch (e: Exception) {
                    LocalTime.of(9, 0)
                }

                for (date in dates) {
                    val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    val key = "NOTE:${note.id}:$dateStr"
                    val occKey = "${note.id}:$dateStr"

                    // If already DONE or SKIPPED, skip
                    if (noteOccurrences[occKey]?.status == "DONE" || noteOccurrences[occKey]?.status == "SKIPPED") {
                        continue
                    }

                    val noteTime = LocalDateTime.of(date, startTime)
                    if (noteTime.isAfter(now) && noteTime.isBefore(windowEnd)) {
                        scheduleAlarm(
                            context = context,
                            alarmManager = alarmManager,
                            triggerTimeMs = noteTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            dedupeKey = key,
                            kind = "NOTE",
                            noteId = note.id,
                            refDate = dateStr,
                            title = "Ghi chú: ${note.title}",
                            body = "Đến giờ ghi chú (${startTime.format(DateTimeFormatter.ofPattern("HH:mm"))})",
                            userId = userId
                        )
                    }
                }
            }

            // 2. VOCABULARY REVIEWS
            val pendingReviews = db.wordReviewDao().getPendingReviewsForScheduler(userId, maxDueDateStr)
            val groupedByTime = pendingReviews.groupBy { reviewWithWord ->
                val rTime = reviewWithWord.word.remind_time
                val dueStr = reviewWithWord.review.due_date
                val remindAt = reviewWithWord.review.remind_at

                if (remindAt != null) {
                    "REMIND_AT:$remindAt"
                } else if (!rTime.isNullOrEmpty()) {
                    "VOCAB:$dueStr:$rTime"
                } else {
                    "VOCAB:$dueStr:DEFAULT"
                }
            }

            val user = db.userDao().getUserById(userId)
            val defaultReviewTimeStr = user?.review_time ?: "20:00"

            for ((groupKey, reviews) in groupedByTime) {
                if (reviews.isEmpty()) continue

                val count = reviews.size
                val firstReview = reviews.first()
                val dateStr = firstReview.review.due_date

                var triggerTime: LocalDateTime? = null
                var dedupeKey = groupKey

                if (groupKey.startsWith("REMIND_AT:")) {
                    val epochMs = firstReview.review.remind_at!!
                    triggerTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(epochMs),
                        ZoneId.systemDefault()
                    )
                    dedupeKey = "VOCAB:$dateStr:TIME:$epochMs"
                } else {
                    val timeStr = if (groupKey.endsWith(":DEFAULT")) defaultReviewTimeStr else {
                        groupKey.substringAfterLast(":")
                    }
                    val localTime = try {
                        LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
                    } catch (e: Exception) {
                        LocalTime.of(20, 0)
                    }
                    val dueLocalDate = try {
                        LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    } catch (e: Exception) {
                        today
                    }
                    triggerTime = LocalDateTime.of(dueLocalDate, localTime)
                    dedupeKey = "VOCAB:$dateStr:$timeStr"
                }

                if (triggerTime != null && triggerTime.isAfter(now) && triggerTime.isBefore(windowEnd)) {
                    scheduleAlarm(
                        context = context,
                        alarmManager = alarmManager,
                        triggerTimeMs = triggerTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        dedupeKey = dedupeKey,
                        kind = "VOCAB",
                        noteId = null,
                        refDate = dateStr,
                        title = "Ôn tập từ vựng",
                        body = "Có $count từ cần ôn tập",
                        userId = userId
                    )
                }
            }

            // 3. MIDNIGHT RESCHEDULE ALARM (00:05 next day)
            val tomorrow0005 = LocalDateTime.of(tomorrow, LocalTime.of(0, 5))
            if (tomorrow0005.isAfter(now)) {
                scheduleAlarm(
                    context = context,
                    alarmManager = alarmManager,
                    triggerTimeMs = tomorrow0005.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    dedupeKey = "RESCHEDULE_MIDNIGHT",
                    kind = "RESCHEDULE",
                    noteId = null,
                    refDate = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    title = "",
                    body = "",
                    userId = userId
                )
            }
        }
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        triggerTimeMs: Long,
        dedupeKey: String,
        kind: String,
        noteId: Long?,
        refDate: String,
        title: String,
        body: String,
        userId: Long
    ) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(EXTRA_DEDUPE_KEY, dedupeKey)
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_NOTE_ID, noteId ?: -1L)
            putExtra(EXTRA_REF_DATE, refDate)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_BODY, body)
            putExtra(EXTRA_USER_ID, userId)
        }

        val requestCode = dedupeKey.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
            }
        } catch (e: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTimeMs, pendingIntent)
        }
    }
}