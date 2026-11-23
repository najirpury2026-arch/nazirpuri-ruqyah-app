package com.nazirpuri.ruqyah;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight continuous PDF reader.
 * Uses ordinary ImageViews and renders only nearby pages on a single worker thread.
 * Never creates a giant bitmap for the complete document.
 */
public class PdfReaderActivity extends Activity {
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private HandlerThread renderThread;
    private Handler renderHandler;
    private ReliableScrollView scroll;
    private PdfPagesView pagesView;
    private TextView pageNumber;
    private final Set<Integer> pending = new HashSet<>();
    private final LinkedHashMap<Integer, Bitmap> cache = new LinkedHashMap<>(4, .75f, true);
    private volatile boolean closing = false;
    private int pageHeight, pageWidth, pageGap, pageCount;
    private int generation = 0;
    private boolean fastScrolling = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(12,73,52));
        getWindow().setNavigationBarColor(Color.rgb(8,49,36));
        try {
            String path = getIntent().getStringExtra("pdf_path");
            if (path == null) throw new IOException("Missing PDF path");
            File file = new File(path).getCanonicalFile();
            File books = new File(getFilesDir(), "kitab_books").getCanonicalFile();
            if (!books.equals(file.getParentFile()) || !file.isFile() || file.length() < 1000)
                throw new IOException("Invalid PDF file");
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            pageCount = renderer.getPageCount();
            if (pageCount < 1) throw new IOException("Empty PDF");
            PdfRenderer.Page first = renderer.openPage(0);
            float ratio = first.getHeight() / (float) first.getWidth();
            first.close();

            pageWidth = Math.max(1, getResources().getDisplayMetrics().widthPixels - dp(16));
            pageHeight = Math.max(1, Math.round(pageWidth * ratio));
            pageGap = dp(9);
            renderThread = new HandlerThread("PdfPageRenderer", android.os.Process.THREAD_PRIORITY_DISPLAY);
            renderThread.start();
            renderHandler = new Handler(renderThread.getLooper());
            buildReader(file.getName());
        } catch (Exception e) {
            Toast.makeText(this, "কিতাবটি খোলা যাচ্ছে না। ফাইলটি আবার ডাউনলোড করে দেখুন।", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void buildReader(String fileName) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(235,239,236));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(4), dp(12), dp(4));
        header.setBackgroundColor(Color.rgb(12,73,52));
        TextView back = new TextView(this);
        back.setText("‹");
        back.setTextSize(32);
        back.setTextColor(Color.WHITE);
        back.setGravity(Gravity.CENTER);
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(46)));
        back.setOnClickListener(v -> finish());
        TextView title = new TextView(this);
        title.setText(fileName.replace(".pdf", ""));
        title.setTextSize(15);
        title.setTextColor(Color.WHITE);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        FrameLayout frame = new FrameLayout(this);
        scroll = new ReliableScrollView();
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        scroll.setSmoothScrollingEnabled(true);
        scroll.setBackgroundColor(Color.rgb(224,228,225));
        // One virtualized drawing surface replaces hundreds of ImageViews.
        // Only pages near the viewport are rendered and drawn.
        pagesView = new PdfPagesView();
        int contentHeight = dp(12) + pageCount * pageHeight + Math.max(0, pageCount - 1) * pageGap;
        scroll.addView(pagesView, new ScrollView.LayoutParams(-1, contentHeight));
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        pageNumber = new TextView(this);
        pageNumber.setText("১ / " + bn(pageCount));
        pageNumber.setTextColor(Color.WHITE);
        pageNumber.setTextSize(12);
        pageNumber.setGravity(Gravity.CENTER);
        pageNumber.setPadding(dp(11), dp(7), dp(11), dp(7));
        pageNumber.setBackgroundColor(0xDD174D38);
        FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.BOTTOM);
        badge.rightMargin = dp(12);
        badge.bottomMargin = dp(12);
        frame.addView(pageNumber, badge);


        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = new TextView(this);
        hint.setText("উপরে-নিচে টান দিয়ে পড়ুন  •  বিজ্ঞাপনমুক্ত অফলাইন রিডার");
        hint.setTextColor(Color.rgb(76,101,86));
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(Color.WHITE);
        root.addView(hint, new LinearLayout.LayoutParams(-1, dp(30)));
        setContentView(root);

        scroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) -> {
            if (fastScrolling) updatePageNumber();
            else updateVisiblePages();
        });
        scroll.post(() -> updateVisiblePages());
    }

    private void updateVisiblePages() {
        if (closing || scroll == null) return;
        int y = scroll.getScrollY();
        int stride = pageHeight + pageGap;
        int first = Math.max(0, y / stride);
        int last = Math.min(pageCount - 1, (y + scroll.getHeight()) / stride);
        updatePageNumber();

        Set<Integer> wanted = new LinkedHashSet<>();
        for (int i = Math.max(0, first - 1); i <= Math.min(pageCount - 1, last + 1); i++) wanted.add(i);

        synchronized (cache) {
            java.util.Iterator<Map.Entry<Integer, Bitmap>> it = cache.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Bitmap> entry = it.next();
                if (!wanted.contains(entry.getKey())) {
                    if (!entry.getValue().isRecycled()) entry.getValue().recycle();
                    it.remove();
                }
            }
        }
        for (int index : wanted) requestPage(index);
    }

    private void updatePageNumber() {
        if (scroll == null || pageNumber == null) return;
        int stride = pageHeight + pageGap;
        int current = Math.min(pageCount, Math.max(1, scroll.getScrollY() / stride + 1));
        pageNumber.setText(bn(current) + " / " + bn(pageCount));
    }

    private void requestPage(final int index) {
        synchronized (cache) {
            Bitmap ready = cache.get(index);
            if (ready != null && !ready.isRecycled()) return;
            if (pending.contains(index)) return;
            pending.add(index);
        }
        final int requestGeneration = generation;
        renderHandler.post(() -> {
            Bitmap bitmap = null;
            PdfRenderer.Page page = null;
            try {
                if (closing || renderer == null) return;
                page = renderer.openPage(index);
                int width = Math.min(pageWidth, 1000);
                int height = Math.max(1, Math.round(width * page.getHeight() / (float) page.getWidth()));
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            } catch (Exception ignored) {
                if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
                bitmap = null;
            } finally {
                if (page != null) page.close();
            }
            final Bitmap result = bitmap;
            runOnUiThread(() -> {
                synchronized (cache) { pending.remove(index); }
                if (result == null) return;
                if (closing || requestGeneration != generation || isFinishing() || isDestroyed()) {
                    result.recycle();
                    return;
                }
                synchronized (cache) {
                    cache.put(index, result);
                    while (cache.size() > 6) {
                        Integer eldest = cache.keySet().iterator().next();
                        Bitmap old = cache.remove(eldest);
                        if (!old.isRecycled()) old.recycle();
                    }
                }
                if (pagesView != null) pagesView.invalidate();
            });
        });
    }

    /** Ensures vertical drags on the PDF surface are intercepted by the ScrollView. */
    private final class ReliableScrollView extends ScrollView {
        private float downX, downY;
        private final int touchSlop;
        ReliableScrollView() {
            super(PdfReaderActivity.this);
            touchSlop = android.view.ViewConfiguration.get(PdfReaderActivity.this).getScaledTouchSlop();
            setFillViewport(true);
            setVerticalScrollBarEnabled(true);
            setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
            setSmoothScrollingEnabled(true);
            setNestedScrollingEnabled(false);
        }
        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    float dx = Math.abs(event.getX() - downX);
                    float dy = Math.abs(event.getY() - downY);
                    if (dy > touchSlop && dy > dx) {
                        return true;
                    }
                    break;
            }
            return super.onInterceptTouchEvent(event);
        }
    }

    /** Draw only the visible PDF pages; no per-page Android views are created. */
    private final class PdfPagesView extends View {
        private final android.graphics.Paint pagePaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        private final android.graphics.Paint shadowPaint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        PdfPagesView() {
            super(PdfReaderActivity.this);
            setWillNotDraw(false);
            setBackgroundColor(Color.rgb(224,228,225));
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (closing || pageCount < 1) return;
            android.graphics.Rect clip = canvas.getClipBounds();
            int stride = pageHeight + pageGap;
            int first = Math.max(0, clip.top / Math.max(1, stride));
            int last = Math.min(pageCount - 1, Math.max(first, clip.bottom / Math.max(1, stride)));
            float left = dp(8);
            float right = getWidth() - dp(8);
            for (int i = first; i <= last; i++) {
                float top = dp(6) + i * stride;
                android.graphics.RectF dst = new android.graphics.RectF(left, top, right, top + pageHeight);
                shadowPaint.setColor(Color.WHITE);
                canvas.drawRect(dst, shadowPaint);
                Bitmap bitmap;
                synchronized (cache) { bitmap = cache.get(i); }
                if (bitmap != null && !bitmap.isRecycled()) {
                    canvas.drawBitmap(bitmap, null, dst, pagePaint);
                } else {
                    requestPage(i);
                }
            }
        }
    }

    private final class FastScrollHandle extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float thumbTop;
        private float thumbHeight;
        private boolean dragging = false;

        FastScrollHandle() {
            super(PdfReaderActivity.this);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setContentDescription("দ্রুত পৃষ্ঠা পরিবর্তন করতে ধরে ওপর-নিচে টানুন");
        }

        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            if (scroll == null) return;
            float w = getWidth(), h = getHeight();
            float centerX = w - dp(13);
            float trackTop = dp(12), trackBottom = h - dp(12);
            float trackHeight = Math.max(1, trackBottom - trackTop);
            int contentHeight = scroll.getChildAt(0) == null ? 0 : scroll.getChildAt(0).getHeight();
            int viewport = scroll.getHeight();
            float ratio = contentHeight <= 0 ? 1f : Math.min(1f, viewport / (float) contentHeight);
            thumbHeight = Math.max(dp(48), trackHeight * ratio);
            thumbHeight = Math.min(trackHeight, thumbHeight);
            float maxScroll = Math.max(0, contentHeight - viewport);
            float fraction = maxScroll == 0 ? 0 : scroll.getScrollY() / (float) maxScroll;
            thumbTop = trackTop + (trackHeight - thumbHeight) * fraction;

            paint.setColor(0x66809086);
            paint.setStrokeWidth(dp(3));
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            canvas.drawLine(centerX, trackTop, centerX, trackBottom, paint);

            paint.setColor(dragging ? Color.rgb(190,145,48) : Color.rgb(12,73,52));
            canvas.drawRoundRect(centerX - dp(8), thumbTop, centerX + dp(8), thumbTop + thumbHeight,
                dp(9), dp(9), paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(dp(16));
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            canvas.drawText("↕", centerX, thumbTop + thumbHeight / 2f + dp(6), paint);
        }

        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if (scroll == null) return false;
            float trackTop = dp(12), trackBottom = getHeight() - dp(12);
            float contentHeight = scroll.getChildAt(0) == null ? 0 : scroll.getChildAt(0).getHeight();
            float maxScroll = Math.max(0, contentHeight - scroll.getHeight());
            switch (event.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    // Only capture a gesture when the user actually touches the thumb.
                    // All other touches pass through so the PDF ScrollView receives swipes.
                    if (event.getY() < thumbTop - dp(8) || event.getY() > thumbTop + thumbHeight + dp(8)) {
                        return false;
                    }
                    fastScrolling = true;
                    dragging = true;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;

                case android.view.MotionEvent.ACTION_MOVE:
                    if (!dragging) return false;
                    float usable = Math.max(1, trackBottom - trackTop - thumbHeight);
                    float targetTop = Math.max(trackTop, Math.min(trackBottom - thumbHeight, event.getY() - thumbHeight / 2f));
                    float fraction = (targetTop - trackTop) / usable;
                    scroll.scrollTo(0, Math.round(maxScroll * fraction));
                    updatePageNumber();
                    invalidate();
                    return true;

                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    if (!dragging) return false;
                    fastScrolling = false;
                    dragging = false;
                    getParent().requestDisallowInterceptTouchEvent(false);
                    generation++;
                    if (renderHandler != null) renderHandler.removeCallbacksAndMessages(null);
                    synchronized (cache) { pending.clear(); }
                    updateVisiblePages();
                    invalidate();
                    return true;
            }
            return dragging;
        }
    }

    private String bn(int n) {
        return String.valueOf(n).replace('0','০').replace('1','১').replace('2','২').replace('3','৩')
            .replace('4','৪').replace('5','৫').replace('6','৬').replace('7','৭').replace('8','৮').replace('9','৯');
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onBackPressed() { finish(); }

    @Override protected void onDestroy() {
        closing = true;
        generation++;
        if (renderHandler != null) {
            renderHandler.removeCallbacksAndMessages(null);
            renderHandler.post(() -> {
                if (renderer != null) {
                    try { renderer.close(); } catch (Exception ignored) { }
                    renderer = null;
                }
                renderThread.quitSafely();
            });
        } else {
            if (renderer != null) { try { renderer.close(); } catch (Exception ignored) { } renderer = null; }
            if (descriptor != null) { try { descriptor.close(); } catch (IOException ignored) { } descriptor = null; }
        }
        synchronized (cache) {
            for (Bitmap b : cache.values()) if (!b.isRecycled()) b.recycle();
            cache.clear();
        }
        super.onDestroy();
    }
}
