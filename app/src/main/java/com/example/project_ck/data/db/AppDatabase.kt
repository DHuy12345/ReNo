package com.example.project_ck.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.project_ck.data.dao.*
import com.example.project_ck.data.entity.*

@Database(
    entities = [
        User::class,
        Folder::class,
        Topic::class,
        PdfAnnotation::class,
        Note::class,
        NoteOccurrence::class,
        Idea::class,
        Word::class,
        WordReview::class,
        AppNotification::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun folderDao(): FolderDao
    abstract fun topicDao(): TopicDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun noteDao(): NoteDao
    abstract fun noteOccurrenceDao(): NoteOccurrenceDao
    abstract fun ideaDao(): IdeaDao
    abstract fun wordDao(): WordDao
    abstract fun wordReviewDao(): WordReviewDao
    abstract fun appNotificationDao(): AppNotificationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "reno.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}