package com.nazirpuri.ruqyah;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;

public class PdfReaderActivity extends Activity {
    public static final String EXTRA_FILE_PATH = "pdf_file_path";
    public static final String EXTRA_TITLE = "pdf_title";
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private ImageView pageImage;
    private TextView pageLabel;
    private int pageIndex = 0;
    private Bitmap currentBitmap;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String path = getIntent().getStringExtra(EXTRA_FILE_PATH);
        if (path == null) { finish(); return; }
        try {
            File file = new File(path);
            if (!file.isFile()) throw new IOException("ফাইল পাওয়া যায়নি");
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            if (renderer.getPageCount() < 1) throw new IOException("PDF-এ কোনো পৃষ্ঠা নেই");
            buildReader(getIntent().getStringExtra(EXTRA_TITLE));
            renderPage();
        } catch (Exception e) {
            Toast.makeText(this, "PDF খোলা যায়নি: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void buildReader(String title) {
        getWindow().setStatusBarColor(Color.rgb(12,73,52));
        getWindow().setNavigationBarColor(Color.rgb(8,49,36));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(235,239,236));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(12, 8, 12, 8);
        top.setBackgroundColor(Color.rgb(12,73,52));
        Button back = new Button(this);
        back.setText("‹");
        back.setTextSize(26);
        back.setTextColor(Color.WHITE);
        back.setBackgroundColor(Color.TRANSPARENT);
        back.setOnClickListener(v -> finish());
        top.addView(back, new LinearLayout.LayoutParams(52, 52));
        TextView heading = new TextView(this);
        heading.setText(title == null ? "কিতাব পাঠ" : title);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(17);
        heading.setMaxLines(1);
        top.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(top);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        pageImage = new ImageView(this);
        pageImage.setAdjustViewBounds(true);
        pageImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pageImage.setBackgroundColor(Color.WHITE);
        pageImage.setPadding(4, 4, 4, 4);
        scroll.addView(pageImage, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(8, 6, 8, 8);
        controls.setBackgroundColor(Color.WHITE);
        Button previous = new Button(this);
        previous.setText("পূর্বের পৃষ্ঠা");
        previous.setOnClickListener(v -> { if (pageIndex > 0) { pageIndex--; renderPage(); scrollToTop(scroll); } });
        pageLabel = new TextView(this);
        pageLabel.setTextColor(Color.rgb(30,65,48));
        pageLabel.setTextSize(15);
        pageLabel.setGravity(Gravity.CENTER);
        Button next = new Button(this);
        next.setText("পরের পৃষ্ঠা");
        next.setOnClickListener(v -> { if (pageIndex + 1 < renderer.getPageCount()) { pageIndex++; renderPage(); scrollToTop(scroll); } });
        controls.addView(previous, new LinearLayout.LayoutParams(0, 52, 1));
        controls.addView(pageLabel, new LinearLayout.LayoutParams(0, 52, 1));
        controls.addView(next, new LinearLayout.LayoutParams(0, 52, 1));
        root.addView(controls);
        setContentView(root);
    }

    private void scrollToTop(ScrollView scroll) { scroll.post(() -> scroll.scrollTo(0, 0)); }

    private void renderPage() {
        if (renderer == null || pageIndex < 0 || pageIndex >= renderer.getPageCount()) return;
        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(pageIndex);
            int width = Math.max(600, getResources().getDisplayMetrics().widthPixels - 16);
            float scale = Math.min(2.0f, (float) width / page.getWidth());
            int bitmapWidth = Math.max(1, (int)(page.getWidth() * scale));
            int bitmapHeight = Math.max(1, (int)(page.getHeight() * scale));
            Bitmap nextBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            nextBitmap.eraseColor(Color.WHITE);
            page.render(nextBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            if (currentBitmap != null && !currentBitmap.isRecycled()) currentBitmap.recycle();
            currentBitmap = nextBitmap;
            pageImage.setImageBitmap(currentBitmap);
            pageLabel.setText((pageIndex + 1) + " / " + renderer.getPageCount());
        } catch (Exception e) {
            Toast.makeText(this, "পৃষ্ঠা দেখানো যায়নি", Toast.LENGTH_SHORT).show();
        } finally {
            if (page != null) page.close();
        }
    }

    @Override protected void onDestroy() {
        if (currentBitmap != null && !currentBitmap.isRecycled()) currentBitmap.recycle();
        if (renderer != null) renderer.close();
        if (descriptor != null) try { descriptor.close(); } catch (IOException ignored) { }
        super.onDestroy();
    }
}
