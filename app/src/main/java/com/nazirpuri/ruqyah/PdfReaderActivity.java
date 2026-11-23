package com.nazirpuri.ruqyah;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

/** Lightweight, ad-free offline PDF reader using Android's built-in PdfRenderer. */
public class PdfReaderActivity extends Activity {
    private PdfRenderer renderer;
    private ParcelFileDescriptor descriptor;
    private ImageView pageImage;
    private TextView pageLabel;
    private SeekBar pageSeek;
    private Button previousButton, nextButton;
    private Bitmap pageBitmap;
    private int pageIndex = 0;
    private boolean changingSeek = false;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(12,73,52));
        getWindow().setNavigationBarColor(Color.rgb(8,49,36));
        String path = getIntent().getStringExtra("pdf_path");
        try {
            if (path == null) throw new IOException("PDF path missing");
            File file = new File(path).getCanonicalFile();
            File books = new File(getFilesDir(), "kitab_books").getCanonicalFile();
            if (!books.equals(file.getParentFile()) || !file.isFile() || file.length() < 1000) {
                throw new IOException("PDF file not found");
            }
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            buildReader(file.getName());
            showPage(0);
        } catch (Exception e) {
            Toast.makeText(this, "কিতাবটি খোলা যাচ্ছে না। আবার ডাউনলোড করে দেখুন।", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void buildReader(String fileName) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(239,243,240));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(8), dp(12), dp(8));
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
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, dp(56)));

        pageImage = new ImageView(this);
        pageImage.setBackgroundColor(Color.WHITE);
        pageImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        pageImage.setAdjustViewBounds(true);
        pageImage.setPadding(dp(8), dp(8), dp(8), dp(8));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(pageImage, new android.widget.ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(7), dp(12), dp(8));
        controls.setBackgroundColor(Color.WHITE);

        pageLabel = new TextView(this);
        pageLabel.setTextColor(Color.rgb(30,70,49));
        pageLabel.setTextSize(13);
        pageLabel.setGravity(Gravity.CENTER);
        controls.addView(pageLabel, new LinearLayout.LayoutParams(-1, dp(25)));

        pageSeek = new SeekBar(this);
        pageSeek.setMax(Math.max(0, renderer.getPageCount() - 1));
        controls.addView(pageSeek, new LinearLayout.LayoutParams(-1, dp(34)));
        pageSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) pageLabel.setText("পৃষ্ঠা " + (progress + 1) + " / " + renderer.getPageCount());
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { showPage(seekBar.getProgress()); }
        });

        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        previousButton = navButton("‹  আগের পৃষ্ঠা");
        nextButton = navButton("পরের পৃষ্ঠা  ›");
        nav.addView(previousButton, new LinearLayout.LayoutParams(0, dp(42), 1));
        nav.addView(nextButton, new LinearLayout.LayoutParams(0, dp(42), 1));
        controls.addView(nav);
        previousButton.setOnClickListener(v -> showPage(pageIndex - 1));
        nextButton.setOnClickListener(v -> showPage(pageIndex + 1));

        TextView offline = new TextView(this);
        offline.setText("সম্পূর্ণ অফলাইন  •  কোনো বিজ্ঞাপন নেই");
        offline.setTextSize(11);
        offline.setTextColor(Color.rgb(100,120,107));
        offline.setGravity(Gravity.CENTER);
        controls.addView(offline, new LinearLayout.LayoutParams(-1, dp(22)));
        root.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    private Button navButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(13);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(18,105,70)));
        return b;
    }

    private void showPage(int index) {
        if (renderer == null || renderer.getPageCount() == 0) return;
        pageIndex = Math.max(0, Math.min(index, renderer.getPageCount() - 1));
        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(pageIndex);
            int width = Math.max(1, getResources().getDisplayMetrics().widthPixels - dp(32));
            int height = Math.max(1, (int)(width * (page.getHeight() / (float)page.getWidth())));
            Bitmap next = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            next.eraseColor(Color.WHITE);
            page.render(next, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            if (pageBitmap != null) pageBitmap.recycle();
            pageBitmap = next;
            pageImage.setImageBitmap(pageBitmap);
            pageLabel.setText("পৃষ্ঠা " + (pageIndex + 1) + " / " + renderer.getPageCount());
            changingSeek = true;
            pageSeek.setProgress(pageIndex);
            changingSeek = false;
            previousButton.setEnabled(pageIndex > 0);
            nextButton.setEnabled(pageIndex < renderer.getPageCount() - 1);
        } catch (Exception e) {
            Toast.makeText(this, "এই পৃষ্ঠাটি দেখানো যাচ্ছে না।", Toast.LENGTH_SHORT).show();
        } finally {
            if (page != null) page.close();
        }
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override public void onBackPressed() { finish(); }

    @Override protected void onDestroy() {
        if (pageBitmap != null) { pageBitmap.recycle(); pageBitmap = null; }
        if (renderer != null) { renderer.close(); renderer = null; }
        if (descriptor != null) {
            try { descriptor.close(); } catch (IOException ignored) { }
            descriptor = null;
        }
        super.onDestroy();
    }
}
