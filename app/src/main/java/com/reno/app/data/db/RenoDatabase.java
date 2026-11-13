package com.reno.app.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.reno.app.data.db.dao.AnnotationDao;
import com.reno.app.data.db.dao.AppNotificationDao;
import com.reno.app.data.db.dao.FolderDao;
import com.reno.app.data.db.dao.IdeaDao;
import com.reno.app.data.db.dao.NoteDao;
import com.reno.app.data.db.dao.NoteOccurrenceDao;
import com.reno.app.data.db.dao.TopicDao;
import com.reno.app.data.db.dao.UserDao;
import com.reno.app.data.db.dao.WordDao;
import com.reno.app.data.db.dao.WordReviewDao;
import com.reno.app.data.db.entity.AnnotationEntity;
import com.reno.app.data.db.entity.AppNotificationEntity;
import com.reno.app.data.db.entity.FolderEntity;
import com.reno.app.data.db.entity.IdeaEntity;
import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.db.entity.NoteOccurrenceEntity;
import com.reno.app.data.db.entity.TopicEntity;
import com.reno.app.data.db.entity.UserEntity;
import com.reno.app.data.db.entity.WordEntity;
import com.reno.app.data.db.entity.WordReviewEntity;

/**
 * CSDL reno.db gồm 10 bảng. Mọi khóa ngoại đều ON DELETE CASCADE; Room tự bật
 * PRAGMA foreign_keys cho mỗi kết nối.
 *
 * <p>Khi thêm hoặc sửa cột: tăng {@code version} và viết Migration tương ứng
 * (truyền vào {@code addMigrations}). Không dùng fallbackToDestructiveMigration.
 */
@Database(entities = {UserEntity.class, FolderEntity.class, TopicEntity.class,
        AnnotationEntity.class, NoteEntity.class, NoteOccurrenceEntity.class,
        IdeaEntity.class, WordEntity.class, WordReviewEntity.class,
        AppNotificationEntity.class}, version = 1)
public abstract class RenoDatabase extends RoomDatabase {
    public static final String DB_NAME = "reno.db";

    private static volatile RenoDatabase INSTANCE;

    public abstract UserDao userDao();

    public abstract FolderDao folderDao();

    public abstract TopicDao topicDao();

    public abstract AnnotationDao annotationDao();

    public abstract NoteDao noteDao();

    public abstract NoteOccurrenceDao noteOccurrenceDao();

    public abstract IdeaDao ideaDao();

    public abstract WordDao wordDao();

    public abstract WordReviewDao wordReviewDao();

    public abstract AppNotificationDao appNotificationDao();

    public static RenoDatabase get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (RenoDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(ctx.getApplicationContext(),
                                    RenoDatabase.class, DB_NAME)
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    DbConstraints.install(db);
                                }
                            })
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /** Chỉ dùng trong kiểm thử: thay CSDL bằng một bản trong bộ nhớ. */
    public static void setInstanceForTest(RenoDatabase db) {
        INSTANCE = db;
    }
}
