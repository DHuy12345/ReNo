package com.reno.app.data.db;

import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Các ràng buộc CHECK của đặc tả (mục 8.2). Room không sinh được CHECK từ chú thích,
 * nên mỗi ràng buộc được cài bằng một cặp trigger BEFORE INSERT / BEFORE UPDATE:
 * dữ liệu sai bị chặn ngay ở tầng CSDL, giống như CHECK.
 */
public final class DbConstraints {
    private DbConstraints() {
    }

    /** {tên bảng, tên ràng buộc, điều kiện VI PHẠM viết theo NEW.cột}. */
    static final String[][] CHECKS = {
            {"users", "contact", "NEW.email IS NULL AND NEW.phone IS NULL"},
            {"folders", "type", "NEW.type NOT IN ('READ','VOCAB')"},
            {"topics", "bg_index", "NEW.bg_index NOT BETWEEN 1 AND 8"},
            {"annotations", "type",
                    "NEW.type NOT IN ('QUESTION','IMPORTANT','STAR','HIGHLIGHT','NOTE')"},
            {"notes", "repeat_type",
                    "NEW.repeat_type NOT IN ('NONE','DAILY','INTERVAL','CUSTOM')"},
            {"notes", "duration", "NEW.duration_min <= 0"},
            {"note_occurrences", "status", "NEW.status NOT IN ('DONE','SKIPPED')"},
            {"word_reviews", "stage", "NEW.stage NOT BETWEEN 1 AND 5"},
            {"word_reviews", "status", "NEW.status NOT IN ('PENDING','DONE','SKIPPED')"},
            {"app_notifications", "kind", "NEW.kind NOT IN ('NOTE','VOCAB')"},
    };

    public static void install(SupportSQLiteDatabase db) {
        for (String[] c : CHECKS) {
            for (String event : new String[]{"INSERT", "UPDATE"}) {
                db.execSQL(triggerSql(c[0], c[1], c[2], event));
            }
        }
    }

    static String triggerSql(String table, String name, String violation, String event) {
        String trigger = "chk_" + table + "_" + name + "_" + event.toLowerCase();
        return "CREATE TRIGGER IF NOT EXISTS " + trigger
                + " BEFORE " + event + " ON " + table
                + " WHEN " + violation
                + " BEGIN SELECT RAISE(ABORT, 'CHECK constraint failed: " + table + "." + name
                + "'); END";
    }
}
