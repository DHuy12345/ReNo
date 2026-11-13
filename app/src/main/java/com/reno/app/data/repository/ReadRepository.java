package com.reno.app.data.repository;

import android.content.Context;
import android.database.Cursor;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.dao.AnnotationDao;
import com.reno.app.data.db.dao.FolderDao;
import com.reno.app.data.db.dao.TopicDao;
import com.reno.app.data.db.entity.AnnotationEntity;
import com.reno.app.data.db.entity.FolderEntity;
import com.reno.app.data.db.entity.TopicEntity;
import com.reno.app.data.model.FolderGroup;
import com.reno.app.data.model.PreparedPdf;
import com.reno.app.util.AppExecutors;
import com.reno.app.util.Callback;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Tab Read: thư mục, topic (file PDF) và đánh dấu. */
public class ReadRepository {
    private static final String TAG = "ReadRepository";
    public static final int RECENT_LIMIT = 3;
    public static final int BG_COUNT = 8;
    private static volatile ReadRepository INSTANCE;

    private final Context app;
    private final RenoDatabase db;
    private final FolderDao folderDao;
    private final TopicDao topicDao;
    private final AnnotationDao annotationDao;
    private final Random random = new Random();

    private ReadRepository(Context ctx) {
        app = ctx.getApplicationContext();
        db = RenoDatabase.get(app);
        folderDao = db.folderDao();
        topicDao = db.topicDao();
        annotationDao = db.annotationDao();
    }

    public static ReadRepository get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (ReadRepository.class) {
                if (INSTANCE == null) INSTANCE = new ReadRepository(ctx);
            }
        }
        return INSTANCE;
    }

    /** Dữ liệu của màn danh sách Read. */
    public static final class ReadData {
        public final List<TopicEntity> recent = new ArrayList<>();
        public final List<FolderGroup<TopicEntity>> folders = new ArrayList<>();
    }

    public LiveData<Integer> tick() {
        return topicDao.tick();
    }

    /** Gọi ở luồng nền. */
    public ReadData loadScreen(long userId) {
        ReadData data = new ReadData();
        data.recent.addAll(topicDao.recent(userId, RECENT_LIMIT));
        Map<Long, FolderGroup<TopicEntity>> byId = new LinkedHashMap<>();
        for (FolderEntity f : folderDao.list(userId, FolderEntity.TYPE_READ)) {
            byId.put(f.id, new FolderGroup<>(f));
        }
        for (TopicEntity t : topicDao.listByUser(userId)) {
            FolderGroup<TopicEntity> g = byId.get(t.folderId);
            if (g != null) g.items.add(t);
        }
        data.folders.addAll(byId.values());
        return data;
    }

    /** Gọi ở luồng nền. */
    public List<FolderEntity> listFoldersSync(long userId) {
        return folderDao.list(userId, FolderEntity.TYPE_READ);
    }

    public void listFolders(long userId, Callback<List<FolderEntity>> callback) {
        AppExecutors.diskIO().execute(() -> {
            List<FolderEntity> list = listFoldersSync(userId);
            AppExecutors.mainThread().execute(() -> callback.onResult(list));
        });
    }

    /** @param callback nhận mã chuỗi lỗi, 0 = thành công */
    public void createFolder(long userId, String name, Callback<Integer> callback) {
        AppExecutors.diskIO().execute(() -> {
            int error = FolderStore.create(folderDao, userId, FolderEntity.TYPE_READ, name).error;
            AppExecutors.mainThread().execute(() -> callback.onResult(error));
        });
    }

    public void renameFolder(long folderId, String name, Callback<Integer> callback) {
        AppExecutors.diskIO().execute(() -> {
            int error = FolderStore.rename(folderDao, folderId, name).error;
            AppExecutors.mainThread().execute(() -> callback.onResult(error));
        });
    }

    /** Xóa thư mục: lấy đường dẫn các file PDF trước, xóa dòng (CASCADE), rồi xóa file. */
    public void deleteFolder(long folderId, Runnable done) {
        AppExecutors.diskIO().execute(() -> {
            List<String> paths = topicDao.filePathsInFolder(folderId);
            folderDao.deleteById(folderId);
            for (String p : paths) UserRepository.deleteQuietly(p);
            AppExecutors.mainThread().execute(done);
        });
    }

    // ---------------------------------------------------------------- Topic

    /**
     * Chép file người dùng chọn vào filesDir/pdfs/&lt;uuid&gt;.pdf rồi mở thử bằng PdfRenderer để
     * lấy số trang. Không lưu URI gốc vì quyền đọc sẽ mất khi app khởi động lại.
     *
     * @param callback nhận file đã chuẩn bị, hoặc null nếu file hỏng / có mật khẩu
     */
    public void preparePdf(Uri source, Callback<PreparedPdf> callback) {
        AppExecutors.heavy().execute(() -> {
            PreparedPdf prepared = copyAndCheck(source);
            AppExecutors.mainThread().execute(() -> callback.onResult(prepared));
        });
    }

    @Nullable
    private PreparedPdf copyAndCheck(Uri source) {
        File dir = new File(app.getFilesDir(), "pdfs");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Không tạo được thư mục pdfs");
            return null;
        }
        File target = new File(dir, UUID.randomUUID() + ".pdf");
        try (InputStream in = app.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) throw new IOException("Không mở được " + source);
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Chép file PDF thất bại", e);
            UserRepository.deleteQuietly(target.getAbsolutePath());
            return null;
        }
        int pages;
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(target,
                ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd)) {
            pages = renderer.getPageCount();
        } catch (IOException | RuntimeException e) {
            // File hỏng hoặc có mật khẩu: xóa bản chép.
            Log.w(TAG, "Không mở được file PDF", e);
            UserRepository.deleteQuietly(target.getAbsolutePath());
            return null;
        }
        if (pages <= 0) {
            UserRepository.deleteQuietly(target.getAbsolutePath());
            return null;
        }
        PreparedPdf prepared = new PreparedPdf();
        prepared.filePath = target.getAbsolutePath();
        prepared.originalName = displayName(source);
        prepared.pageCount = pages;
        return prepared;
    }

    @Nullable
    private String displayName(Uri uri) {
        try (Cursor c = app.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getString(0);
        } catch (RuntimeException e) {
            Log.w(TAG, "Không đọc được tên file", e);
        }
        return uri.getLastPathSegment();
    }

    /** Người dùng hủy đặt tên: xóa bản chép. */
    public void discardPrepared(PreparedPdf prepared) {
        if (prepared == null) return;
        AppExecutors.diskIO().execute(() -> UserRepository.deleteQuietly(prepared.filePath));
    }

    /** Tạo topic; ảnh nền chọn ngẫu nhiên 1–8 và không bao giờ đổi. */
    public void createTopic(long folderId, String rawName, PreparedPdf prepared, Runnable done) {
        AppExecutors.diskIO().execute(() -> {
            long now = System.currentTimeMillis();
            TopicEntity t = new TopicEntity();
            t.folderId = folderId;
            String name = rawName == null ? "" : rawName.trim();
            t.name = name.isEmpty() ? prepared.suggestedName() : name;
            t.filePath = prepared.filePath;
            t.originalName = prepared.originalName;
            t.pageCount = prepared.pageCount;
            t.bgIndex = 1 + random.nextInt(BG_COUNT);
            t.lastPage = 0;
            t.lastOpenedAt = now;     // khi tạo, bằng thời điểm tạo
            t.createdAt = now;
            topicDao.insert(t);
            AppExecutors.mainThread().execute(done);
        });
    }

    public void findTopic(long topicId, Callback<TopicEntity> callback) {
        AppExecutors.diskIO().execute(() -> {
            TopicEntity t = topicDao.findById(topicId);
            AppExecutors.mainThread().execute(() -> callback.onResult(t));
        });
    }

    public void renameTopic(long topicId, String name) {
        AppExecutors.diskIO().execute(() -> topicDao.rename(topicId, name.trim()));
    }

    public void moveTopic(long topicId, long folderId) {
        AppExecutors.diskIO().execute(() -> topicDao.move(topicId, folderId));
    }

    /** Xóa topic xóa luôn file PDF; đánh dấu mất theo CASCADE. */
    public void deleteTopic(long topicId) {
        AppExecutors.diskIO().execute(() -> {
            TopicEntity t = topicDao.findById(topicId);
            if (t == null) return;
            topicDao.deleteById(topicId);
            UserRepository.deleteQuietly(t.filePath);
        });
    }

    /** Mở topic: cập nhật last_opened_at cho mục Gần đây. */
    public void touchTopic(long topicId) {
        AppExecutors.diskIO().execute(() ->
                topicDao.touch(topicId, System.currentTimeMillis()));
    }

    public void saveLastPage(long topicId, int page) {
        AppExecutors.diskIO().execute(() -> topicDao.setLastPage(topicId, Math.max(0, page)));
    }

    // ------------------------------------------------------------ Đánh dấu

    public LiveData<List<AnnotationEntity>> observeAnnotations(long topicId) {
        return annotationDao.observeByTopic(topicId);
    }

    public void addAnnotation(AnnotationEntity a) {
        AppExecutors.diskIO().execute(() -> {
            long now = System.currentTimeMillis();
            a.createdAt = now;
            a.updatedAt = now;
            annotationDao.insert(a);
        });
    }

    public void updateAnnotation(long id, @Nullable String title, @Nullable String detail,
                                 @Nullable String color) {
        AppExecutors.diskIO().execute(() ->
                annotationDao.update(id, title, detail, color, System.currentTimeMillis()));
    }

    public void deleteAnnotation(long id) {
        AppExecutors.diskIO().execute(() -> annotationDao.deleteById(id));
    }
}
