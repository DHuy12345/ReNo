package com.reno.app.ui.common;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.reno.app.R;

/** Một hàng ô màu tròn để chọn màu (bảng 8 màu ghi chú, bảng 5 màu Highlight). */
public class ColorPaletteView extends LinearLayout {
    public interface OnColorSelected {
        void onColorSelected(String color);
    }

    private String[] colors = new String[0];
    private String selected;
    private OnColorSelected listener;

    public ColorPaletteView(@NonNull Context context) {
        this(context, null);
    }

    public ColorPaletteView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
    }

    public void setOnColorSelected(@Nullable OnColorSelected listener) {
        this.listener = listener;
    }

    public void setColors(@NonNull String[] colors, @Nullable String selected) {
        this.colors = colors;
        this.selected = selected;
        rebuild();
    }

    @Nullable
    public String getSelected() {
        return selected;
    }

    public void setSelected(@Nullable String color) {
        selected = color;
        rebuild();
    }

    private void rebuild() {
        removeAllViews();
        int size = getResources().getDimensionPixelSize(R.dimen.color_swatch);
        int cell = getResources().getDimensionPixelSize(R.dimen.touch_min);
        float density = getResources().getDisplayMetrics().density;
        for (String color : colors) {
            FrameLayout holder = new FrameLayout(getContext());
            View swatch = new View(getContext());
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(parse(color));
            boolean isSelected = color.equalsIgnoreCase(selected);
            shape.setStroke(Math.round((isSelected ? 3 : 1) * density),
                    ContextCompat.getColor(getContext(),
                            isSelected ? R.color.text_primary : R.color.line));
            swatch.setBackground(shape);
            holder.addView(swatch, new FrameLayout.LayoutParams(size, size, Gravity.CENTER));
            holder.setContentDescription(color);
            holder.setOnClickListener(v -> {
                selected = color;
                rebuild();
                if (listener != null) listener.onColorSelected(color);
            });
            addView(holder, new LayoutParams(0, cell, 1f));
        }
    }

    private static int parse(String color) {
        try {
            return Color.parseColor(color);
        } catch (IllegalArgumentException e) {
            return Color.GRAY;
        }
    }
}
