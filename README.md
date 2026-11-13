# ReNo – đọc tài liệu, ghi chú và ôn từ vựng

ReNo (**Re**ad + **No**te) là ứng dụng Android giúp người học đọc và đánh dấu tài liệu PDF,
ghi chú công việc theo ngày (có lặp lại), lưu ý tưởng và ôn từ vựng theo phương pháp lặp lại
ngắt quãng. Phiên bản 1 chạy hoàn toàn trên máy, dữ liệu lưu bằng SQLite (Room), không cần máy
chủ.

## Yêu cầu

| Hạng mục | Phiên bản |
| --- | --- |
| Android Studio | bản mới nhất (hỗ trợ AGP 9.4) |
| JDK cho Gradle | 25 (Android Studio tự tải theo `gradle/gradle-daemon-jvm.properties`) |
| Android SDK | Platform 37 (compileSdk / targetSdk 37), minSdk 26 |
| Thiết bị / máy ảo | Android 8.0 trở lên |

## Chạy ứng dụng

1. Mở thư mục dự án bằng Android Studio, chờ Gradle sync xong.
2. Chọn cấu hình `app` và bấm **Run**.

Dòng lệnh:

```bash
./gradlew :app:assembleDebug
```

APK nằm ở `app/build/outputs/apk/debug/app-debug.apk`.

Lần đầu mở app: bấm **Đăng ký** để tạo tài khoản (email hoặc số điện thoại Việt Nam 10 số,
username 3–30 ký tự, mật khẩu 6–64 ký tự), rồi đăng nhập bằng username.

Lưu ý khi chạy thử:

- **Máy ảnh / Dịch**: lần dịch đầu tiên cần mạng để tải gói ngôn ngữ Anh – Việt của ML Kit.
- **Nhắc nhở đúng phút** (Android 12+): app giải thích một lần rồi mở cài đặt
  "Báo thức và lời nhắc". Nếu không cấp, thông báo có thể đến trễ vài phút.
- **PDF scan** (toàn ảnh, không có lớp chữ) mở được nhưng không bôi đen để đánh dấu được.

## Kiểm thử

```bash
./gradlew :app:testDebugUnitTest
```

Bộ kiểm thử chạy trên JVM bằng Robolectric, không cần máy ảo:

- `domain/` – băm mật khẩu, kiểm tra dữ liệu, sinh ngày lặp (RRULE/RDATE/EXDATE), lịch ôn 5 lần.
- `integration/` – các tình huống ở mục 11.2 của đặc tả: trùng username/email, xóa thư mục kéo
  theo topic + đánh dấu + file PDF, ghi chú cách 3 ngày có ngày bỏ qua, tích hôm nay không ảnh
  hưởng ngày mai, 30 từ cùng giờ chỉ một thông báo, bỏ qua vượt lần ôn kế tiếp bị chặn,
  đăng xuất hủy báo thức…
- `ui/ScreensTest` – mở thử mọi màn hình với dữ liệu mẫu.

Chụp ảnh các màn hình (khung 390 × 844 dp như bản thiết kế) vào `app/build/screenshots`:

```bash
./gradlew :app:testDebugUnitTest -PrenoScreenshots=true --tests '*ScreenshotTest'
```

Ảnh chụp sẵn của 19 màn hình (dữ liệu mẫu) nằm trong `docs/screenshots/`.

## Công nghệ

| Hạng mục | Lựa chọn |
| --- | --- |
| Ngôn ngữ, giao diện | Java, layout XML, Material Components 3, ViewBinding |
| Kiến trúc | MVVM: Activity/Fragment → ViewModel (LiveData) → Repository → DAO; truy vấn chạy trên `ExecutorService` |
| CSDL | SQLite qua Room (10 bảng, khóa ngoại `ON DELETE CASCADE`, ràng buộc CHECK cài bằng trigger) |
| Đọc PDF | PDF.js 4.10 (`assets/pdfjs`) trong WebView, nạp qua `WebViewAssetLoader` |
| Cắt avatar | uCrop (Yalantis, qua JitPack) |
| OCR, dịch | ML Kit Text Recognition v2 (Latin), ML Kit Translation (Anh → Việt) |
| Quy luật lặp | lib-recur (dmfs) |
| Lịch tháng | kizitonwose Calendar (bản View); thời khóa biểu tự vẽ bằng Canvas |
| Chạy nền | AlarmManager (48 giờ tới, lập lại 00:05 mỗi ngày) + WorkManager (12 giờ một lần) |

## Cấu trúc thư mục

```
app/src/main/
├── java/com/reno/app/
│   ├── data/
│   │   ├── db/          RenoDatabase, DbConstraints, entity/ (10 Entity), dao/ (10 DAO)
│   │   ├── model/       Occurrence, ReviewItem, FolderGroup…
│   │   └── repository/  User, Read, Note, Idea, Vocab, Notification
│   ├── domain/          PasswordHasher, RecurrenceHelper, ReviewPlanner, Validators
│   ├── reminder/        ReminderScheduler, ReminderReceiver, SystemEventReceiver,
│   │                    RescheduleWorker, NotificationHelper, SkipDialogActivity
│   ├── ui/              auth, main, read, notes, idea, vocab, stats, camera,
│   │                    calendar, notification, profile, common
│   └── util/            AppExecutors, SessionManager, DateUtils, LiveQuery…
├── assets/pdfjs/        pdf.mjs, pdf.worker.mjs, pdf_viewer.mjs, reader.html, reader.js
└── res/                 layout, drawable, values (colors, strings, dimens, themes)
```

## Ngoài phạm vi phiên bản 1

Tóm tắt PDF bằng AI (tab Tóm tắt hiện "Tính năng tóm tắt đang được phát triển"), quên mật
khẩu, đăng nhập Google, đồng bộ nhiều máy.
