package com.example.project_ck.ui.screens.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_ck.data.dao.WordReviewWithWord
import com.example.project_ck.ui.theme.ErrorColor
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.viewmodel.StatsViewModel

@Composable
fun StatsScreen(
    viewModel: StatsViewModel
) {
    val missedReviews by viewModel.missedReviews.collectAsState(initial = emptyList())
    val upcomingReviews by viewModel.upcomingReviews.collectAsState(initial = emptyList())
    val completedReviews by viewModel.completedReviews.collectAsState(initial = emptyList())

    var isMissedExpanded by remember { mutableStateOf(true) } // Default expanded
    var isUpcomingExpanded by remember { mutableStateOf(false) }
    var isCompletedExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section 1: Đã bỏ lỡ
        item {
            StatsSectionHeader(
                title = "Đã bỏ lỡ",
                count = missedReviews.size,
                countColor = ErrorColor,
                isExpanded = isMissedExpanded,
                onToggle = { isMissedExpanded = !isMissedExpanded }
            )
        }

        if (isMissedExpanded) {
            if (missedReviews.isEmpty()) {
                item { Text("Không có từ vựng nào bị bỏ lỡ", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(start = 16.dp)) }
            } else {
                items(missedReviews, key = { "missed_${it.review.id}" }) { item ->
                    ReviewStatRow(item = item, labelColor = ErrorColor)
                }
            }
        }

        // Section 2: Sắp tới
        item {
            StatsSectionHeader(
                title = "Sắp tới (7 ngày)",
                count = upcomingReviews.size,
                countColor = PrimaryAccent,
                isExpanded = isUpcomingExpanded,
                onToggle = { isUpcomingExpanded = !isUpcomingExpanded }
            )
        }

        if (isUpcomingExpanded) {
            if (upcomingReviews.isEmpty()) {
                item { Text("Không có lịch ôn tập sắp tới", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(start = 16.dp)) }
            } else {
                items(upcomingReviews, key = { "upcoming_${it.review.id}" }) { item ->
                    ReviewStatRow(item = item, labelColor = PrimaryAccent)
                }
            }
        }

        // Section 3: Đã hoàn thành
        item {
            StatsSectionHeader(
                title = "Đã hoàn thành",
                count = completedReviews.size,
                countColor = PrimaryAccent,
                isExpanded = isCompletedExpanded,
                onToggle = { isCompletedExpanded = !isCompletedExpanded }
            )
        }

        if (isCompletedExpanded) {
            if (completedReviews.isEmpty()) {
                item { Text("Chưa có lượt ôn nào hoàn thành", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(start = 16.dp)) }
            } else {
                items(completedReviews, key = { "completed_${it.review.id}" }) { item ->
                    ReviewStatRow(item = item, labelColor = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun StatsSectionHeader(
    title: String,
    count: Int,
    countColor: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = countColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = count.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = countColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle"
            )
        }
    }
}

@Composable
fun ReviewStatRow(
    item: WordReviewWithWord,
    labelColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.word.word, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = labelColor)
                Text(item.word.meaning, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("Lần ôn thứ ${item.review.stage} - Ngày: ${item.review.due_date}", fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}