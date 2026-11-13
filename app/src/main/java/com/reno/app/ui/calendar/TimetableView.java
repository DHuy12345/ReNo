package com.reno.app.ui.calendar;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.OverScroller;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.reno.app.R;
import com.reno.app.data.model.Occurrence;
import com.reno.app.util.DateUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Thời khóa biểu tự vẽ bằng Canvas: cột giờ 0–24h bên trái, 7 cột ngày của tuần đang chọn,
 * màn hình thấy 3 cột. Cuộn hai chiều bằng GestureDetector + OverScroller. Ghi chú là khối chữ
 * nhật cao theo thời lượng, tô màu riêng; từ vựng là một vạch ngang xanh rêu tại mỗi giờ nhắc.
 */
public class TimetableView extends View {
    private static final int VISIBLE_DAYS = 3;
    private static final int DAYS = 7;

    /** Một mốc ôn từ vựng: "Ôn N từ" lúc time của ngày date. */
    public static final class VocabMark {
        public final LocalDate date;
        public final LocalTime time;
        public final int count;

        public VocabMark(LocalDate date, LocalTime time, int count) {
            this.date = date;
            this.time = time;
            this.count = count;
        }
    }

    public interface Listener {
        void onNoteClick(Occurrence occurrence);

        void onVocabClick(VocabMark mark);
    }

    private static final class Block {
        final Occurrence occurrence;
        final RectF rect = new RectF();   // toạ độ nội dung (chưa trừ cuộn)
        int column;
        int columns = 1;

        Block(Occurrence occurrence) {
            this.occurrence = occurrence;
        }
    }

    private final float density;
    private final float hourHeight;
    private final float timeColumnWidth;
    private final float headerHeight;
    private final Paint linePaint = new Paint();
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint vocabPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bgPaint = new Paint();
    private final Paint labelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint timeText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint headerText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint blockText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint vocabText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final OverScroller scroller;
    private final GestureDetector gestures;
    private final RectF tmp = new RectF();
    private final String[] weekdayLabels;

    private LocalDate weekStart = DateUtils.today();
    private LocalDate selected = DateUtils.today();
    private final List<Block> blocks = new ArrayList<>();
    private final List<VocabMark> marks = new ArrayList<>();
    @Nullable private Listener listener;
    private float scrollXf;
    private float scrollYf;
    private boolean initialScrollDone;

    public TimetableView(Context context) {
        this(context, null);
    }

    public TimetableView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        hourHeight = 56 * density;
        timeColumnWidth = 48 * density;
        headerHeight = 36 * density;

        int line = ContextCompat.getColor(context, R.color.line);
        int textPrimary = ContextCompat.getColor(context, R.color.text_primary);
        int textSecondary = ContextCompat.getColor(context, R.color.text_secondary);
        int moss = ContextCompat.getColor(context, R.color.moss);
        linePaint.setColor(line);
        linePaint.setStrokeWidth(1);
        bgPaint.setColor(ContextCompat.getColor(context, R.color.bg_main));
        vocabPaint.setColor(moss);
        vocabPaint.setStrokeWidth(2 * density);
        nowPaint.setColor(ContextCompat.getColor(context, R.color.timetable_now));
        nowPaint.setStrokeWidth(1.5f * density);
        timeText.setColor(textSecondary);
        timeText.setTextSize(11 * sp);
        timeText.setTextAlign(Paint.Align.RIGHT);
        headerText.setColor(textPrimary);
        headerText.setTextSize(13 * sp);
        headerText.setTextAlign(Paint.Align.CENTER);
        // Chữ màu chữ chính trên nền màu ghi chú, vì chữ trắng không đủ tương phản.
        blockText.setColor(textPrimary);
        blockText.setTextSize(12 * sp);
        vocabText.setColor(moss);
        vocabText.setTextSize(11 * sp);
        vocabText.setFakeBoldText(true);
        labelBgPaint.setColor(ContextCompat.getColor(context, R.color.moss_light));

        weekdayLabels = new String[]{context.getString(R.string.weekday_mon),
                context.getString(R.string.weekday_tue), context.getString(R.string.weekday_wed),
                context.getString(R.string.weekday_thu), context.getString(R.string.weekday_fri),
                context.getString(R.string.weekday_sat), context.getString(R.string.weekday_sun)};

        scroller = new OverScroller(context);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(@NonNull MotionEvent e) {
                scroller.forceFinished(true);
                return true;
            }

            @Override
            public boolean onScroll(@Nullable MotionEvent e1, @NonNull MotionEvent e2,
                                    float dx, float dy) {
                setContentScroll(scrollXf + dx, scrollYf + dy);
                return true;
            }

            @Override
            public boolean onFling(@Nullable MotionEvent e1, @NonNull MotionEvent e2,
                                   float vx, float vy) {
                scroller.fling(Math.round(scrollXf), Math.round(scrollYf),
                        Math.round(-vx), Math.round(-vy), 0, Math.round(maxScrollX()),
                        0, Math.round(maxScrollY()));
                postInvalidateOnAnimation();
                return true;
            }

            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                performClick();
                handleTap(e.getX(), e.getY());
                return true;
            }
        });
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    /** Đặt tuần hiển thị (thứ Hai đầu tuần), ngày đang chọn và dữ liệu đã lọc theo My calendars. */
    public void setData(LocalDate weekStart, LocalDate selected, List<Occurrence> notes,
                        List<VocabMark> vocab) {
        boolean weekChanged = !weekStart.equals(this.weekStart);
        this.weekStart = weekStart;
        this.selected = selected;
        marks.clear();
        marks.addAll(vocab);
        layoutBlocks(notes);
        if (weekChanged || !initialScrollDone) scrollToDay(selected);
        invalidate();
    }

    private float columnWidth() {
        return Math.max(1f, (getWidth() - timeColumnWidth) / VISIBLE_DAYS);
    }

    private float maxScrollX() {
        return Math.max(0f, columnWidth() * DAYS - (getWidth() - timeColumnWidth));
    }

    private float maxScrollY() {
        return Math.max(0f, hourHeight * 24 - (getHeight() - headerHeight));
    }

    /**
     * Đổi vị trí cuộn của NỘI DUNG thời khóa biểu (không phải View.scrollTo). Đặt tên khác hẳn
     * để không bị Java chọn nhầm View.scrollTo(int, int) khi truyền số nguyên — lỗi đó từng
     * dịch cả khung vẽ của view đi và làm thời khóa biểu "biến mất" khi buông tay.
     */
    private void setContentScroll(float x, float y) {
        scrollXf = Math.max(0f, Math.min(maxScrollX(), x));
        scrollYf = Math.max(0f, Math.min(maxScrollY(), y));
        invalidate();
    }

    /** Cuộn ngang để ngày được chọn nằm trong 3 cột đang thấy. */
    private void scrollToDay(LocalDate day) {
        if (getWidth() == 0) return;
        int index = (int) (day.toEpochDay() - weekStart.toEpochDay());
        index = Math.max(0, Math.min(DAYS - 1, index));
        int first = Math.max(0, Math.min(DAYS - VISIBLE_DAYS, index - 1));
        setContentScroll(first * columnWidth(), scrollYf);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (!initialScrollDone && w > 0 && h > 0) {
            initialScrollDone = true;
            // Mở màn thì tự cuộn tới giờ hiện tại.
            LocalTime now = LocalTime.now();
            float y = (now.getHour() + now.getMinute() / 60f) * hourHeight
                    - (h - headerHeight) / 3f;
            scrollYf = 0;
            setContentScroll(0, y);
            scrollToDay(selected);
        } else {
            setContentScroll(scrollXf, scrollYf);
        }
        rebuildRects();
    }

    /** Hai khối trùng giờ trong cùng một ngày thì chia đều chiều rộng cột. */
    private void layoutBlocks(List<Occurrence> notes) {
        blocks.clear();
        for (Occurrence o : notes) {
            long day = o.date.toEpochDay() - weekStart.toEpochDay();
            if (day >= 0 && day < DAYS) blocks.add(new Block(o));
        }
        blocks.sort((a, b) -> a.occurrence.start().compareTo(b.occurrence.start()));
        for (int d = 0; d < DAYS; d++) {
            LocalDate date = weekStart.plusDays(d);
            List<Block> cluster = new ArrayList<>();
            List<LocalDateTime> columnEnds = new ArrayList<>();
            LocalDateTime clusterEnd = null;
            for (Block block : blocks) {
                if (!block.occurrence.date.equals(date)) continue;
                LocalDateTime start = block.occurrence.start();
                if (clusterEnd != null && !start.isBefore(clusterEnd)) {
                    finishCluster(cluster, columnEnds.size());
                    cluster.clear();
                    columnEnds.clear();
                }
                int col = 0;
                while (col < columnEnds.size() && start.isBefore(columnEnds.get(col))) col++;
                if (col == columnEnds.size()) {
                    columnEnds.add(block.occurrence.end());
                } else {
                    columnEnds.set(col, block.occurrence.end());
                }
                block.column = col;
                cluster.add(block);
                LocalDateTime end = block.occurrence.end();
                clusterEnd = clusterEnd == null || end.isAfter(clusterEnd) ? end : clusterEnd;
            }
            finishCluster(cluster, columnEnds.size());
        }
        rebuildRects();
    }

    private static void finishCluster(List<Block> cluster, int columns) {
        for (Block b : cluster) b.columns = Math.max(1, columns);
    }

    private void rebuildRects() {
        float cw = columnWidth();
        for (Block block : blocks) {
            int day = (int) (block.occurrence.date.toEpochDay() - weekStart.toEpochDay());
            LocalTime start = block.occurrence.startTime();
            float top = (start.getHour() + start.getMinute() / 60f) * hourHeight;
            float minutes = Math.min(block.occurrence.note.durationMin,
                    24 * 60 - start.getHour() * 60 - start.getMinute());
            float height = Math.max(minutes / 60f * hourHeight, 20 * density);
            float width = cw / block.columns;
            float left = day * cw + block.column * width;
            block.rect.set(left + 1 * density, top + 1 * density,
                    left + width - 1 * density, top + height - 1 * density);
        }
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            setContentScroll(scroller.getCurrX(), scroller.getCurrY());
            postInvalidateOnAnimation();
        }
    }

    @SuppressLint("ClickableViewAccessibility")   // performClick() được gọi trong onSingleTapUp
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Không để bảng kéo hay ViewPager phía ngoài giành thao tác cuộn.
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return gestures.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        return super.performClick();   // để dịch vụ trợ năng nhận được thao tác chạm
    }

    private void handleTap(float x, float y) {
        if (listener == null || x < timeColumnWidth || y < headerHeight) return;
        float cx = x - timeColumnWidth + scrollXf;
        float cy = y - headerHeight + scrollYf;
        for (Block block : blocks) {
            if (block.rect.contains(cx, cy)) {
                listener.onNoteClick(block.occurrence);
                return;
            }
        }
        float cw = columnWidth();
        float slop = 12 * density;
        for (VocabMark mark : marks) {
            int day = (int) (mark.date.toEpochDay() - weekStart.toEpochDay());
            float my = (mark.time.getHour() + mark.time.getMinute() / 60f) * hourHeight;
            if (cx >= day * cw && cx <= (day + 1) * cw && Math.abs(cy - my) <= slop) {
                listener.onVocabClick(mark);
                return;
            }
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float cw = columnWidth();
        int w = getWidth();
        int h = getHeight();

        // --- vùng nội dung (lưới, khối, vạch)
        canvas.save();
        canvas.clipRect(timeColumnWidth, headerHeight, w, h);
        canvas.translate(timeColumnWidth - scrollXf, headerHeight - scrollYf);
        for (int hour = 0; hour <= 24; hour++) {
            float y = hour * hourHeight;
            canvas.drawLine(0, y, cw * DAYS, y, linePaint);
        }
        for (int d = 0; d <= DAYS; d++) {
            canvas.drawLine(d * cw, 0, d * cw, hourHeight * 24, linePaint);
        }
        for (Block block : blocks) {
            fillPaint.setColor(parse(block.occurrence.note.color));
            fillPaint.setAlpha(block.occurrence.isDone() ? 110 : 255);
            canvas.drawRoundRect(block.rect, 6 * density, 6 * density, fillPaint);
            drawBlockText(canvas, block);
        }
        for (VocabMark mark : marks) {
            int day = (int) (mark.date.toEpochDay() - weekStart.toEpochDay());
            if (day < 0 || day >= DAYS) continue;
            float y = (mark.time.getHour() + mark.time.getMinute() / 60f) * hourHeight;
            canvas.drawLine(day * cw + 2 * density, y, (day + 1) * cw - 2 * density, y,
                    vocabPaint);
            String label = getContext().getString(R.string.calendar_vocab_line, mark.count);
            float textWidth = vocabText.measureText(label);
            float right = (day + 1) * cw - 4 * density;
            tmp.set(right - textWidth - 8 * density, y - vocabText.getTextSize() - 6 * density,
                    right, y - 1 * density);
            canvas.drawRoundRect(tmp, 4 * density, 4 * density, labelBgPaint);
            canvas.drawText(label, right - textWidth - 4 * density, y - 4 * density, vocabText);
        }
        // Vạch đỏ mảnh đánh dấu giờ hiện tại.
        LocalDate today = DateUtils.today();
        int todayIndex = (int) (today.toEpochDay() - weekStart.toEpochDay());
        if (todayIndex >= 0 && todayIndex < DAYS) {
            LocalTime now = LocalTime.now();
            float y = (now.getHour() + now.getMinute() / 60f) * hourHeight;
            canvas.drawLine(todayIndex * cw, y, (todayIndex + 1) * cw, y, nowPaint);
            canvas.drawCircle(todayIndex * cw, y, 3 * density, nowPaint);
        }
        canvas.restore();

        // --- cột giờ bên trái
        canvas.drawRect(0, headerHeight, timeColumnWidth, h, bgPaint);
        canvas.save();
        canvas.clipRect(0, headerHeight, timeColumnWidth, h);
        canvas.translate(0, headerHeight - scrollYf);
        for (int hour = 0; hour <= 24; hour++) {
            float y = hour * hourHeight;
            canvas.drawText(String.format(Locale.ROOT, "%02d:00", hour),
                    timeColumnWidth - 6 * density, y + timeText.getTextSize() / 2.5f, timeText);
        }
        canvas.restore();

        // --- hàng tiêu đề ngày
        canvas.drawRect(0, 0, w, headerHeight, bgPaint);
        canvas.save();
        canvas.clipRect(timeColumnWidth, 0, w, headerHeight);
        canvas.translate(timeColumnWidth - scrollXf, 0);
        for (int d = 0; d < DAYS; d++) {
            LocalDate date = weekStart.plusDays(d);
            boolean isSelected = date.equals(selected);
            if (isSelected) {
                fillPaint.setColor(ContextCompat.getColor(getContext(), R.color.moss_light));
                fillPaint.setAlpha(255);
                tmp.set(d * cw + 4 * density, 4 * density, (d + 1) * cw - 4 * density,
                        headerHeight - 4 * density);
                canvas.drawRoundRect(tmp, 8 * density, 8 * density, fillPaint);
            }
            headerText.setFakeBoldText(date.equals(today));
            headerText.setColor(ContextCompat.getColor(getContext(),
                    date.equals(today) ? R.color.moss : R.color.text_primary));
            String label = weekdayLabels[d] + " " + DateUtils.formatDayMonth(date);
            canvas.drawText(label, d * cw + cw / 2, headerHeight / 2 + headerText.getTextSize() / 3,
                    headerText);
        }
        canvas.restore();
        canvas.drawLine(0, headerHeight, w, headerHeight, linePaint);
    }

    private void drawBlockText(Canvas canvas, Block block) {
        RectF r = block.rect;
        float pad = 4 * density;
        float available = r.width() - 2 * pad;
        if (available <= 0) return;
        float lineHeight = blockText.getTextSize() * 1.2f;
        float y = r.top + pad + blockText.getTextSize();
        blockText.setFakeBoldText(true);
        CharSequence title = TextUtils.ellipsize(block.occurrence.note.title, blockText,
                available, TextUtils.TruncateAt.END);
        canvas.drawText(title, 0, title.length(), r.left + pad, y, blockText);
        blockText.setFakeBoldText(false);
        if (y + lineHeight <= r.bottom - pad / 2) {
            String time = DateUtils.formatTime(block.occurrence.startTime()) + " – "
                    + DateUtils.formatTime(block.occurrence.end().toLocalTime());
            CharSequence t = TextUtils.ellipsize(time, blockText, available,
                    TextUtils.TruncateAt.END);
            canvas.drawText(t, 0, t.length(), r.left + pad, y + lineHeight, blockText);
        }
    }

    private static int parse(String color) {
        try {
            return Color.parseColor(color);
        } catch (IllegalArgumentException e) {
            return Color.GRAY;
        }
    }
}
