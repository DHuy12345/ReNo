package com.reno.app.ui.read;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.reno.app.data.db.entity.AnnotationEntity;
import com.reno.app.data.db.entity.TopicEntity;
import com.reno.app.data.repository.ReadRepository;

import java.util.Collections;
import java.util.List;

/** Trạng thái của màn topic: file PDF đang đọc và các đánh dấu của nó. */
public class TopicViewModel extends AndroidViewModel {
    private final ReadRepository repo;
    private final MutableLiveData<Long> topicId = new MutableLiveData<>();
    private final MutableLiveData<TopicEntity> topic = new MutableLiveData<>();
    private final LiveData<List<AnnotationEntity>> annotations;

    /** Tab chính đang chọn: 0 Ghi chú, 1 Thống kê, 2 Tóm tắt. */
    public int selectedTab;
    /** Tab lọc của Thống kê: chỉ số trong AnnotationEntity.TYPES. */
    public int selectedFilter;
    /** Vùng chọn mới nhất do JavaScript báo (dự phòng). */
    @Nullable public String lastSelectionJson;
    public boolean isScan;

    public TopicViewModel(@NonNull Application app) {
        super(app);
        repo = ReadRepository.get(app);
        annotations = Transformations.switchMap(topicId, repo::observeAnnotations);
    }

    public void load(long id) {
        if (topicId.getValue() != null) return;
        topicId.setValue(id);
        repo.findTopic(id, topic::setValue);
    }

    public LiveData<TopicEntity> topic() {
        return topic;
    }

    public LiveData<List<AnnotationEntity>> annotations() {
        return annotations;
    }

    public List<AnnotationEntity> currentAnnotations() {
        List<AnnotationEntity> list = annotations.getValue();
        return list == null ? Collections.emptyList() : list;
    }

    @Nullable
    public AnnotationEntity findAnnotation(long id) {
        for (AnnotationEntity a : currentAnnotations()) {
            if (a.id == id) return a;
        }
        return null;
    }

    public void addAnnotation(AnnotationEntity a) {
        repo.addAnnotation(a);
    }

    public void updateAnnotation(long id, @Nullable String title, @Nullable String detail,
                                 @Nullable String color) {
        repo.updateAnnotation(id, title, detail, color);
    }

    public void deleteAnnotation(long id) {
        repo.deleteAnnotation(id);
    }

    public void saveLastPage(int page) {
        Long id = topicId.getValue();
        if (id != null) repo.saveLastPage(id, page);
    }
}
