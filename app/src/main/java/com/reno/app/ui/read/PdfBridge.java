package com.reno.app.ui.read;

import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;

/**
 * Cầu nối JavaScript → Java của trình đọc PDF. Chỉ nhận dữ liệu (số trang, vùng chọn, mã đánh
 * dấu), không chạy lệnh tùy ý. Các phương thức được gọi trên luồng của WebView nên mọi việc
 * được chuyển về luồng giao diện.
 */
public class PdfBridge {
    public interface Listener {
        void onReady();

        void onLoaded(int pageCount);

        void onLoadError(String message);

        void onScanDetected(boolean isScan);

        void onSelection(String json);

        void onAnnotationTap(long id);

        void onPageChanged(int page);
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;

    public PdfBridge(Listener listener) {
        this.listener = listener;
    }

    @JavascriptInterface
    public void onReady() {
        main.post(listener::onReady);
    }

    @JavascriptInterface
    public void onLoaded(int pageCount) {
        main.post(() -> listener.onLoaded(pageCount));
    }

    @JavascriptInterface
    public void onLoadError(String message) {
        main.post(() -> listener.onLoadError(message));
    }

    @JavascriptInterface
    public void onScanDetected(boolean isScan) {
        main.post(() -> listener.onScanDetected(isScan));
    }

    @JavascriptInterface
    public void onSelection(String json) {
        main.post(() -> listener.onSelection(json));
    }

    @JavascriptInterface
    public void onAnnotationTap(long id) {
        main.post(() -> listener.onAnnotationTap(id));
    }

    @JavascriptInterface
    public void onPageChanged(int page) {
        main.post(() -> listener.onPageChanged(page));
    }
}
