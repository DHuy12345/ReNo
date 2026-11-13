package com.example.project_ck.data.repository

import com.example.project_ck.data.db.AppDatabase
import com.example.project_ck.data.entity.*
import com.example.project_ck.data.dao.WordReviewWithWord
import com.example.project_ck.util.RecurrenceUtils
import com.example.project_ck.util.SecurityUtils
import com.example.project_ck.util.SpacedRepetitionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class AppRepository(private val db: AppDatabase) {

    // --- USER ---
    suspend fun registerUser(username: String, phoneOrEmail: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        try {
            val isEmail = phoneOrEmail.contains("@")
            val email = if (isEmail) phoneOrEmail.lowercase() else null
            val phone = if (!isEmail) {
                var p = phoneOrEmail.trim()
                if (p.startsWith("+84")) p = "0" + p.substring(3)
                p
            } else null

            // Check existing
            if (db.userDao().getUserByUsername(username) != null) {
                return@withContext Result.failure(Exception("Username này đã có người dùng"))
            }
            if (email != null && db.userDao().getUserByEmail(email) != null) {
                return@withContext Result.failure(Exception("Email/số điện thoại này đã được dùng cho tài khoản khác"))
            }
            if (phone != null && db.userDao().getUserByPhone(phone) != null) {
                return@withContext Result.failure(Exception("Email/số điện thoại này đã được dùng cho tài khoản khác"))
            }

            val hash = SecurityUtils.hashPassword(password)
            val now = System.currentTimeMillis()
            val nowTimeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            val user = User(
                username = username,
                email = email,
                phone = phone,
                password_hash = hash,
                review_time = nowTimeStr,
                created_at = now,
                updated_at = now
            )
            val userId = db.userDao().insertUser(user)
            Result.success(user.copy(id = userId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(username: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        val user = db.userDao().getUserByUsername(username)
            ?: return@withContext Result.failure(Exception("Sai username hoặc mật khẩu"))

        if (SecurityUtils.verifyPassword(password, user.password_hash)) {
            Result.success(user)
        } else {
            Result.failure(Exception("Sai username hoặc mật khẩu"))
        }
    }

    fun getUserFlow(userId: Long): Flow<User?> = db.userDao().getUserFlow(userId)

    suspend fun updateUser(user: User): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.userDao().updateUser(user.copy(updated_at = System.currentTimeMillis()))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- FOLDERS ---
    fun getFolders(userId: Long, type: String): Flow<List<Folder>> =
        db.folderDao().getFoldersByUserAndType(userId, type)

    suspend fun addFolder(userId: Long, type: String, name: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val existing = db.folderDao().getFolderByName(userId, type, name)
            if (existing != null) return@withContext Result.failure(Exception("Thư mục này đã tồn tại"))
            val folder = Folder(user_id = userId, type = type, name = name)
            val id = db.folderDao().insertFolder(folder)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFolder(folder: Folder): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.folderDao().updateFolder(folder)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(folder: Folder): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            db.folderDao().deleteFolder(folder)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- TOPICS ---
    fun getTopicsByFolder(folderId: Long): Flow<List<Topic>> = db.topicDao().getTopicsByFolder(folderId)

    fun getRecentTopics(userId: Long, limit: Int = 3): Flow<List<Topic>> =
        db.topicDao().getRecentTopics(userId, limit)

    suspend fun getTopicById(id: Long): Topic? = withContext(Dispatchers.IO) { db.topicDao().getTopicById(id) }

    suspend fun addTopic(topic: Topic): Long = withContext(Dispatchers.IO) { db.topicDao().insertTopic(topic) }

    suspend fun updateTopic(topic: Topic) = withContext(Dispatchers.IO) { db.topicDao().updateTopic(topic) }

    suspend fun deleteTopic(topic: Topic) = withContext(Dispatchers.IO) {
        // Delete PDF file if exists
        try {
            val file = File(topic.file_path)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
        db.topicDao().deleteTopic(topic)
    }

    // --- ANNOTATIONS ---
    fun getAnnotationsByTopic(topicId: Long): Flow<List<PdfAnnotation>> =
        db.annotationDao().getAnnotationsByTopic(topicId)

    fun getAnnotationsByTopicAndType(topicId: Long, type: String): Flow<List<PdfAnnotation>> =
        db.annotationDao().getAnnotationsByTopicAndType(topicId, type)

    fun getAnnotationCounts(topicId: Long) = db.annotationDao().getAnnotationCounts(topicId)

    suspend fun addAnnotation(annotation: PdfAnnotation): Long =
        withContext(Dispatchers.IO) { db.annotationDao().insertAnnotation(annotation) }

    suspend fun deleteAnnotation(annotation: PdfAnnotation) =
        withContext(Dispatchers.IO) { db.annotationDao().deleteAnnotation(annotation) }

    // --- NOTES ---
    fun getNotesByUser(userId: Long): Flow<List<Note>> = db.noteDao().getNotesByUser(userId)

    suspend fun getNoteById(id: Long): Note? = withContext(Dispatchers.IO) { db.noteDao().getNoteById(id) }

    suspend fun addNote(note: Note): Long = withContext(Dispatchers.IO) { db.noteDao().insertNote(note) }

    suspend fun updateNote(note: Note) = withContext(Dispatchers.IO) { db.noteDao().updateNote(note) }

    suspend fun deleteNote(note: Note) = withContext(Dispatchers.IO) { db.noteDao().deleteNote(note) }

    // Note Occurrences
    fun getNoteOccurrencesByRange(userId: Long, fromDate: String, toDate: String): Flow<List<NoteOccurrence>> =
        db.noteOccurrenceDao().getOccurrencesByUserAndRange(userId, fromDate, toDate)

    suspend fun toggleNoteOccurrence(noteId: Long, occDate: String, isDone: Boolean) = withContext(Dispatchers.IO) {
        if (isDone) {
            db.noteOccurrenceDao().insertOrReplaceOccurrence(
                NoteOccurrence(note_id = noteId, occ_date = occDate, status = "DONE")
            )
        } else {
            db.noteOccurrenceDao().deleteOccurrence(noteId, occDate)
        }
    }

    suspend fun setNoteOccurrenceStatus(noteId: Long, occDate: String, status: String) = withContext(Dispatchers.IO) {
        db.noteOccurrenceDao().insertOrReplaceOccurrence(
            NoteOccurrence(note_id = noteId, occ_date = occDate, status = status)
        )
    }

    // --- IDEAS ---
    fun getIdeasByUser(userId: Long, isDone: Int, isAscending: Boolean): Flow<List<Idea>> =
        db.ideaDao().getIdeasByUser(userId, isDone, isAscending)

    suspend fun addIdea(idea: Idea): Long = withContext(Dispatchers.IO) { db.ideaDao().insertIdea(idea) }

    suspend fun updateIdea(idea: Idea) = withContext(Dispatchers.IO) { db.ideaDao().updateIdea(idea) }

    suspend fun deleteIdea(idea: Idea) = withContext(Dispatchers.IO) { db.ideaDao().deleteIdea(idea) }

    suspend fun toggleIdeaStatus(idea: Idea) = withContext(Dispatchers.IO) {
        val newDone = if (idea.is_done == 1) 0 else 1
        val doneAt = if (newDone == 1) System.currentTimeMillis() else null
        db.ideaDao().updateIdea(idea.copy(is_done = newDone, done_at = doneAt, updated_at = System.currentTimeMillis()))
    }

    // --- WORDS & VOCABULARY ---
    fun getWordsByFolder(folderId: Long): Flow<List<Word>> = db.wordDao().getWordsByFolder(folderId)

    suspend fun addWord(folderId: Long, wordText: String, meaningText: String, remindTime: String?, createdDateStr: String): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val existing = db.wordDao().getWordByFolderAndWord(folderId, wordText)
            if (existing != null) return@withContext Result.failure(Exception("Từ này đã có trong thư mục"))

            val word = Word(
                folder_id = folderId,
                word = wordText,
                meaning = meaningText,
                remind_time = remindTime,
                created_date = createdDateStr
            )
            val wordId = db.wordDao().insertWord(word)

            // Generate initial 5 reviews
            val reviews = SpacedRepetitionUtils.generateInitialReviews(wordId, createdDateStr)
            db.wordReviewDao().insertReviews(reviews)

            Result.success(wordId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateWord(word: Word) = withContext(Dispatchers.IO) { db.wordDao().updateWord(word) }

    suspend fun deleteWord(word: Word) = withContext(Dispatchers.IO) { db.wordDao().deleteWord(word) }

    fun getReviewsForUserByDate(userId: Long, date: String): Flow<List<WordReviewWithWord>> =
        db.wordReviewDao().getReviewsForUserByDate(userId, date)

    fun getMissedReviews(userId: Long, today: String, thirtyDaysAgo: String): Flow<List<WordReviewWithWord>> =
        db.wordReviewDao().getMissedReviews(userId, today, thirtyDaysAgo)

    fun getUpcomingReviews(userId: Long, today: String, sevenDaysLater: String): Flow<List<WordReviewWithWord>> =
        db.wordReviewDao().getUpcomingReviews(userId, today, sevenDaysLater)

    fun getCompletedReviews(userId: Long, thirtyDaysAgo: String): Flow<List<WordReviewWithWord>> =
        db.wordReviewDao().getCompletedReviews(userId, thirtyDaysAgo)

    suspend fun updateWordReview(review: WordReview) = withContext(Dispatchers.IO) {
        db.wordReviewDao().updateReview(review)
    }

    // --- APP NOTIFICATIONS ---
    fun getNotificationsByUser(userId: Long): Flow<List<AppNotification>> =
        db.appNotificationDao().getNotificationsByUser(userId)

    fun getUnreadNotificationCount(userId: Long): Flow<Int> =
        db.appNotificationDao().getUnreadCount(userId)

    suspend fun markNotificationAsRead(id: Long) = withContext(Dispatchers.IO) {
        db.appNotificationDao().markAsRead(id)
    }

    suspend fun deleteNotification(id: Long) = withContext(Dispatchers.IO) {
        db.appNotificationDao().deleteNotification(id)
    }
}