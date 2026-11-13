package com.example.project_ck.ui.screens.home

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_ck.data.entity.Note
import com.example.project_ck.data.entity.NoteOccurrence
import com.example.project_ck.ui.theme.NoteColors
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.util.RecurrenceUtils
import com.example.project_ck.viewmodel.HomeViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotesTab(
    viewModel: HomeViewModel,
    showAddNoteDialogState: MutableState<Boolean>
) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedNoteDate.collectAsState()
    val today = remember { LocalDate.now() }
    val isToday = selectedDate == today

    val allNotes by viewModel.notesForUser.collectAsState(initial = emptyList())
    val occurrences by viewModel.occurrencesForUser.collectAsState(initial = emptyList())

    val occMap = remember(occurrences) {
        occurrences.associateBy { "${it.note_id}:${it.occ_date}" }
    }

    val dateStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val occurrencesForDay = remember(allNotes, selectedDate, occMap) {
        allNotes.mapNotNull { note ->
            val dates = RecurrenceUtils.occurrencesBetween(note, selectedDate, selectedDate)
            if (dates.contains(selectedDate)) {
                val occ = occMap["${note.id}:$dateStr"]
                val status = occ?.status ?: "PENDING"
                NoteOccurrenceItem(note = note, status = status)
            } else {
                null
            }
        }.sortedWith(compareBy({ it.status == "DONE" }, { it.note.dtstart }))
    }

    var editingNote by remember { mutableStateOf<Note?>(null) }
    var deletingNote by remember { mutableStateOf<Note?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isToday) "Ghi chú hôm nay" else "Ghi chú ngày ${selectedDate.format(DateTimeFormatter.ofPattern("dd/MM"))}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isToday) {
                    TextButton(onClick = { viewModel.setSelectedNoteDate(today) }) {
                        Text("Hôm nay", color = PrimaryAccent)
                    }
                }

                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Pick Date", tint = PrimaryAccent)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (occurrencesForDay.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Chưa có ghi chú nào cho ngày này. Bấm + để thêm.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(occurrencesForDay, key = { "${it.note.id}:$dateStr" }) { item ->
                    NoteRow(
                        item = item,
                        dateStr = dateStr,
                        isFuture = selectedDate.isAfter(today),
                        onToggleDone = { isDone ->
                            viewModel.toggleNoteOccurrence(item.note.id, dateStr, isDone)
                        },
                        onClick = { editingNote = item.note },
                        onDelete = { deletingNote = item.note }
                    )
                }
            }
        }
    }

    // DatePicker Dialog
    if (showDatePicker) {
        DisposableEffect(Unit) {
            val dialog = DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    viewModel.setSelectedNoteDate(LocalDate.of(year, month + 1, dayOfMonth))
                    showDatePicker = false
                },
                selectedDate.year,
                selectedDate.monthValue - 1,
                selectedDate.dayOfMonth
            )
            dialog.setOnCancelListener { showDatePicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }

    // Add / Edit Note Dialog
    if (showAddNoteDialogState.value || editingNote != null) {
        AddEditNoteDialog(
            note = editingNote,
            selectedDate = selectedDate,
            onDismiss = {
                showAddNoteDialogState.value = false
                editingNote = null
            },
            onSave = { title, colorHex, dtstartMs, durationMin, repeatType, rrule, repeatUntil, exdates ->
                if (editingNote != null) {
                    val updated = editingNote!!.copy(
                        title = title,
                        color = colorHex,
                        dtstart = dtstartMs,
                        duration_min = durationMin,
                        repeat_type = repeatType,
                        rrule = rrule,
                        repeat_until = repeatUntil,
                        exdate = exdates?.joinToString(","),
                        updated_at = System.currentTimeMillis()
                    )
                    viewModel.updateNote(updated)
                } else {
                    viewModel.addNote(
                        title = title,
                        color = colorHex,
                        dtstartMs = dtstartMs,
                        durationMin = durationMin,
                        repeatType = repeatType,
                        rrule = rrule,
                        repeatUntil = repeatUntil,
                        exdates = exdates
                    ) { _, _ -> }
                }
                showAddNoteDialogState.value = false
                editingNote = null
            }
        )
    }

    // Delete Confirm Dialog
    if (deletingNote != null) {
        AlertDialog(
            onDismissRequest = { deletingNote = null },
            title = { Text("Xóa ghi chú?") },
            text = { Text("Mọi lần lặp của ghi chú này sẽ bị xóa và không còn nhắc.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteNote(deletingNote!!)
                        deletingNote = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingNote = null }) {
                    Text("Hủy")
                }
            }
        )
    }
}

data class NoteOccurrenceItem(
    val note: Note,
    val status: String
)

@Composable
fun NoteRow(
    item: NoteOccurrenceItem,
    dateStr: String,
    isFuture: Boolean,
    onToggleDone: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val isDone = item.status == "DONE"
    val noteColor = try {
        Color(android.graphics.Color.parseColor(item.note.color))
    } catch (e: Exception) {
        NoteColors[0]
    }

    val startTimeStr = remember(item.note.dtstart) {
        Instant.ofEpochMilli(item.note.dtstart)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val endTimeStr = remember(item.note.dtstart, item.note.duration_min) {
        Instant.ofEpochMilli(item.note.dtstart + item.note.duration_min * 60 * 1000L)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isDone,
                onCheckedChange = { onToggleDone(it) },
                enabled = !isFuture,
                colors = CheckboxDefaults.colors(checkedColor = PrimaryAccent)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .background(noteColor, RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.note.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (isDone) Color.Gray else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$startTimeStr - $endTimeStr",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (item.note.repeat_type != "NONE") {
                Icon(
                    Icons.Default.Repeat,
                    contentDescription = "Repeat",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
            }
        }
    }
}

@Composable
fun AddEditNoteDialog(
    note: Note?,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        colorHex: String,
        dtstartMs: Long,
        durationMin: Int,
        repeatType: String,
        rrule: String?,
        repeatUntil: String?,
        exdates: List<String>?
    ) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(note?.title ?: "") }
    var colorHex by remember { mutableStateOf(note?.color ?: "#5B8DEF") }

    var startDate by remember { mutableStateOf(if (note != null) RecurrenceUtils.epochMsToLocalDate(note.dtstart) else selectedDate) }
    var startTime by remember {
        mutableStateOf(
            if (note != null) {
                LocalTime.ofNanoOfDay((note.dtstart % (24 * 60 * 60 * 1000L)) * 1_000_000L)
            } else {
                LocalTime.now()
            }
        )
    }

    var durationMin by remember { mutableStateOf(note?.duration_min ?: 30) }
    var repeatType by remember { mutableStateOf(note?.repeat_type ?: "NONE") }
    var intervalDays by remember { mutableStateOf(2) }
    var repeatUntilDate by remember { mutableStateOf<LocalDate?>(note?.repeat_until?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }) }
    var exdates by remember { mutableStateOf(note?.exdate?.split(",")?.filter { it.isNotBlank() } ?: emptyList()) }

    var errStr by remember { mutableStateOf<String?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note != null) "Sửa ghi chú" else "Thêm ghi chú") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 200) title = it; errStr = null },
                    label = { Text("Tên ghi chú (Tối đa 200 ký tự)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Start Time Picker
                OutlinedTextField(
                    value = startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Giờ bắt đầu") },
                    trailingIcon = {
                        IconButton(onClick = { showTimePicker = true }) {
                            Icon(Icons.Default.Schedule, contentDescription = "Time")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTimePicker = true }
                )

                // Duration
                OutlinedTextField(
                    value = durationMin.toString(),
                    onValueChange = { durationMin = it.toIntOrNull() ?: 30 },
                    label = { Text("Thời lượng (phút, 5–720)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Color Picker
                Text("Màu ghi chú:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    NoteColors.take(8).forEach { color ->
                        val hex = "#" + Integer.toHexString(color.value.toInt()).substring(2).uppercase()
                        val isSelected = colorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { colorHex = hex },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                // Repeat options
                Text("Lặp lại:", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                val options = listOf("NONE" to "Không lặp lại", "DAILY" to "Mỗi ngày", "INTERVAL" to "Cách n ngày", "CUSTOM" to "Các ngày tự chọn")
                options.forEach { (type, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { repeatType = type }
                    ) {
                        RadioButton(selected = repeatType == type, onClick = { repeatType = type })
                        Text(label, fontSize = 14.sp)
                    }
                }

                if (repeatType == "INTERVAL") {
                    OutlinedTextField(
                        value = intervalDays.toString(),
                        onValueChange = { intervalDays = (it.toIntOrNull() ?: 2).coerceIn(2, 65) },
                        label = { Text("Số ngày lặp n (2–65)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                errStr?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        errStr = "Tên ghi chú không được để trống"
                        return@Button
                    }
                    val startDateTime = startDate.atTime(startTime)
                    val dtstartMs = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                    val rrule = when (repeatType) {
                        "DAILY" -> "FREQ=DAILY;INTERVAL=1"
                        "INTERVAL" -> "FREQ=DAILY;INTERVAL=$intervalDays"
                        else -> null
                    }
                    val untilStr = repeatUntilDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                    onSave(title, colorHex, dtstartMs, durationMin, repeatType, rrule, untilStr, exdates)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )

    if (showTimePicker) {
        DisposableEffect(Unit) {
            val dialog = TimePickerDialog(
                context,
                { _, hour, minute ->
                    startTime = LocalTime.of(hour, minute)
                    showTimePicker = false
                },
                startTime.hour,
                startTime.minute,
                true
            )
            dialog.setOnCancelListener { showTimePicker = false }
            dialog.show()
            onDispose { dialog.dismiss() }
        }
    }
}