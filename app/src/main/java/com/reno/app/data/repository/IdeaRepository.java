package com.reno.app.data.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.dao.IdeaDao;
import com.reno.app.data.db.entity.IdeaEntity;
import com.reno.app.util.AppExecutors;

import java.util.List;

/** Ý tưởng: thêm, sửa, xóa và chuyển giữa Chưa thực hiện / Đã thực hiện. */
public class IdeaRepository {
    public static final int MAX_LENGTH = 1000;
    private static volatile IdeaRepository INSTANCE;

    private final IdeaDao ideaDao;

    private IdeaRepository(Context ctx) {
        ideaDao = RenoDatabase.get(ctx.getApplicationContext()).ideaDao();
    }

    public static IdeaRepository get(Context ctx) {
        if (INSTANCE == null) {
            synchronized (IdeaRepository.class) {
                if (INSTANCE == null) INSTANCE = new IdeaRepository(ctx);
            }
        }
        return INSTANCE;
    }

    public LiveData<Integer> tick() {
        return ideaDao.tick();
    }

    /** Gọi ở luồng nền. */
    public List<IdeaEntity> list(long userId, boolean done, boolean oldestFirst) {
        int flag = done ? 1 : 0;
        return oldestFirst ? ideaDao.listOldestFirst(userId, flag)
                : ideaDao.listNewestFirst(userId, flag);
    }

    public void add(long userId, String content) {
        AppExecutors.diskIO().execute(() -> {
            long now = System.currentTimeMillis();
            IdeaEntity idea = new IdeaEntity();
            idea.userId = userId;
            idea.content = content.trim();
            idea.isDone = 0;
            idea.createdAt = now;
            idea.updatedAt = now;
            ideaDao.insert(idea);
        });
    }

    public void updateContent(long ideaId, String content) {
        AppExecutors.diskIO().execute(() ->
                ideaDao.updateContent(ideaId, content.trim(), System.currentTimeMillis()));
    }

    /** Tích → Đã thực hiện (lưu done_at); bỏ tích → về Chưa thực hiện. */
    public void setDone(long ideaId, boolean done) {
        AppExecutors.diskIO().execute(() -> {
            long now = System.currentTimeMillis();
            ideaDao.setDone(ideaId, done ? 1 : 0, done ? now : null, now);
        });
    }

    public void delete(long ideaId) {
        AppExecutors.diskIO().execute(() -> ideaDao.deleteById(ideaId));
    }
}
