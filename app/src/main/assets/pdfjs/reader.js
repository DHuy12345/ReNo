/*
 * ReNo – trình đọc PDF chạy trong WebView, dựng trên thành phần PDFViewer của PDF.js.
 *
 * Java gọi vào đây:  reno.load(url, lastPage), reno.render(annotations), reno.scrollTo(id),
 *                    reno.getSelection()
 * Ở đây gọi lại Java (đối tượng "Android", chỉ truyền dữ liệu):
 *                    onLoaded(pageCount), onLoadError(message), onScanDetected(isScan),
 *                    onSelection(json), onAnnotationTap(id), onPageChanged(page)
 */
import * as pdfjsLib from './pdf.mjs';

const { EventBus, PDFViewer, PDFLinkService } = await import('./pdf_viewer.mjs');

pdfjsLib.GlobalWorkerOptions.workerSrc = './pdf.worker.mjs';

const MIN_SCALE = 0.5;
const MAX_SCALE = 4;
const BADGES = { QUESTION: '?', IMPORTANT: '!', STAR: '★', NOTE: '✎' };

const container = document.getElementById('viewerContainer');
const statusEl = document.getElementById('status');
const eventBus = new EventBus();
const linkService = new PDFLinkService({ eventBus });
const pdfViewer = new PDFViewer({
  container,
  eventBus,
  linkService,
  textLayerMode: 1,        // có lớp chữ để bôi đen
  annotationMode: 0,       // không vẽ chú thích có sẵn trong file
  removePageBorders: true,
});
linkService.setViewer(pdfViewer);

/** Gọi sang Java nếu đang chạy trong WebView; trong trình duyệt thường thì bỏ qua. */
function callAndroid(method, ...args) {
  const bridge = window.Android;
  if (bridge && typeof bridge[method] === 'function') {
    try { bridge[method](...args); } catch (e) { console.error(method, e); }
  }
}

let annotations = [];      // [{id, type, page, rects: [[x, y, w, h]], color}]
let pendingFlashId = null; // đánh dấu cần nháy sáng khi trang của nó vẽ xong
let initialPage = 1;
let loadingTask = null;

function pageDiv(pageNumber) {
  return container.querySelector(`.page[data-page-number="${pageNumber}"]`);
}

/** Vẽ lại lớp đánh dấu của một trang. Toạ độ theo % nên vẫn đúng ở mọi mức phóng. */
function drawPage(pageNumber) {
  const div = pageDiv(pageNumber);
  if (!div) return;
  const old = div.querySelector(':scope > .reno-layer');
  if (old) old.remove();
  const list = annotations.filter(a => a.page === pageNumber - 1);
  if (!list.length) return;
  const layer = document.createElement('div');
  layer.className = 'reno-layer';
  for (const a of list) {
    a.rects.forEach((r, i) => {
      const el = document.createElement('div');
      const isHighlight = a.type === 'HIGHLIGHT';
      el.className = 'reno-rect ' + (isHighlight ? 'hl' : 'ul');
      el.dataset.id = String(a.id);
      el.style.left = (r[0] * 100) + '%';
      el.style.top = (r[1] * 100) + '%';
      el.style.width = (r[2] * 100) + '%';
      el.style.height = (r[3] * 100) + '%';
      if (isHighlight) el.style.backgroundColor = a.color || '#FFE58A';
      layer.appendChild(el);
      if (i === 0 && BADGES[a.type]) {
        const badge = document.createElement('div');
        badge.className = 'reno-badge';
        badge.textContent = BADGES[a.type];
        badge.style.left = (r[0] * 100) + '%';
        badge.style.top = (r[1] * 100) + '%';
        layer.appendChild(badge);
      }
    });
  }
  div.appendChild(layer);
  if (pendingFlashId !== null) flash(pendingFlashId);
}

function drawAll() {
  for (let n = 1; n <= pdfViewer.pagesCount; n++) {
    const view = pdfViewer.getPageView(n - 1);
    // chỉ vẽ lên trang đã dựng xong; trang khác sẽ được vẽ ở sự kiện pagerendered
    if (view && view.renderingState === 3) drawPage(n);
  }
}

/** Nháy sáng vùng đánh dấu 2 lần. */
function flash(id) {
  const els = container.querySelectorAll(`.reno-rect[data-id="${id}"]`);
  if (!els.length) return false;
  pendingFlashId = null;
  els.forEach(el => {
    el.classList.remove('reno-flash');
    void el.offsetWidth;            // khởi động lại animation
    el.classList.add('reno-flash');
  });
  return true;
}

/** Gộp các khung chữ nhỏ nằm trên cùng một dòng thành một khung. */
function mergeRects(rects) {
  const sorted = rects.slice().sort((a, b) => (a[1] - b[1]) || (a[0] - b[0]));
  const out = [];
  for (const r of sorted) {
    const last = out[out.length - 1];
    const sameLine = last
      && Math.abs(last[1] - r[1]) < Math.max(last[3], r[3]) * 0.5
      && r[0] <= last[0] + last[2] + 0.01;
    if (sameLine) {
      const right = Math.max(last[0] + last[2], r[0] + r[2]);
      const top = Math.min(last[1], r[1]);
      const bottom = Math.max(last[1] + last[3], r[1] + r[3]);
      last[0] = Math.min(last[0], r[0]);
      last[2] = right - last[0];
      last[1] = top;
      last[3] = bottom - top;
    } else {
      out.push(r.slice());
    }
  }
  return out.map(r => r.map(v => Math.round(v * 100000) / 100000));
}

function closestPage(node) {
  const el = node && (node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement);
  return el ? el.closest('.page') : null;
}

const reno = {
  /** Nạp file PDF và cuộn tới trang đọc lần trước (đếm từ 0). */
  async load(url, lastPage) {
    initialPage = (Number(lastPage) || 0) + 1;
    statusEl.textContent = '';
    try {
      if (loadingTask) await loadingTask.destroy();
      loadingTask = pdfjsLib.getDocument({
        url,
        cMapUrl: './cmaps/',
        cMapPacked: true,
        standardFontDataUrl: './standard_fonts/',
        isEvalSupported: false,
      });
      const pdf = await loadingTask.promise;
      pdfViewer.setDocument(pdf);
      linkService.setDocument(pdf, null);
      callAndroid('onLoaded', pdf.numPages);
      // Trang đầu không có chữ: coi là file scan, không bôi đen được.
      const first = await pdf.getPage(1);
      const text = await first.getTextContent();
      const hasText = text.items.some(it => it.str && it.str.trim().length > 0);
      callAndroid('onScanDetected', !hasText);
    } catch (e) {
      console.error(e);
      callAndroid('onLoadError', String(e && e.message ? e.message : e));
    }
  },

  /** Nhận toàn bộ đánh dấu của topic và vẽ lại. */
  render(list) {
    annotations = Array.isArray(list) ? list : [];
    drawAll();
  },

  /** Cuộn tới đánh dấu và nháy sáng 2 lần. */
  scrollTo(id) {
    const a = annotations.find(x => x.id === id);
    if (!a) return false;
    const div = pageDiv(a.page + 1);
    if (!div) return false;
    const y = a.rects.length ? a.rects[0][1] : 0;
    const top = div.getBoundingClientRect().top - container.getBoundingClientRect().top
      + container.scrollTop + y * div.clientHeight;
    container.scrollTop = Math.max(0, top - container.clientHeight / 3);
    pendingFlashId = id;
    flash(id);    // nếu trang chưa vẽ xong, drawPage sẽ nháy sau
    return true;
  },

  /** Vùng đang bôi đen: {page (đếm từ 0), rects theo tỉ lệ 0–1 của trang, text}; không có thì null. */
  getSelection() {
    const sel = window.getSelection();
    if (!sel || sel.isCollapsed || !sel.rangeCount) return null;
    const range = sel.getRangeAt(0);
    const page = closestPage(range.startContainer);
    if (!page) return null;
    const box = page.getBoundingClientRect();
    if (!box.width || !box.height) return null;
    const rects = [];
    for (const r of range.getClientRects()) {
      if (r.width < 1 || r.height < 1) continue;
      // chỉ giữ phần nằm trong trang bắt đầu vùng chọn
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      if (cx < box.left || cx > box.right || cy < box.top || cy > box.bottom) continue;
      rects.push([(r.left - box.left) / box.width, (r.top - box.top) / box.height,
        r.width / box.width, r.height / box.height]);
    }
    const text = sel.toString().replace(/\s+/g, ' ').trim();
    if (!rects.length || !text) return null;
    return { page: Number(page.dataset.pageNumber) - 1, rects: mergeRects(rects), text };
  },

  zoomBy(factor) {
    const next = Math.min(MAX_SCALE, Math.max(MIN_SCALE, pdfViewer.currentScale * factor));
    pdfViewer.currentScale = next;
  },
};
window.reno = reno;

eventBus.on('pagesinit', () => {
  pdfViewer.currentScaleValue = 'page-width';
  if (initialPage > 1 && initialPage <= pdfViewer.pagesCount) {
    pdfViewer.currentPageNumber = initialPage;
  }
});
eventBus.on('pagerendered', evt => drawPage(evt.pageNumber));
eventBus.on('pagechanging', evt => callAndroid('onPageChanged', evt.pageNumber - 1));

// Báo vùng chọn cho Java (dự phòng khi Java không gọi kịp reno.getSelection()).
let selectionTimer = 0;
document.addEventListener('selectionchange', () => {
  clearTimeout(selectionTimer);
  selectionTimer = setTimeout(() => {
    const s = reno.getSelection();
    callAndroid('onSelection', s ? JSON.stringify(s) : '');
  }, 150);
});

// Bấm vào một đánh dấu có sẵn: so toạ độ điểm bấm với các khung đã lưu.
container.addEventListener('click', evt => {
  const sel = window.getSelection();
  if (sel && !sel.isCollapsed) return;
  const page = closestPage(evt.target);
  if (!page) return;
  const box = page.getBoundingClientRect();
  const x = (evt.clientX - box.left) / box.width;
  const y = (evt.clientY - box.top) / box.height;
  const pageIndex = Number(page.dataset.pageNumber) - 1;
  const padY = 6 / box.height;
  const hit = annotations.find(a => a.page === pageIndex && a.rects.some(r =>
    x >= r[0] && x <= r[0] + r[2] && y >= r[1] - padY && y <= r[1] + r[3] + padY));
  if (hit) callAndroid('onAnnotationTap', hit.id);
});

document.getElementById('zoomIn').addEventListener('click', () => reno.zoomBy(1.25));
document.getElementById('zoomOut').addEventListener('click', () => reno.zoomBy(0.8));

// Màn hình đổi kích thước (bàn phím, xoay): giữ trang vừa bề ngang nếu đang ở chế độ đó.
window.addEventListener('resize', () => {
  if (pdfViewer.currentScaleValue === 'page-width') pdfViewer.currentScaleValue = 'page-width';
});

callAndroid('onReady');
