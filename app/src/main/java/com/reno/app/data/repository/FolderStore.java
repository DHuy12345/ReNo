package com.reno.app.data.repository;

import androidx.annotation.StringRes;

import com.reno.app.R;
import com.reno.app.data.db.dao.FolderDao;
import com.reno.app.data.db.entity.FolderEntity;

/** Tạo và đổi tên thư mục – dùng chung cho tab Read và tab Vocabulary. Gọi ở luồng nền. */
final class FolderStore {
    private FolderStore() {
    }

    /** Kết quả tạo / đổi tên: error = 0 nghĩa là thành công. */
    static final class Result {
        @StringRes int error;
        long folderId = -1;
    }

    /** Tên không trống, không trùng trong cùng tài khoản và cùng loại (không phân biệt hoa thường). */
    static Result create(FolderDao dao, long userId, String type, String rawName) {
        Result r = new Result();
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            r.error = R.string.error_folder_name_empty;
        } else if (dao.findByName(userId, type, name) != null) {
            r.error = R.string.error_folder_name_taken;
        } else {
            FolderEntity f = new FolderEntity();
            f.userId = userId;
            f.type = type;
            f.name = name;
            f.createdAt = System.currentTimeMillis();
            r.folderId = dao.insert(f);
        }
        return r;
    }

    static Result rename(FolderDao dao, long folderId, String rawName) {
        Result r = new Result();
        r.folderId = folderId;
        String name = rawName == null ? "" : rawName.trim();
        FolderEntity current = dao.findById(folderId);
        if (current == null) {
            r.error = R.string.error_generic;
        } else if (name.isEmpty()) {
            r.error = R.string.error_folder_name_empty;
        } else {
            FolderEntity other = dao.findByName(current.userId, current.type, name);
            if (other != null && other.id != folderId) {
                r.error = R.string.error_folder_name_taken;
            } else {
                dao.rename(folderId, name);
            }
        }
        return r;
    }
}
