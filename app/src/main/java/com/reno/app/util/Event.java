package com.reno.app.util;

import androidx.annotation.Nullable;

/** Giá trị LiveData chỉ được xử lý một lần (thông báo, điều hướng), không lặp lại khi màn được tạo lại. */
public class Event<T> {
    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    /** @return nội dung nếu chưa ai xử lý, ngược lại null */
    @Nullable
    public T consume() {
        if (handled) return null;
        handled = true;
        return content;
    }

    public T peek() {
        return content;
    }
}
