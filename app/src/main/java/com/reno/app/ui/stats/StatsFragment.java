package com.reno.app.ui.stats;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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

import com.reno.app.R;
import com.reno.app.databinding.FragmentStatsBinding;
import com.reno.app.databinding.ItemStatBinding;
import com.reno.app.databinding.ItemStatEmptyBinding;
import com.reno.app.databinding.ItemStatHeaderBinding;
import com.reno.app.ui.notes.NoteDetailActivity;
import com.reno.app.ui.vocab.FlashcardActivity;
import com.reno.app.ui.vocab.ReviewActivity;
import com.reno.app.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

/** Mục Thống kê: xếp dọc 3 mục Đã bỏ lỡ, Sắp tới, Đã hoàn thành; bấm tên mục để mở / đóng. */
public class StatsFragment extends Fragment {
    private static final int[] TITLES = {R.string.stats_missed, R.string.stats_upcoming,
            R.string.stats_done};
    private static final int[] EMPTY_TEXTS = {R.string.stats_empty_missed,
            R.string.stats_empty_upcoming, R.string.stats_empty_done};

    private FragmentStatsBinding b;
    private StatsViewModel vm;
    private final StatsAdapter adapter = new StatsAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentStatsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(StatsViewModel.class);
        b.list.setAdapter(adapter);
        vm.data().observe(getViewLifecycleOwner(), adapter::submit);
    }

    @Override
    public void onResume() {
        super.onResume();
        vm.refresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void open(StatsViewModel.Row row) {
        if (row.isNote) {
            startActivity(NoteDetailActivity.intent(requireContext(), row.occurrence.note.id,
                    row.date.toString(), null));
        } else if (row.section == StatsViewModel.SECTION_DONE) {
            long[] ids = new long[row.reviewIds.size()];
            for (int i = 0; i < ids.length; i++) ids[i] = row.reviewIds.get(i);
            startActivity(FlashcardActivity.intentForReviews(requireContext(), ids));
        } else {
            // Ôn bù từ mục Đã bỏ lỡ thì lấy cả các lần đã SKIPPED.
            startActivity(ReviewActivity.intentForDay(requireContext(), row.date.toString(),
                    row.section == StatsViewModel.SECTION_MISSED));
        }
    }

    private final class StatsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ROW = 1;
        private static final int TYPE_EMPTY = 2;

        private StatsViewModel.Data data = new StatsViewModel.Data();
        /** Integer = tiêu đề mục; Row = một dòng; int[] {mục} = dòng "trống". */
        private final List<Object> rows = new ArrayList<>();

        void submit(StatsViewModel.Data data) {
            this.data = data;
            rebuild();
        }

        private void rebuild() {
            rows.clear();
            for (int s = 0; s < data.sections.size(); s++) {
                rows.add(s);
                if (!vm.expanded.contains(s)) continue;
                List<StatsViewModel.Row> list = data.sections.get(s);
                if (list.isEmpty()) {
                    rows.add(new int[]{s});
                } else {
                    rows.addAll(list);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            if (row instanceof Integer) return TYPE_HEADER;
            return row instanceof int[] ? TYPE_EMPTY : TYPE_ROW;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (type == TYPE_HEADER) {
                return new HeaderHolder(ItemStatHeaderBinding.inflate(inflater, parent, false));
            }
            if (type == TYPE_EMPTY) {
                return new EmptyHolder(ItemStatEmptyBinding.inflate(inflater, parent, false));
            }
            return new RowHolder(ItemStatBinding.inflate(inflater, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (holder instanceof HeaderHolder) {
                int section = (Integer) row;
                ItemStatHeaderBinding hb = ((HeaderHolder) holder).b;
                int count = data.sections.get(section).size();
                hb.textTitle.setText(TITLES[section]);
                hb.textCount.setText(String.valueOf(count));
                // Số lượng "Đã bỏ lỡ" dùng màu lỗi.
                hb.textCount.setTextColor(ContextCompat.getColor(requireContext(),
                        section == StatsViewModel.SECTION_MISSED && count > 0
                                ? R.color.error : R.color.text_secondary));
                hb.imgArrow.setRotation(vm.expanded.contains(section) ? 180f : 0f);
                hb.row.setOnClickListener(v -> {
                    if (!vm.expanded.remove(section)) vm.expanded.add(section);
                    rebuild();
                });
            } else if (holder instanceof EmptyHolder) {
                ((EmptyHolder) holder).b.text.setText(EMPTY_TEXTS[((int[]) row)[0]]);
            } else {
                bindRow(((RowHolder) holder).b, (StatsViewModel.Row) row);
            }
        }

        private void bindRow(ItemStatBinding rb, StatsViewModel.Row row) {
            GradientDrawable box = new GradientDrawable();
            box.setCornerRadius(8 * getResources().getDisplayMetrics().density);
            if (row.isNote) {
                box.setColor(parse(row.occurrence.note.color));
                rb.imgIcon.setVisibility(View.GONE);
                rb.textTitle.setText(row.occurrence.note.title);
                rb.textSubtitle.setText(getString(R.string.stats_note_subtitle,
                        DateUtils.formatDayMonth(row.date),
                        getString(R.string.notes_time_range,
                                DateUtils.formatTime(row.occurrence.startTime()),
                                DateUtils.formatTime(row.occurrence.end().toLocalTime()))));
            } else {
                box.setColor(ContextCompat.getColor(requireContext(), R.color.moss_light));
                rb.imgIcon.setVisibility(View.VISIBLE);
                rb.textTitle.setText(getString(R.string.stats_vocab_row, row.reviewIds.size(),
                        DateUtils.formatDayMonth(row.date)));
                rb.textSubtitle.setText(R.string.stats_vocab_subtitle);
            }
            rb.viewColor.setBackground(box);
            rb.getRoot().setOnClickListener(v -> open(row));
        }
    }

    private static int parse(String color) {
        try {
            return Color.parseColor(color);
        } catch (IllegalArgumentException e) {
            return Color.GRAY;
        }
    }

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final ItemStatHeaderBinding b;

        HeaderHolder(ItemStatHeaderBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final ItemStatBinding b;

        RowHolder(ItemStatBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }

    private static final class EmptyHolder extends RecyclerView.ViewHolder {
        final ItemStatEmptyBinding b;

        EmptyHolder(ItemStatEmptyBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
