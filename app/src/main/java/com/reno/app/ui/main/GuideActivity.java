package com.reno.app.ui.main;

import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.reno.app.R;
import com.reno.app.databinding.ActivityGuideBinding;
import com.reno.app.ui.common.BaseActivity;

/** Hướng dẫn sử dụng: một màn chỉ có chữ, mỗi chức năng một đoạn ngắn. */
public class GuideActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityGuideBinding b = ActivityGuideBinding.inflate(getLayoutInflater());
        setContentRoot(b.getRoot());
        b.toolbar.textTitle.setText(R.string.guide_title);
        b.toolbar.btnBack.setOnClickListener(v -> finish());

        String[] titles = getResources().getStringArray(R.array.guide_titles);
        String[] bodies = getResources().getStringArray(R.array.guide_bodies);
        int gap = getResources().getDimensionPixelSize(R.dimen.space_2);
        for (int i = 0; i < titles.length && i < bodies.length; i++) {
            TextView title = new TextView(this);
            TextViewCompat.setTextAppearance(title, R.style.TextAppearance_Reno_Section);
            title.setText(titles[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.topMargin = gap;
            b.content.addView(title, lp);

            TextView body = new TextView(this);
            TextViewCompat.setTextAppearance(body, R.style.TextAppearance_Reno_Body);
            body.setText(bodies[i]);
            body.setLineSpacing(0f, 1.25f);
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp2.topMargin = gap / 4;
            b.content.addView(body, lp2);
        }
    }
}
