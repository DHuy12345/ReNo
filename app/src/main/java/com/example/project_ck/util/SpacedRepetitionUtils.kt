package com.example.project_ck.util

import com.example.project_ck.data.entity.WordReview
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object SpacedRepetitionUtils {
    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun generateInitialReviews(wordId: Long, createdDateStr: String): List<WordReview> {
        val createdDate = try {
            LocalDate.parse(createdDateStr, DATE_FORMATTER)
        } catch (e: Exception) {
            LocalDate.now()
        }

        val dueDates = listOf(
            createdDate.plusDays(1),
            createdDate.plusDays(3),
            createdDate.plusDays(7),
            createdDate.plusMonths(1),
            createdDate.plusMonths(3)
        )

        return dueDates.mapIndexed { index, date ->
            val dateStr = date.format(DATE_FORMATTER)
            WordReview(
                word_id = wordId,
                stage = index + 1,
                original_due_date = dateStr,
                due_date = dateStr,
                remind_at = null,
                status = "PENDING"
            )
        }
    }

    fun getMaxPostponeDate(stage: Int, originalDueDateStr: String): LocalDate {
        val originalDueDate = try {
            LocalDate.parse(originalDueDateStr, DATE_FORMATTER)
        } catch (e: Exception) {
            LocalDate.now()
        }

        return when (stage) {
            1 -> originalDueDate.plusDays(3)
            2 -> originalDueDate.plusDays(7)
            3 -> originalDueDate.plusMonths(1)
            4 -> originalDueDate.plusMonths(3)
            5 -> originalDueDate.plusDays(30)
            else -> originalDueDate.plusDays(30)
        }
    }
}