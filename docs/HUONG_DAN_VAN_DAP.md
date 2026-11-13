# Tài liệu ôn vấn đáp – ReNo

Cách dùng: đọc phần 1–3 để nắm bức tranh chung, phần 4 để hiểu từng chức năng chạy thế nào,
phần 5 là các câu cô hay hỏi kèm câu trả lời mẫu. Mỗi ý đều ghi **file** để bạn mở ra chỉ cho cô.
Đường dẫn bên dưới tính từ `app/src/main/java/com/reno/app/` (viết tắt `…/`).

---

## 1. ReNo là gì, làm được gì (nói được trong 30 giây)

ReNo là ứng dụng Android cho sinh viên, gom 4 việc vào một chỗ: **đọc và đánh dấu PDF**,
**ghi chú theo ngày (có lặp lại)**, **lưu ý tưởng**, **học từ vựng bằng flashcard với lịch ôn
ngắt quãng**. Có thêm **Máy ảnh** (nhận chữ từ ảnh rồi dịch), **Lịch**, **Thống kê** và
**Thông báo nhắc nhở**. Ứng dụng chạy hoàn toàn trên máy, dữ liệu lưu bằng SQLite, không cần máy chủ.

Ngôn ngữ **Java**, giao diện viết bằng **XML**. minSdk 26 (Android 8.0), targetSdk 37.

---

## 2. Kiến trúc: MVVM

```
Giao diện (Activity / Fragment + layout XML)
        │  quan sát LiveData, gọi hàm
        ▼
ViewModel   (giữ trạng thái màn hình, không biết View là gì)
        │
        ▼
Repository  (gom DAO, chạy việc nặng ở luồng nền, xử lý file)
        │
        ▼
DAO ──► Room ──► SQLite (file reno.db)
```

**Vì sao dùng MVVM?** Tách giao diện khỏi xử lý dữ liệu. ViewModel sống lâu hơn Activity nên khi
xoay màn hình hoặc Activity bị tạo lại thì dữ liệu đang nhập không mất. Cũng dễ kiểm thử hơn.

**Quy tắc quan trọng:** mọi truy vấn CSDL, băm mật khẩu, chép file, OCR, dịch **đều chạy ở luồng
nền** (`ExecutorService`), kết quả đưa về giao diện qua **LiveData** hoặc **callback chạy trên luồng
chính**. Nếu truy vấn trên luồng giao diện, Room sẽ ném lỗi ngay, nên không thể vô tình làm sai.

Cấu trúc thư mục:

| Gói | Vai trò |
| --- | --- |
| `…/data/db/entity` | 10 lớp Entity = 10 bảng |
| `…/data/db/dao` | 10 DAO chứa câu truy vấn |
| `…/data/db/RenoDatabase.java` | lớp CSDL Room |
| `…/data/repository` | `UserRepository`, `ReadRepository`, `NoteRepository`, `IdeaRepository`, `VocabRepository`, `NotificationRepository` |
| `…/domain` | logic thuần Java: `PasswordHasher`, `Validators`, `RecurrenceHelper`, `ReviewPlanner` |
| `…/ui/…` | Activity, Fragment, ViewModel, Adapter của từng màn hình |
| `…/reminder` | báo thức và thông báo |
| `…/util` | `AppExecutors`, `SessionManager`, `DateUtils`, `LiveQuery`… |
| `assets/pdfjs` | thư viện PDF.js + `reader.html`, `reader.js` do mình viết |

---

## 3. Công cụ và thư viện (bảng "dùng để làm gì – ở đâu")

| Thư viện / công cụ | Dùng để làm gì | Nằm ở đâu trong code |
| --- | --- | --- |
| **Room** | lưu dữ liệu vào SQLite, kiểm tra câu SQL lúc biên dịch | `data/db/*` |
| **LiveData + ViewModel** (Jetpack) | giao diện tự cập nhật khi dữ liệu đổi; giữ dữ liệu khi xoay màn | `ui/*/…ViewModel.java` |
| **ViewBinding** | truy cập view an toàn (`b.btnSave`) thay cho `findViewById` | bật trong `app/build.gradle` (`viewBinding true`) |
| **Material Components** | nút, thẻ, TabLayout, BottomNavigationView, hộp thoại, DatePicker/TimePicker | layout XML, `ui/common/Pickers.java`, `Dialogs.java` |
| **RecyclerView + Adapter** | hiển thị danh sách | 8 adapter (xem 5.4) |
| **ViewPager2 + TabLayout** | 4 tab con của mục Nhà | `ui/main/HomeFragment.java` |
| **PDF.js** (Mozilla) + **WebView** | đọc PDF, bôi đen chữ, vẽ lớp đánh dấu | `assets/pdfjs/reader.js`, `ui/read/TopicActivity.java` |
| **WebViewAssetLoader** (androidx.webkit) | nạp trang và file PDF của app qua địa chỉ `https://appassets.androidplatform.net/` | `TopicActivity.java` dòng ~166 |
| **uCrop** (Yalantis) | cắt avatar trong khung tròn | `ui/profile/ProfileActivity.java` (`startCrop`) |
| **ML Kit Text Recognition v2** | nhận chữ từ ảnh (OCR) | `ui/camera/CameraViewModel.java` (`recognize`) |
| **ML Kit Translation** | dịch Anh → Việt trên máy | `CameraViewModel.java` (`translate`) |
| **lib-recur** (dmfs) | đọc và sinh ngày lặp từ RRULE | `domain/RecurrenceHelper.java` |
| **kizitonwose Calendar** | lịch tháng / tuần | `ui/calendar/CalendarFragment.java` |
| **AlarmManager** | đặt báo thức nhắc đúng giờ | `reminder/ReminderScheduler.java` |
| **WorkManager** | tác vụ định kỳ 12 giờ, phòng báo thức bị hệ thống xóa | `reminder/RescheduleWorker.java` |
| **BroadcastReceiver** | nhận báo thức; nhận sự kiện khởi động lại máy, đổi giờ | `reminder/ReminderReceiver.java`, `SystemEventReceiver.java` |
| **FileProvider** | cho app máy ảnh ghi ảnh vào file tạm của mình | `res/xml/file_paths.xml`, `CameraFragment.java` |
| **SharedPreferences** | lưu phiên đăng nhập và vài lựa chọn nhỏ | `util/SessionManager.java` |
| **javax.crypto (PBKDF2)** | băm mật khẩu | `domain/PasswordHasher.java` |
| **java.time** | xử lý ngày giờ (`LocalDate`, `LocalTime`) | khắp nơi, gom ở `util/DateUtils.java` |
| **Robolectric + JUnit** | kiểm thử trên JVM, không cần máy ảo | `app/src/test` |

> Cô hỏi "em có dùng **ListView** không?" → **Không**. Em dùng **RecyclerView**, vì RecyclerView tái
> sử dụng view (ViewHolder), mượt hơn ListView và là chuẩn hiện nay. ListView là thành phần cũ.

---

## 4. Từng chức năng chạy như thế nào

### 4.1 Tài khoản (đăng ký, đăng nhập, đăng xuất, thông tin)

- **Màn:** `ui/auth/LoginActivity`, `RegisterActivity`; `ui/profile/ProfileActivity`.
- **Đăng ký** → `AuthViewModel.register` → `UserRepository.register`:
  1. Kiểm tra từng ô theo bảng quy tắc (`domain/Validators.java`): email dùng
     `PatternsCompat.EMAIL_ADDRESS`; số điện thoại Việt Nam 10 số bắt đầu bằng 0 (`+84` đổi thành `0`);
     username 3–30 ký tự, không có `@`; mật khẩu 6–64 ký tự; nhập lại phải khớp.
  2. Kiểm tra trùng email/số điện thoại/username (không phân biệt hoa thường).
  3. Băm mật khẩu rồi lưu.
  4. Lỗi hiện ngay dưới ô nhập bằng `TextInputLayout.setError` (không dùng Toast).
- **Băm mật khẩu** (`PasswordHasher`): PBKDF2WithHmacSHA256, **salt ngẫu nhiên 16 byte** cho mỗi tài khoản,
  **100.000 vòng lặp**, khóa 256 bit. Chuỗi lưu: `pbkdf2$100000$<salt>$<hash>`. So sánh bằng
  `MessageDigest.isEqual` (so sánh thời gian hằng, tránh tấn công đo thời gian). Việc băm mất khoảng nửa
  giây nên chạy ở luồng nền (`AppExecutors.heavy()`).
- **Đăng nhập:** tìm theo username, so mật khẩu; sai bất kể ô nào đều báo chung "Sai username hoặc mật khẩu".
  Thành công thì lưu `current_user_id` vào SharedPreferences (`SessionManager.login`) rồi mở `MainActivity`.
  Lần sau mở app, `LoginActivity.onCreate` thấy đã có phiên thì vào thẳng màn chính.
- **Đăng xuất** (`MainActivity.logout`): hủy mọi báo thức, xóa `current_user_id`, về màn đăng nhập và
  xóa ngăn xếp màn hình (`FLAG_ACTIVITY_CLEAR_TASK`).
- **Dữ liệu tách theo tài khoản:** mọi bảng truy ngược được về `users.id` qua cột `user_id`, nên hai tài
  khoản trên cùng máy không thấy dữ liệu của nhau.
- **Avatar:** chọn Chụp ảnh / Thư viện / Mặc định → mở uCrop (khung tròn, tỉ lệ 1:1, kết quả 512×512, JPEG 85%)
  → lưu `filesDir/avatars/<userId>_<thời gian>.jpg`, CSDL chỉ lưu đường dẫn; ảnh cũ bị xóa khi lưu ảnh mới.

### 4.2 Khung màn hình chính (`ui/main/MainActivity`)

- Thanh trên cùng: avatar + username, nút "…" mở `PopupMenu` (Thông tin, Hướng dẫn, Đăng xuất).
- Giữa: một `FragmentContainerView`; chọn mục ở `BottomNavigationView` thì thay Fragment tương ứng
  (`showSection`): Nhà, Thống kê, Máy ảnh, Lịch, Thông báo.
- Huy hiệu số thông báo chưa đọc: `BadgeDrawable` quan sát `LiveData` của `MainViewModel`.
- **Nhà** (`HomeFragment`) là `ViewPager2` + `TabLayout` + `FragmentStateAdapter` với 4 Fragment:
  `ReadFragment`, `NotesFragment`, `IdeaFragment`, `VocabFragment`.

### 4.3 Read: thư mục, topic, đọc và đánh dấu PDF

- **Thư mục/topic** (`ui/read/ReadFragment`, `data/repository/ReadRepository`): bảng `folders` (cột `type`
  = `READ`) và `topics`. Mỗi topic là đúng một file PDF.
- **Thêm PDF:**
  1. `ActivityResultContracts.OpenDocument` mở bộ chọn file hệ thống (không cần xin quyền bộ nhớ).
  2. `ReadRepository.preparePdf`: **chép file vào `filesDir/pdfs/<uuid>.pdf`** ở luồng nền (vì quyền đọc URI
     gốc sẽ mất khi app khởi động lại), rồi mở thử bằng `PdfRenderer` để lấy số trang. File hỏng/có mật khẩu
     thì xóa bản chép và báo lỗi.
  3. Người dùng đặt tên → `createTopic`, chọn **ngẫu nhiên ảnh nền 1–8** (`bg_index`), không bao giờ đổi.
- **Mục "Gần đây":** 3 topic có `last_opened_at` mới nhất; 3 thẻ rộng bằng nhau, tỉ lệ 1:1
  (`ui/common/SquareFrameLayout`). Ảnh nền là họa tiết nhỏ nên hiển thị **kiểu lát gạch** (`bg_n_tile.xml`
  với `tileMode="repeat"`) để không bị mờ.
- **Xóa thư mục/topic:** lấy danh sách file trước, xóa dòng trong CSDL (CASCADE kéo theo topic + đánh dấu),
  rồi xóa file PDF trên đĩa (CASCADE không tự xóa file).
- **Đọc PDF** (`ui/read/TopicActivity`):
  - `WebView` nạp `assets/pdfjs/reader.html` qua `WebViewAssetLoader` (hai đường: `/assets/` và `/pdfs/`).
    Tắt `setAllowFileAccess`, chặn mọi địa chỉ ngoài app trong `shouldInterceptRequest`.
  - `reader.js` dùng `PDFViewer` của PDF.js (hiển thị trang khi cần, có lớp chữ để bôi đen).
  - **Java ↔ JavaScript:**
    - Java gọi JS bằng `evaluateJavascript("reno.load(...)")`, `reno.render(json)`, `reno.scrollTo(id)`,
      `reno.getSelection()`.
    - JS gọi Java qua đối tượng `Android` (lớp `ui/read/PdfBridge`, các hàm có `@JavascriptInterface`):
      `onLoaded`, `onPageChanged`, `onAnnotationTap`, `onSelection`, `onScanDetected`…
    - `PdfBridge` chỉ nhận dữ liệu (số trang, mã đánh dấu), không chạy lệnh tùy ý — đây là biện pháp an toàn.
- **Bôi đen → 5 nút:** `ui/read/SelectableWebView` là lớp con của `WebView`, **ghi đè `startActionMode`**
  để thay menu hệ thống bằng 5 nút `?`, `!`, `★`, `Highlight`, `Notes`.
- **Lưu đánh dấu:** bấm nút → Java gọi `reno.getSelection()` lấy `{trang, toạ độ, đoạn chữ}` → hộp thoại
  Tên/Chi tiết (Highlight có thêm bảng 5 màu) → lưu vào bảng `annotations`. **Không ghi vào file PDF.**
- **Toạ độ lưu theo tỉ lệ 0–1 so với trang** (`rects` là JSON `[[x,y,rộng,cao]]`), nên khi phóng to/thu nhỏ
  vùng tô vẫn nằm đúng chữ. JS vẽ lại lớp đánh dấu bằng phần trăm (`left: x*100%`).
- **PDF scan** (không có lớp chữ): `reader.js` kiểm tra trang 1, báo `onScanDetected(true)`, app hiện dải cảnh
  báo và tắt nút đánh dấu.
- **Tab Thống kê của topic:** 5 ô đếm theo loại, 5 tab lọc, mỗi mục có liên kết "Nhấn để di chuyển…" →
  `TopicActivity.jumpTo` chuyển về tab Ghi chú rồi gọi `reno.scrollTo(id)`; JS cuộn tới và **nháy sáng 2 lần**
  (CSS animation).
- **Tab Tóm tắt:** chỉ hiện "Tính năng tóm tắt đang được phát triển" (để phiên bản sau).
- Nhớ trang đọc: `onPageChanged` → `TopicViewModel.saveLastPage` → cột `last_page`; mở lại thì `reno.load(url, lastPage)`.

### 4.4 Notes: ghi chú theo ngày, có lặp lại

- **Màn:** `ui/notes/NotesFragment` (danh sách), `NoteFormActivity` (thêm/sửa), `NoteDetailActivity` (chi tiết).
- **Ý tưởng cốt lõi:** *không tạo sẵn các lần lặp trong CSDL* (lặp vô hạn sẽ sinh vô hạn dòng). Bảng `notes`
  chỉ lưu **quy luật**; mỗi lần mở màn thì **tính** ra các ngày cần hiển thị.
- **Quy luật lưu theo chuẩn iCalendar (RFC 5545):**

  | Chọn trên màn | `repeat_type` | `rrule` | `rdate` | `exdate` |
  | --- | --- | --- | --- | --- |
  | Không lặp | NONE | NULL | NULL | NULL |
  | Mỗi ngày | DAILY | `FREQ=DAILY;INTERVAL=1` | NULL | ngày bỏ qua |
  | Cách n ngày (2–65) | INTERVAL | `FREQ=DAILY;INTERVAL=n` | NULL | ngày bỏ qua |
  | Các ngày tự chọn | CUSTOM | NULL | danh sách ngày | NULL |

  Ngày kết thúc lặp lưu ở cột riêng `repeat_until` (không ghi `UNTIL` vào RRULE).
- **Sinh ngày:** `domain/RecurrenceHelper.occurrenceDates(...)` dùng `RecurrenceSet` của lib-recur:
  ngày bắt đầu + `OfRule` (RRULE) + `OfList` (RDATE) → `Merged`, trừ `Difference` (EXDATE), giới hạn bằng
  `Preceding` (repeat_until và ngày cuối khoảng) và `FastForwarded` (bỏ qua phần trước ngày cần xem).
  **Luôn có điểm dừng**, không bao giờ duyệt vô hạn.
- **Ví dụ trong đặc tả:** bắt đầu 13/11, cách 3 ngày, bỏ ngày 19/11 → ra 13/11, 16/11, 22/11, 25/11…
  (có kiểm thử `RecurrenceHelperTest`).
- **Trạng thái từng ngày:** bảng `note_occurrences` (`note_id`, `occ_date`, `status` = DONE/SKIPPED),
  `UNIQUE(note_id, occ_date)`. **Không có dòng nghĩa là chưa làm.** Tích → thêm dòng DONE; bỏ tích → xóa dòng.
  Nhờ vậy tích hôm nay thì ngày mai vẫn chưa tích.
- **Ghép dữ liệu:** `NoteRepository.occurrencesBetween` lấy ngày từ `RecurrenceHelper` rồi ghép với
  `note_occurrences` thành đối tượng `Occurrence` (`data/model`). Hàm này dùng chung cho tab Notes (1 ngày),
  Lịch (1 tuần), Thống kê (30 ngày trước → 7 ngày sau) và bộ đặt báo thức.
- **Quy tắc giao diện:** chỉ tích được hôm nay hoặc ngày đã qua (`check.setEnabled(!future)`); ghi chú đã
  tích bị gạch ngang và xếp xuống cuối (`NotesViewModel.load` sắp xếp); xóa ghi chú lặp có cảnh báo
  "Mọi lần lặp sẽ bị xóa".
- **Kiểm tra khi lưu** (`NoteFormActivity.save`): tên không trống; ngày giờ bắt đầu không trước hiện tại (chỉ
  kiểm tra khi tạo mới hoặc khi đổi trường này); thời lượng 5–720 phút; n từ 2–65.

### 4.5 Idea

`ui/idea/IdeaFragment` + `IdeaViewModel` + `IdeaRepository`. Hai tab con (Chưa thực hiện / Đã thực hiện).
Tích ô → `is_done = 1` và lưu `done_at`; dòng biến mất khỏi tab nên hiện **Snackbar có nút Hoàn tác**.
Kiểu sắp xếp (mới nhất/cũ nhất trước) lưu trong SharedPreferences (`SessionManager.setIdeaOldestFirst`).

### 4.6 Vocabulary: từ vựng, flashcard, ôn tập ngắt quãng

- **Thêm từ** (`ui/vocab/WordFormActivity` → `VocabRepository.saveWordSync`):
  kiểm tra bắt buộc/độ dài, **từ không trùng trong cùng thư mục (không phân biệt hoa thường)**, rồi trong một
  **giao dịch** (`db.runInTransaction`) thêm từ và **tạo sẵn 5 dòng `word_reviews`**.
- **Lịch ôn cố định 5 lần** tính từ ngày tạo (`domain/ReviewPlanner`): +1 ngày, +3 ngày, +7 ngày, +1 tháng,
  +3 tháng (dùng `java.time.Period`). Ôn sớm/muộn/bỏ qua một lần **không làm đổi** các lần sau. Cơ sở lý thuyết:
  **đường cong quên lãng của Ebbinghaus** / phương pháp lặp lại ngắt quãng.
- **Giờ nhắc:** mỗi từ có thể dùng "Giờ mặc định" (`users.review_time`, `words.remind_time` để NULL) hoặc
  "Tự chọn" (`words.remind_time` = HH:mm). Câu truy vấn dùng `COALESCE(w.remind_time, u.review_time)`
  (`WordReviewDao.ITEM_COLUMNS`). Đổi giờ chung thì các từ dùng giờ mặc định đổi theo.
- **Flashcard** (`ui/vocab/FlashcardActivity`, `FlashcardController`): mặt trước là nghĩa tiếng Việt; bấm thẻ thì
  **lật** bằng `ObjectAnimator` xoay `rotationY` 0→90°, đổi nội dung, rồi −90°→0° (mỗi nửa 150 ms = 300 ms),
  `setCameraDistance` để hình không méo khi xoay. Mũi tên trái/phải **tối đi và không bấm được ở hai đầu**, không quay vòng.
- **Ôn tập** (`ui/vocab/ReviewActivity` + `ReviewViewModel`): bộ thẻ là các từ PENDING của một thông báo (một mốc giờ).
  Mỗi thẻ có **Xong** (lần ôn → DONE, lưu `done_at`) hoặc **Bỏ qua**; có thanh tiến độ "Đã xử lý 5 / 12" và màn tổng kết.
- **Hộp thoại Bỏ qua 3 lựa chọn** (`reminder/SkipDialogActivity`, xử lý ở `VocabRepository`):

  | Lựa chọn | Kết quả | Giới hạn |
  | --- | --- | --- |
  | Dời sang lần ôn tiếp theo (`skipToNextSync`) | SKIPPED, vào "Đã bỏ lỡ", không đặt lại lịch | — |
  | Nhắc vào thời điểm cụ thể (`remindAtSync`) | `due_date` = ngày chọn, `remind_at` = ngày giờ chọn, vẫn PENDING | sau hiện tại và **trước lần ôn kế tiếp** |
  | Lùi n ngày (`postponeSync`) | `due_date` + n, vẫn PENDING | n ≥ 1, ngày mới **trước lần ôn kế tiếp** |

  Lần ôn thứ 5 (không còn lần sau) được dời tối đa 30 ngày kể từ ngày ôn gốc (`ReviewPlanner.limitExclusive`).
  `original_due_date` luôn giữ nguyên ngày gốc. Hộp thoại dùng chung cho cả nhóm và từng từ.
- **"Đã bỏ lỡ":** hết ngày ôn mà lần ôn chưa DONE (còn PENDING hoặc đã SKIPPED). Tính bằng truy vấn mỗi lần mở
  màn Thống kê, **không cần tác vụ chạy nền**.

### 4.7 Thống kê (`ui/stats/StatsFragment` + `StatsViewModel`)

Ba mục xếp dọc: **Đã bỏ lỡ** (30 ngày gần nhất), **Sắp tới** (7 ngày tới), **Đã hoàn thành** (30 ngày gần nhất).
Mỗi mục có số lượng và mũi tên mở/đóng (mặc định chỉ mở "Đã bỏ lỡ"). Một dòng là một lần xuất hiện của ghi chú
hoặc **một nhóm từ vựng trong một ngày** ("Ôn 12 từ – 14/11") để danh sách không dài hàng trăm dòng.

### 4.8 Máy ảnh: OCR + dịch (`ui/camera/CameraFragment` + `CameraViewModel`)

1. Chụp: `ActivityResultContracts.TakePicture` ghi vào file tạm trong `cacheDir`, chia sẻ qua **FileProvider**
   (nên **không cần khai báo quyền CAMERA**). Chọn ảnh: **Photo Picker** (`PickVisualMedia`), cũng không cần quyền.
2. **ML Kit Text Recognition v2 (bản Latin)**: `InputImage.fromFilePath` (tự xử lý ảnh bị xoay) →
   `recognizer.process(image)`. Chạy trên máy, miễn phí.
3. Văn bản hiện trong `TextView` bật `textIsSelectable`; `setCustomSelectionActionModeCallback` **thêm mục "Dịch"**
   vào menu bôi đen.
4. **ML Kit Translation** (Anh → Việt, chạy trên máy): lần đầu chưa có gói ngôn ngữ thì hỏi tải (cần mạng);
   đoạn chọn dài quá 200 ký tự thì báo chọn đoạn ngắn hơn. Kết quả hiện "từ → nghĩa" kèm nút "+".
5. Bấm "+" → mở `WordFormActivity` đã điền sẵn từ và nghĩa. **Kết quả OCR không được lưu.**

### 4.9 Lịch (`ui/calendar/CalendarFragment`, `CalendarViewModel`, `TimetableView`)

- **Lịch tháng:** thư viện kizitonwose (`CalendarView`); mỗi ô ngày dùng layout `calendar_day.xml` với chấm xám
  (có ghi chú) và chấm xanh rêu (có từ cần ôn). Nút thu gọn chuyển sang `WeekCalendarView` (một hàng tuần).
- **Thời khóa biểu** là **View tự vẽ bằng Canvas** (`TimetableView.onDraw`): cột giờ 0–24h, 7 cột ngày, thấy 3 cột.
  Cuộn hai chiều bằng **`GestureDetector` + `OverScroller`** (`onScroll` kéo, `onFling` trượt quán tính,
  `computeScroll` chạy tiếp hiệu ứng). Có vạch đỏ giờ hiện tại. Ghi chú là khối chữ nhật theo thời lượng; hai
  khối trùng giờ thì chia đôi chiều rộng cột (thuật toán xếp cột trong `layoutBlocks`).
- **Từ vựng:** một vạch ngang xanh rêu tại mỗi giờ nhắc, ghi "Ôn N từ". Bấm khối ghi chú → chi tiết; bấm vạch → màn ôn tập.
- **My calendars:** `BottomSheetBehavior` (bảng kéo từ đáy); mỗi dòng có checkbox ẩn/hiện, lưu bằng
  `SessionManager.setHiddenCalendars`.
- Vì sao tự vẽ Canvas? Tránh việc một danh sách cuộn dọc nằm trong một trang cũng cuộn dọc (hai thao tác cuộn tranh nhau).

### 4.10 Thông báo và nhắc nhở (`…/reminder`, `ui/notification`)

Đây là phần dễ bị hỏi sâu nhất. Nguyên tắc: **chỉ đặt báo thức cho 48 giờ tới**, rồi tự lập lại mỗi ngày.
Như vậy không bao giờ phải đặt vô hạn báo thức cho ghi chú lặp vô hạn, và luôn khớp dữ liệu mới nhất.

| Thành phần | Việc làm |
| --- | --- |
| `ReminderScheduler.rescheduleNow(userId)` | hủy báo thức cũ → tính các mốc trong 48 giờ (`computeMarks`) → đặt lại |
| `ReminderReceiver` | nhận báo thức → kiểm tra lại CSDL → ghi `app_notifications` → hiện thông báo |
| `SystemEventReceiver` | nhận `BOOT_COMPLETED`, `TIME_SET`, `TIMEZONE_CHANGED`, `MY_PACKAGE_REPLACED` → lập lại lịch |
| `RescheduleWorker` (WorkManager) | chạy định kỳ 12 giờ, phòng khi hệ thống xóa báo thức |
| `NotificationHelper` | tạo kênh "Nhắc nhở ReNo" và dựng thông báo |
| `SkipDialogActivity` | Activity nhỏ dạng hộp thoại mở từ nút "Bỏ qua" trên thông báo |

Chi tiết cần nhớ:

- **Gọi lại lịch khi:** đăng nhập, mở app (`RenoApp.onCreate`, `MainActivity.onStart`), thêm/sửa/xóa ghi chú
  hoặc từ, bấm Xong/Bỏ qua, đổi giờ nhắc, mốc 00:05 mỗi ngày, và khi nhận sự kiện hệ thống.
- **Các mốc:** (a) mỗi lần xuất hiện của ghi chú chưa có trạng thái, đúng giờ bắt đầu; (b) mỗi giờ nhắc khác
  nhau của từ có lần ôn PENDING; (c) mỗi `remind_at` đã hẹn; (d) mốc 00:05 ngày mai để lập lại.
- **Mã báo thức = `dedupe_key.hashCode()`**; `PendingIntent` dùng `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`
  (Android 12+ bắt buộc có cờ mutability; IMMUTABLE an toàn hơn).
- **Báo thức chính xác:** `setExactAndAllowWhileIdle` nếu `canScheduleExactAlarms()`; không có quyền thì dùng
  `setAndAllowWhileIdle` (có thể trễ vài phút). Quyền `SCHEDULE_EXACT_ALARM` khai báo trong manifest.
- **`ReminderReceiver`:** `goAsync()` rồi đọc CSDL ở luồng nền (`PendingResult.finish()` khi xong), vì `onReceive`
  chạy trên luồng chính và phải trả về nhanh. Trước khi báo, **kiểm tra lại** ghi chú còn tồn tại / chưa DONE, hoặc
  còn từ PENDING; không còn thì không báo.
- **Chống trùng thông báo:** cột `dedupe_key` (vd `NOTE:12:2026-11-13`, `VOCAB:2026-11-13T20:30`) có chỉ mục
  `UNIQUE(user_id, dedupe_key)` và `@Insert(onConflict = IGNORE)`: nếu báo thức chạy hai lần thì lần hai không
  tạo thêm dòng (trả về -1).
- **Gom thông báo từ vựng:** mỗi mốc giờ chỉ **một** thông báo "Có N từ cần ôn tập" (30 từ cùng giờ → 1 thông báo).
  Thông báo ôn từ vựng luôn màu xanh rêu `#2E6F40`, biểu tượng sách, có nút **Bỏ qua** cho cả nhóm. Thông báo ghi chú
  mang màu riêng của ghi chú.
- **Quyền thông báo (Android 13+):** xin sau lần đăng nhập đầu (`MainActivity.askNotificationPermissionOnce`).
  Bị từ chối thì thông báo vẫn được ghi vào màn Thông báo, và màn này hiện dải "Bạn đang tắt thông báo" + nút mở cài đặt.
- **Bấm thông báo khi chưa đăng nhập:** `ui/common/DeepLink` mang đích đến qua màn Đăng nhập; chỉ truyền *dữ liệu*
  (loại, mã, ngày, giờ), Intent thật do app tự dựng lại nên app khác không lợi dụng được.

---

## 5. Câu hỏi cô hay hỏi và cách trả lời

### 5.1 Về Room / cơ sở dữ liệu

**Room là gì?** Thư viện của Android Jetpack, lớp trừu tượng nằm trên SQLite. Có 3 phần: **Entity** (lớp Java ánh xạ
tới một bảng), **DAO** (interface chứa câu truy vấn có chú thích `@Query`, `@Insert`, `@Update`), **Database**
(lớp trừu tượng kế thừa `RoomDatabase`, khai báo danh sách Entity và version). Lợi ích: **kiểm tra câu SQL ngay lúc
biên dịch**, tự sinh mã truy cập, hỗ trợ khóa ngoại và migration.

**Em dùng Room ở đâu?** `data/db/entity` (10 Entity), `data/db/dao` (10 DAO), `data/db/RenoDatabase.java`.
Ví dụ `WordEntity` có `@Entity(tableName = "words", foreignKeys = …, indices = …)`.

**Tại sao không dùng SQLiteOpenHelper?** Vì Room kiểm tra SQL lúc biên dịch và ít code lặp hơn. (Nếu dùng
SQLiteOpenHelper phải tự bật khóa ngoại bằng `setForeignKeyConstraintsEnabled(true)`.)

**Khóa ngoại và CASCADE là gì? Em có bật không?** `ON DELETE CASCADE`: xóa dòng cha thì dòng con tự bị xóa.
Khai báo bằng `@ForeignKey(onDelete = ForeignKey.CASCADE)`. SQLite mặc định tắt khóa ngoại, nhưng **Room tự bật**
`PRAGMA foreign_keys` cho mỗi kết nối. Ví dụ xóa thư mục → xóa topic → xóa đánh dấu.

**Vì sao xóa thư mục vẫn phải tự xóa file PDF?** CASCADE chỉ xóa dòng trong CSDL, không xóa file trên đĩa. Nên
`ReadRepository.deleteFolder` lấy danh sách đường dẫn **trước**, xóa dòng, rồi xóa từng file.

**CHECK là gì? Em cài thế nào?** CHECK là ràng buộc giá trị (vd `type IN ('READ','VOCAB')`, `bg_index` 1–8).
Room không sinh được CHECK từ chú thích nên em cài bằng **trigger** `BEFORE INSERT/UPDATE` gọi `RAISE(ABORT, …)`
(`data/db/DbConstraints.java`); kết quả giống CHECK: dữ liệu sai bị chặn ngay ở tầng CSDL.

**Chỉ mục (index) để làm gì?** Tăng tốc truy vấn và đảm bảo duy nhất (`unique = true`). Ví dụ
`word_reviews(due_date, status)` phục vụ truy vấn "từ cần ôn hôm nay", `app_notifications(user_id, dedupe_key)` duy nhất để chống trùng.

**Kiểu dữ liệu ngày giờ lưu thế nào?** Thời điểm có giờ phút = `INTEGER` epoch mili giây; ngày = `TEXT` `yyyy-MM-dd`
(so sánh chuỗi cho đúng thứ tự ngày); giờ = `TEXT` `HH:mm`; đúng/sai = `INTEGER 0/1`.

**Khi sửa cấu trúc bảng thì sao?** Tăng `version` và viết `Migration`. Em **không dùng**
`fallbackToDestructiveMigration` (nó xóa sạch dữ liệu). `exportSchema` bật nên có `app/schemas/.../1.json`.

**Singleton của `RenoDatabase.get()`?** Tạo CSDL một lần, dùng khóa kép (`volatile` + `synchronized`) để an toàn đa luồng.

**`COLLATE NOCASE` dùng làm gì? Có lỗi gì không?** Để username/tên không phân biệt hoa thường ("Huy" = "huy").
Nhưng NOCASE của SQLite chỉ xử lý chữ ASCII, "Đức" khác "đức". Nên em kiểm tra trùng thêm bằng Java
(`util/TextKeys.java`). Kiểm thử đã bắt được lỗi này.

### 5.2 Về MVVM, LiveData, luồng nền

**ViewModel là gì, để làm gì?** Lớp giữ trạng thái màn hình, sống lâu hơn Activity/Fragment nên **không mất dữ
liệu khi xoay màn hình**. Không được giữ tham chiếu tới View (sẽ rò rỉ bộ nhớ).

**Em dùng `AndroidViewModel` hay `ViewModel`?** Cả hai. `AndroidViewModel` khi cần `Application`/Context
(đa số, vd `NotesViewModel`); `ViewModel` thường cho lớp chỉ giữ dữ liệu nhập dở (`NoteFormViewModel`, `WordFormViewModel`).

**LiveData là gì?** Dữ liệu quan sát được, **biết vòng đời**: chỉ phát cho View đang hoạt động, tự hủy đăng ký khi
View bị hủy nên không rò rỉ. Giao diện gọi `observe(...)`.

**Làm sao danh sách tự cập nhật khi dữ liệu đổi?** Mỗi DAO có một truy vấn `tick()` trả về `LiveData<Integer>`
(đếm dòng của các bảng liên quan). Room tự phát lại LiveData khi bảng đổi. `util/LiveQuery.of(loader, triggers…)`
dùng `MediatorLiveData` lắng nghe các `tick()` đó (và ngày đang chọn…) rồi chạy lại `loader` ở luồng nền và
`postValue` kết quả. Dùng khi dữ liệu phải tính bằng Java (vd các ngày lặp của ghi chú).

**Luồng nền em làm thế nào?** `util/AppExecutors`: `diskIO()` là `newSingleThreadExecutor` (truy vấn CSDL chạy lần lượt,
kết quả về đúng thứ tự), `heavy()` là pool 2 luồng cho việc nặng (băm mật khẩu, chép file), `mainThread()` là
`Handler` của `Looper.getMainLooper()`. Repository nhận một `Callback<T>` và gọi nó **trên luồng chính**.

**Vì sao không chạy truy vấn trên luồng chính?** Sẽ treo giao diện, gây lỗi **ANR**. Room còn tự ném
`IllegalStateException` nếu truy vấn trên luồng chính.

**Repository để làm gì?** Là lớp trung gian gom nhiều DAO, quyết định luồng, xử lý file. ViewModel/Fragment không gọi DAO trực tiếp.

**Event (`util/Event`) là gì?** Bọc giá trị chỉ được xử lý **một lần** (vd kết quả đăng nhập), để khi xoay màn hình
LiveData phát lại thì không báo lỗi hay điều hướng lại lần nữa.

### 5.3 Về Activity, Fragment, Intent…

**Activity khác Fragment?** Activity là một màn hình có vòng đời `onCreate → onStart → onResume → onPause →
onStop → onDestroy`. Fragment là một phần giao diện có vòng đời riêng, gắn vào Activity. Em dùng Fragment cho các
mục trong `MainActivity` (Nhà, Thống kê…) và các tab con; dùng Activity cho màn mở sâu (topic, form, flashcard…).

**Intent là gì? Em dùng ở đâu?** Thông điệp để mở màn hình, gọi app khác hoặc gửi sự kiện. Intent tường minh mở
màn của app (`NoteDetailActivity.intent(...)`), `Intent` ngầm cho app máy ảnh/bộ chọn file, và `PendingIntent` cho
thông báo và báo thức.

**`registerForActivityResult` là gì?** API mới thay `startActivityForResult`: đăng ký "bộ khởi chạy" nhận kết quả
(chọn file PDF, chụp ảnh, cắt ảnh uCrop, xin quyền). Ví dụ `ReadFragment.openPdf`, `ProfileActivity.cropLauncher`.

**`BroadcastReceiver` là gì?** Thành phần nhận sự kiện hệ thống hoặc của app. Em có `ReminderReceiver` (báo thức) và
`SystemEventReceiver` (khởi động lại máy, đổi giờ). Khai báo trong `AndroidManifest.xml`.

**Vì sao cần `SystemEventReceiver`?** Báo thức **bị mất khi khởi động lại máy** và lệch khi đổi giờ/múi giờ, nên phải
đặt lại.

**Service / WorkManager?** Em không tự viết Service; việc chạy nền định kỳ dùng **WorkManager**
(`RescheduleWorker`) — được hệ thống đảm bảo chạy kể cả khi tắt app/khởi động lại.

**`PendingIntent` là gì?** Một Intent "ủy quyền" cho hệ thống (thông báo, báo thức) chạy sau thay cho app. Em dùng
`FLAG_IMMUTABLE` để app khác không sửa được nội dung.

**ContentProvider?** Em dùng `FileProvider` (một loại ContentProvider) để app máy ảnh ghi ảnh vào thư mục tạm của
mình mà không lộ đường dẫn thật. Cấu hình ở `res/xml/file_paths.xml`.

**ViewBinding khác `findViewById`?** ViewBinding sinh lớp theo tên layout (`activity_login.xml` →
`ActivityLoginBinding`), truy cập view an toàn kiểu và không bị null nhầm. Em bật trong `build.gradle`.

**Quyền nào em khai báo? Quyền CAMERA thì sao?** `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
`RECEIVE_BOOT_COMPLETED`, `INTERNET` (tải gói dịch). **Không khai báo CAMERA** vì chỉ gọi app máy ảnh hệ thống
qua FileProvider; nếu khai báo mà không xin quyền thì app sẽ crash.

**SharedPreferences dùng để làm gì?** Lưu giá trị nhỏ dạng khóa–giá trị: `current_user_id`, kiểu sắp xếp ý tưởng,
thư mục từ vựng dùng lần trước, lịch đang ẩn (`util/SessionManager.java`).

### 5.4 Về danh sách và giao diện

**Em có dùng ListView, Adapter không?** Em dùng **RecyclerView + `RecyclerView.Adapter` + `ViewHolder`** (không dùng ListView).
Có 8 adapter: `NoteAdapter` (`NotesFragment`), `IdeaAdapter` (`IdeaFragment`), `StatsAdapter` (`StatsFragment`),
`RowAdapter` (`NotificationFragment`), `AnnotationAdapter` (`TopicActivity`), `TopicAdapter` (`ReadFragment`),
`WordAdapter` (`VocabFragment`) và lớp cha dùng chung `FolderListAdapter` (`ui/common`).

**Adapter và ViewHolder làm gì?** Adapter nối dữ liệu với danh sách: `onCreateViewHolder` (tạo view từ layout),
`onBindViewHolder` (đổ dữ liệu vào view), `getItemCount`. ViewHolder giữ tham chiếu view để **tái sử dụng**, tránh
tìm view lặp lại.

**Danh sách thư mục mở/đóng làm sao?** `FolderListAdapter<T>` có 3 loại dòng (`getItemViewType`): thư mục, phần tử
bên trong, và dòng "Thư mục trống". Bấm dòng thư mục thì thêm/bớt các phần tử vào danh sách `rows` rồi `notifyDataSetChanged`;
mũi tên xoay 180° bằng `setRotation`. Dùng chung cho Read (topic) và Vocabulary (từ).

**Layout em dùng loại nào?** `LinearLayout`, `FrameLayout`, `CoordinatorLayout` (màn Lịch), `ScrollView`/`NestedScrollView`.
Các thẻ "Gần đây" chia đều bằng `layout_weight`. Thư viện `ConstraintLayout` có khai báo trong `build.gradle` nhưng
các layout hiện tại chưa dùng tới; nếu cô hỏi thì nói thẳng như vậy (báo cáo mục 1.5.2 có nhắc ConstraintLayout là ví dụ
các loại layout, nên chuẩn bị câu trả lời cho khớp).

**Màu, chữ, kích thước có hard-code không?** Không: màu trong `res/values/colors.xml`, chuỗi trong `strings.xml`,
kích thước trong `dimens.xml`, kiểu trong `themes.xml`. Bảng màu theo tỉ lệ **60–30–10** (nền `#FAF9F6`, chữ `#2F3437`,
nhấn xanh rêu `#2E6F40`).

**Dialog, DatePicker, TimePicker?** Hộp thoại xác nhận/nhập liệu ở `ui/common/Dialogs.java`
(`MaterialAlertDialogBuilder`); chọn ngày/giờ ở `ui/common/Pickers.java` (`MaterialDatePicker`, `MaterialTimePicker`).

**Custom View là gì? Em có tự vẽ gì?** Em tự viết `TimetableView` (thời khóa biểu), `SquareFrameLayout` (khung 1:1),
`ColorPaletteView` (hàng ô màu), `SelectableWebView`. Tự vẽ bằng `Canvas` trong `onDraw`.

**Hoạt ảnh em dùng gì?** `ObjectAnimator` cho lật thẻ; CSS animation cho nháy sáng đánh dấu; `setRotation` cho mũi tên.

### 5.5 Về PDF.js, WebView, thư viện ngoài

**Vì sao dùng PDF.js trong WebView mà không dùng PdfRenderer/Jetpack PDF?** PdfRenderer chỉ vẽ ảnh trang, **không có lớp
chữ để bôi đen**. Jetpack PDF còn beta và ghi highlight thẳng vào file PDF, không có sẵn loại `?`, `!`, ★. PDF.js cho
toàn quyền: menu khi bôi đen, lớp tô màu, cuộn tới đúng chỗ.

**WebView an toàn thế nào?** Chỉ nạp nội dung của app qua `WebViewAssetLoader`; tắt `setAllowFileAccess`; chặn địa chỉ
ngoài; `@JavascriptInterface` chỉ nhận dữ liệu; xử lý `onRenderProcessGone` để không crash khi thiếu bộ nhớ.

**`@JavascriptInterface` chạy ở luồng nào?** Luồng riêng của WebView, nên `PdfBridge` chuyển về luồng chính bằng `Handler`.

**Vì sao toạ độ lưu theo tỉ lệ 0–1?** Để vùng đánh dấu vẫn đúng khi phóng to/thu nhỏ hoặc đổi kích thước màn.

**uCrop dùng làm gì? Cấu hình thế nào?** Cắt/phóng/kéo ảnh trong khung tròn: `UCrop.of(src, dst).withAspectRatio(1,1)
.withMaxResultSize(512,512)` với `Options.setCircleDimmedLayer(true)`, JPEG 85%.

**ML Kit chạy ở đâu?** Trên thiết bị (OCR bản Latin đóng gói sẵn; dịch cần tải gói ngôn ngữ một lần). Không gửi dữ
liệu người dùng lên máy chủ.

**lib-recur khác tự viết?** Là bộ xử lý quy luật lặp đúng chuẩn iCalendar, tránh tự viết bộ phân tích RRULE dễ sai.

**RRULE/RDATE/EXDATE là gì?** Ba thuộc tính của RFC 5545: RRULE = quy luật lặp (`FREQ=DAILY;INTERVAL=3` = mỗi 3 ngày),
RDATE = ngày thêm vào, EXDATE = ngày loại ra.

**PBKDF2 là gì? Vì sao cần salt và nhiều vòng lặp?** Hàm dẫn xuất khóa/băm mật khẩu. **Salt** ngẫu nhiên khiến hai người
cùng mật khẩu vẫn ra hai chuỗi băm khác nhau; **nhiều vòng lặp** làm dò mật khẩu bằng vét cạn rất chậm.

### 5.6 "Cái này nằm ở chỗ nào trong code?" (tra nhanh)

| Chức năng | File chính |
| --- | --- |
| Đăng ký / đăng nhập | `ui/auth/*`, `data/repository/UserRepository.java`, `domain/Validators.java`, `PasswordHasher.java` |
| Phiên đăng nhập | `util/SessionManager.java` |
| Màn chính, menu "…", đăng xuất | `ui/main/MainActivity.java` |
| 4 tab của Nhà | `ui/main/HomeFragment.java` |
| Thư mục/topic PDF | `ui/read/ReadFragment.java`, `data/repository/ReadRepository.java` |
| Đọc PDF, đánh dấu | `ui/read/TopicActivity.java`, `SelectableWebView.java`, `PdfBridge.java`, `assets/pdfjs/reader.js` |
| Ghi chú, ngày lặp | `ui/notes/*`, `domain/RecurrenceHelper.java`, `data/repository/NoteRepository.java` |
| Idea | `ui/idea/*`, `data/repository/IdeaRepository.java` |
| Từ vựng, flashcard, ôn tập | `ui/vocab/*`, `domain/ReviewPlanner.java`, `data/repository/VocabRepository.java` |
| Hộp thoại Bỏ qua | `reminder/SkipDialogActivity.java` |
| Thống kê | `ui/stats/*` |
| Máy ảnh OCR + dịch | `ui/camera/*` |
| Lịch, thời khóa biểu | `ui/calendar/*` (`TimetableView.java`) |
| Thông báo (màn hình) | `ui/notification/*`, `data/repository/NotificationRepository.java` |
| Đặt báo thức, nhắc nhở | `reminder/*` |
| Khai báo bảng | `data/db/entity/*`, `data/db/RenoDatabase.java`, `DbConstraints.java` |
| Câu truy vấn | `data/db/dao/*` |
| Quyền, Activity, Receiver | `app/src/main/AndroidManifest.xml` |
| Thư viện phụ thuộc | `app/build.gradle`, `gradle/libs.versions.toml` |
| Màu, chuỗi, kích thước | `res/values/colors.xml`, `strings.xml`, `dimens.xml`, `themes.xml` |

---

## 6. Kịch bản demo gợi ý (khoảng 5 phút)

1. Đăng ký → thử lỗi (mật khẩu nhập lại không khớp) → đăng ký thành công → đăng nhập.
2. **Read:** tạo thư mục → thêm một PDF → mở → bôi đen → chọn Highlight → mở tab Thống kê → bấm "Nhấn để di chuyển…".
3. **Notes:** thêm ghi chú "Cách 3 ngày", bỏ một ngày → xem ngày xuất hiện → tích hoàn thành.
4. **Vocabulary:** thêm từ (thấy 5 ngày ôn) → mở flashcard, lật thẻ → mở "Hôm nay cần ôn" → thử nút Bỏ qua và 3 lựa chọn.
5. **Máy ảnh:** chọn ảnh có chữ → bôi đen một từ → Dịch → "+".
6. **Lịch:** kéo thời khóa biểu, mở My calendars. **Thông báo:** xem danh sách và nút Bỏ qua cả nhóm.

---

## 7. Điểm cần trả lời trung thực

- Ứng dụng **chưa được thử trên máy ảo/thiết bị thật trong lúc phát triển** ở môi trường này; phần logic và các màn hình
  được kiểm thử tự động bằng Robolectric (45 kiểm thử), còn đọc PDF thật, ML Kit, thông báo đúng giờ nên tự chạy thử
  trên máy và ghi kết quả vào bảng kiểm thử ở báo cáo (TC01–TC23).
- Tính năng **để sau** (nói rõ là chưa làm, đúng đặc tả): tóm tắt PDF bằng AI, quên mật khẩu, đăng nhập Google, đồng bộ
  nhiều máy. Tab Tóm tắt chỉ hiện dòng "đang phát triển".
- Hạn chế: PDF dạng ảnh scan không đánh dấu được; bản dịch trên máy phù hợp từ/cụm từ ngắn; không có quyền báo thức
  chính xác thì thông báo có thể trễ vài phút; dữ liệu chỉ nằm trên một máy (chưa sao lưu).
- Nếu cô hỏi điều em chưa chắc, nói "em xin phép mở code để chỉ cho cô" rồi mở đúng file ở bảng 5.6, đọc phần chú thích
  đầu hàm — hầu hết hàm quan trọng đều có chú thích tiếng Việt giải thích mục đích.

**Cách tự luyện:** với mỗi chức năng ở phần 4, mở file đầu tiên trong bảng 5.6, đi theo chuỗi
`Fragment/Activity → ViewModel → Repository → DAO` và tự kể lại bằng lời của mình. Kể được chuỗi đó là trả lời được
hầu hết câu hỏi "chỗ này em dùng gì".
