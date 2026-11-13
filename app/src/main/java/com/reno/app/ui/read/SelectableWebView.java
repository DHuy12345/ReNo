package com.reno.app.ui.read;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import com.reno.app.R;
import com.reno.app.data.db.entity.AnnotationEntity;

/**
 * WebView đọc PDF. Khi người dùng bôi đen, menu hệ thống (Sao chép, Chia sẻ…) được thay bằng
 * 5 nút đánh dấu: ?, !, ★, Highlight, Notes.
 */
public class SelectableWebView extends WebView {
    /** Nhận loại đánh dấu được bấm (một trong AnnotationEntity.TYPES). */
    public interface OnMarkListener {
        void onMark(String type, ActionMode mode);
    }

    private static final int[] LABELS = {R.string.mark_question, R.string.mark_important,
            R.string.mark_star, R.string.mark_highlight, R.string.mark_note};

    @Nullable private OnMarkListener markListener;
    private boolean markEnabled = true;

    public SelectableWebView(Context context) {
        super(context);
    }

    public SelectableWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnMarkListener(@Nullable OnMarkListener listener) {
        markListener = listener;
    }

    /** File scan không có lớp chữ thì không hiện nút đánh dấu. */
    public void setMarkEnabled(boolean enabled) {
        markEnabled = enabled;
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback) {
        return super.startActionMode(new MarkCallback(callback));
    }

    @Override
    public ActionMode startActionMode(ActionMode.Callback callback, int type) {
        return super.startActionMode(new MarkCallback(callback), type);
    }

    /** Bọc callback của WebView: giữ nguyên vòng đời vùng chọn, chỉ thay các mục menu. */
    private final class MarkCallback extends ActionMode.Callback2 {
        private final ActionMode.Callback wrapped;

        MarkCallback(ActionMode.Callback wrapped) {
            this.wrapped = wrapped;
        }

        private void fill(Menu menu) {
            menu.clear();
            if (!markEnabled) return;
            for (int i = 0; i < LABELS.length; i++) {
                menu.add(Menu.NONE, i, i, LABELS[i])
                        .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);   // các nút đánh dấu / Dịch phải luôn hiện
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            boolean created = wrapped.onCreateActionMode(mode, menu);
            fill(menu);
            return created;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            wrapped.onPrepareActionMode(mode, menu);
            fill(menu);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int index = item.getItemId();
            if (markListener != null && index >= 0 && index < AnnotationEntity.TYPES.length) {
                // Chưa đóng action mode ở đây: đóng sẽ làm mất vùng chọn trước khi kịp đọc.
                markListener.onMark(AnnotationEntity.TYPES[index], mode);
            } else {
                mode.finish();
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            wrapped.onDestroyActionMode(mode);
        }

        @Override
        public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
            if (wrapped instanceof ActionMode.Callback2) {
                ((ActionMode.Callback2) wrapped).onGetContentRect(mode, view, outRect);
            } else {
                super.onGetContentRect(mode, view, outRect);
            }
        }
    }
}
