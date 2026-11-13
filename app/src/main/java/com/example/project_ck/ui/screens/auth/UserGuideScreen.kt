package com.example.project_ck.ui.screens.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.project_ck.ui.theme.PrimaryAccent
import com.example.project_ck.ui.theme.PrimaryBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hướng dẫn sử dụng", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBackground)
            )
        },
        containerColor = PrimaryBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GuideSection(
                title = "1. Tab Read (Đọc tài liệu PDF)",
                content = "Thêm file PDF từ máy vào thư mục. Bôi đen văn bản trong tài liệu để đánh dấu với 5 nút (?, !, Star, Highlight, Note). Quản lý danh sách đánh dấu trong tab Thống kê của Topic."
            )

            GuideSection(
                title = "2. Tab Notes (Ghi chú công việc)",
                content = "Tạo ghi chú công việc theo ngày với các quy luật lặp lại (Mỗi ngày, Cách n ngày, Các ngày tự chọn). Tích chọn công việc khi hoàn thành."
            )

            GuideSection(
                title = "3. Tab Idea (Ý tưởng)",
                content = "Lưu nhanh các ý tưởng công việc hoặc học tập. Phân loại theo Chưa thực hiện và Đã thực hiện, sắp xếp theo thời gian tạo."
            )

            GuideSection(
                title = "4. Tab Vocabulary (Từ vựng & Ôn tập)",
                content = "Thêm từ vựng vào các thư mục. Ứng dụng tự động lập lịch 5 lần ôn tập lặp lại ngắt quãng (+1 ngày, +3 ngày, +7 ngày, +1 tháng, +3 tháng). Ôn tập từ vựng bằng thẻ ghi nhớ (Flashcard 3D)."
            )

            GuideSection(
                title = "5. Thống kê & Lịch",
                content = "Xem danh sách từ vựng/ghi chú Đã bỏ lỡ, Sắp tới và Đã hoàn thành trong 30 ngày. Xem thời khóa biểu và lịch học trên màn hình Lịch."
            )

            GuideSection(
                title = "6. Máy ảnh (OCR & Dịch)",
                content = "Chụp ảnh hoặc chọn ảnh chứa văn bản tiếng Anh để trích xuất chữ bằng OCR ML Kit, chọn từ/câu để dịch sang tiếng Việt và thêm trực tiếp vào từ vựng."
            )
        }
    }
}

@Composable
private fun GuideSection(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryAccent
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}