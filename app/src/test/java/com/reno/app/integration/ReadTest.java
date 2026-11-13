package com.reno.app.integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.reno.app.TestEnv;
import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.entity.AnnotationEntity;
import com.reno.app.data.db.entity.FolderEntity;
import com.reno.app.data.db.entity.TopicEntity;
import com.reno.app.data.repository.ReadRepository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** 11.2: "Xóa thư mục Read có 3 topic: topic, đánh dấu và file PDF đều mất" (TC09). */
@RunWith(RobolectricTestRunner.class)
public class ReadTest {
    private RenoDatabase db;
    private ReadRepository repo;
    private long userId;

    @Before
    public void setUp() throws Exception {
        TestEnv.reset();
        db = RenoDatabase.get(TestEnv.context());
        repo = ReadRepository.get(TestEnv.context());
        userId = Fixtures.loggedInUser("reader", "20:00");
    }

    @Test
    public void deleteFolder_removesTopicsAnnotationsAndFiles() throws Exception {
        int[] err = {-1};
        repo.createFolder(userId, "Giáo trình", e -> err[0] = e);
        TestEnv.drain();
        assertEquals(0, err[0]);
        repo.createFolder(userId, "giáo TRÌNH", e -> err[0] = e);
        TestEnv.drain();
        assertTrue("tên trùng không phân biệt hoa thường phải bị chặn", err[0] != 0);

        long folderId = db.folderDao().list(userId, FolderEntity.TYPE_READ).get(0).id;
        File dir = new File(TestEnv.context().getFilesDir(), "pdfs");
        assertTrue(dir.mkdirs() || dir.isDirectory());
        List<File> files = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            File f = new File(dir, "t" + i + ".pdf");
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write("%PDF-1.4".getBytes());
            }
            files.add(f);
            TopicEntity t = new TopicEntity();
            t.folderId = folderId;
            t.name = "Topic " + i;
            t.filePath = f.getAbsolutePath();
            t.pageCount = 3;
            t.bgIndex = 1 + i;
            t.lastOpenedAt = i;
            t.createdAt = i;
            long topicId = db.topicDao().insert(t);
            AnnotationEntity a = new AnnotationEntity();
            a.topicId = topicId;
            a.type = AnnotationEntity.TYPE_HIGHLIGHT;
            a.rects = "[[0.1,0.2,0.3,0.02]]";
            a.selectedText = "abc";
            a.color = "#FFE58A";
            db.annotationDao().insert(a);
        }
        ReadRepository.ReadData data = repo.loadScreen(userId);
        assertEquals(3, data.recent.size());
        assertEquals("Topic 2", data.recent.get(0).name);   // mở gần nhất trước
        assertEquals(3, data.folders.get(0).items.size());

        repo.deleteFolder(folderId, () -> { });
        TestEnv.drain();
        assertTrue(db.topicDao().listByUser(userId).isEmpty());
        assertEquals(0, (int) db.annotationDao().countByType(1).size());
        for (File f : files) assertFalse(f.exists());
        assertNull(db.folderDao().findById(folderId));
    }

    @Test(expected = android.database.sqlite.SQLiteConstraintException.class)
    public void bgIndex_mustBe1to8() {
        FolderEntity f = new FolderEntity();
        f.userId = userId;
        f.type = FolderEntity.TYPE_READ;
        f.name = "x";
        long folderId = db.folderDao().insert(f);
        TopicEntity t = new TopicEntity();
        t.folderId = folderId;
        t.name = "x";
        t.filePath = "/x";
        t.bgIndex = 9;
        db.topicDao().insert(t);
    }
}
