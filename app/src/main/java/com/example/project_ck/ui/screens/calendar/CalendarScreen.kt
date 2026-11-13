package com.example.project_ck.ui.screens.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_ck.data.entity.Note
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.util.RecurrenceUtils
import com.example.project_ck.viewmodel.CalendarViewModel
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenNoteDetail: (Long) -> Unit
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(LocalDate.now().withDayOfMonth(1)) }
    var isCalendarCollapsed by remember { mutableStateOf(false) }

    val notes by viewModel.notes.collectAsState(initial = emptyList())

    // Filter visibility
    val disabledNoteIds = remember { mutableStateListOf<Long>() }
    var isVocabVisible by remember { mutableStateOf(true) }

    val todayStr = remember(selectedDate) { selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) }
    val reviewsForDay by viewModel.getReviewsForDate(todayStr).collectAsState(initial = emptyList())

    val sheetState = rememberBottomSheetScaffoldState()

    BottomSheetScaffold(
        scaffoldState = sheetState,
        sheetPeekHeight = 48.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContent = {
            // My Calendars BottomSheet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "My calendars",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryAccent,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Vocab option
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isVocabVisible = !isVocabVisible }
                ) {
                    Checkbox(
                        checked = isVocabVisible,
                        onCheckedChange = { isVocabVisible = it },
                        colors = CheckboxDefaults.colors(checkedColor = PrimaryAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(12.dp).background(PrimaryAccent, CircleShape))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Ôn tập từ vựng", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }

                Divider(modifier = Modifier.padding(vertical = 8.dp))

                // Notes options
                LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                    items(notes, key = { it.id }) { note ->
                        val isChecked = !disabledNoteIds.contains(note.id)
                        val noteColor = try { Color(android.graphics.Color.parseColor(note.color)) } catch (e: Exception) { PrimaryAccent }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) disabledNoteIds.add(note.id) else disabledNoteIds.remove(note.id)
                                }
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    if (it) disabledNoteIds.remove(note.id) else disabledNoteIds.add(note.id)
                                },
                                colors = CheckboxDefaults.colors(checkedColor = PrimaryAccent)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.size(12.dp).background(noteColor, RoundedCornerShape(2.dp)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(note.title, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Month Header Navigation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = { isCalendarCollapsed = !isCalendarCollapsed }) {
                        Icon(
                            imageVector = if (isCalendarCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Collapse"
                        )
                    }
                }

                Row {
                    IconButton(onClick = { currentMonth = currentMonth.minusMonths(1) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Month")
                    }
                    IconButton(onClick = { currentMonth = currentMonth.plusMonths(1) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                    }
                }
            }

            // Month / Week Calendar Grid
            CalendarGrid(
                currentMonth = currentMonth,
                selectedDate = selectedDate,
                isCollapsed = isCalendarCollapsed,
                onDateSelected = { selectedDate = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Timetable View (2-way Canvas Scrollable)
            TimetableCanvasView(
                selectedDate = selectedDate,
                notes = notes.filter { !disabledNoteIds.contains(it.id) },
                vocabCount = if (isVocabVisible) reviewsForDay.size else 0,
                onNoteClick = { onOpenNoteDetail(it) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CalendarGrid(
    currentMonth: LocalDate,
    selectedDate: LocalDate,
    isCollapsed: Boolean,
    onDateSelected: (LocalDate) -> Unit
) {
    val weekFields = WeekFields.of(Locale.getDefault())
    val firstDayOfMonth = currentMonth.withDayOfMonth(1)
    val dayOfWeekOffset = firstDayOfMonth.dayOfWeek.value % 7

    val daysInMonth = currentMonth.lengthOfMonth()
    val totalGridDays = if (isCollapsed) 7 else 35

    val startDate = if (isCollapsed) {
        selectedDate.with(weekFields.dayOfWeek(), 1)
    } else {
        firstDayOfMonth.minusDays(dayOfWeekOffset.toLong())
    }

    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        // Weekday labels (Sun..Sat)
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("CN", "T2", "T3", "T4", "T5", "T6", "T7").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val rows = totalGridDays / 7
        for (r in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val date = startDate.plusDays((r * 7 + c).toLong())
                    val isSelected = date == selectedDate
                    val isCurrentMonth = date.month == currentMonth.month

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1.2f)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) PrimaryAccent else Color.Transparent)
                            .clickable { onDateSelected(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else if (isCurrentMonth) MaterialTheme.colorScheme.onSurface else Color.LightGray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimetableCanvasView(
    selectedDate: LocalDate,
    notes: List<Note>,
    vocabCount: Int,
    onNoteClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(24 * 60.dp) // 24 hours * 60dp per hour
        ) {
            val hourHeight = 60.dp.toPx()
            val totalWidth = size.width
            val hourColumnWidth = 50.dp.toPx()
            val gridWidth = totalWidth - hourColumnWidth

            // Draw hour lines & labels
            for (h in 0..24) {
                val y = h * hourHeight
                drawLine(
                    color = Color(0xFFE3E0DA),
                    start = Offset(hourColumnWidth, y),
                    end = Offset(totalWidth, y),
                    strokeWidth = 1f
                )
            }

            // Draw current time red line if today
            val now = LocalTime.now()
            if (selectedDate == LocalDate.now()) {
                val currentY = (now.hour + now.minute / 60f) * hourHeight
                drawLine(
                    color = Color.Red,
                    start = Offset(hourColumnWidth, currentY),
                    end = Offset(totalWidth, currentY),
                    strokeWidth = 3f
                )
                drawCircle(color = Color.Red, radius = 5f, center = Offset(hourColumnWidth, currentY))
            }

            // Draw Vocab review green line if any
            if (vocabCount > 0) {
                val vocabY = 20 * hourHeight // default 20:00
                drawLine(
                    color = Color(0xFF2E6F40),
                    start = Offset(hourColumnWidth, vocabY),
                    end = Offset(totalWidth, vocabY),
                    strokeWidth = 4f
                )
            }
        }
    }
}