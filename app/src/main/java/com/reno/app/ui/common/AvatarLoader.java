package com.reno.app.ui.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.reno.app.R;
import com.reno.app.util.AppExecutors;

/** Nạp avatar từ file ở luồng nền; không có ảnh thì dùng ảnh mặc định. */
public final class AvatarLoader {
    private AvatarLoader() {
    }

    public static void load(ImageView view, @Nullable String path) {
        view.setTag(R.id.tag_avatar_path, path);
        if (path == null) {
            view.setImageResource(R.drawable.avatar_default);
            return;
        }
        AppExecutors.diskIO().execute(() -> {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
            view.post(() -> {
                // ảnh có thể đã đổi trong lúc đang giải mã
                if (!path.equals(view.getTag(R.id.tag_avatar_path))) return;
                if (bitmap != null) {
                    view.setImageBitmap(bitmap);
                } else {
                    view.setImageResource(R.drawable.avatar_default);
                }
            });
        });
    }
}
