package com.reno.app.data.model;

import androidx.annotation.NonNull;

import com.reno.app.data.db.entity.FolderEntity;

import java.util.ArrayList;
import java.util.List;

/** Một thư mục kèm các phần tử bên trong (topic của tab Read hoặc từ của tab Vocabulary). */
public class FolderGroup<T> {
    @NonNull public final FolderEntity folder;
    @NonNull public final List<T> items = new ArrayList<>();

    public FolderGroup(@NonNull FolderEntity folder) {
        this.folder = folder;
    }
}
