package com.reno.app.ui.read;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.reno.app.R;
import com.reno.app.data.db.entity.AnnotationEntity;
import com.reno.app.data.db.entity.TopicEntity;
import com.reno.app.databinding.ActivityTopicBinding;
import com.reno.app.databinding.DialogAnnotationBinding;
import com.reno.app.databinding.ItemAnnotationBinding;
import com.reno.app.databinding.SheetAnnotationBinding;
import com.reno.app.databinding.ViewCountCellBinding;
import com.reno.app.ui.common.BaseActivity;
import com.reno.app.ui.common.Dialogs;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Màn topic. Từ trên xuống: thanh "← Tên topic", dải ảnh nền lát gạch, 3 tab Ghi chú /
 * Thống kê / Tóm tắt. PDF hiển thị bằng PDF.js trong WebView; đánh dấu lưu trong SQLite,
 * không ghi vào file PDF.
 */
public class TopicActivity extends BaseActivity implements PdfBridge.Listener {
    private static final String TAG = "TopicActivity";
    private static final String EXTRA_TOPIC_ID = "topic_id";
    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String READER_URL =
            "https://" + APP_HOST + "/assets/pdfjs/reader.html";

    /** Bảng màu Highlight; màu đầu (Vàng) là mặc định. */
    public static final String[] HIGHLIGHT_COLORS = {
            "#FFE58A", "#B7E4C7", "#BBD7F2", "#F7C6D9", "#FCD5B0"};
    private static final int[] TYPE_LABELS = {R.string.mark_question, R.string.mark_important,
            R.string.mark_star, R.string.mark_highlight, R.string.mark_note};

    private ActivityTopicBinding b;
    private TopicViewModel vm;
    private final AnnotationAdapter adapter = new AnnotationAdapter();
    private final ViewCountCellBinding[] countCells =
            new ViewCountCellBinding[AnnotationEntity.TYPES.length];
    private TopicEntity topic;
    private boolean readerReady;
    private boolean documentLoaded;

    public static Intent intent(Context ctx, long topicId) {
        return new Intent(ctx, TopicActivity.class).putExtra(EXTRA_TOPIC_ID, topicId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;
        b = ActivityTopicBinding.inflate(getLayoutInflater());
        setContentRoot(b.getRoot());
        vm = new ViewModelProvider(this).get(TopicViewModel.class);

        b.toolbar.btnBack.setOnClickListener(v -> finish());
        setUpTabs();
        setUpStats();
        setUpWebView();

        vm.topic().observe(this, t -> {
            if (t == null) {
                android.widget.Toast.makeText(this, R.string.topic_missing,
                        android.widget.Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            topic = t;
            b.toolbar.textTitle.setText(t.name);
            b.viewStrip.setBackgroundResource(TopicBackgrounds.tile(t.bgIndex));
            loadDocumentIfReady();
        });
        vm.annotations().observe(this, list -> {
            pushAnnotations();
            refreshStats();
        });
        vm.load(getIntent().getLongExtra(EXTRA_TOPIC_ID, -1));
    }

    @Override
    protected void onDestroy() {
        if (b != null) {
            b.web.removeJavascriptInterface("Android");
            b.web.destroy();
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ Tab

    private void setUpTabs() {
        int[] titles = {R.string.topic_tab_notes, R.string.topic_tab_stats,
                R.string.topic_tab_summary};
        for (int title : titles) b.tabs.addTab(b.tabs.newTab().setText(title));
        b.tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                vm.selectedTab = tab.getPosition();
                showTab();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        selectTab(vm.selectedTab);
        showTab();
    }

    private void selectTab(int index) {
        TabLayout.Tab tab = b.tabs.getTabAt(index);
        if (tab != null) tab.select();
    }

    private void showTab() {
        // WebView chỉ bị ẩn chứ không bị hủy, để còn cuộn tới chỗ đánh dấu khi quay lại.
        b.pageNotes.setVisibility(vm.selectedTab == 0 ? View.VISIBLE : View.INVISIBLE);
        b.pageStats.setVisibility(vm.selectedTab == 1 ? View.VISIBLE : View.GONE);
        b.pageSummary.setVisibility(vm.selectedTab == 2 ? View.VISIBLE : View.GONE);
    }

    // -------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private void setUpWebView() {
        // Trang đọc và file PDF đều được phục vụ qua https://appassets.androidplatform.net/,
        // tránh lỗi bảo mật của file://.
        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .addPathHandler("/pdfs/", new WebViewAssetLoader.InternalStoragePathHandler(
                        this, new File(getFilesDir(), "pdfs")))
                .build();

        WebSettings settings = b.web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        b.web.setBackgroundColor(getColor(R.color.bg_alt));
        b.web.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(@NonNull WebView view,
                                                              @NonNull WebResourceRequest request) {
                if (!APP_HOST.equals(request.getUrl().getHost())) {
                    // WebView này chỉ nạp nội dung của app: chặn mọi địa chỉ khác.
                    return new WebResourceResponse("text/plain", "utf-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                WebResourceResponse response = loader.shouldInterceptRequest(request.getUrl());
                String path = request.getUrl().getPath();
                if (response != null && path != null && path.endsWith(".mjs")) {
                    response.setMimeType("text/javascript");   // module JS cần đúng kiểu MIME
                }
                return response;
            }

            @Override
            public boolean shouldOverrideUrlLoading(@NonNull WebView view,
                                                    @NonNull WebResourceRequest request) {
                return !APP_HOST.equals(request.getUrl().getHost());
            }
        });
        b.web.addJavascriptInterface(new PdfBridge(this), "Android");
        b.web.setOnMarkListener(this::onMarkClicked);
        b.web.loadUrl(READER_URL);
    }

    private void loadDocumentIfReady() {
        if (!readerReady || topic == null || documentLoaded) return;
        documentLoaded = true;
        String url = "/pdfs/" + new File(topic.filePath).getName();
        b.web.evaluateJavascript("reno.load(" + JSONObject.quote(url) + ", "
                + topic.lastPage + ")", null);
    }

    /** Đưa toàn bộ đánh dấu sang JavaScript để vẽ lên trang. */
    private void pushAnnotations() {
        if (!readerReady) return;
        JSONArray array = new JSONArray();
        for (AnnotationEntity a : vm.currentAnnotations()) {
            try {
                JSONObject o = new JSONObject();
                o.put("id", a.id);
                o.put("type", a.type);
                o.put("page", a.pageIndex);
                o.put("rects", new JSONArray(a.rects));
                o.put("color", a.color == null ? JSONObject.NULL : a.color);
                array.put(o);
            } catch (JSONException e) {
                Log.w(TAG, "Bỏ qua đánh dấu có toạ độ hỏng: " + a.id, e);
            }
        }
        b.web.evaluateJavascript("reno.render(" + array + ")", null);
    }

    // --------------------------------------------- Sự kiện từ JavaScript

    @Override
    public void onReady() {
        readerReady = true;
        loadDocumentIfReady();
    }

    @Override
    public void onLoaded(int pageCount) {
        b.progress.setVisibility(View.GONE);
        pushAnnotations();
    }

    @Override
    public void onLoadError(String message) {
        Log.e(TAG, "PDF.js không mở được file: " + message);
        b.progress.setVisibility(View.GONE);
        b.textError.setVisibility(View.VISIBLE);
    }

    @Override
    public void onScanDetected(boolean isScan) {
        vm.isScan = isScan;
        b.textScan.setVisibility(isScan ? View.VISIBLE : View.GONE);
        b.web.setMarkEnabled(!isScan);
    }

    @Override
    public void onSelection(String json) {
        vm.lastSelectionJson = json == null || json.isEmpty() ? null : json;
    }

    @Override
    public void onAnnotationTap(long id) {
        AnnotationEntity a = vm.findAnnotation(id);
        if (a != null) showAnnotationSheet(a);
    }

    @Override
    public void onPageChanged(int page) {
        vm.saveLastPage(page);
    }

    // ------------------------------------------------------------ Đánh dấu

    /** Bấm một trong 5 nút: hỏi JavaScript trang, toạ độ và đoạn chữ đang bôi đen. */
    private void onMarkClicked(String type, ActionMode mode) {
        b.web.evaluateJavascript("JSON.stringify(reno.getSelection())", value -> {
            String json = decodeJsString(value);
            if (json == null) json = vm.lastSelectionJson;
            mode.finish();
            if (json == null) {
                Snackbar.make(b.getRoot(), R.string.annotation_no_selection,
                        Snackbar.LENGTH_SHORT).show();
                return;
            }
            try {
                JSONObject sel = new JSONObject(json);
                AnnotationEntity a = new AnnotationEntity();
                a.topicId = topic.id;
                a.type = type;
                a.pageIndex = sel.getInt("page");
                a.rects = sel.getJSONArray("rects").toString();
                a.selectedText = sel.getString("text");
                showAnnotationDialog(a, true);
            } catch (JSONException | RuntimeException e) {
                Log.e(TAG, "Vùng chọn không hợp lệ: " + json, e);
            }
        });
    }

    /** evaluateJavascript trả về chuỗi JSON đã bọc trong dấu nháy; "null" nghĩa là không có. */
    @Nullable
    private static String decodeJsString(@Nullable String value) {
        if (value == null || "null".equals(value) || value.isEmpty()) return null;
        try {
            Object decoded = new org.json.JSONTokener(value).nextValue();
            if (decoded instanceof String) {
                String s = (String) decoded;
                return s.isEmpty() || "null".equals(s) ? null : s;
            }
        } catch (JSONException ignored) {
            // rơi xuống trả về null
        }
        return null;
    }

    /** Hộp thoại Tên / Chi tiết (đều được để trống); riêng Highlight có bảng màu. */
    private void showAnnotationDialog(AnnotationEntity a, boolean isNew) {
        DialogAnnotationBinding d = DialogAnnotationBinding.inflate(getLayoutInflater());
        boolean highlight = AnnotationEntity.TYPE_HIGHLIGHT.equals(a.type);
        d.textQuote.setText(a.selectedText);
        d.editTitle.setText(a.title);
        d.editDetail.setText(a.detail);
        d.labelColor.setVisibility(highlight ? View.VISIBLE : View.GONE);
        d.palette.setVisibility(highlight ? View.VISIBLE : View.GONE);
        d.palette.setColors(HIGHLIGHT_COLORS, a.color == null ? HIGHLIGHT_COLORS[0] : a.color);
        new MaterialAlertDialogBuilder(this)
                .setTitle(isNew ? getString(labelOf(a.type)) : getString(R.string.annotation_edit))
                .setView(d.getRoot())
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    String title = emptyToNull(d.editTitle.getText());
                    String detail = emptyToNull(d.editDetail.getText());
                    String color = highlight ? d.palette.getSelected() : null;
                    if (isNew) {
                        a.title = title;
                        a.detail = detail;
                        a.color = color;
                        vm.addAnnotation(a);          // LiveData sẽ vẽ ngay lên trang
                    } else {
                        vm.updateAnnotation(a.id, title, detail, color);
                    }
                })
                .show();
    }

    /** Bấm vào một đánh dấu có sẵn: bảng dưới hiện tên, chi tiết, nút Sửa và Xóa. */
    private void showAnnotationSheet(AnnotationEntity a) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        SheetAnnotationBinding s = SheetAnnotationBinding.inflate(getLayoutInflater());
        s.textType.setText(getString(labelOf(a.type)) + " · "
                + getString(R.string.annotation_page, a.pageIndex + 1));
        s.textTitle.setText(isEmpty(a.title) ? getString(R.string.annotation_no_title) : a.title);
        s.textDetail.setText(a.detail);
        s.textDetail.setVisibility(isEmpty(a.detail) ? View.GONE : View.VISIBLE);
        s.textQuote.setText(a.selectedText);
        s.btnEdit.setOnClickListener(v -> {
            sheet.dismiss();
            showAnnotationDialog(a, false);
        });
        s.btnDelete.setOnClickListener(v -> {
            sheet.dismiss();
            confirmDelete(a);
        });
        sheet.setContentView(s.getRoot());
        sheet.show();
    }

    private void confirmDelete(AnnotationEntity a) {
        Dialogs.confirmDelete(this, getString(R.string.annotation_delete_title),
                getString(R.string.annotation_delete_message),
                () -> vm.deleteAnnotation(a.id));
    }

    /** Chuyển sang tab Ghi chú, cuộn tới đúng trang và vị trí, nháy sáng vùng đánh dấu 2 lần. */
    private void jumpTo(AnnotationEntity a) {
        selectTab(0);
        b.web.postDelayed(() ->
                b.web.evaluateJavascript("reno.scrollTo(" + a.id + ")", null), 150);
    }

    // ------------------------------------------------------------ Thống kê

    private void setUpStats() {
        LayoutInflater inflater = getLayoutInflater();
        for (int i = 0; i < countCells.length; i++) {
            countCells[i] = ViewCountCellBinding.inflate(inflater, b.rowCounts, true);
            countCells[i].textLabel.setText(TYPE_LABELS[i]);
            countCells[i].textCount.setText("0");
            b.tabsFilter.addTab(b.tabsFilter.newTab().setText(TYPE_LABELS[i]));
        }
        TabLayout.Tab filter = b.tabsFilter.getTabAt(vm.selectedFilter);
        if (filter != null) filter.select();
        b.tabsFilter.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                vm.selectedFilter = tab.getPosition();
                refreshStats();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        b.listAnnotations.setAdapter(adapter);
    }

    /** Số đếm và danh sách lọc đều tính lại từ LiveData nên cập nhật ngay sau khi thêm / xóa. */
    private void refreshStats() {
        int[] counts = new int[AnnotationEntity.TYPES.length];
        String filterType = AnnotationEntity.TYPES[vm.selectedFilter];
        List<AnnotationEntity> filtered = new ArrayList<>();
        for (AnnotationEntity a : vm.currentAnnotations()) {
            for (int i = 0; i < counts.length; i++) {
                if (AnnotationEntity.TYPES[i].equals(a.type)) counts[i]++;
            }
            if (filterType.equals(a.type)) filtered.add(a);
        }
        for (int i = 0; i < counts.length; i++) {
            countCells[i].textCount.setText(String.valueOf(counts[i]));
        }
        adapter.submit(filtered);
        b.textEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static int labelOf(String type) {
        for (int i = 0; i < AnnotationEntity.TYPES.length; i++) {
            if (AnnotationEntity.TYPES[i].equals(type)) return TYPE_LABELS[i];
        }
        return R.string.mark_note;
    }

    private static boolean isEmpty(@Nullable String s) {
        return s == null || s.trim().isEmpty();
    }

    @Nullable
    private static String emptyToNull(@Nullable CharSequence cs) {
        String s = cs == null ? "" : cs.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private final class AnnotationAdapter extends RecyclerView.Adapter<AnnotationHolder> {
        private List<AnnotationEntity> items = new ArrayList<>();

        void submit(List<AnnotationEntity> list) {
            items = list;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AnnotationHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new AnnotationHolder(ItemAnnotationBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull AnnotationHolder holder, int position) {
            AnnotationEntity a = items.get(position);
            ItemAnnotationBinding ib = holder.b;
            ib.textTitle.setText(isEmpty(a.title)
                    ? getString(R.string.annotation_no_title) : a.title);
            ib.textDetail.setText(a.detail);
            ib.textDetail.setVisibility(isEmpty(a.detail) ? View.GONE : View.VISIBLE);
            ib.textQuote.setText(a.selectedText);
            ib.textPage.setText(getString(R.string.annotation_page, a.pageIndex + 1));
            ib.textJump.setOnClickListener(v -> jumpTo(a));
            ib.btnDelete.setOnClickListener(v -> confirmDelete(a));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static final class AnnotationHolder extends RecyclerView.ViewHolder {
        final ItemAnnotationBinding b;

        AnnotationHolder(ItemAnnotationBinding b) {
            super(b.getRoot());
            this.b = b;
        }
    }
}
