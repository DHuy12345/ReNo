package com.reno.app.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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

import java.io.File;
import java.io.FileOutputStream;

/**
 * Thời khóa biểu phải luôn còn chỗ hiển thị. Trước đây ô ngày của lịch tháng vuông (cao bằng
 * 1/7 chiều rộng) nên với tháng 6 hàng tuần, thời khóa biểu bị ép gần như mất hẳn.
 */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CalendarLayoutTest {
    private ActivityController<MainActivity> controller;

    @After
    public void tearDown() throws Exception {
        if (controller != null) controller.pause().stop().destroy();
        TestEnv.settle();
        TestEnv.flushSharedPreferences();
    }

    private MainActivity openCalendar(String qualifiers) throws Exception {
        RuntimeEnvironment.setQualifiers(qualifiers);
        TestEnv.reset();
        UserEntity u = new UserEntity();
        u.username = "huy";
        u.email = "huy@reno.test";
        u.passwordHash = "x";
        u.reviewTime = "20:00";
        long id = RenoDatabase.get(TestEnv.context()).userDao().insert(u);
        SessionManager.login(TestEnv.context(), id);
        controller = Robolectric.buildActivity(MainActivity.class,
                new Intent(TestEnv.context(), MainActivity.class)).setup();
        MainActivity main = controller.get();
        ((BottomNavigationView) main.findViewById(R.id.bottom_nav))
                .setSelectedItemId(R.id.nav_calendar);
        TestEnv.settle();
        return main;
    }

    private static float dp(View v, int px) {
        return px / v.getResources().getDisplayMetrics().density;
    }

    @Test
    public void mediumPhone_monthStaysOpen_timetableHasRoom() throws Exception {
        MainActivity main = openCalendar("w393dp-h851dp-port-xxhdpi");
        View month = main.findViewById(R.id.month_calendar);
        View timetable = main.findViewById(R.id.timetable);
        assertEquals(View.VISIBLE, month.getVisibility());
        // 6 hàng tuần × 44dp: không còn phụ thuộc chiều rộng màn hình
        assertTrue("lịch tháng cao " + dp(month, month.getHeight()) + "dp",
                dp(month, month.getHeight()) <= 6 * 44 + 1);
        assertTrue("thời khóa biểu chỉ cao " + dp(timetable, timetable.getHeight()) + "dp",
                dp(timetable, timetable.getHeight()) >= 200);
    }

    @Test
    public void smallPhone_monthAutoCollapses_toKeepTimetableVisible() throws Exception {
        MainActivity main = openCalendar("w360dp-h640dp-port-xhdpi");
        View timetable = main.findViewById(R.id.timetable);
        assertEquals(View.GONE, main.findViewById(R.id.month_calendar).getVisibility());
        assertEquals(View.VISIBLE, main.findViewById(R.id.week_calendar).getVisibility());
        assertTrue("thời khóa biểu chỉ cao " + dp(timetable, timetable.getHeight()) + "dp",
                dp(timetable, timetable.getHeight()) >= 200);

        // Người dùng tự mở rộng lại thì tôn trọng lựa chọn đó.
        main.findViewById(R.id.btn_collapse).performClick();
        TestEnv.settle();
        assertEquals(View.VISIBLE, main.findViewById(R.id.month_calendar).getVisibility());

        if (Boolean.getBoolean("reno.screenshots")) {
            main.findViewById(R.id.btn_collapse).performClick();
            TestEnv.settle();
            View root = main.getWindow().getDecorView();
            Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(),
                    Bitmap.Config.ARGB_8888);
            root.draw(new Canvas(bitmap));
            File dir = new File(System.getProperty("reno.screenshotDir", "build/screenshots"));
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir " + dir);
            try (FileOutputStream out = new FileOutputStream(new File(dir,
                    "calendar_small_phone.png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
        }
    }
}
