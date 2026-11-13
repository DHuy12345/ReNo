package com.reno.app.ui.vocab;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.reno.app.data.db.entity.FolderEntity;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Giữ nội dung đang nhập dở của màn thêm / sửa từ vựng. */
public class WordFormViewModel extends ViewModel {
    public boolean initialized;
    public long wordId;
    public final List<FolderEntity> folders = new ArrayList<>();
    public long folderId = -1;
    /** false = "Giờ mặc định", true = "Tự chọn". */
    public boolean customTime;
    @Nullable public LocalTime time;
    /** Giờ nhắc chung của tài khoản (HH:mm). */
    public String defaultTime = "20:00";
}
