package com.example.project_ck.data.dao

import androidx.room.*
import com.example.project_ck.data.entity.*
import kotlinx.coroutines.flow.Flow

data class AnnotationCount(
    val type: String,
    val count: Int
)

data class WordReviewWithWord(
    @Embedded val review: WordReview,
    @Relation(
        parentColumn = "word_id",
        entityColumn = "id"
    )
    val word: Word
)

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Long): User?

    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserFlow(id: Long): Flow<User?>

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhone(phone: String): User?
}

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("SELECT * FROM folders WHERE user_id = :userId AND type = :type ORDER BY LOWER(name) ASC")
    fun getFoldersByUserAndType(userId: Long, type: String): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE user_id = :userId AND type = :type AND LOWER(name) = LOWER(:name) LIMIT 1")
    suspend fun getFolderByName(userId: Long, type: String, name: String): Folder?

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): Folder?
}

@Dao
interface TopicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: Topic): Long

    @Update
    suspend fun updateTopic(topic: Topic)

    @Delete
    suspend fun deleteTopic(topic: Topic)

    @Query("SELECT * FROM topics WHERE folder_id = :folderId ORDER BY created_at DESC")
    fun getTopicsByFolder(folderId: Long): Flow<List<Topic>>

    @Query("SELECT t.* FROM topics t JOIN folders f ON f.id = t.folder_id WHERE f.user_id = :userId ORDER BY t.last_opened_at DESC LIMIT :limit")
    fun getRecentTopics(userId: Long, limit: Int = 3): Flow<List<Topic>>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun getTopicById(id: Long): Topic?
}

@Dao
interface AnnotationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: PdfAnnotation): Long

    @Update
    suspend fun updateAnnotation(annotation: PdfAnnotation)

    @Delete
    suspend fun deleteAnnotation(annotation: PdfAnnotation)

    @Query("SELECT * FROM annotations WHERE topic_id = :topicId ORDER BY page_index ASC, created_at ASC")
    fun getAnnotationsByTopic(topicId: Long): Flow<List<PdfAnnotation>>

    @Query("SELECT * FROM annotations WHERE topic_id = :topicId AND type = :type ORDER BY page_index ASC, created_at ASC")
    fun getAnnotationsByTopicAndType(topicId: Long, type: String): Flow<List<PdfAnnotation>>

    @Query("SELECT type, COUNT(*) as count FROM annotations WHERE topic_id = :topicId GROUP BY type")
    fun getAnnotationCounts(topicId: Long): Flow<List<AnnotationCount>>
}

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("SELECT * FROM notes WHERE user_id = :userId ORDER BY dtstart ASC")
    fun getNotesByUser(userId: Long): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE user_id = :userId ORDER BY dtstart ASC")
    suspend fun getNotesByUserSync(userId: Long): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNoteById(id: Long): Note?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun getNoteByIdFlow(id: Long): Flow<Note?>
}

@Dao
interface NoteOccurrenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceOccurrence(occ: NoteOccurrence)

    @Query("DELETE FROM note_occurrences WHERE note_id = :noteId AND occ_date = :occDate")
    suspend fun deleteOccurrence(noteId: Long, occDate: String)

    @Query("SELECT * FROM note_occurrences WHERE note_id = :noteId")
    fun getOccurrencesByNote(noteId: Long): Flow<List<NoteOccurrence>>

    @Query("SELECT occ.* FROM note_occurrences occ JOIN notes n ON n.id = occ.note_id WHERE n.user_id = :userId AND occ.occ_date BETWEEN :fromDate AND :toDate")
    fun getOccurrencesByUserAndRange(userId: Long, fromDate: String, toDate: String): Flow<List<NoteOccurrence>>

    @Query("SELECT occ.* FROM note_occurrences occ JOIN notes n ON n.id = occ.note_id WHERE n.user_id = :userId")
    suspend fun getOccurrencesByUserSync(userId: Long): List<NoteOccurrence>

    @Query("SELECT * FROM note_occurrences WHERE note_id = :noteId AND occ_date = :occDate LIMIT 1")
    suspend fun getOccurrence(noteId: Long, occDate: String): NoteOccurrence?
}

@Dao
interface IdeaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdea(idea: Idea): Long

    @Update
    suspend fun updateIdea(idea: Idea)

    @Delete
    suspend fun deleteIdea(idea: Idea)

    @Query("SELECT * FROM ideas WHERE user_id = :userId AND is_done = :isDone ORDER BY CASE WHEN :isAsc = 1 THEN created_at END ASC, CASE WHEN :isAsc = 0 THEN created_at END DESC")
    fun getIdeasByUser(userId: Long, isDone: Int, isAsc: Boolean): Flow<List<Idea>>
}

@Dao
interface WordDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWord(word: Word): Long

    @Update
    suspend fun updateWord(word: Word)

    @Delete
    suspend fun deleteWord(word: Word)

    @Query("SELECT * FROM words WHERE folder_id = :folderId ORDER BY created_at ASC")
    fun getWordsByFolder(folderId: Long): Flow<List<Word>>

    @Query("SELECT w.* FROM words w JOIN folders f ON f.id = w.folder_id WHERE f.user_id = :userId ORDER BY w.created_at ASC")
    fun getWordsByUser(userId: Long): Flow<List<Word>>

    @Query("SELECT * FROM words WHERE id = :id")
    suspend fun getWordById(id: Long): Word?

    @Query("SELECT * FROM words WHERE folder_id = :folderId AND LOWER(word) = LOWER(:word) LIMIT 1")
    suspend fun getWordByFolderAndWord(folderId: Long, word: String): Word?
}

@Dao
interface WordReviewDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviews(reviews: List<WordReview>)

    @Update
    suspend fun updateReview(review: WordReview)

    @Query("SELECT * FROM word_reviews WHERE word_id = :wordId ORDER BY stage ASC")
    fun getReviewsForWord(wordId: Long): Flow<List<WordReview>>

    @Transaction
    @Query("SELECT r.* FROM word_reviews r JOIN words w ON w.id = r.word_id JOIN folders f ON f.id = w.folder_id WHERE f.user_id = :userId AND r.due_date = :date")
    fun getReviewsForUserByDate(userId: Long, date: String): Flow<List<WordReviewWithWord>>

    @Query("SELECT COUNT(*) FROM word_reviews r JOIN words w ON w.id = r.word_id JOIN folders f ON f.id = w.folder_id WHERE f.user_id = :userId AND r.due_date = :today AND r.status = 'PENDING' AND r.remind_at IS NULL")
    suspend fun getDueTodayCount(userId: Long, today: String): Int

    @Transaction
    @Query("SELECT r.* FROM word_reviews r JOIN words w ON w.id = r.word_id JOIN folders f ON f.id = w.folder_id WHERE f.user_id = :userId AND r.due_date >= :thirtyDaysAgo AND ((r.status = 'PENDING' AND r.due_date < :today) OR r.status = 'SKIPPED') ORDER BY r.due_date DESC")
    fun getMissedReviews(userId: Long, today: String, thirtyDaysAgo: String): Flow<List<WordReviewWithWord>>

    @Transaction
    @Query("SELECT r.* FROM word_reviews r JOIN words w ON w.id = r.word_id JOIN folders f ON f.id = w.folder_id WHERE f.user_id = :userId AND r.due_date BETWEEN :today AND :sevenDaysLater AND r.status = 'PENDING' ORDER BY r.due_date ASC")
    fun getUpcomingReviews(userId: Long, today: String, sevenDaysLater: String): Flow<List<WordReviewWithWord>>

    @Transaction
    @Query("SELECT r.* FROM word_reviews r JOIN words w ON w.id = r.word_id JOIN folders f ON f.id = w.folder_id WHERE f.user_id = :userId AND r.status = 'DONE' AND r.due_date >= :thirtyDaysAgo ORDER BY r.done_at DESC")
    fun getCompletedReviews(userId: Long, thirtyDaysAgo: String): Flow<List<WordReviewWithWord>>

    @Transaction
    @Query("SELECT r.* FROM word_reviews r JOIN words w ON w.id = r.word_id JOIN folders f ON f.id = w.folder_id WHERE f.user_id = :userId AND r.status = 'PENDING' AND r.due_date <= :maxDueDate")
    suspend fun getPendingReviewsForScheduler(userId: Long, maxDueDate: String): List<WordReviewWithWord>
}

@Dao
interface AppNotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotification(notification: AppNotification): Long

    @Query("SELECT * FROM app_notifications WHERE user_id = :userId ORDER BY created_at DESC")
    fun getNotificationsByUser(userId: Long): Flow<List<AppNotification>>

    @Query("UPDATE app_notifications SET is_read = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("DELETE FROM app_notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)

    @Query("SELECT COUNT(*) FROM app_notifications WHERE user_id = :userId AND is_read = 0")
    fun getUnreadCount(userId: Long): Flow<Int>
}