package com.nazirpuri.ruqyah;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ad-free continuous vertical PDF reader. Only visible pages are rendered and cached. */
public class PdfReaderActivity extends Activity {
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private ScrollView scroll;
    private PdfPagesView pagesView;
    private TextView pageNumber;
    private int pageHeight;
    private int pageGap;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(12,73,52));
        getWindow().setNavigationBarColor(Color.rgb(8,49,36));
        try {
            String path = getIntent().getStringExtra("pdf_path");
            if (path == null) throw new IOException("Missing PDF");
            File file = new File(path).getCanonicalFile();
            File books = new File(getFilesDir(), "kitab_books").getCanonicalFile();
            if (!books.equals(file.getParentFile()) || !file.isFile() || file.length() < 1000)
                throw new IOException("Invalid PDF");
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            buildReader(file.getName());
        } catch (Exception e) {
            Toast.makeText(this, "কিতাবটি খোলা যাচ্ছে না। আবার ডাউনলোড করে দেখুন।", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void buildReader(String fileName) throws IOException {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(235,239,236));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(5), dp(12), dp(5));
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
        title.setTextSize(16);
        title.setTextColor(Color.WHITE);
        title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(52)));

        int viewWidth = getResources().getDisplayMetrics().widthPixels;
        int contentWidth = Math.max(1, viewWidth - dp(16));
        PdfRenderer.Page first = renderer.openPage(0);
        float ratio = first.getHeight() / (float) first.getWidth();
        first.close();
        pageHeight = Math.max(1, (int)(contentWidth * ratio));
        pageGap = dp(10);

        FrameLayout frame = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        pagesView = new PdfPagesView(contentWidth);
        scroll.addView(pagesView, new ScrollView.LayoutParams(contentWidth, pageHeight * renderer.getPageCount() + pageGap * (renderer.getPageCount() - 1)));
        frame.addView(scroll, new FrameLayout.LayoutParams(-1, -1));

        pageNumber = new TextView(this);
        pageNumber.setText("১ / " + bn(renderer.getPageCount()));
        pageNumber.setTextColor(Color.WHITE);
        pageNumber.setTextSize(12);
        pageNumber.setGravity(Gravity.CENTER);
        pageNumber.setPadding(dp(10), dp(7), dp(10), dp(7));
        pageNumber.setBackgroundColor(0xCC174D38);
        FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(-2, -2, Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        badge.rightMargin = dp(8);
        frame.addView(pageNumber, badge);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView hint = new TextView(this);
        hint.setText("উপরে-নিচে টান দিয়ে সম্পূর্ণ কিতাব পড়ুন  •  বিজ্ঞাপনমুক্ত");
        hint.setTextColor(Color.rgb(76,101,86));
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        hint.setBackgroundColor(Color.WHITE);
        root.addView(hint, new LinearLayout.LayoutParams(-1, dp(30)));
        setContentView(root);

        scroll.setOnScrollChangeListener((View v, int x, int y, int oldX, int oldY) -> {
            int current = Math.min(renderer.getPageCount(), Math.max(1, y / (pageHeight + pageGap) + 1));
            pageNumber.setText(bn(current) + " / " + bn(renderer.getPageCount()));
            pagesView.invalidate();
        });
    }

    private String bn(int number) {
        return String.valueOf(number).replace('0','০').replace('1','১').replace('2','২')
            .replace('3','৩').replace('4','৪').replace('5','৫').replace('6','৬')
            .replace('7','৭').replace('8','৮').replace('9','৯');
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private final class PdfPagesView extends View {
        private final int contentWidth;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final LinkedHashMap<Integer, Bitmap> cache = new LinkedHashMap<Integer, Bitmap>(4, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<Integer, Bitmap> eldest) {
                if (size() > 3) {
                    eldest.getValue().recycle();
                    return true;
                }
                return false;
            }
        };

        PdfPagesView(int width) {
            super(PdfReaderActivity.this);
            contentWidth = width;
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            Rect clip = canvas.getClipBounds();
            int first = Math.max(0, clip.top / (pageHeight + pageGap));
            int last = Math.min(renderer.getPageCount() - 1, clip.bottom / (pageHeight + pageGap));
            for (int index = first; index <= last; index++) {
                int top = index * (pageHeight + pageGap);
                Bitmap bitmap = getPageBitmap(index);
                if (bitmap != null) {
                    RectF target = new RectF(0, top, contentWidth, top + pageHeight);
                    Paint paper = new Paint();
                    paper.setColor(Color.WHITE);
                    canvas.drawRect(target, paper);
                    canvas.drawBitmap(bitmap, null, target, paint);
                }
            }
        }

        private Bitmap getPageBitmap(int index) {
            Bitmap existing = cache.get(index);
            if (existing != null && !existing.isRecycled()) return existing;
            PdfRenderer.Page page = null;
            try {
                page = renderer.openPage(index);
                Bitmap bitmap = Bitmap.createBitmap(contentWidth, pageHeight, Bitmap.Config.ARGB_8888);
                bitmap.eraseColor(Color.WHITE);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                cache.put(index, bitmap);
                return bitmap;
            } catch (Exception e) {
                return null;
            } finally {
                if (page != null) page.close();
            }
        }

        @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int desiredHeight = pageHeight * renderer.getPageCount() + pageGap * (renderer.getPageCount() - 1);
            setMeasuredDimension(resolveSize(contentWidth, widthMeasureSpec), desiredHeight);
        }

        void releaseBitmaps() {
            for (Bitmap bitmap : cache.values()) if (!bitmap.isRecycled()) bitmap.recycle();
            cache.clear();
        }
    }

    @Override public void onBackPressed() { finish(); }

    @Override protected void onDestroy() {
        if (pagesView != null) pagesView.releaseBitmaps();
        if (renderer != null) { renderer.close(); renderer = null; }
        if (descriptor != null) {
            try { descriptor.close(); } catch (IOException ignored) { }
            descriptor = null;
        }
        super.onDestroy();
    }
}
