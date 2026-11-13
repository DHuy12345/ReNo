package com.reno.app.ui.idea;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.reno.app.R;
import com.reno.app.data.db.entity.IdeaEntity;
import com.reno.app.data.repository.IdeaRepository;
import com.reno.app.databinding.FragmentIdeaBinding;
import com.reno.app.databinding.ItemIdeaBinding;
import com.reno.app.ui.common.Dialogs;
import com.reno.app.util.DateUtils;

import java.util.ArrayList;
import java.util.List;

/** Tab Idea: 2 tab con Chưa thực hiện (mặc định) và Đã thực hiện. Không có thư mục. */
public class IdeaFragment extends Fragment {
    private FragmentIdeaBinding b;
    private IdeaViewModel vm;
    private final IdeaAdapter adapter = new IdeaAdapter();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentIdeaBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(IdeaViewModel.class);
        b.list.setAdapter(adapter);

        b.tabs.addTab(b.tabs.newTab().setText(R.string.idea_tab_todo));
        b.tabs.addTab(b.tabs.newTab().setText(R.string.idea_tab_done));
        TabLayout.Tab current = b.tabs.getTabAt(vm.isShowingDone() ? 1 : 0);
        if (current != null) current.select();
        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                vm.setShowDone(tab.getPosition() == 1);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        b.btnSort.setOnClickListener(this::showSortMenu);
        b.fab.setOnClickListener(v -> showEditor(null));

        vm.ideas().observe(getViewLifecycleOwner(), list -> {
            adapter.submit(list, vm.isShowingDone());
            b.textEmpty.setText(vm.isShowingDone() ? R.string.idea_empty_done
                    : R.string.idea_empty_todo);
            b.textEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    /** Sắp xếp theo thời gian tạo: "Mới nhất trước" (mặc định) hoặc "Cũ nhất trước". */
    private void showSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.inflate(R.menu.idea_sort);
        menu.getMenu().findItem(vm.isOldestFirst() ? R.id.menu_oldest : R.id.menu_newest)
                .setChecked(true);
        menu.setOnMenuItemClickListener(item -> {
            vm.setOldestFirst(item.getItemId() == R.id.menu_oldest);
            return true;
        });
        menu.show();
    }

    /** Hộp thoại nhập ý tưởng (bắt buộc, tối đa 1.000 ký tự); dùng cho cả thêm và sửa. */
    private void showEditor(@Nullable IdeaEntity idea) {
        Dialogs.input(requireContext(),
                getString(idea == null ? R.string.idea_add_title : R.string.idea_edit_title),
                getString(R.string.idea_hint), idea == null ? null : idea.content,
                IdeaRepository.MAX_LENGTH, true, (text, handle) -> {
                    if (text.trim().isEmpty()) {
                        handle.setError(getString(R.string.error_idea_empty));
                        return;
                    }
                    if (idea == null) {
                        vm.repo.add(vm.userId, text);
                    } else {
                        vm.repo.updateContent(idea.id, text);
                    }
                    handle.dismiss();
                });
    }

    /** Dòng biến mất ngay khỏi danh sách nên hiện Snackbar kèm nút Hoàn tác. */
    private void move(IdeaEntity idea, boolean toDone) {
        vm.repo.setDone(idea.id, toDone);
        Snackbar.make(b.getRoot(), toDone ? R.string.idea_moved_done : R.string.idea_moved_todo,
                        Snackbar.LENGTH_LONG)
                .setAnchorView(b.fab)
                .setAction(R.string.undo, v -> vm.repo.setDone(idea.id, !toDone))
                .show();
    }

    private final class IdeaAdapter extends RecyclerView.Adapter<IdeaHolder> {
        private List<IdeaEntity> items = new ArrayList<>();
        private boolean doneTab;

        void submit(List<IdeaEntity> list, boolean doneTab) {
            this.items = list;
            this.doneTab = doneTab;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public IdeaHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new IdeaHolder(ItemIdeaBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull IdeaHolder holder, int position) {
            IdeaEntity idea = items.get(position);
            ItemIdeaBinding ib = holder.b;
            ib.textContent.setText(idea.content);
            ib.textDate.setText(DateUtils.formatFullDate(DateUtils.toLocalDate(idea.createdAt)));
            ib.check.setOnCheckedChangeListener(null);
            ib.check.setChecked(doneTab);
            ib.check.setOnCheckedChangeListener((button, checked) -> move(idea, checked));
            ib.row.setOnClickListener(v -> showEditor(idea));
            ib.btnDelete.setOnClickListener(v -> Dialogs.confirmDelete(requireContext(),
                    getString(R.string.idea_delete_title), idea.content,
                    () -> vm.repo.delete(idea.id)));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static final class IdeaHolder extends RecyclerView.ViewHolder {
        final ItemIdeaBinding b;

        IdeaHolder(ItemIdeaBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
