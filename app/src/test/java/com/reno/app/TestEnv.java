package com.reno.app;

import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;

import com.reno.app.data.db.DbConstraints;
import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.repository.IdeaRepository;
import com.reno.app.data.repository.NoteRepository;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.data.repository.ReadRepository;
import com.reno.app.data.repository.UserRepository;
import com.reno.app.data.repository.VocabRepository;
import com.reno.app.reminder.ReminderScheduler;
import com.reno.app.util.AppExecutors;

import java.lang.reflect.Field;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Tiện ích cho kiểm thử Robolectric: làm mới các singleton và chờ luồng nền chạy xong. */
public final class TestEnv {
    private TestEnv() {
    }

    public static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    /**
     * Mỗi kiểm thử dùng một CSDL mới trong bộ nhớ, cùng cấu hình như app (trigger CHECK, khóa
     * ngoại) nhưng cho phép kiểm thử truy vấn trực tiếp trên luồng kiểm thử.
     *
     * <p>CSDL mới được gắn vào trước khi đóng bản cũ: luồng nền (ví dụ WorkManager chạy
     * RescheduleWorker) gọi RenoDatabase.get() vào lúc nào cũng nhận đúng bản mới.
     */
    public static void reset() throws Exception {
        drain();
        flushSharedPreferences();
        resetViewModelFactory();
        RenoDatabase fresh = Room.inMemoryDatabaseBuilder(context(), RenoDatabase.class)
                .allowMainThreadQueries()
                .addCallback(new RoomDatabase.Callback() {
                    @Override
                    public void onOpen(@NonNull SupportSQLiteDatabase db) {
                        DbConstraints.install(db);
                    }
                })
                .build();
        RenoDatabase old;
        synchronized (RenoDatabase.class) {
            Field f = RenoDatabase.class.getDeclaredField("INSTANCE");
            f.setAccessible(true);
            old = (RenoDatabase) f.get(null);
            f.set(null, fresh);
        }
        clearRepositories();
        drain();
        clearRepositories();     // bỏ cả bản nào vừa được tạo kèm CSDL cũ trong lúc chờ
        if (old != null) old.close();
    }

    /**
     * SharedPreferences.apply() ghi xuống đĩa trên một luồng riêng (QueuedWork). Chờ các lần
     * ghi của kiểm thử trước xong hẳn, rồi xóa sạch, để chúng không ghi đè phiên đăng nhập của
     * kiểm thử hiện tại.
     */
    public static void flushSharedPreferences() throws Exception {
        Class.forName("android.app.QueuedWork").getMethod("waitToFinish").invoke(null);
        for (String name : new String[]{"reno_session", "reno_alarms"}) {
            context().getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
        }
    }

    /**
     * AndroidViewModelFactory giữ một bản tĩnh gắn với Application đầu tiên. Robolectric tạo
     * Application mới (thư mục dữ liệu mới) cho mỗi kiểm thử, nên phải bỏ bản cũ đi.
     */
    private static void resetViewModelFactory() throws Exception {
        Field f = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.class
                .getDeclaredField("_instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    private static void clearRepositories() throws Exception {
        Class<?>[] singletons = {UserRepository.class, ReadRepository.class,
                NoteRepository.class, IdeaRepository.class, VocabRepository.class,
                NotificationRepository.class, ReminderScheduler.class};
        for (Class<?> c : singletons) {
            synchronized (c) {
                Field f = c.getDeclaredField("INSTANCE");
                f.setAccessible(true);
                f.set(null, null);
            }
        }
    }

    /** Chờ hàng đợi CSDL, việc nặng và luồng giao diện chạy hết. */
    public static void drain() throws Exception {
        for (int i = 0; i < 5; i++) {
            waitIdle((ThreadPoolExecutor) AppExecutors.heavy());
            AppExecutors.diskIO().submit(() -> { }).get(30, TimeUnit.SECONDS);
            shadowOf(Looper.getMainLooper()).idle();
        }
    }

    /**
     * Chờ giao diện ổn định: Room báo thay đổi bảng (InvalidationTracker) trên executor riêng
     * của nó, nên ngoài việc chạy hết hàng đợi còn phải chờ thêm một khoảng ngắn.
     */
    public static void settle() throws Exception {
        for (int i = 0; i < 10; i++) {
            Thread.sleep(30);
            drain();
        }
    }

    /** Chờ một điều kiện (ví dụ LiveData của Room, chạy trên executor riêng của Room). */
    public static void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("Hết thời gian chờ");
            Thread.sleep(20);
            drain();
        }
    }

    private static void waitIdle(ThreadPoolExecutor pool) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 30_000;
        while ((pool.getActiveCount() > 0 || !pool.getQueue().isEmpty())
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
    }
}
