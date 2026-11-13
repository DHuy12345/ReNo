package com.reno.app.ui.vocab;

import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.reno.app.R;
import com.reno.app.data.db.entity.FolderEntity;
import com.reno.app.data.db.entity.WordEntity;
import com.reno.app.databinding.FragmentVocabBinding;
import com.reno.app.databinding.ItemWordBinding;
import com.reno.app.ui.common.Dialogs;
import com.reno.app.ui.common.FolderListAdapter;
import com.reno.app.util.DateUtils;

/** Tab Vocabulary: thẻ "Hôm nay cần ôn" và danh sách từ theo thư mục (nhóm từ). */
public class VocabFragment extends Fragment {
    private static final int NAME_MAX = 100;

    private FragmentVocabBinding b;
    private VocabViewModel vm;
    private WordAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentVocabBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        vm = new ViewModelProvider(this).get(VocabViewModel.class);
        adapter = new WordAdapter();
        b.list.setAdapter(adapter);
        b.fab.setOnClickListener(this::showAddMenu);

        vm.data().observe(getViewLifecycleOwner(), data -> {
            boolean hasDue = data.dueToday > 0;
            b.textDue.setText(hasDue ? getString(R.string.vocab_due_today, data.dueToday)
                    : getString(R.string.vocab_due_none));
            b.textDueHint.setVisibility(hasDue ? View.VISIBLE : View.GONE);
            b.imgDueArrow.setVisibility(hasDue ? View.VISIBLE : View.GONE);
            b.cardDue.setClickable(hasDue);
            b.cardDue.setOnClickListener(hasDue ? v -> startActivity(ReviewActivity.intentForDay(
                    requireContext(), DateUtils.today().toString(), false)) : null);
            adapter.submit(data.folders);
            b.textNoFolder.setVisibility(data.folders.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }

    private void showAddMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.inflate(R.menu.vocab_add);
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_add_word) {
                startActivity(WordFormActivity.intentForNew(requireContext(), null, null));
            } else {
                Dialogs.input(requireContext(), getString(R.string.folder_create),
                        getString(R.string.folder_name_hint), null, NAME_MAX, false,
                        (text, handle) -> vm.repo.createFolder(vm.userId, text, result -> {
                            if (result[0] == 0) {
                                vm.expanded.add(result[1]);
                                handle.dismiss();
                            } else if (isAdded()) {
                                handle.setError(getString((int) result[0]));
                            }
                        }));
            }
            return true;
        });
        menu.show();
    }

    private void showFolderMenu(View anchor, FolderEntity folder, int wordCount) {
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
                        getString(R.string.vocab_folder_delete_message, wordCount),
                        () -> vm.repo.deleteFolder(vm.userId, folder.id));
            }
            return true;
        });
        menu.show();
    }

    private final class WordAdapter extends FolderListAdapter<WordEntity> {
        WordAdapter() {
            super(vm.expanded);
        }

        @Override
        protected CharSequence countLabel(View view, int count) {
            return view.getContext().getString(R.string.vocab_word_count, count);
        }

        @Override
        protected void onFolderLongClick(View anchor, FolderEntity folder, int count) {
            showFolderMenu(anchor, folder, count);
        }

        @Override
        protected RecyclerView.ViewHolder onCreateItemHolder(LayoutInflater inflater,
                                                             ViewGroup parent) {
            return new WordHolder(ItemWordBinding.inflate(inflater, parent, false));
        }

        @Override
        protected void onBindItemHolder(RecyclerView.ViewHolder holder, WordEntity word) {
            ItemWordBinding ib = ((WordHolder) holder).b;
            // Từ tiếng Anh (đậm), nghĩa tiếng Việt bên cạnh.
            SpannableStringBuilder text = new SpannableStringBuilder(word.word);
            text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            int start = text.length();
            text.append("  ").append(word.meaning);
            text.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(),
                    R.color.text_secondary)), start, text.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ib.textWord.setText(text);
            // Bấm vào từ → flashcard, bắt đầu từ từ đó; bấm bút chì → màn sửa.
            ib.getRoot().setOnClickListener(v -> startActivity(
                    FlashcardActivity.intentForFolder(requireContext(), word.folderId, word.id)));
            ib.btnEdit.setOnClickListener(v -> startActivity(
                    WordFormActivity.intentForEdit(requireContext(), word.id)));
            ib.btnDelete.setOnClickListener(v -> Dialogs.confirmDelete(requireContext(),
                    getString(R.string.vocab_delete_word_title, word.word),
                    getString(R.string.vocab_delete_word_message),
                    () -> vm.repo.deleteWord(vm.userId, word.id)));
        }
    }

    private static final class WordHolder extends RecyclerView.ViewHolder {
        final ItemWordBinding b;

        WordHolder(ItemWordBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
