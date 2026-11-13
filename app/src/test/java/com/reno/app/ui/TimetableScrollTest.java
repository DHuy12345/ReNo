package com.reno.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.reno.app.R;
import com.reno.app.TestEnv;
import com.reno.app.data.db.RenoDatabase;
import com.reno.app.data.db.entity.UserEntity;
import com.reno.app.ui.main.MainActivity;
import com.reno.app.util.SessionManager;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.GraphicsMode;

import java.lang.reflect.Field;
import java.time.Duration;

/**
 * Kéo / vuốt thời khóa biểu rồi buông tay: nội dung phải cuộn theo, còn khung vẽ của view thì
 * không được dịch đi (lỗi cũ: View.scrollTo bị gọi nhầm khi trượt quán tính, thời khóa biểu
 * trắng trơn sau khi buông tay).
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class TimetableScrollTest {
    private ActivityController<MainActivity> controller;

    @After
    public void tearDown() throws Exception {
        if (controller != null) controller.pause().stop().destroy();
        TestEnv.settle();
        TestEnv.flushSharedPreferences();
    }

    private static float field(View v, String name) throws Exception {
        Field f = v.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (float) f.get(v);
    }

    private static void swipe(View root, float x0, float y0, float x1, float y1) {
        long down = SystemClock.uptimeMillis();
        root.dispatchTouchEvent(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN,
                x0, y0, 0));
        for (int i = 1; i <= 3; i++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10));
            float t = i / 3f;
            root.dispatchTouchEvent(MotionEvent.obtain(down, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_MOVE, x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, 0));
        }
        root.dispatchTouchEvent(MotionEvent.obtain(down, SystemClock.uptimeMillis(),
                MotionEvent.ACTION_UP, x1, y1, 0));
        // để hiệu ứng trượt quán tính chạy hết
        for (int i = 0; i < 40; i++) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50));
        }
    }

    /** Đếm điểm ảnh khác màu nền trong vùng thời khóa biểu: nội dung còn được vẽ hay không. */
    private static int drawnPixels(View root, View timetable) {
        Bitmap bmp = Bitmap.createBitmap(root.getWidth(), root.getHeight(),
                Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bmp));
        int[] loc = new int[2];
        timetable.getLocationInWindow(loc);
        int background = bmp.getPixel(loc[0] + timetable.getWidth() - 2, loc[1] + 2);
        int count = 0;
        for (int y = loc[1]; y < loc[1] + timetable.getHeight(); y += 4) {
            for (int x = loc[0]; x < loc[0] + timetable.getWidth(); x += 4) {
                if (bmp.getPixel(x, y) != background) count++;
            }
        }
        return count;
    }

    @Test
    public void flingAndRelease_keepsTimetableDrawn() throws Exception {
        RuntimeEnvironment.setQualifiers("w393dp-h851dp-port-xxhdpi");
        TestEnv.reset();
        UserEntity u = new UserEntity();
        u.username = "huy";
        u.email = "huy@reno.test";
        u.passwordHash = "x";
        u.reviewTime = "20:00";
        long id = RenoDatabase.get(TestEnv.context()).userDao().insert(u);
        SessionManager.login(TestEnv.context(), id);
        controller = Robolectric.buildActivity(MainActivity.class,
                new Intent(TestEnv.context(), MainActivity.class)).setup().visible();
        MainActivity main = controller.get();
        ((BottomNavigationView) main.findViewById(R.id.bottom_nav))
                .setSelectedItemId(R.id.nav_calendar);
        TestEnv.settle();

        View root = main.getWindow().getDecorView();
        View timetable = main.findViewById(R.id.timetable);
        int[] loc = new int[2];
        timetable.getLocationInWindow(loc);
        float cx = loc[0] + timetable.getWidth() * 0.6f;
        float cy = loc[1] + timetable.getHeight() * 0.5f;
        int before = drawnPixels(root, timetable);
        assertTrue("thời khóa biểu phải có nội dung trước khi kéo", before > 100);

        float x0 = field(timetable, "scrollXf");
        float y0 = field(timetable, "scrollYf");
        swipe(root, cx, cy, cx - 400, cy - 400);       // vuốt nhanh chéo rồi buông

        assertEquals("khung vẽ của view không được bị dịch", 0, timetable.getScrollX());
        assertEquals("khung vẽ của view không được bị dịch", 0, timetable.getScrollY());
        assertNotEquals("nội dung phải cuộn ngang", x0, field(timetable, "scrollXf"), 0.5f);
        assertNotEquals("nội dung phải cuộn dọc", y0, field(timetable, "scrollYf"), 0.5f);
        int after = drawnPixels(root, timetable);
        assertTrue("thời khóa biểu trắng trơn sau khi buông tay (" + after + " điểm ảnh)",
                after > before / 2);
    }
}
