package com.example.project_ck.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["email"], unique = true),
        Index(value = ["phone"], unique = true)
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val email: String? = null,
    val phone: String? = null,
    val password_hash: String,
    val avatar_path: String? = null,
    val review_time: String = "20:00",
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id", "type", "name"], unique = true),
        Index(value = ["user_id", "type"])
    ]
)
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val user_id: Long,
    val type: String, // 'READ' or 'VOCAB'
    val name: String,
    val created_at: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "topics",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["folder_id"]),
        Index(value = ["last_opened_at"])
    ]
)
data class Topic(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folder_id: Long,
    val name: String,
    val file_path: String,
    val original_name: String? = null,
    val page_count: Int,
    val bg_index: Int, // 1..8
    val last_page: Int = 0,
    val last_opened_at: Long = System.currentTimeMillis(),
    val created_at: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = Topic::class,
            parentColumns = ["id"],
            childColumns = ["topic_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["topic_id", "type"])
    ]
)
data class PdfAnnotation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val topic_id: Long,
    val type: String, // 'QUESTION','IMPORTANT','STAR','HIGHLIGHT','NOTE'
    val page_index: Int,
    val rects: String, // JSON e.g. "[[0.12,0.30,0.55,0.02]]"
    val selected_text: String,
    val title: String? = null,
    val detail: String? = null,
    val color: String? = null, // #RRGGBB for HIGHLIGHT
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id"])
    ]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val user_id: Long,
    val title: String,
    val color: String, // #RRGGBB
    val dtstart: Long, // start epoch ms
    val duration_min: Int = 30,
    val repeat_type: String = "NONE", // 'NONE','DAILY','INTERVAL','CUSTOM'
    val rrule: String? = null,
    val repeat_until: String? = null, // YYYY-MM-DD
    val rdate: String? = null, // comma separated YYYY-MM-DD
    val exdate: String? = null, // comma separated YYYY-MM-DD
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "note_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["note_id", "occ_date"], unique = true)
    ]
)
data class NoteOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val note_id: Long,
    val occ_date: String, // YYYY-MM-DD
    val status: String, // 'DONE' or 'SKIPPED'
    val updated_at: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ideas",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id", "is_done", "created_at"])
    ]
)
data class Idea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val user_id: Long,
    val content: String,
    val is_done: Int = 0, // 0 or 1
    val done_at: Long? = null,
    val created_at: Long = System.currentTimeMillis(),
    val updated_at: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["folder_id", "word"], unique = true),
        Index(value = ["folder_id"])
    ]
)
data class Word(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folder_id: Long,
    val word: String,
    val meaning: String,
    val remind_time: String? = null, // HH:mm or NULL
    val created_date: String, // YYYY-MM-DD
    val created_at: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "word_reviews",
    foreignKeys = [
        ForeignKey(
            entity = Word::class,
            parentColumns = ["id"],
            childColumns = ["word_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["word_id", "stage"], unique = true),
        Index(value = ["due_date", "status"]),
        Index(value = ["word_id"])
    ]
)
data class WordReview(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word_id: Long,
    val stage: Int, // 1..5
    val original_due_date: String, // YYYY-MM-DD
    val due_date: String, // YYYY-MM-DD
    val remind_at: Long? = null, // epoch ms if specifically rescheduled
    val status: String = "PENDING", // 'PENDING','DONE','SKIPPED'
    val done_at: Long? = null
)

@Entity(
    tableName = "app_notifications",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["user_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Note::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id", "dedupe_key"], unique = true),
        Index(value = ["user_id", "created_at"])
    ]
)
data class AppNotification(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val user_id: Long,
    val kind: String, // 'NOTE' or 'VOCAB'
    val note_id: Long? = null,
    val ref_date: String, // YYYY-MM-DD
    val dedupe_key: String,
    val title: String,
    val body: String,
    val is_read: Int = 0,
    val created_at: Long = System.currentTimeMillis()
)