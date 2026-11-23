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
    private ScrollView scroll;
    private LinearLayout pageColumn;
    private TextView pageNumber;
    private final java.util.ArrayList<ImageView> pageViews = new java.util.ArrayList<>();
    private final Set<Integer> pending = new HashSet<>();
    private final LinkedHashMap<Integer, Bitmap> cache = new LinkedHashMap<>(4, .75f, true);
    private volatile boolean closing = false;
    private int pageHeight, pageWidth, pageGap, pageCount;
    private int generation = 0;

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
            renderThread = new HandlerThread("PdfPageRenderer");
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
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(224,228,225));
        pageColumn = new LinearLayout(this);
        pageColumn.setOrientation(LinearLayout.VERTICAL);
        pageColumn.setPadding(dp(8), dp(6), dp(8), dp(6));
        for (int i = 0; i < pageCount; i++) {
            ImageView image = new ImageView(this);
            image.setBackgroundColor(Color.WHITE);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setAdjustViewBounds(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, pageHeight);
            if (i > 0) lp.topMargin = pageGap;
            pageColumn.addView(image, lp);
            pageViews.add(image);
        }
        scroll.addView(pageColumn, new ScrollView.LayoutParams(-1, -2));
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

        scroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) -> updateVisiblePages());
        scroll.post(() -> updateVisiblePages());
    }

    private void updateVisiblePages() {
        if (closing || scroll == null) return;
        int y = scroll.getScrollY();
        int stride = pageHeight + pageGap;
        int first = Math.max(0, y / stride);
        int last = Math.min(pageCount - 1, (y + scroll.getHeight()) / stride);
        int current = Math.min(pageCount, first + 1);
        pageNumber.setText(bn(current) + " / " + bn(pageCount));

        Set<Integer> wanted = new HashSet<>();
        for (int i = Math.max(0, first - 1); i <= Math.min(pageCount - 1, last + 1); i++) wanted.add(i);

        synchronized (cache) {
            java.util.Iterator<Map.Entry<Integer, Bitmap>> it = cache.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Bitmap> entry = it.next();
                if (!wanted.contains(entry.getKey())) {
                    ImageView iv = pageViews.get(entry.getKey());
                    if (iv.getDrawable() != null) iv.setImageDrawable(null);
                    if (!entry.getValue().isRecycled()) entry.getValue().recycle();
                    it.remove();
                }
            }
        }
        for (int index : wanted) requestPage(index);
    }

    private void requestPage(final int index) {
        synchronized (cache) {
            Bitmap ready = cache.get(index);
            if (ready != null && !ready.isRecycled()) {
                if (pageViews.get(index).getDrawable() == null) pageViews.get(index).setImageBitmap(ready);
                return;
            }
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
                int width = Math.min(pageWidth, 1200);
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
                    while (cache.size() > 4) {
                        Integer eldest = cache.keySet().iterator().next();
                        Bitmap old = cache.remove(eldest);
                        ImageView oldView = pageViews.get(eldest);
                        if (oldView.getDrawable() != null) oldView.setImageDrawable(null);
                        if (!old.isRecycled()) old.recycle();
                    }
                }
                pageViews.get(index).setImageBitmap(result);
            });
        });
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
