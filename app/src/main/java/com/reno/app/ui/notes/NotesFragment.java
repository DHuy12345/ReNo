package com.reno.app.ui.notes;

import android.graphics.Paint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.reno.app.R;
import com.reno.app.data.db.entity.NoteOccurrenceEntity;
import com.reno.app.data.model.Occurrence;
import com.reno.app.databinding.FragmentNotesBinding;
import com.reno.app.databinding.ItemNoteBinding;
import com.reno.app.ui.common.Dialogs;
import com.reno.app.ui.common.Pickers;
import com.reno.app.util.DateUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Tab Notes: ghi chú phải làm trong một ngày. Tích hoàn thành là tích cho riêng ngày đó. */
public class NotesFragment extends Fragment {
    private FragmentNotesBinding b;
    private NotesViewModel vm;
    private final NoteAdapter adapter = new NoteAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentNotesBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(NotesViewModel.class);
        b.list.setAdapter(adapter);

        b.btnDate.setOnClickListener(v -> Pickers.pickDate(getChildFragmentManager(),
                vm.currentDate(), null, vm::setDate));
        b.btnToday.setOnClickListener(v -> vm.setDate(DateUtils.today()));
        b.fab.setOnClickListener(v -> startActivity(
                NoteFormActivity.intentForNew(requireContext(), vm.currentDate())));

        vm.date().observe(getViewLifecycleOwner(), day -> {
            boolean isToday = day.equals(DateUtils.today());
            String dayMonth = DateUtils.formatDayMonth(day);
            b.textTitle.setText(isToday ? getString(R.string.notes_today_title)
                    : getString(R.string.notes_date_title, dayMonth));
            b.btnDate.setText(dayMonth);
            b.btnToday.setVisibility(isToday ? View.GONE : View.VISIBLE);
        });
        vm.occurrences().observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list);
            b.textEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // qua nửa đêm trong lúc app mở: "hôm nay" đã đổi
        if (vm != null) vm.setDate(vm.currentDate());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void confirmDelete(Occurrence o) {
        Dialogs.confirmDelete(requireContext(),
                getString(R.string.notes_delete_title, o.note.title),
                getString(o.note.isRepeating() ? R.string.notes_delete_message_repeat
                        : R.string.notes_delete_message),
                () -> vm.repo.delete(o.note.id));
    }

    private final class NoteAdapter extends RecyclerView.Adapter<NoteHolder> {
        private List<Occurrence> items = new ArrayList<>();

        void submit(List<Occurrence> list) {
            items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public NoteHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new NoteHolder(ItemNoteBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull NoteHolder holder, int position) {
            Occurrence o = items.get(position);
            ItemNoteBinding ib = holder.b;
            boolean done = o.isDone();
            // Chỉ tích được ngày hôm nay hoặc ngày đã qua.
            boolean future = o.date.isAfter(DateUtils.today());

            ib.textName.setText(o.note.title);
            // Đã tích: tên bị gạch ngang, chữ chuyển màu chữ phụ.
            ib.textName.setPaintFlags(done
                    ? ib.textName.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : ib.textName.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            ib.textName.setTextColor(ContextCompat.getColor(requireContext(),
                    done ? R.color.text_secondary : R.color.text_primary));

            String range = getString(R.string.notes_time_range,
                    DateUtils.formatTime(o.startTime()),
                    DateUtils.formatTime(o.end().toLocalTime()));
            ib.textTime.setText(o.isSkipped()
                    ? getString(R.string.notes_skipped_suffix, range) : range);
            ib.viewColor.setBackgroundColor(parseColor(o.note.color));
            ib.imgRepeat.setVisibility(o.note.isRepeating() ? View.VISIBLE : View.GONE);

            ib.check.setOnCheckedChangeListener(null);
            ib.check.setChecked(done);
            ib.check.setEnabled(!future);
            ib.check.setAlpha(future ? 0.38f : 1f);
            ib.check.setOnCheckedChangeListener((button, checked) ->
                    // Bỏ tích thì lần xuất hiện quay về trạng thái chưa làm.
                    vm.repo.setStatus(o.note.id, o.date,
                            checked ? NoteOccurrenceEntity.STATUS_DONE : null));

            ib.row.setOnClickListener(v -> startActivity(
                    NoteFormActivity.intentForEdit(requireContext(), o.note.id)));
            ib.row.setOnLongClickListener(v -> {
                if (future) {
                    Snackbar.make(b.getRoot(), R.string.notes_future_locked,
                            Snackbar.LENGTH_SHORT).show();
                }
                return future;
            });
            ib.btnDelete.setOnClickListener(v -> confirmDelete(o));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    static int parseColor(String color) {
        return NotesFragmentColors.parse(color);
    }

    private static final class NoteHolder extends RecyclerView.ViewHolder {
        final ItemNoteBinding b;

        NoteHolder(ItemNoteBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
