package com.reno.app.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.google.android.material.tabs.TabLayoutMediator;
import com.reno.app.R;
import com.reno.app.databinding.FragmentHomeBinding;
import com.reno.app.ui.idea.IdeaFragment;
import com.reno.app.ui.notes.NotesFragment;
import com.reno.app.ui.read.ReadFragment;
import com.reno.app.ui.vocab.VocabFragment;

/** Mục Nhà: 4 tab con Read, Notes, Idea, Vocabulary. */
public class HomeFragment extends Fragment {
    private static final int[] TITLES = {
            R.string.tab_read, R.string.tab_notes, R.string.tab_idea, R.string.tab_vocabulary};

    private FragmentHomeBinding b;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        b = FragmentHomeBinding.inflate(inflater, container, false);
        return b.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        b.pager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 1:
                        return new NotesFragment();
                    case 2:
                        return new IdeaFragment();
                    case 3:
                        return new VocabFragment();
                    default:
                        return new ReadFragment();
                }
            }

            @Override
            public int getItemCount() {
                return TITLES.length;
            }
        });
        new TabLayoutMediator(b.tabs, b.pager,
                (tab, position) -> tab.setText(TITLES[position])).attach();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        b = null;
    }
}
