package com.nazirpuri.ruqyah;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://nazirpuriruqyah.blogspot.com/";
    private static final String WHATSAPP_URL = "https://wa.me/8801614776592";
    private WebView webView;
    private Typeface banglaTypeface;
    private String fontBase64 = "";
    private ProgressBar progressBar;
    private LinearLayout root;
    private boolean showingWeb = false;
    private boolean openQuranWhenLoaded = false;
    private final String[] titles = {"কুরআন","হাদীস","নামাযের সময়","তিলাওয়াত","কিতাব","কুরআন শিক্ষা","দু'আ","কিবলা","তাসবীহ্","মসজিদ খুঁজুন","ইসলামিক নাম","ক্যালেন্ডার","গুরুত্বপূর্ণ দিন","অন্যান্য","ওয়েবসাইট"};
    private final String[] icons = {"📖","📚","🕌","🎧","📘","🌙","🤲","🧭","📿","🕌","👶","🗓️","🌙","☪️","🌐"};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(12,73,52));
        getWindow().setNavigationBarColor(Color.rgb(8,49,36));
        try {
            banglaTypeface = Typeface.createFromAsset(getAssets(), "SolaimanLipi.ttf");
            InputStream in = getAssets().open("SolaimanLipi.ttf");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            in.close();
            fontBase64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) { }
        showHome();
    }

    private void showHome() {
        showingWeb = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246,249,246));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(16),dp(25),dp(16),dp(22));
        header.setBackgroundColor(Color.rgb(12,73,52));
        header.addView(text("নাজিরপুরী রুকইয়া ও দাওয়াখানা",22,Color.WHITE,true));
        TextView sub = text("কুরআন • সুন্নাহ • ইলম • আমল",13,Color.rgb(232,216,165),false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2,-2);
        sp.topMargin=dp(7); header.addView(sub,sp); root.addView(header);

        TextView heading=text("আপনার পছন্দের বিভাগ নির্বাচন করুন",16,Color.rgb(35,65,49),true);
        heading.setPadding(dp(10),dp(18),dp(10),dp(10)); root.addView(heading);

        ScrollView scroll=new ScrollView(this);
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        GridLayout grid=new GridLayout(this); grid.setColumnCount(3);
        grid.setPadding(dp(10),dp(4),dp(10),dp(12));
        for(int i=0;i<titles.length;i++){
            final int index=i;
            LinearLayout tile=new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER); tile.setPadding(dp(4),dp(15),dp(4),dp(15));
            GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(16)); bg.setStroke(dp(1),Color.rgb(221,231,221));
            tile.setBackground(bg); tile.setElevation(dp(2));
            tile.addView(text(icons[i],29,Color.rgb(12,73,52),false));
            TextView label=text(titles[i],13,Color.rgb(31,63,45),true);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.topMargin=dp(8); tile.addView(label,lp);
            GridLayout.LayoutParams gp=new GridLayout.LayoutParams();
            gp.width=0; gp.height=dp(112); gp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
            gp.setMargins(dp(5),dp(5),dp(5),dp(5)); grid.addView(tile,gp);
            tile.setOnClickListener(v->openSection(index));
        }
        content.addView(grid);

        LinearLayout footer=new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(14),dp(16),dp(14),dp(18));
        footer.setBackgroundColor(Color.rgb(12,73,52));
        TextView managed=text("পরিচালনায়",15,Color.rgb(232,216,165),true);
        footer.addView(managed);
        TextView name=text("মুফতী রুহুল আমীন নাজিরপুরী",18,Color.WHITE,true);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
        np.topMargin=dp(5); footer.addView(name,np);
        TextView role=text("মুফতী, মুহাদ্দিস, লেখক এবং বিশিষ্ট রাক্বী ও মুদাব্বির",13,Color.WHITE,false);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
        rp.topMargin=dp(4); footer.addView(role,rp);
        TextView whatsapp=text("হোয়াটসঅ্যাপ নাম্বার 01614-776592",15,Color.rgb(255,224,130),true);
        whatsapp.setPaintFlags(whatsapp.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        whatsapp.setOnClickListener(v->{
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WHATSAPP_URL))); }
            catch (Exception ignored) { }
        });
        whatsapp.setContentDescription("হোয়াটসঅ্যাপে যোগাযোগ করুন");
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);
        wp.topMargin=dp(8); footer.addView(whatsapp,wp);
        content.addView(footer);
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void openSection(int index) {
        if (index == 0) {
            // Open the website's own embedded Quran reader inside this app's WebView.
            openQuranWhenLoaded = true;
            showWeb(HOME_URL);
            return;
        }
        String url=index==14?HOME_URL:HOME_URL+"search?q="+Uri.encode(titles[index]);
        showWeb(url);
    }

    private void showWeb(String url) {
        showingWeb=true; root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12),dp(5),dp(12),dp(5)); bar.setBackgroundColor(Color.rgb(12,73,52));
        TextView back=text("‹  বিভাগসমূহ",16,Color.WHITE,true);
        back.setPadding(dp(4),dp(8),dp(14),dp(8)); back.setOnClickListener(v->showHome());
        bar.addView(back); root.addView(bar);
        progressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(190,150,55)));
        root.addView(progressBar,new LinearLayout.LayoutParams(-1,dp(3)));
        webView=new WebView(this); WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setLoadsImagesAutomatically(true);
        s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setAllowFileAccess(true);
        webView.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view, String pageUrl){
                super.onPageFinished(view, pageUrl);
                if (!fontBase64.isEmpty()) {
                    String css = "@font-face{font-family:SolaimanLipi;src:url(data:font/ttf;base64," + fontBase64 + ") format('truetype');font-weight:100 900;}html,body,body *:not(i):not([class*='icon']){font-family:SolaimanLipi,sans-serif !important;}";
                    String js = "(function(){var st=document.getElementById('nazirpuri-solaiman-font');if(!st){st=document.createElement('style');st.id='nazirpuri-solaiman-font';document.head.appendChild(st);}st.textContent='" + css + "';})();";
                    view.evaluateJavascript(js, null);
                }
                if (openQuranWhenLoaded) {
                    openQuranWhenLoaded = false;
                    // The Blogger theme exposes this function for its own Quran reader.
                    view.evaluateJavascript("(function(){if(typeof window.openNazQuran==='function'){window.openNazQuran();}else{var a=document.querySelector('a[data-open-own-quran=\\\"true\\\"]');if(a)a.click();}})();", null);
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onProgressChanged(WebView v,int p){
                progressBar.setProgress(p); progressBar.setVisibility(p>=100?View.GONE:View.VISIBLE);
            }
        });
        root.addView(webView,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        webView.loadUrl(url);
    }

    private TextView text(String value,int size,int color,boolean bold){
        TextView v=new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        boolean hasBangla = value.codePoints().anyMatch(c -> c >= 0x0980 && c <= 0x09FF);
        if (hasBangla && banglaTypeface != null) v.setTypeface(Typeface.create(banglaTypeface, bold ? Typeface.BOLD : Typeface.NORMAL));
        else if(bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setGravity(Gravity.CENTER); return v;
    }
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    @Override public void onBackPressed(){
        if(showingWeb){if(webView!=null&&webView.canGoBack())webView.goBack();else showHome();}
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        if(webView!=null){webView.destroy();webView=null;} super.onDestroy();
    }
}
