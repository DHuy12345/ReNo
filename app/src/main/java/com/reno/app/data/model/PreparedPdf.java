package com.reno.app.data.model;

/** File PDF đã chép vào bộ nhớ trong và mở thử thành công, chờ người dùng đặt tên topic. */
public class PreparedPdf {
    /** Đường dẫn tuyệt đối trong filesDir/pdfs. */
    public String filePath;
    /** Tên file lúc chọn. */
    public String originalName;
    public int pageCount;

    /** Tên gợi ý cho topic: tên file bỏ đuôi ".pdf". */
    public String suggestedName() {
        if (originalName == null) return "";
        String n = originalName.trim();
        return n.toLowerCase().endsWith(".pdf") ? n.substring(0, n.length() - 4) : n;
    }
}
