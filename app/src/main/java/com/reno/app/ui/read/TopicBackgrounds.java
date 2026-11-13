package com.reno.app.ui.read;

import androidx.annotation.DrawableRes;

import com.reno.app.R;

/** Ảnh nền của topic. Đây là các họa tiết lặp, luôn hiển thị kiểu lát gạch để không bị mờ. */
public final class TopicBackgrounds {
    private static final int[] TILES = {
            R.drawable.bg_1_tile, R.drawable.bg_2_tile, R.drawable.bg_3_tile,
            R.drawable.bg_4_tile, R.drawable.bg_5_tile, R.drawable.bg_6_tile,
            R.drawable.bg_7_tile, R.drawable.bg_8_tile};

    private TopicBackgrounds() {
    }

    /** @param bgIndex 1–8 (cột topics.bg_index) */
    @DrawableRes
    public static int tile(int bgIndex) {
        int i = Math.min(Math.max(bgIndex, 1), TILES.length) - 1;
        return TILES[i];
    }
}
