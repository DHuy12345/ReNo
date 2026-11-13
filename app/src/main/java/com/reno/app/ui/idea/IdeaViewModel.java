package com.reno.app.ui.idea;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.reno.app.data.db.entity.IdeaEntity;
import com.reno.app.data.repository.IdeaRepository;
import com.reno.app.util.LiveQuery;
import com.reno.app.util.SessionManager;

import java.util.List;

/** Tab Idea: hai tab con và kiểu sắp xếp (được nhớ trong SharedPreferences). */
public class IdeaViewModel extends AndroidViewModel {
    public final long userId;
    public final IdeaRepository repo;
    private final MutableLiveData<Boolean> showDone = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> oldestFirst;
    private final LiveData<List<IdeaEntity>> ideas;

    public IdeaViewModel(@NonNull Application app) {
        super(app);
        userId = SessionManager.currentUserId(app);
        repo = IdeaRepository.get(app);
        oldestFirst = new MutableLiveData<>(SessionManager.ideaOldestFirst(app, userId));
        ideas = LiveQuery.of(() -> repo.list(userId, isShowingDone(), isOldestFirst()),
                repo.tick(), showDone, oldestFirst);
    }

    public LiveData<List<IdeaEntity>> ideas() {
        return ideas;
    }

    public boolean isShowingDone() {
        return Boolean.TRUE.equals(showDone.getValue());
    }

    public void setShowDone(boolean done) {
        if (isShowingDone() != done) showDone.setValue(done);
    }

    public boolean isOldestFirst() {
        return Boolean.TRUE.equals(oldestFirst.getValue());
    }

    public void setOldestFirst(boolean value) {
        SessionManager.setIdeaOldestFirst(getApplication(), userId, value);
        oldestFirst.setValue(value);
    }
}
