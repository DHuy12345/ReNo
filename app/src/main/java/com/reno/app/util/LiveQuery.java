package com.reno.app.util;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.function.Supplier;

/**
 * Tạo LiveData cho những dữ liệu phải tính bằng Java (ví dụ các lần xuất hiện của ghi chú lặp).
 * Mỗi khi một "nguồn kích" phát giá trị (bảng thay đổi, người dùng đổi ngày…), hàm tải được
 * chạy lại trên luồng nền và kết quả được đẩy về giao diện bằng postValue.
 */
public final class LiveQuery {
    private LiveQuery() {
    }

    public static <T> LiveData<T> of(Supplier<T> loader, LiveData<?>... triggers) {
        MediatorLiveData<T> out = new MediatorLiveData<>();
        for (LiveData<?> trigger : triggers) {
            out.addSource(trigger, ignored ->
                    AppExecutors.diskIO().execute(() -> out.postValue(loader.get())));
        }
        return out;
    }
}
