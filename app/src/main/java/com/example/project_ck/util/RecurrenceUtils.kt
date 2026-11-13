package com.example.project_ck.util

import com.example.project_ck.data.entity.Note
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object RecurrenceUtils {
    val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun epochMsToLocalDate(epochMs: Long): LocalDate {
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    fun occurrencesBetween(note: Note, from: LocalDate, to: LocalDate): List<LocalDate> {
        val startDate = epochMsToLocalDate(note.dtstart)
        val result = mutableListOf<LocalDate>()

        val untilDate = note.repeat_until?.let {
            try { LocalDate.parse(it, DATE_FORMATTER) } catch (e: Exception) { null }
        }

        val effectiveTo = if (untilDate != null && untilDate.isBefore(to)) untilDate else to

        val exdates = note.exdate?.split(",")
            ?.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
            ?.mapNotNull { try { LocalDate.parse(it, DATE_FORMATTER) } catch (e: Exception) { null } }
            ?.toSet() ?: emptySet()

        when (note.repeat_type) {
            "NONE" -> {
                if (!startDate.isBefore(from) && !startDate.isAfter(effectiveTo) && !exdates.contains(startDate)) {
                    result.add(startDate)
                }
            }
            "DAILY" -> {
                var current = if (startDate.isAfter(from)) startDate else from
                while (!current.isAfter(effectiveTo)) {
                    if (!current.isBefore(startDate) && !exdates.contains(current)) {
                        result.add(current)
                    }
                    current = current.plusDays(1)
                }
            }
            "INTERVAL" -> {
                val interval = parseInterval(note.rrule) ?: 1
                var current = startDate
                while (!current.isAfter(effectiveTo)) {
                    if (!current.isBefore(from) && !exdates.contains(current)) {
                        result.add(current)
                    }
                    current = current.plusDays(interval.toLong())
                }
            }
            "CUSTOM" -> {
                val customDates = mutableSetOf(startDate)
                note.rdate?.split(",")
                    ?.mapNotNull { it.trim().takeIf { s -> s.isNotEmpty() } }
                    ?.forEach {
                        try { customDates.add(LocalDate.parse(it, DATE_FORMATTER)) } catch (_: Exception) {}
                    }

                customDates.filter { d -> !d.isBefore(from) && !d.isAfter(effectiveTo) && !exdates.contains(d) }
                    .sorted()
                    .forEach { result.add(it) }
            }
        }

        return result
    }

    private fun parseInterval(rrule: String?): Int? {
        if (rrule.isNullOrEmpty()) return 1
        return try {
            val parts = rrule.split(";")
            for (part in parts) {
                if (part.startsWith("INTERVAL=")) {
                    return part.substring("INTERVAL=".length).toInt()
                }
            }
            1
        } catch (e: Exception) {
            1
        }
    }
}