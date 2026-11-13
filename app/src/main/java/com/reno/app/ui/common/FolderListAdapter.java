package com.reno.app.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.reno.app.data.db.entity.FolderEntity;
import com.reno.app.data.model.FolderGroup;
import com.reno.app.databinding.ItemFolderBinding;
import com.reno.app.databinding.ItemFolderEmptyBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Danh sách thư mục mở/đóng được, dùng chung cho tab Read (topic) và tab Vocabulary (từ).
 * Bấm dòng thư mục để mở ra các phần tử bên trong, bấm lần nữa để đóng (mũi tên xoay 180°).
 */
public abstract class FolderListAdapter<T> extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_FOLDER = 0;
    private static final int TYPE_ITEM = 1;
    private static final int TYPE_EMPTY = 2;

    private final List<Object> rows = new ArrayList<>();
    private List<FolderGroup<T>> groups = new ArrayList<>();
    private final Set<Long> expanded;

    /** Dòng "Thư mục trống" của một thư mục đang mở mà chưa có phần tử. */
    private static final class EmptyRow {
    }

    protected FolderListAdapter(Set<Long> expanded) {
        this.expanded = expanded;
    }

    public void submit(List<FolderGroup<T>> groups) {
        this.groups = groups;
        rebuild();
    }

    private void rebuild() {
        rows.clear();
        for (FolderGroup<T> g : groups) {
            rows.add(g);
            if (expanded.contains(g.folder.id)) {
                if (g.items.isEmpty()) {
                    rows.add(new EmptyRow());
                } else {
                    rows.addAll(g.items);
                }
            }
        }
        notifyDataSetChanged();
    }

    /** Chữ đếm bên phải tên thư mục, ví dụ "3 topic" hoặc "12 từ". */
    protected abstract CharSequence countLabel(View view, int count);

    protected abstract void onFolderLongClick(View anchor, FolderEntity folder, int count);

    protected abstract RecyclerView.ViewHolder onCreateItemHolder(LayoutInflater inflater,
                                                                  ViewGroup parent);

    protected abstract void onBindItemHolder(RecyclerView.ViewHolder holder, T item);

    @Override
    public int getItemViewType(int position) {
        Object row = rows.get(position);
        if (row instanceof FolderGroup) return TYPE_FOLDER;
        return row instanceof EmptyRow ? TYPE_EMPTY : TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_FOLDER) {
            return new FolderHolder(ItemFolderBinding.inflate(inflater, parent, false));
        }
        if (viewType == TYPE_EMPTY) {
            return new RecyclerView.ViewHolder(
                    ItemFolderEmptyBinding.inflate(inflater, parent, false).getRoot()) {
            };
        }
        return onCreateItemHolder(inflater, parent);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof FolderHolder) {
            FolderGroup<T> g = (FolderGroup<T>) row;
            ItemFolderBinding b = ((FolderHolder) holder).b;
            boolean open = expanded.contains(g.folder.id);
            b.textName.setText(g.folder.name);
            b.textCount.setText(countLabel(b.getRoot(), g.items.size()));
            b.imgArrow.setRotation(open ? 180f : 0f);
            b.getRoot().setOnClickListener(v -> {
                if (!expanded.remove(g.folder.id)) expanded.add(g.folder.id);
                rebuild();
            });
            b.getRoot().setOnLongClickListener(v -> {
                onFolderLongClick(v, g.folder, g.items.size());
                return true;
            });
        } else if (getItemViewType(position) == TYPE_ITEM) {
            onBindItemHolder(holder, (T) row);
        }
    }

    private static final class FolderHolder extends RecyclerView.ViewHolder {
        final ItemFolderBinding b;

        FolderHolder(ItemFolderBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
