package com.example.chatserver;

import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/** Edge-to-edge 下讓畫面避開狀態列 / 導覽列 / 鍵盤 */
final class Insets {
    private Insets() {}

    static void apply(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            androidx.core.graphics.Insets b = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(b.left, b.top, b.right, b.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
