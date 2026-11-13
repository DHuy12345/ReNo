package com.example.project_ck.viewmodel

import android.app.Application
import android.net.Uri
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.project_ck.ReNoApplication
import com.example.project_ck.data.SessionManager
import com.example.project_ck.data.dao.WordReviewWithWord
import com.example.project_ck.data.entity.*
import com.example.project_ck.data.repository.AppRepository
import com.example.project_ck.util.RecurrenceUtils
import com.example.project_ck.util.ReminderScheduler
import com.example.project_ck.util.SpacedRepetitionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppRepository((application as ReNoApplication).database)
    private val sessionManager = SessionManager(application)
    val userId: Long get() = sessionManager.currentUserId

    // --- READ TAB ---
    val readFolders: Flow<List<Folder>> = repo.getFolders(userId, "READ")
    val recentTopics: Flow<List<Topic>> = repo.getRecentTopics(userId, 3)

    fun addReadFolder(name: String, onResult: (Boolean, String?) -> Unit) {
        if (name.isBlank()) {
            onResult(false, "Tên thư mục không được để trống")
            return
        }
        viewModelScope.launch {
            val res = repo.addFolder(userId, "READ", name.trim())
            res.onSuccess { onResult(true, null) }
                .onFailure { err -> onResult(false, err.message ?: "Tạo thư mục thất bại") }
        }
    }

    fun updateFolder(folder: Folder) {
        viewModelScope.launch { repo.updateFolder(folder) }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch { repo.deleteFolder(folder) }
    }

    fun getTopicsForFolder(folderId: Long): Flow<List<Topic>> = repo.getTopicsByFolder(folderId)

    fun addTopicFromPdf(folderId: Long, pdfUri: Uri, topicName: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val pdfDir = File(context.filesDir, "pdfs")
                if (!pdfDir.exists()) pdfDir.mkdirs()

                val uuid = UUID.randomUUID().toString()
                val destFile = File(pdfDir, "$uuid.pdf")

                context.contentResolver.openInputStream(pdfUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: run {
                    onResult(false, "Không thể mở file PDF")
                    return@launch
                }

                // Get page count via PdfRenderer
                var pageCount = 0
                try {
                    val pfd = ParcelFileDescriptor.open(destFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(pfd)
                    pageCount = renderer.pageCount
                    renderer.close()
                    pfd.close()
                } catch (e: Exception) {
                    destFile.delete()
                    onResult(false, "Không mở được file PDF này")
                    return@launch
                }

                val bgIndex = Random.nextInt(1, 9) // 1..8
                val topic = Topic(
                    folder_id = folderId,
                    name = topicName.ifBlank { "Topic" },
                    file_path = destFile.absolutePath,
                    page_count = pageCount,
                    bg_index = bgIndex,
                    last_opened_at = System.currentTimeMillis(),
                    created_at = System.currentTimeMillis()
                )
                repo.addTopic(topic)
                onResult(true, null)
            } catch (e: Exception) {
                onResult(false, e.message ?: "Thêm PDF thất bại")
            }
        }
    }

    fun updateTopic(topic: Topic) {
        viewModelScope.launch { repo.updateTopic(topic) }
    }

    fun deleteTopic(topic: Topic) {
        viewModelScope.launch { repo.deleteTopic(topic) }
    }

    // --- ANNOTATIONS ---
    fun getAnnotationsByTopic(topicId: Long): Flow<List<PdfAnnotation>> = repo.getAnnotationsByTopic(topicId)
    fun addAnnotation(annotation: PdfAnnotation) { viewModelScope.launch { repo.addAnnotation(annotation) } }
    fun deleteAnnotation(annotation: PdfAnnotation) { viewModelScope.launch { repo.deleteAnnotation(annotation) } }

    // --- NOTES TAB ---
    private val _selectedNoteDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedNoteDate: StateFlow<LocalDate> = _selectedNoteDate.asStateFlow()

    fun setSelectedNoteDate(date: LocalDate) {
        _selectedNoteDate.value = date
    }

    val notesForUser: Flow<List<Note>> = repo.getNotesByUser(userId)
    val occurrencesForUser: Flow<List<NoteOccurrence>> = repo.getNoteOccurrencesByRange(
        userId,
        LocalDate.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
        LocalDate.now().plusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    )

    fun addNote(
        title: String,
        color: String,
        dtstartMs: Long,
        durationMin: Int,
        repeatType: String,
        rrule: String?,
        repeatUntil: String?,
        exdates: List<String>?,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (title.isBlank()) {
            onResult(false, "Tên ghi chú không được để trống")
            return
        }
        viewModelScope.launch {
            val exdateStr = exdates?.joinToString(",")
            val note = Note(
                user_id = userId,
                title = title.trim(),
                color = color,
                dtstart = dtstartMs,
                duration_min = durationMin,
                repeat_type = repeatType,
                rrule = rrule,
                repeat_until = repeatUntil,
                exdate = exdateStr
            )
            repo.addNote(note)
            ReminderScheduler.rescheduleAll(getApplication(), userId)
            onResult(true, null)
        }
    }

    fun toggleNoteOccurrence(noteId: Long, occDateStr: String, isDone: Boolean) {
        viewModelScope.launch {
            repo.toggleNoteOccurrence(noteId, occDateStr, isDone)
            ReminderScheduler.rescheduleAll(getApplication(), userId)
        }
    }

    fun setNoteOccurrenceStatus(noteId: Long, occDateStr: String, status: String) {
        viewModelScope.launch {
            repo.setNoteOccurrenceStatus(noteId, occDateStr, status)
            ReminderScheduler.rescheduleAll(getApplication(), userId)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repo.deleteNote(note)
            ReminderScheduler.rescheduleAll(getApplication(), userId)
        }
    }

    // --- IDEA TAB ---
    private val _ideaSortAscending = MutableStateFlow<Boolean>(sessionManager.ideaSortAscending)
    val ideaSortAscending: StateFlow<Boolean> = _ideaSortAscending.asStateFlow()

    fun setIdeaSortAscending(asc: Boolean) {
        _ideaSortAscending.value = asc
        sessionManager.ideaSortAscending = asc
    }

    val pendingIdeas: Flow<List<Idea>> = ideaSortAscending.flatMapLatest { asc ->
        repo.getIdeasByUser(userId, 0, asc)
    }

    val doneIdeas: Flow<List<Idea>> = ideaSortAscending.flatMapLatest { asc ->
        repo.getIdeasByUser(userId, 1, asc)
    }

    fun addIdea(content: String, onResult: (Boolean, String?) -> Unit) {
        if (content.isBlank()) {
            onResult(false, "Nội dung ý tưởng không được để trống")
            return
        }
        viewModelScope.launch {
            val idea = Idea(user_id = userId, content = content.trim())
            repo.addIdea(idea)
            onResult(true, null)
        }
    }

    fun updateIdea(idea: Idea) {
        viewModelScope.launch { repo.updateIdea(idea) }
    }

    fun toggleIdeaStatus(idea: Idea) {
        viewModelScope.launch { repo.toggleIdeaStatus(idea) }
    }

    fun deleteIdea(idea: Idea) {
        viewModelScope.launch { repo.deleteIdea(idea) }
    }

    // --- VOCABULARY TAB ---
    val vocabFolders: Flow<List<Folder>> = repo.getFolders(userId, "VOCAB")

    fun addVocabFolder(name: String, onResult: (Boolean, String?) -> Unit) {
        if (name.isBlank()) {
            onResult(false, "Tên thư mục không được để trống")
            return
        }
        viewModelScope.launch {
            val res = repo.addFolder(userId, "VOCAB", name.trim())
            res.onSuccess { onResult(true, null) }
                .onFailure { err -> onResult(false, err.message ?: "Tạo thư mục thất bại") }
        }
    }

    fun getWordsForFolder(folderId: Long): Flow<List<Word>> = repo.getWordsByFolder(folderId)

    fun addWord(
        folderId: Long,
        wordText: String,
        meaningText: String,
        remindTime: String?,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (wordText.isBlank() || meaningText.isBlank()) {
            onResult(false, "Vui lòng nhập từ và nghĩa")
            return
        }
        val todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        viewModelScope.launch {
            val res = repo.addWord(folderId, wordText.trim(), meaningText.trim(), remindTime, todayStr)
            res.onSuccess {
                ReminderScheduler.rescheduleAll(getApplication(), userId)
                val tomorrowStr = LocalDate.now().plusDays(1).format(DateTimeFormatter.ofPattern("dd/MM"))
                onResult(true, "Đã thêm. Lần ôn đầu tiên: $tomorrowStr")
            }.onFailure { err ->
                onResult(false, err.message ?: "Thêm từ thất bại")
            }
        }
    }

    fun updateWord(word: Word) {
        viewModelScope.launch { repo.updateWord(word) }
    }

    fun deleteWord(word: Word) {
        viewModelScope.launch {
            repo.deleteWord(word)
            ReminderScheduler.rescheduleAll(getApplication(), userId)
        }
    }

    fun updateReviewStatus(review: WordReview, newStatus: String) {
        viewModelScope.launch {
            val updated = review.copy(
                status = newStatus,
                done_at = if (newStatus == "DONE") System.currentTimeMillis() else null
            )
            repo.updateWordReview(updated)
            ReminderScheduler.rescheduleAll(getApplication(), userId)
        }
    }

    fun snoozeReviewToNextStage(review: WordReview) {
        viewModelScope.launch {
            val updated = review.copy(status = "SKIPPED")
            repo.updateWordReview(updated)
            ReminderScheduler.rescheduleAll(getApplication(), userId)
        }
    }

    fun scheduleReviewToDate(review: WordReview, newDateStr: String, newRemindTimeMs: Long) {
        viewModelScope.launch {
            val updated = review.copy(
                due_date = newDateStr,
                remind_at = newRemindTimeMs,
                status = "PENDING"
            )
            repo.updateWordReview(updated)
            ReminderScheduler.rescheduleAll(getApplication(), userId)
        }
    }

    fun postponeReviewNDays(review: WordReview, days: Int) {
        viewModelScope.launch {
            val curDate = try {
                LocalDate.parse(review.due_date, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            } catch (e: Exception) {
                LocalDate.now()
            }
            val newDateStr = curDate.plusDays(days.toLong()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val updated = review.copy(due_date = newDateStr, status = "PENDING")
            repo.updateWordReview(updated)
            ReminderScheduler.rescheduleAll(getApplication(), userId)
        }
    }

    fun getReviewsForDate(dateStr: String): Flow<List<WordReviewWithWord>> =
        repo.getReviewsForUserByDate(userId, dateStr)
}