package com.reno.app.ui.notification;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
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
import com.reno.app.data.db.entity.AppNotificationEntity;
import com.reno.app.data.repository.NotificationRepository;
import com.reno.app.databinding.FragmentNotificationsBinding;
import com.reno.app.databinding.ItemNotificationBinding;
import com.reno.app.reminder.NotificationHelper;
import com.reno.app.reminder.SkipDialogActivity;
import com.reno.app.ui.common.Dialogs;
import com.reno.app.ui.notes.NoteDetailActivity;
import com.reno.app.ui.notes.NotesFragmentColors;
import com.reno.app.ui.vocab.ReviewActivity;
import com.reno.app.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

/** Mục Thông báo: danh sách thông báo đã gửi, mới nhất trên cùng. */
public class NotificationFragment extends Fragment {
    private FragmentNotificationsBinding b;
    private NotificationViewModel vm;
    private final RowAdapter adapter = new RowAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentNotificationsBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(NotificationViewModel.class);
        b.list.setAdapter(adapter);
        b.btnSettings.setOnClickListener(v -> startActivity(
                new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName())));
        vm.rows().observe(getViewLifecycleOwner(), rows -> {
            adapter.submit(rows);
            b.textEmpty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        // Người dùng có thể vừa bật / tắt quyền trong cài đặt.
        b.bannerDisabled.setVisibility(NotificationHelper.canPost(requireContext())
                ? View.GONE : View.VISIBLE);
        vm.refresh();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    /** Bấm thông báo → đánh dấu đã đọc → mở màn chi tiết ghi chú hoặc màn ôn tập. */
    private void open(NotificationRepository.Row row) {
        AppNotificationEntity n = row.notification;
        vm.repo.markRead(n.id);
        if (AppNotificationEntity.KIND_NOTE.equals(n.kind) && n.noteId != null) {
            startActivity(NoteDetailActivity.intent(requireContext(), n.noteId, n.refDate,
                    n.dedupeKey));
        } else {
            startActivity(ReviewActivity.intentForGroup(requireContext(), n.refDate, row.time,
                    n.dedupeKey));
        }
    }

    private final class RowAdapter extends RecyclerView.Adapter<RowHolder> {
        private List<NotificationRepository.Row> items = new ArrayList<>();

        void submit(List<NotificationRepository.Row> rows) {
            items = rows;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RowHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RowHolder(ItemNotificationBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RowHolder holder, int position) {
            NotificationRepository.Row row = items.get(position);
            AppNotificationEntity n = row.notification;
            ItemNotificationBinding ib = holder.b;
            boolean unread = n.isRead == 0;
            boolean isNote = AppNotificationEntity.KIND_NOTE.equals(n.kind);

            // Biểu tượng: ô màu ghi chú, hoặc biểu tượng sách trên nền xanh rêu nhạt.
            GradientDrawable box = new GradientDrawable();
            box.setCornerRadius(10 * getResources().getDisplayMetrics().density);
            box.setColor(isNote ? NotesFragmentColors.parse(row.noteColor)
                    : ContextCompat.getColor(requireContext(), R.color.moss_light));
            ib.viewIconBg.setBackground(box);
            ib.imgIcon.setVisibility(isNote ? View.GONE : View.VISIBLE);

            ib.textTitle.setText(n.title);
            ib.textTitle.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
            ib.dotUnread.setVisibility(unread ? View.VISIBLE : View.GONE);
            ib.textBody.setText(n.body);
            int status = row.status == NotificationRepository.STATUS_DONE ? R.string.status_done
                    : row.status == NotificationRepository.STATUS_SKIPPED
                    ? R.string.status_skipped : R.string.status_pending;
            ib.textMeta.setText(getString(R.string.notif_time_status,
                    DateUtils.relativeTime(requireContext(), n.createdAt), getString(status)));

            // Dòng thông báo ôn tập chưa xử lý có thêm nút Bỏ qua cả nhóm.
            boolean canSkip = !isNote && row.pendingCount > 0;
            ib.btnSkipGroup.setVisibility(canSkip ? View.VISIBLE : View.GONE);
            ib.btnSkipGroup.setText(getString(R.string.notif_skip_group, row.pendingCount));
            ib.btnSkipGroup.setOnClickListener(v -> {
                vm.repo.markRead(n.id);
                startActivity(SkipDialogActivity.intentForGroup(requireContext(), n.refDate,
                        row.time, n.dedupeKey));
            });

            ib.row.setOnClickListener(v -> open(row));
            // Thùng rác chỉ xóa thông báo, không xóa ghi chú hay từ vựng.
            ib.btnDelete.setOnClickListener(v -> Dialogs.confirmDelete(requireContext(),
                    getString(R.string.notif_delete_title),
                    getString(R.string.notif_delete_message), () -> vm.repo.delete(n.id)));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static final class RowHolder extends RecyclerView.ViewHolder {
        final ItemNotificationBinding b;

        RowHolder(ItemNotificationBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
