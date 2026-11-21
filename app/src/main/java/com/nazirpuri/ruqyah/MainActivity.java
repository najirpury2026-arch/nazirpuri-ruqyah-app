package com.nazirpuri.ruqyah;

import android.app.Activity;
import android.os.Bundle;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
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
    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout root;
    private boolean showingWeb = false;
    private final String[] titles = {"কুরআন","হাদীস","নামাযের সময়","তিলাওয়াত","কিতাব","প্রবন্ধ","কুরআন শিক্ষা","দু'আ","ওয়েবসাইট"};
    private final String[] icons = {"📖","📚","🕌","🎧","📘","📝","🌙","🤲","🌐"};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(12,73,52));
        getWindow().setNavigationBarColor(Color.rgb(8,49,36));
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
        GridLayout grid=new GridLayout(this); grid.setColumnCount(3);
        grid.setPadding(dp(10),dp(4),dp(10),dp(18));
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
        scroll.addView(grid); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }

    private void openSection(int index) {
        String url=index==8?HOME_URL:HOME_URL+"search?q="+Uri.encode(titles[index]);
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
        webView.setWebViewClient(new WebViewClient());
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
        if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); v.setGravity(Gravity.CENTER); return v;
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