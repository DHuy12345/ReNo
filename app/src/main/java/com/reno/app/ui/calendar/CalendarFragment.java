package com.reno.app.ui.calendar;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.kizitonwose.calendar.core.CalendarDay;
import com.kizitonwose.calendar.core.CalendarMonth;
import com.kizitonwose.calendar.core.DayPosition;
import com.kizitonwose.calendar.core.Week;
import com.kizitonwose.calendar.core.WeekDay;
import com.kizitonwose.calendar.view.MonthDayBinder;
import com.kizitonwose.calendar.view.ViewContainer;
import com.kizitonwose.calendar.view.WeekDayBinder;
import com.reno.app.R;
import com.reno.app.data.db.entity.NoteEntity;
import com.reno.app.data.model.Occurrence;
import com.reno.app.databinding.CalendarDayBinding;
import com.reno.app.databinding.FragmentCalendarBinding;
import com.reno.app.databinding.ItemMyCalendarBinding;
import com.reno.app.ui.notes.NoteDetailActivity;
import com.reno.app.ui.notes.NotesFragmentColors;
import com.reno.app.ui.vocab.ReviewActivity;
import com.reno.app.util.DateUtils;
import com.reno.app.util.SessionManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;

import kotlin.Unit;

/**
 * Mục Lịch: lịch tháng ở trên (thu gọn được thành một hàng tuần), thời khóa biểu 3 cột cuộn hai
 * chiều chiếm phần còn lại, và bảng My calendars kéo từ đáy màn.
 */
public class CalendarFragment extends Fragment {
    private static final int MONTH_RANGE = 24;

    private FragmentCalendarBinding b;
    private CalendarViewModel vm;
    @Nullable private CalendarViewModel.Data data;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentCalendarBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(CalendarViewModel.class);
        setUpWeekdayHeader();
        setUpCalendars();
        setUpSheet();

        b.timetable.setListener(new TimetableView.Listener() {
            @Override
            public void onNoteClick(Occurrence o) {
                startActivity(NoteDetailActivity.intent(requireContext(), o.note.id,
                        o.date.toString(), null));
            }

            @Override
            public void onVocabClick(TimetableView.VocabMark mark) {
                startActivity(ReviewActivity.intentForGroup(requireContext(),
                        mark.date.toString(), CalendarViewModel.timeKey(mark.time), null));
            }
        });
        b.btnCollapse.setOnClickListener(v -> {
            vm.collapsed = !vm.collapsed;
            if (!vm.collapsed) vm.userExpanded = true;
            applyCollapsed();
        });
        // Màn hình thấp (hoặc tháng có 6 hàng tuần): lịch tháng chiếm gần hết chỗ và thời khóa
        // biểu bị ép mất. Khi đó tự thu gọn lịch về một hàng tuần, trừ khi người dùng đã tự
        // mở rộng lại.
        b.timetable.addOnLayoutChangeListener((v, l, t, r, bottom, ol, ot, or, ob) -> {
            if (vm.collapsed || vm.userExpanded || b == null) return;
            int min = getResources().getDimensionPixelSize(R.dimen.timetable_min_height);
            if (bottom - t < min) {
                vm.collapsed = true;
                v.post(() -> {
                    if (b != null) applyCollapsed();
                });
            }
        });
        b.btnToday.setOnClickListener(v -> selectDate(DateUtils.today(), true));
        b.btnPrev.setOnClickListener(v -> step(-1));
        b.btnNext.setOnClickListener(v -> step(1));
        applyCollapsed();

        vm.data().observe(getViewLifecycleOwner(), d -> {
            data = d;
            b.monthCalendar.notifyCalendarChanged();
            b.weekCalendar.notifyCalendarChanged();
            b.timetable.setData(d.weekStart, d.selected, d.weekNotes, d.weekVocab);
            bindMyCalendars(d);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void setUpWeekdayHeader() {
        int[] labels = {R.string.weekday_mon, R.string.weekday_tue, R.string.weekday_wed,
                R.string.weekday_thu, R.string.weekday_fri, R.string.weekday_sat,
                R.string.weekday_sun};
        for (int label : labels) {
            TextView tv = new TextView(requireContext());
            TextViewCompat.setTextAppearance(tv, R.style.TextAppearance_Reno_Caption);
            tv.setText(label);
            tv.setGravity(android.view.Gravity.CENTER);
            b.rowWeekdays.addView(tv, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
    }

    private static final class DayContainer extends ViewContainer {
        final CalendarDayBinding b;
        LocalDate date;

        DayContainer(View view) {
            super(view);
            b = CalendarDayBinding.bind(view);
        }
    }

    private void setUpCalendars() {
        YearMonth current = YearMonth.now();
        b.monthCalendar.setDayBinder(new MonthDayBinder<DayContainer>() {
            @NonNull
            @Override
            public DayContainer create(@NonNull View view) {
                DayContainer c = new DayContainer(view);
                view.setOnClickListener(v -> {
                    if (c.date != null) selectDate(c.date, false);
                });
                return c;
            }

            @Override
            public void bind(@NonNull DayContainer c, CalendarDay day) {
                bindDay(c, day.getDate(), day.getPosition() == DayPosition.MonthDate);
            }
        });
        b.monthCalendar.setMonthScrollListener((CalendarMonth month) -> {
            vm.setMonth(month.getYearMonth());
            updateTitle();
            return Unit.INSTANCE;
        });
        b.monthCalendar.setup(current.minusMonths(MONTH_RANGE), current.plusMonths(MONTH_RANGE),
                DayOfWeek.MONDAY);
        b.monthCalendar.scrollToMonth(vm.visibleMonth());

        b.weekCalendar.setDayBinder(new WeekDayBinder<DayContainer>() {
            @NonNull
            @Override
            public DayContainer create(@NonNull View view) {
                DayContainer c = new DayContainer(view);
                view.setOnClickListener(v -> {
                    if (c.date != null) selectDate(c.date, false);
                });
                return c;
            }

            @Override
            public void bind(@NonNull DayContainer c, WeekDay day) {
                bindDay(c, day.getDate(), true);
            }
        });
        b.weekCalendar.setWeekScrollListener((Week week) -> {
            LocalDate first = week.getDays().get(0).getDate();
            vm.setMonth(YearMonth.from(first.plusDays(3)));
            updateTitle();
            return Unit.INSTANCE;
        });
        b.weekCalendar.setup(current.minusMonths(MONTH_RANGE).atDay(1),
                current.plusMonths(MONTH_RANGE).atEndOfMonth(), DayOfWeek.MONDAY);
        b.weekCalendar.scrollToWeek(vm.selectedDate());
        updateTitle();
    }

    private void bindDay(DayContainer c, LocalDate date, boolean inMonth) {
        c.date = date;
        c.b.textDay.setText(String.valueOf(date.getDayOfMonth()));
        boolean selected = date.equals(vm.selectedDate());
        boolean today = date.equals(DateUtils.today());
        c.b.textDay.setBackgroundResource(selected ? R.drawable.bg_day_selected
                : today ? R.drawable.bg_day_today : 0);
        int color = selected ? R.color.white : inMonth ? R.color.text_primary : R.color.outline;
        c.b.textDay.setTextColor(ContextCompat.getColor(requireContext(), color));
        boolean hasNote = data != null && data.noteDays.contains(date);
        boolean hasVocab = data != null && data.vocabDays.contains(date);
        c.b.dotNote.setVisibility(hasNote ? View.VISIBLE : View.GONE);
        c.b.dotVocab.setVisibility(hasVocab ? View.VISIBLE : View.GONE);
        c.getView().setAlpha(inMonth ? 1f : 0.6f);
    }

    private void selectDate(LocalDate date, boolean scroll) {
        LocalDate old = vm.selectedDate();
        vm.select(date);
        b.monthCalendar.notifyDateChanged(old);
        b.monthCalendar.notifyDateChanged(date);
        b.weekCalendar.notifyCalendarChanged();
        if (scroll || !YearMonth.from(date).equals(vm.visibleMonth())) {
            b.monthCalendar.smoothScrollToMonth(YearMonth.from(date));
            b.weekCalendar.smoothScrollToWeek(date);
        }
    }

    /** Nút trước / sau: theo tháng khi mở rộng, theo tuần khi thu gọn. */
    private void step(int direction) {
        if (vm.collapsed) {
            selectDate(vm.selectedDate().plusWeeks(direction), true);
        } else {
            YearMonth next = vm.visibleMonth().plusMonths(direction);
            b.monthCalendar.smoothScrollToMonth(next);
        }
    }

    /** Thu gọn: lịch chỉ còn một hàng tuần, nhường chỗ cho thời khóa biểu. */
    private void applyCollapsed() {
        b.monthCalendar.setVisibility(vm.collapsed ? View.GONE : View.VISIBLE);
        b.weekCalendar.setVisibility(vm.collapsed ? View.VISIBLE : View.GONE);
        b.btnCollapse.setRotation(vm.collapsed ? 0f : 180f);
        if (vm.collapsed) {
            b.weekCalendar.scrollToWeek(vm.selectedDate());
        } else {
            b.monthCalendar.scrollToMonth(YearMonth.from(vm.selectedDate()));
        }
    }

    private void updateTitle() {
        YearMonth ym = vm.visibleMonth();
        b.textMonth.setText(getString(R.string.calendar_month_title, ym.getMonthValue(),
                ym.getYear()));
    }

    private void setUpSheet() {
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(b.sheet);
        behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        b.sheetHeader.setOnClickListener(v -> behavior.setState(
                behavior.getState() == BottomSheetBehavior.STATE_EXPANDED
                        ? BottomSheetBehavior.STATE_COLLAPSED
                        : BottomSheetBehavior.STATE_EXPANDED));
        behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View sheet, int state) {
            }

            @Override
            public void onSlide(@NonNull View sheet, float offset) {
                b.imgSheetArrow.setRotation(180f - 180f * Math.max(0f, offset));
            }
        });
    }

    /** Từng ghi chú (ô màu, tên, kiểu lặp) và dòng "Ôn tập từ vựng"; checkbox ẩn / hiện. */
    private void bindMyCalendars(CalendarViewModel.Data d) {
        b.listCalendars.removeAllViews();
        addCalendarRow(SessionManager.CALENDAR_VOCAB, getString(R.string.calendar_vocab), null,
                ContextCompat.getColor(requireContext(), R.color.moss), d);
        for (NoteEntity n : d.allNotes) {
            addCalendarRow(String.valueOf(n.id), n.title,
                    NoteDetailActivity.describeRepeat(requireContext(), n),
                    NotesFragmentColors.parse(n.color), d);
        }
        if (d.allNotes.isEmpty()) {
            TextView empty = new TextView(requireContext());
            TextViewCompat.setTextAppearance(empty, R.style.TextAppearance_Reno_Caption);
            empty.setText(R.string.calendar_no_notes);
            int pad = getResources().getDimensionPixelSize(R.dimen.space_2);
            empty.setPadding(pad, pad / 2, pad, pad / 2);
            b.listCalendars.addView(empty);
        }
    }

    private void addCalendarRow(String key, String name, @Nullable String repeat, int color,
                                CalendarViewModel.Data d) {
        ItemMyCalendarBinding row = ItemMyCalendarBinding.inflate(getLayoutInflater(),
                b.listCalendars, true);
        row.viewColor.setBackgroundColor(color);
        row.textName.setText(name);
        row.textRepeat.setText(repeat);
        row.textRepeat.setVisibility(repeat == null ? View.GONE : View.VISIBLE);
        row.check.setChecked(!d.hidden.contains(key));
        row.check.setButtonTintList(android.content.res.ColorStateList.valueOf(color));
        row.check.setOnCheckedChangeListener((btn, checked) -> vm.setHidden(key, !checked));
        row.getRoot().setOnClickListener(v -> row.check.toggle());
    }
}
