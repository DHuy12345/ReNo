package com.reno.app.ui.notes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.reno.app.R;
import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.db.entity.NoteOccurrenceEntity;
import com.reno.app.data.model.Occurrence;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.databinding.ActivityNoteDetailBinding;
import com.reno.app.domain.RecurrenceHelper;
import com.reno.app.reminder.NotificationHelper;
import com.reno.app.ui.common.BaseActivity;
import com.reno.app.ui.common.DeepLink;
import com.reno.app.util.DateUtils;

import java.time.LocalDate;

/**
 * Màn chi tiết ghi chú – dùng chung cho Thông báo, Thống kê và Lịch: tên, ngày, giờ bắt đầu –
 * kết thúc, kiểu lặp, trạng thái, liên kết "Sửa ghi chú" và hai nút Xong / Bỏ qua.
 */
public class NoteDetailActivity extends BaseActivity {
    private ActivityNoteDetailBinding b;
    private long noteId;
    private LocalDate date;

    /**
     * @param dedupeKey khóa của thông báo đã dẫn tới màn này (để đánh dấu đã đọc); có thể null
     */
    public static Intent intent(Context ctx, long noteId, String date,
                                @Nullable String dedupeKey) {
        return DeepLink.put(new Intent(ctx, NoteDetailActivity.class), DeepLink.KIND_NOTE,
                noteId, date, null, dedupeKey);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;
        noteId = getIntent().getLongExtra(DeepLink.EXTRA_NOTE_ID, 0);
        try {
            date = LocalDate.parse(getIntent().getStringExtra(DeepLink.EXTRA_DATE));
        } catch (RuntimeException e) {
            finishToMain();
            return;
        }
        b = ActivityNoteDetailBinding.inflate(getLayoutInflater());
        setContentRoot(b.getRoot());
        b.toolbar.textTitle.setText(R.string.note_detail_title);
        b.toolbar.btnBack.setOnClickListener(v -> finishToMain());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishToMain();
            }
        });

        // Mở từ thông báo: đánh dấu đã đọc và gỡ thông báo hệ thống.
        String key = getIntent().getStringExtra(DeepLink.EXTRA_KEY);
        if (key == null) key = NotificationRepository.noteKey(noteId, date.toString());
        NotificationRepository.get(this).markReadByKey(userId, key);
        NotificationHelper.cancel(this, key);

        NoteDetailViewModel vm = new ViewModelProvider(this).get(NoteDetailViewModel.class);
        b.btnEdit.setOnClickListener(v ->
                startActivity(NoteFormActivity.intentForEdit(this, noteId)));
        // Xong: lưu DONE cho ngày đó, giống như tích ở tab Notes.
        b.btnDone.setOnClickListener(v ->
                vm.repo.setStatus(noteId, date, NoteOccurrenceEntity.STATUS_DONE));
        // Bỏ qua: lưu SKIPPED; lần xuất hiện này vào danh sách "Đã bỏ lỡ". Không dời lịch.
        b.btnSkip.setOnClickListener(v ->
                vm.repo.setStatus(noteId, date, NoteOccurrenceEntity.STATUS_SKIPPED));

        vm.occurrence(noteId, date).observe(this, result -> {
            if (!result.isPresent() || result.get().note.userId != userId) {
                android.widget.Toast.makeText(this, R.string.note_missing,
                        android.widget.Toast.LENGTH_SHORT).show();
                finishToMain();
                return;
            }
            bind(result.get());
        });
    }

    private void bind(Occurrence o) {
        b.textName.setText(o.note.title);
        b.viewColor.setBackgroundColor(NotesFragment.parseColor(o.note.color));
        b.textDate.setText(DateUtils.formatFullDate(o.date));
        b.textTime.setText(getString(R.string.notes_time_range,
                DateUtils.formatTime(o.startTime()),
                DateUtils.formatTime(o.end().toLocalTime())));
        b.textRepeat.setText(describeRepeat(this, o.note));

        int label = o.isDone() ? R.string.status_done
                : o.isSkipped() ? R.string.status_skipped : R.string.status_pending;
        b.textStatus.setText(label);
        b.textStatus.setTextColor(ContextCompat.getColor(this, o.isDone() ? R.color.moss
                : o.isSkipped() ? R.color.error : R.color.text_primary));

        // Hai nút bị ẩn với ngày tương lai.
        boolean future = o.date.isAfter(DateUtils.today());
        b.rowActions.setVisibility(future ? View.GONE : View.VISIBLE);
        b.btnDone.setEnabled(!o.isDone());
        b.btnSkip.setEnabled(!o.isSkipped());
    }

    /** "Không lặp lại", "Mỗi ngày", "Cách 3 ngày", "Các ngày tự chọn" (+ ngày kết thúc nếu có). */
    public static String describeRepeat(Context ctx, NoteEntity note) {
        String text;
        switch (note.repeatType) {
            case NoteEntity.REPEAT_DAILY:
                text = ctx.getString(R.string.note_repeat_daily);
                break;
            case NoteEntity.REPEAT_INTERVAL:
                text = ctx.getString(R.string.note_repeat_interval_value,
                        RecurrenceHelper.intervalOf(note.rrule));
                break;
            case NoteEntity.REPEAT_CUSTOM:
                text = ctx.getString(R.string.note_repeat_custom);
                break;
            default:
                return ctx.getString(R.string.note_repeat_none);
        }
        if (note.repeatUntil != null) {
            try {
                text += " " + ctx.getString(R.string.note_repeat_until_value,
                        DateUtils.formatFullDate(LocalDate.parse(note.repeatUntil)));
            } catch (RuntimeException ignored) {
                // ngày kết thúc hỏng: chỉ hiện kiểu lặp
            }
        }
        return text;
    }
}
