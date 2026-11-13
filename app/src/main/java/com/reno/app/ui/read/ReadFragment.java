package com.reno.app.ui.read;

import android.content.ActivityNotFoundException;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.reno.app.R;
import com.reno.app.data.db.entity.FolderEntity;
import com.reno.app.data.db.entity.TopicEntity;
import com.reno.app.data.model.FolderGroup;
import com.reno.app.data.model.PreparedPdf;
import com.reno.app.data.repository.ReadRepository;
import com.reno.app.databinding.FragmentReadBinding;
import com.reno.app.databinding.ItemTopicBinding;
import com.reno.app.databinding.ViewRecentTopicBinding;
import com.reno.app.ui.common.Dialogs;
import com.reno.app.ui.common.FolderListAdapter;

import java.util.ArrayList;
import java.util.List;

/** Tab Read: mục Gần đây (3 thẻ) và danh sách thư mục – topic. Mỗi topic là một file PDF. */
public class ReadFragment extends Fragment {
    private static final int NAME_MAX = 100;

    private FragmentReadBinding b;
    private ReadViewModel vm;
    private TopicAdapter adapter;
    private AlertDialog progressDialog;
    private List<FolderGroup<TopicEntity>> folders = new ArrayList<>();

    /** Bộ chọn file hệ thống, lọc application/pdf; không cần xin quyền bộ nhớ. */
    private final ActivityResultLauncher<String[]> openPdf = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null || vm.pendingFolderId < 0) return;
                long folderId = vm.pendingFolderId;
                vm.setBusy(true);
                vm.repo.preparePdf(uri, prepared -> {
                    vm.setBusy(false);
                    if (b == null) {
                        vm.repo.discardPrepared(prepared);
                    } else if (prepared == null) {
                        Snackbar.make(b.getRoot(), R.string.read_pdf_error,
                                Snackbar.LENGTH_LONG).show();
                    } else {
                        askTopicName(folderId, prepared);
                    }
                });
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentReadBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(ReadViewModel.class);
        adapter = new TopicAdapter();
        b.list.setAdapter(adapter);
        b.fab.setOnClickListener(this::showAddMenu);

        vm.data().observe(getViewLifecycleOwner(), data -> {
            folders = data.folders;
            bindRecent(data.recent);
            adapter.submit(data.folders);
            b.textNoFolder.setVisibility(data.folders.isEmpty() ? View.VISIBLE : View.GONE);
        });
        vm.busy().observe(getViewLifecycleOwner(), this::showProgress);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        showProgress(false);
        b = null;
    }

    // ------------------------------------------------------------ Gần đây

    private void bindRecent(List<TopicEntity> recent) {
        ViewRecentTopicBinding[] cards = {b.recent1, b.recent2, b.recent3};
        for (int i = 0; i < cards.length; i++) {
            ViewRecentTopicBinding card = cards[i];
            if (i < recent.size()) {
                TopicEntity topic = recent.get(i);
                card.getRoot().setVisibility(View.VISIBLE);
                card.textName.setText(topic.name);
                card.viewBg.setBackgroundResource(TopicBackgrounds.tile(topic.bgIndex));
                card.card.setOnClickListener(v -> openTopic(topic));
            } else {
                // ô trống vẫn giữ chỗ để các thẻ không giãn ra
                card.getRoot().setVisibility(View.INVISIBLE);
                card.card.setOnClickListener(null);
            }
        }
        boolean none = recent.isEmpty();
        b.rowRecent.setVisibility(none ? View.GONE : View.VISIBLE);
        b.textNoTopic.setVisibility(none ? View.VISIBLE : View.GONE);
    }

    private void openTopic(TopicEntity topic) {
        vm.repo.touchTopic(topic.id);
        startActivity(TopicActivity.intent(requireContext(), topic.id));
    }

    // ------------------------------------------------------------ Thêm mới

    private void showAddMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.inflate(R.menu.read_add);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_create_folder) {
                createFolder();
            } else {
                addTopic();
            }
            return true;
        });
        menu.show();
    }

    private void createFolder() {
        Dialogs.input(requireContext(), getString(R.string.folder_create),
                getString(R.string.folder_name_hint), null, NAME_MAX, false,
                (text, handle) -> vm.repo.createFolder(vm.userId, text, error -> {
                    if (error == 0) {
                        handle.dismiss();
                    } else if (isAdded()) {
                        handle.setError(getString(error));
                    }
                }));
    }

    /** Thêm topic: chọn thư mục → chọn file PDF → nhập tên. */
    private void addTopic() {
        if (folders.isEmpty()) {
            Snackbar.make(b.getRoot(), R.string.read_need_folder, Snackbar.LENGTH_LONG).show();
            return;
        }
        chooseFolder(-1, folder -> {
            vm.pendingFolderId = folder.id;
            try {
                openPdf.launch(new String[]{"application/pdf"});
            } catch (ActivityNotFoundException e) {
                Snackbar.make(b.getRoot(), R.string.picker_unavailable,
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private interface FolderChosen {
        void onChosen(FolderEntity folder);
    }

    private void chooseFolder(long currentFolderId, FolderChosen callback) {
        CharSequence[] names = new CharSequence[folders.size()];
        int checked = -1;
        for (int i = 0; i < names.length; i++) {
            names[i] = folders.get(i).folder.name;
            if (folders.get(i).folder.id == currentFolderId) checked = i;
        }
        List<FolderGroup<TopicEntity>> snapshot = new ArrayList<>(folders);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.read_choose_folder)
                .setSingleChoiceItems(names, checked, (d, which) -> {
                    d.dismiss();
                    callback.onChosen(snapshot.get(which).folder);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Tên mặc định là tên file bỏ ".pdf". Hủy thì xóa bản chép. */
    private void askTopicName(long folderId, PreparedPdf prepared) {
        Dialogs.input(requireContext(), getString(R.string.read_topic_name),
                getString(R.string.read_topic_name_hint), prepared.suggestedName(), NAME_MAX,
                false, (text, handle) -> {
                    if (text.trim().isEmpty()) {
                        handle.setError(getString(R.string.error_topic_name_empty));
                        return;
                    }
                    vm.expanded.add(folderId);
                    vm.repo.createTopic(folderId, text, prepared, handle::dismiss);
                }, () -> vm.repo.discardPrepared(prepared));
    }

    private void showProgress(boolean show) {
        if (show && progressDialog == null && getContext() != null) {
            progressDialog = new MaterialAlertDialogBuilder(requireContext())
                    .setView(R.layout.dialog_progress)
                    .setCancelable(false)
                    .show();
        } else if (!show && progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    // ----------------------------------------------------- Thư mục và topic

    private void showFolderMenu(View anchor, FolderEntity folder, int topicCount) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.inflate(R.menu.folder_actions);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_rename) {
                Dialogs.input(requireContext(), getString(R.string.folder_rename),
                        getString(R.string.folder_name_hint), folder.name, NAME_MAX, false,
                        (text, handle) -> vm.repo.renameFolder(folder.id, text, error -> {
                            if (error == 0) {
                                handle.dismiss();
                            } else if (isAdded()) {
                                handle.setError(getString(error));
                            }
                        }));
            } else {
                Dialogs.confirmDelete(requireContext(),
                        getString(R.string.folder_delete_title, folder.name),
                        getString(R.string.read_folder_delete_message, topicCount),
                        () -> vm.repo.deleteFolder(folder.id, () -> { }));
            }
            return true;
        });
        menu.show();
    }

    private void showTopicMenu(View anchor, TopicEntity topic) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.inflate(R.menu.topic_actions);
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_rename) {
                Dialogs.input(requireContext(), getString(R.string.read_topic_rename),
                        getString(R.string.read_topic_name_hint), topic.name, NAME_MAX, false,
                        (text, handle) -> {
                            if (text.trim().isEmpty()) {
                                handle.setError(getString(R.string.error_topic_name_empty));
                                return;
                            }
                            vm.repo.renameTopic(topic.id, text);
                            handle.dismiss();
                        });
            } else if (id == R.id.menu_move) {
                chooseFolder(topic.folderId, folder -> {
                    vm.expanded.add(folder.id);
                    vm.repo.moveTopic(topic.id, folder.id);
                });
            } else {
                Dialogs.confirmDelete(requireContext(),
                        getString(R.string.read_topic_delete_title, topic.name),
                        getString(R.string.read_topic_delete_message),
                        () -> vm.repo.deleteTopic(topic.id));
            }
            return true;
        });
        menu.show();
    }

    private final class TopicAdapter extends FolderListAdapter<TopicEntity> {
        TopicAdapter() {
            super(vm.expanded);
        }

        @Override
        protected CharSequence countLabel(View view, int count) {
            return view.getContext().getString(R.string.read_topic_count, count);
        }

        @Override
        protected void onFolderLongClick(View anchor, FolderEntity folder, int count) {
            showFolderMenu(anchor, folder, count);
        }

        @Override
        protected RecyclerView.ViewHolder onCreateItemHolder(LayoutInflater inflater,
                                                             ViewGroup parent) {
            return new TopicHolder(ItemTopicBinding.inflate(inflater, parent, false));
        }

        @Override
        protected void onBindItemHolder(RecyclerView.ViewHolder holder, TopicEntity topic) {
            ItemTopicBinding ib = ((TopicHolder) holder).b;
            ib.textName.setText(topic.name);
            ib.textPages.setText(getString(R.string.annotation_page, topic.pageCount));
            ib.getRoot().setOnClickListener(v -> openTopic(topic));
            ib.getRoot().setOnLongClickListener(v -> {
                showTopicMenu(v, topic);
                return true;
            });
        }
    }

    private static final class TopicHolder extends RecyclerView.ViewHolder {
        final ItemTopicBinding b;

        TopicHolder(ItemTopicBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
