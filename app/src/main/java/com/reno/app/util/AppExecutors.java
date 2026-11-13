package com.reno.app.util;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Các bộ thực thi dùng chung. Mọi truy vấn CSDL, băm mật khẩu, chép file đều chạy ở đây,
 * không bao giờ chạy trên luồng giao diện.
 */
public final class AppExecutors {
    /** Một luồng duy nhất: các thao tác CSDL chạy lần lượt, kết quả tới giao diện đúng thứ tự. */
    private static final ExecutorService DISK_IO = Executors.newSingleThreadExecutor();
    /** Việc nặng (băm mật khẩu, chép file) để không chặn hàng đợi CSDL. */
    private static final ExecutorService HEAVY = Executors.newFixedThreadPool(2);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Executor MAIN = MAIN_HANDLER::post;

    private AppExecutors() {
    }

    public static ExecutorService diskIO() {
        return DISK_IO;
    }

    public static ExecutorService heavy() {
        return HEAVY;
    }

    public static Executor mainThread() {
        return MAIN;
    }
}
