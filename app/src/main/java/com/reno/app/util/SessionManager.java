package com.reno.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Phiên đăng nhập và các lựa chọn nhỏ của người dùng, lưu bằng SharedPreferences. */
public final class SessionManager {
    private static final String PREFS = "reno_session";
    private static final String KEY_USER_ID = "current_user_id";
    private static final String KEY_IDEA_SORT_OLDEST = "idea_sort_oldest_first_";
    private static final String KEY_LAST_VOCAB_FOLDER = "last_vocab_folder_";
    private static final String KEY_HIDDEN_CALENDARS = "hidden_calendars_";
    private static final String KEY_ASKED_NOTIFICATION = "asked_notification_permission_";
    private static final String KEY_EXPLAINED_EXACT_ALARM = "explained_exact_alarm_";

    /** Giá trị trong tập "lịch bị ẩn" dành cho dòng Ôn tập từ vựng. */
    public static final String CALENDAR_VOCAB = "VOCAB";

    public static final long NO_USER = -1L;

    private SessionManager() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static long currentUserId(Context ctx) {
        return prefs(ctx).getLong(KEY_USER_ID, NO_USER);
    }

    public static boolean isLoggedIn(Context ctx) {
        return currentUserId(ctx) != NO_USER;
    }

    public static void login(Context ctx, long userId) {
        prefs(ctx).edit().putLong(KEY_USER_ID, userId).apply();
    }

    public static void logout(Context ctx) {
        prefs(ctx).edit().remove(KEY_USER_ID).apply();
    }

    public static boolean ideaOldestFirst(Context ctx, long userId) {
        return prefs(ctx).getBoolean(KEY_IDEA_SORT_OLDEST + userId, false);
    }

    public static void setIdeaOldestFirst(Context ctx, long userId, boolean oldestFirst) {
        prefs(ctx).edit().putBoolean(KEY_IDEA_SORT_OLDEST + userId, oldestFirst).apply();
    }

    public static long lastVocabFolder(Context ctx, long userId) {
        return prefs(ctx).getLong(KEY_LAST_VOCAB_FOLDER + userId, -1L);
    }

    public static void setLastVocabFolder(Context ctx, long userId, long folderId) {
        prefs(ctx).edit().putLong(KEY_LAST_VOCAB_FOLDER + userId, folderId).apply();
    }

    /** Tập các lịch đang bị ẩn trên thời khóa biểu: mã ghi chú dạng chuỗi, hoặc "VOCAB". */
    public static Set<String> hiddenCalendars(Context ctx, long userId) {
        return new HashSet<>(prefs(ctx).getStringSet(KEY_HIDDEN_CALENDARS + userId, new HashSet<>()));
    }

    public static void setHiddenCalendars(Context ctx, long userId, Set<String> hidden) {
        prefs(ctx).edit().putStringSet(KEY_HIDDEN_CALENDARS + userId, new HashSet<>(hidden)).apply();
    }

    public static boolean askedNotificationPermission(Context ctx, long userId) {
        return prefs(ctx).getBoolean(KEY_ASKED_NOTIFICATION + userId, false);
    }

    public static void setAskedNotificationPermission(Context ctx, long userId) {
        prefs(ctx).edit().putBoolean(KEY_ASKED_NOTIFICATION + userId, true).apply();
    }

    public static boolean explainedExactAlarm(Context ctx, long userId) {
        return prefs(ctx).getBoolean(KEY_EXPLAINED_EXACT_ALARM + userId, false);
    }

    public static void setExplainedExactAlarm(Context ctx, long userId) {
        prefs(ctx).edit().putBoolean(KEY_EXPLAINED_EXACT_ALARM + userId, true).apply();
    }
}
