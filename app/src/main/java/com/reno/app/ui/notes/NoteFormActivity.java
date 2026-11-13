package com.reno.app.ui.notes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.reno.app.R;
import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.repository.NoteRepository;
import com.reno.app.databinding.ActivityNoteFormBinding;
import com.reno.app.domain.RecurrenceHelper;
import com.reno.app.ui.common.BaseActivity;
import com.reno.app.ui.common.Pickers;
import com.reno.app.util.DateUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

/** Màn thêm / sửa ghi chú: tên, ngày giờ bắt đầu, thời lượng, màu, kiểu lặp, ngày bỏ qua. */
public class NoteFormActivity extends BaseActivity {
    private static final String EXTRA_NOTE_ID = "note_id";
    private static final String EXTRA_DATE = "date";
    private static final int DEFAULT_DURATION = 30;
    private static final int DURATION_MIN = 5;
    private static final int DURATION_MAX = 720;
    private static final int INTERVAL_MIN = 2;
    private static final int INTERVAL_MAX = 65;
    private static final int PREVIEW_COUNT = 5;

    private ActivityNoteFormBinding b;
    private NoteFormViewModel vm;
    private NoteRepository repo;

    /** Thêm mới; ngày mặc định là hôm nay, hoặc ngày đang xem nếu là ngày sau. */
    public static Intent intentForNew(Context ctx, LocalDate viewingDate) {
        return new Intent(ctx, NoteFormActivity.class)
                .putExtra(EXTRA_DATE, viewingDate.toString());
    }

    public static Intent intentForEdit(Context ctx, long noteId) {
        return new Intent(ctx, NoteFormActivity.class).putExtra(EXTRA_NOTE_ID, noteId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;
        b = ActivityNoteFormBinding.inflate(getLayoutInflater());
        setContentRoot(b.getRoot());
        vm = new ViewModelProvider(this).get(NoteFormViewModel.class);
        repo = NoteRepository.get(this);

        long noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, 0);
        b.toolbar.textTitle.setText(noteId == 0 ? R.string.note_add_title
                : R.string.note_edit_title);
        b.toolbar.btnBack.setOnClickListener(v -> finish());
        b.btnSave.setOnClickListener(v -> save());
        setUpListeners();

        if (vm.initialized) {
            bindState();
        } else if (noteId == 0) {
            initNew();
        } else {
            b.btnSave.setEnabled(false);
            repo.find(noteId, note -> {
                if (note == null || note.userId != userId) {
                    android.widget.Toast.makeText(this, R.string.note_missing,
                            android.widget.Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                initEdit(note);
            });
        }
    }

    private void initNew() {
        LocalDate today = DateUtils.today();
        LocalDate viewing = today;
        String extra = getIntent().getStringExtra(EXTRA_DATE);
        if (extra != null) {
            try {
                viewing = LocalDate.parse(extra);
            } catch (RuntimeException ignored) {
                // giữ hôm nay
            }
        }
        vm.date = viewing.isAfter(today) ? viewing : today;
        vm.time = LocalTime.now().withSecond(0).withNano(0);   // thời điểm hiện tại
        vm.color = NoteRepository.NOTE_COLORS[0];
        vm.initialized = true;
        b.editDuration.setText(String.valueOf(DEFAULT_DURATION));   // mặc định 30 phút
        bindState();
        // Màu mặc định: ngẫu nhiên, ưu tiên màu chưa dùng.
        repo.suggestColor(userId, color -> {
            vm.color = color;
            b.palette.setSelected(color);
        });
    }

    private void initEdit(NoteEntity note) {
        LocalDateTime start = DateUtils.toLocalDateTime(note.dtstart);
        vm.editing = note;
        vm.date = start.toLocalDate();
        vm.time = start.toLocalTime();
        vm.originalDate = vm.date;
        vm.originalTime = vm.time;
        vm.color = note.color;
        vm.repeatType = note.repeatType;
        vm.repeatUntil = note.repeatUntil == null ? null : LocalDate.parse(note.repeatUntil);
        vm.exdates.addAll(DateUtils.parseDateList(note.exdate));
        vm.rdates.addAll(DateUtils.parseDateList(note.rdate));
        vm.initialized = true;
        b.editTitle.setText(note.title);
        b.editDuration.setText(String.valueOf(note.durationMin));
        if (NoteEntity.REPEAT_INTERVAL.equals(note.repeatType)) {
            b.editInterval.setText(String.valueOf(RecurrenceHelper.intervalOf(note.rrule)));
        }
        b.btnSave.setEnabled(true);
        bindState();
    }

    private void setUpListeners() {
        b.btnDate.setOnClickListener(v -> Pickers.pickDate(getSupportFragmentManager(), vm.date,
                null, date -> {
                    vm.date = date;
                    bindState();
                }));
        b.btnTime.setOnClickListener(v -> Pickers.pickTime(getSupportFragmentManager(), vm.time,
                time -> {
                    vm.time = time;
                    bindState();
                }));
        b.palette.setOnColorSelected(color -> vm.color = color);
        b.groupRepeat.setOnCheckedChangeListener((group, checkedId) -> {
            if (!vm.initialized) return;
            if (checkedId == R.id.radio_daily) {
                vm.repeatType = NoteEntity.REPEAT_DAILY;
            } else if (checkedId == R.id.radio_interval) {
                vm.repeatType = NoteEntity.REPEAT_INTERVAL;
            } else if (checkedId == R.id.radio_custom) {
                vm.repeatType = NoteEntity.REPEAT_CUSTOM;
            } else {
                vm.repeatType = NoteEntity.REPEAT_NONE;
            }
            bindRepeatSections();
        });
        b.btnUntil.setOnClickListener(v -> Pickers.pickDate(getSupportFragmentManager(),
                vm.repeatUntil == null ? vm.date : vm.repeatUntil, vm.date, date -> {
                    vm.repeatUntil = date;
                    bindRepeatSections();
                }));
        b.btnUntilClear.setOnClickListener(v -> {
            vm.repeatUntil = null;
            bindRepeatSections();
        });
        b.editInterval.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (vm.initialized) bindPreview();
            }
        });
    }

    /** Đưa trạng thái trong ViewModel lên giao diện. */
    private void bindState() {
        b.btnDate.setText(DateUtils.formatFullDate(vm.date));
        b.btnTime.setText(DateUtils.formatTime(vm.time));
        b.palette.setColors(NoteRepository.NOTE_COLORS, vm.color);
        int radio = R.id.radio_none;
        if (NoteEntity.REPEAT_DAILY.equals(vm.repeatType)) {
            radio = R.id.radio_daily;
        } else if (NoteEntity.REPEAT_INTERVAL.equals(vm.repeatType)) {
            radio = R.id.radio_interval;
        } else if (NoteEntity.REPEAT_CUSTOM.equals(vm.repeatType)) {
            radio = R.id.radio_custom;
        }
        if (b.groupRepeat.getCheckedRadioButtonId() != radio) {
            b.groupRepeat.check(radio);
            b.groupRepeat.jumpDrawablesToCurrentState();
        }
        bindRepeatSections();
    }

    private void bindRepeatSections() {
        boolean rule = NoteEntity.REPEAT_DAILY.equals(vm.repeatType)
                || NoteEntity.REPEAT_INTERVAL.equals(vm.repeatType);
        boolean custom = NoteEntity.REPEAT_CUSTOM.equals(vm.repeatType);
        b.tilInterval.setVisibility(NoteEntity.REPEAT_INTERVAL.equals(vm.repeatType)
                ? View.VISIBLE : View.GONE);
        b.sectionRule.setVisibility(rule ? View.VISIBLE : View.GONE);
        b.sectionCustom.setVisibility(custom ? View.VISIBLE : View.GONE);
        b.btnUntil.setText(vm.repeatUntil == null ? getString(R.string.note_repeat_unlimited)
                : DateUtils.formatFullDate(vm.repeatUntil));
        b.btnUntilClear.setVisibility(vm.repeatUntil == null ? View.INVISIBLE : View.VISIBLE);
        b.textUntilError.setVisibility(View.GONE);
        bindChips(b.chipsExdate, vm.exdates);
        bindChips(b.chipsRdate, vm.rdates);
        bindPreview();
    }

    /** Danh sách ngày dạng chip; chip cuối "+ Thêm ngày" mở DatePicker. */
    private void bindChips(ChipGroup group, List<LocalDate> dates) {
        group.removeAllViews();
        Collections.sort(dates);
        for (LocalDate d : dates) {
            Chip chip = new Chip(this);
            chip.setText(DateUtils.formatFullDate(d));
            chip.setCloseIconVisible(true);
            chip.setOnCloseIconClickListener(v -> {
                dates.remove(d);
                bindRepeatSections();
            });
            group.addView(chip);
        }
        Chip add = new Chip(this);
        add.setText(R.string.note_add_date);
        add.setOnClickListener(v -> Pickers.pickDate(getSupportFragmentManager(),
                vm.date.plusDays(1), vm.date, date -> {
                    if (!dates.contains(date) && !date.equals(vm.date)) dates.add(date);
                    bindRepeatSections();
                }));
        group.addView(add);
    }

    /** Dòng xem trước các lần xuất hiện sắp tới theo quy luật đang nhập. */
    private void bindPreview() {
        LocalDate from = vm.date.isBefore(DateUtils.today()) ? DateUtils.today() : vm.date;
        List<LocalDate> dates = RecurrenceHelper.occurrenceDates(vm.date, currentRrule(),
                currentUntil(), currentRdate(), currentExdate(), from, from.plusDays(366));
        if (dates.isEmpty()) {
            b.textPreview.setText(R.string.note_preview_none);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dates.size() && i < PREVIEW_COUNT; i++) {
            if (i > 0) sb.append(", ");
            sb.append(DateUtils.formatDayMonth(dates.get(i)));
        }
        if (dates.size() > PREVIEW_COUNT) sb.append(", …");
        b.textPreview.setText(getString(R.string.note_preview, sb.toString()));
    }

    private int parseInt(@Nullable CharSequence cs) {
        try {
            return Integer.parseInt(cs == null ? "" : cs.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Nullable
    private String currentRrule() {
        if (NoteEntity.REPEAT_DAILY.equals(vm.repeatType)) return RecurrenceHelper.dailyRule(1);
        if (NoteEntity.REPEAT_INTERVAL.equals(vm.repeatType)) {
            int n = parseInt(b.editInterval.getText());
            return n >= INTERVAL_MIN && n <= INTERVAL_MAX ? RecurrenceHelper.dailyRule(n) : null;
        }
        return null;
    }

    private boolean hasRule() {
        return NoteEntity.REPEAT_DAILY.equals(vm.repeatType)
                || NoteEntity.REPEAT_INTERVAL.equals(vm.repeatType);
    }

    @Nullable
    private String currentUntil() {
        return hasRule() && vm.repeatUntil != null ? vm.repeatUntil.toString() : null;
    }

    @Nullable
    private String currentExdate() {
        return hasRule() ? DateUtils.joinDateList(vm.exdates) : null;
    }

    @Nullable
    private String currentRdate() {
        return NoteEntity.REPEAT_CUSTOM.equals(vm.repeatType)
                ? DateUtils.joinDateList(vm.rdates) : null;
    }

    private void save() {
        if (!vm.initialized) return;
        boolean ok = true;
        String title = b.editTitle.getText() == null ? ""
                : b.editTitle.getText().toString().trim();
        b.tilTitle.setError(title.isEmpty() ? getString(R.string.error_note_name_empty) : null);
        ok &= !title.isEmpty();

        // Ngày giờ bắt đầu không được trước hiện tại – chỉ kiểm tra khi tạo mới
        // hoặc khi người dùng đổi trường này.
        LocalDateTime start = LocalDateTime.of(vm.date, vm.time);
        boolean startChanged = vm.editing == null
                || !vm.date.equals(vm.originalDate) || !vm.time.equals(vm.originalTime);
        boolean startPast = startChanged
                && start.isBefore(DateUtils.now().withSecond(0).withNano(0));
        b.textStartError.setText(R.string.error_note_start_past);
        b.textStartError.setVisibility(startPast ? View.VISIBLE : View.GONE);
        ok &= !startPast;

        int duration = parseInt(b.editDuration.getText());
        boolean durationOk = duration >= DURATION_MIN && duration <= DURATION_MAX;
        b.tilDuration.setError(durationOk ? null : getString(R.string.error_note_duration));
        ok &= durationOk;

        if (NoteEntity.REPEAT_INTERVAL.equals(vm.repeatType)) {
            int n = parseInt(b.editInterval.getText());
            boolean intervalOk = n >= INTERVAL_MIN && n <= INTERVAL_MAX;
            b.tilInterval.setError(intervalOk ? null : getString(R.string.error_note_interval));
            ok &= intervalOk;
        }
        boolean untilBad = hasRule() && vm.repeatUntil != null
                && vm.repeatUntil.isBefore(vm.date);
        b.textUntilError.setVisibility(untilBad ? View.VISIBLE : View.GONE);
        ok &= !untilBad;
        if (!ok) return;

        NoteEntity note = vm.editing != null ? vm.editing : new NoteEntity();
        note.userId = userId;
        note.title = title;
        note.color = vm.color;
        note.dtstart = DateUtils.toEpochMs(start);
        note.durationMin = duration;
        note.repeatType = vm.repeatType;
        note.rrule = currentRrule();
        note.repeatUntil = currentUntil();      // cột riêng, không ghi UNTIL vào RRULE
        note.exdate = currentExdate();
        note.rdate = currentRdate();
        b.btnSave.setEnabled(false);
        repo.save(note, this::finish);
    }
}
