package com.reno.app.util;

/** Nhận kết quả của một thao tác chạy nền; luôn được gọi trên luồng giao diện. */
public interface Callback<T> {
    void onResult(T result);
}
