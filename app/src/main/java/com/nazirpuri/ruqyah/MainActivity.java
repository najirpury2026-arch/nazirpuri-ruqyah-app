package com.nazirpuri.ruqyah;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
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
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String[] titles = {"কুরআন","তাফসীর","হাদীস","নামাযের সময়","তিলাওয়াত","অডিও তেলাওয়াত","অনুবাদসহ তেলাওয়াত","রুকইয়া","কিতাব","কুরআন শিক্ষা","দু'আ","কিবলা","তাসবীহ্","মসজিদ খুঁজুন","ইসলামিক নাম","ক্যালেন্ডার","গুরুত্বপূর্ণ দিন","অন্যান্য","আমাদের সেবা","আমাদের ওয়েবসাইট"};
    private final String[] icons = {"📖","📜","📚","🕌","🎧","🔊","🎙️","🤲","📘","🌙","🤲","🧭","📿","🕌","👶","🗓️","🌙","☪️","🤝","🌐"};

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
        checkForAppUpdate();
    }

    private void checkForAppUpdate() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.github.com/repos/najirpury2026-arch/nazirpuri-ruqyah-app/releases/tags/latest-apk");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                int status = connection.getResponseCode();
                if (status != 200) return;
                InputStream input = connection.getInputStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] data = new byte[4096]; int length;
                while ((length = input.read(data)) != -1) output.write(data, 0, length);
                input.close();
                JSONObject release = new JSONObject(output.toString("UTF-8"));
                org.json.JSONArray assets = release.optJSONArray("assets");
                if (assets == null || assets.length() == 0) return;
                int latestVersion = -1;
                String downloadUrl = "";
                java.util.regex.Pattern versionPattern = java.util.regex.Pattern.compile("nazirpuri-(\\d+)\\.apk");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject candidate = assets.getJSONObject(i);
                    java.util.regex.Matcher matcher = versionPattern.matcher(candidate.optString("name", ""));
                    if (matcher.matches()) {
                        int candidateVersion = Integer.parseInt(matcher.group(1));
                        if (candidateVersion > latestVersion) {
                            latestVersion = candidateVersion;
                            downloadUrl = candidate.optString("browser_download_url", "");
                        }
                    }
                }
                if (latestVersion < 0 || downloadUrl.isEmpty()) return;
                int installedVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
                if (latestVersion > installedVersion) {
                    final String apkDownloadUrl = downloadUrl;
                    mainHandler.post(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        new android.app.AlertDialog.Builder(this)
                            .setTitle("নতুন আপডেট আবশ্যক")
                            .setMessage("নাজিরপুরী রুকইয়া ও দাওয়াখানা অ্যাপের নতুন সংস্করণ প্রকাশিত হয়েছে। অ্যাপ ব্যবহার চালিয়ে যেতে অনুগ্রহ করে আপডেট ইনস্টল করুন।")
                            .setPositiveButton("আপডেট ডাউনলোড", (dialog, which) -> {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(apkDownloadUrl));
                                try { startActivity(intent); } catch (Exception ignored) { }
                            })
                            .setCancelable(false)
                            .show();
                    });
                }
            } catch (Exception ignored) {
                // Keep the app usable when the network or update service is unavailable.
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void showHome() {
        showingWeb = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246,249,246));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(16),dp(10),dp(16),dp(22));
        header.setBackgroundColor(Color.rgb(12,73,52));
        LinearLayout headerTop = new LinearLayout(this);
        headerTop.setGravity(Gravity.CENTER_VERTICAL);
        headerTop.setOrientation(LinearLayout.HORIZONTAL);
        TextView headerSpacer = text("",20,Color.WHITE,false);
        headerTop.addView(headerSpacer,new LinearLayout.LayoutParams(0,dp(38),1));
        TextView settings = text("⚙",26,Color.WHITE,true);
        settings.setContentDescription("সেটিংস");
        settings.setPadding(dp(10),dp(3),dp(3),dp(3));
        settings.setOnClickListener(v -> showSettings());
        headerTop.addView(settings,new LinearLayout.LayoutParams(dp(44),dp(42)));
        header.addView(headerTop,new LinearLayout.LayoutParams(-1,dp(42)));
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
            tile.setGravity(Gravity.CENTER); tile.setPadding(dp(4),dp(11),dp(4),dp(11));
            GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.WHITE);
            bg.setCornerRadius(dp(16)); bg.setStroke(dp(1),Color.rgb(221,231,221));
            tile.setBackground(bg); tile.setElevation(dp(2));
            tile.addView(text(icons[i],22,Color.rgb(12,73,52),false));
            TextView label=text(titles[i],13,Color.rgb(31,63,45),true);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.topMargin=dp(8); tile.addView(label,lp);
            GridLayout.LayoutParams gp=new GridLayout.LayoutParams();
            gp.width=0; gp.height=dp(104); gp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
            gp.setMargins(dp(5),dp(5),dp(5),dp(5)); grid.addView(tile,gp);
            tile.setOnClickListener(v->openSection(index));
        }
        content.addView(grid);

        LinearLayout footer=new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(14),dp(9),dp(14),dp(10));
        footer.setBackgroundColor(Color.rgb(12,73,52));
        TextView managed=text("পরিচালনায়",15,Color.rgb(232,216,165),true);
        footer.addView(managed);
        TextView name=text("মুফতী রুহুল আমীন নাজিরপুরী",18,Color.WHITE,true);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
        np.topMargin=dp(3); footer.addView(name,np);
        TextView role=text("মুফতী, মুহাদ্দিস, লেখক এবং বিশিষ্ট রাক্বী ও মুদাব্বির",13,Color.WHITE,false);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
        rp.topMargin=dp(2); footer.addView(role,rp);
        TextView whatsapp=text("হোয়াটসঅ্যাপ নাম্বার 01614-776592",15,Color.rgb(255,224,130),true);
        whatsapp.setPaintFlags(whatsapp.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        whatsapp.setOnClickListener(v->{
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WHATSAPP_URL))); }
            catch (Exception ignored) { }
        });
        whatsapp.setContentDescription("হোয়াটসঅ্যাপে যোগাযোগ করুন");
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);
        wp.topMargin=dp(5); footer.addView(whatsapp,wp);
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
        if (index == 18) {
            showServicesPlaceholder();
            return;
        }
        String url=index==19?HOME_URL:HOME_URL+"search?q="+Uri.encode(titles[index]);
        showWeb(url);
    }


    private void showServicesPlaceholder() {
        showingWeb = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246,249,246));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), dp(8), dp(14), dp(8));
        bar.setBackgroundColor(Color.rgb(12,73,52));
        TextView back = text("‹  বিভাগসমূহ", 16, Color.WHITE, true);
        back.setOnClickListener(v -> showHome());
        bar.addView(back);
        root.addView(bar);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(20), dp(30), dp(20), dp(24));
        TextView title = text("আমাদের রুকইয়া সেবা", 23, Color.rgb(12,73,52), true);
        page.addView(title);
        TextView intro = text("কুরআন ও সুন্নাহভিত্তিক শরিয়তসম্মত রুকইয়া সেবা", 16, Color.rgb(49,73,57), true);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = dp(14);
        page.addView(intro, ip);
        TextView note = text("রুকইয়া সেবা বিভাগের বিস্তারিত লেখা ও সেবার বিবরণ পরবর্তী আপডেটে যুক্ত করা হবে।", 15, Color.rgb(80,90,82), false);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(24);
        page.addView(note, np);
        root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void showSettings() {
        new android.app.AlertDialog.Builder(this)
            .setTitle("সেটিংস")
            .setItems(new String[]{"ওয়েবসাইট খুলুন", "অ্যাপ সম্পর্কে"}, (dialog, which) -> {
                if (which == 0) showWeb(HOME_URL);
                else new android.app.AlertDialog.Builder(this)
                    .setTitle("নাজিরপুরী রুকইয়া ও দাওয়াখানা")
                    .setMessage("কুরআন • সুন্নাহ • ইলম • আমল\\n\\nআপনার দ্বীনি সেবা ও তথ্যের সহযোগী অ্যাপ।")
                    .setPositiveButton("ঠিক আছে", null)
                    .show();
            })
            .setNegativeButton("বন্ধ", null)
            .show();
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
        webView=new WebView(this);
        if (openQuranWhenLoaded) webView.setVisibility(View.INVISIBLE);
        WebSettings s=webView.getSettings();
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
                    // Keep the Blogger homepage hidden; reveal only after the Quran reader opens.
                    tryOpenOwnQuran(view, 0);
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

    private void tryOpenOwnQuran(WebView view, int attempt) {
        if (view != webView || !showingWeb) return;
        view.evaluateJavascript("(function(){if(typeof window.openNazQuran==='function'){window.openNazQuran();return 'opened';}return 'waiting';})();", result -> {
            if (view != webView || !showingWeb) return;
            if ("\"opened\"".equals(result)) {
                // App-side repair: make the 30-para index open the selected juz inside this WebView.
                view.evaluateJavascript(buildAppJuzFixScript(), null);
                view.postDelayed(() -> {
                    if (view == webView && showingWeb) view.setVisibility(View.VISIBLE);
                }, 900);
            } else if (attempt < 40) {
                mainHandler.postDelayed(() -> tryOpenOwnQuran(view, attempt + 1), 500);
            } else {
                // Do not expose the homepage as a fallback; the requested destination is Quran.
            }
        });
    }

    private String buildAppJuzFixScript() {
        return "(function(){"
            + "if(window.__nqAppJuzFixInstalled)return;window.__nqAppJuzFixInstalled=true;"
            + "var root=document.getElementById('naz-own-quran'),home=document.getElementById('nq-home'),reader=document.getElementById('nq-reader'),content=document.getElementById('nq-reader-content'),status=document.getElementById('nq-reader-status');"
            + "function esc(v){return String(v==null?'':v).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}"
            + "function digits(n){return String(n).replace(/\\d/g,function(d){return '০১২৩৪৫৬৭৮৯'[Number(d)];});}"
            + "function getEdition(j,e){var bases=['https://api.alquran.cloud/v1','https://alquran.api.islamic.network/v1','https://alquran.api.alislam.ru/v1'];"
            + "function go(i){if(i>=bases.length)return Promise.reject(new Error('Quran API unavailable'));"
            + "return fetch(bases[i]+'/juz/'+j+'/'+e,{mode:'cors',credentials:'omit',cache:'no-store'}).then(function(r){if(!r.ok)throw new Error('HTTP '+r.status);return r.json();})"
            + ".then(function(x){if(!x||x.code!==200||!x.data||!x.data.ayahs)throw new Error('Invalid Quran data');return x.data;}).catch(function(){return go(i+1);});}return go(0);}"
            + "window.__nqTranslationOnly=false;window.__nqOriginalParaHtml='';"
            + "window.nqToggleRecitationMode=function(){window.__nqTranslationOnly=!window.__nqTranslationOnly;"
            + "var b=document.getElementById('nq-recitation-toggle');if(window.__nqTranslationOnly){window.__nqOriginalParaHtml=content?content.innerHTML:'';if(content)content.innerHTML='<div style=\"width:100%;height:calc(100vh - 145px);min-height:650px;background:#fff\"><iframe title=\"নূরানী হাফেজী কুরআন শরীফ এমদাদিয়া লাইব্রেরী\" src=\"https://drive.google.com/file/d/1nT0VuIJVOaAuFCmYOGy3htSuWePAwLvI/preview\" style=\"width:100%;height:100%;border:0\"></iframe></div>';}"
            + "else if(content&&window.__nqOriginalParaHtml){content.innerHTML=window.__nqOriginalParaHtml;}"
            + "if(b){b.textContent=window.__nqTranslationOnly?'অনুবাদসহ পড়ুন':'শুধু তেলাওয়াত';b.setAttribute('aria-pressed',window.__nqTranslationOnly?'true':'false');}};"
            + "window.nqOpenJuz=function(juz){juz=Number(juz);if(!juz||juz<1||juz>30||!root||!content)return;"
            + "window.__nqTranslationOnly=false;if(home)home.hidden=true;if(reader)reader.hidden=false;if(status)status.textContent='লোড হচ্ছে…';"
            + "content.innerHTML='<div class=\"nq-status\">'+digits(juz)+' নম্বর পারার আরবি ও বাংলা অনুবাদ আনা হচ্ছে…</div>';"
            + "Promise.all([getEdition(juz,'quran-uthmani'),getEdition(juz,'bn.bengali')]).then(function(d){var ar=d[0],bn=d[1];"
            + "if(window.__nqSetCurrentData)window.__nqSetCurrentData(ar,bn);"
            + "var html=ar.ayahs.map(function(a,i){var tr=bn.ayahs[i]||{},s=a.surah||{},sn=s.number||1,si=a.numberInSurah||1,st=esc(s.englishName||s.name||('সূরা '+sn));"
            + "return '<article id=\"nq-ayah-'+a.number+'\" class=\"nq-card\"><div class=\"nq-ayah-top\"><span>'+st+' · আয়াত '+digits(si)+'</span><span>পারা '+digits(juz)+'</span></div><div class=\"nq-arabic\" lang=\"ar\">'+esc(a.text)+'</div><div class=\"nq-translation\">'+esc(tr.text||'বাংলা অনুবাদ পাওয়া যায়নি')+'</div><div class=\"nq-actions\"><button type=\"button\" onclick=\"window.nqPlayAyah('+a.number+',this)\">▶ তিলাওয়াত</button><button type=\"button\" onclick=\"window.nqWordByWord('+sn+','+si+')\">শব্দে শব্দে</button><button type=\"button\" onclick=\"window.nqSpeakTranslation('+a.number+')\">অনুবাদ শুনুন</button><button type=\"button\" onclick=\"window.nqOpenTafsir('+sn+','+si+')\">তাফসির</button><button type=\"button\" onclick=\"window.nqShareAyah('+sn+','+si+')\">শেয়ার</button></div><div id=\"nq-audio-'+a.number+'\"></div><div id=\"nq-words-'+a.number+'\"></div></article>';}).join('');"
            + "content.innerHTML='<div class=\"nq-surah-title\"><b>পারা '+digits(juz)+'</b><span>'+digits(ar.ayahs.length)+' আয়াত</span></div>'+html;"
            + "var modeButton=document.getElementById('nq-recitation-toggle');if(!modeButton){modeButton=document.createElement('button');modeButton.id='nq-recitation-toggle';modeButton.type='button';modeButton.onclick=window.nqToggleRecitationMode;modeButton.style.cssText='display:block;margin:10px auto;padding:9px 16px;border:0;border-radius:20px;background:#0c4934;color:#fff;font-weight:600';content.parentNode.insertBefore(modeButton,content);}modeButton.textContent='শুধু তেলাওয়াত';modeButton.setAttribute('aria-pressed','false');if(status)status.textContent='';"
            + "}).catch(function(){if(status)status.textContent='';content.innerHTML='<div class=\"nq-status\">এই মুহূর্তে পারাটি লোড করা যাচ্ছে না। ইন্টারনেট সংযোগ পরীক্ষা করে আবার চেষ্টা করুন।<br><button class=\"nq-primary\" type=\"button\" onclick=\"window.nqOpenJuz('+juz+')\">আবার চেষ্টা করুন</button></div>';});};"
            + "document.addEventListener('click',function(ev){var el=ev.target&&ev.target.closest?ev.target.closest('button,a,[role=tab],.nq-tab,.nq-tools button'):null;if(!el)return;"
            + "var label=(el.innerText||el.textContent||'').replace(/\\s+/g,'').trim();if(label==='সূরাসমূহ'||label==='সূরা'||label==='পারা'||label==='পারাসমূহ'){"
            + "var parent=el.parentElement;if(parent){parent.querySelectorAll('button,a,[role=tab],.nq-tab').forEach(function(x){x.classList.remove('nq-app-tab-active');x.setAttribute('aria-selected','false');x.style.backgroundColor='';x.style.color='';x.style.borderColor='';x.style.boxShadow='';});}"
            + "el.classList.add('nq-app-tab-active');el.setAttribute('aria-selected','true');el.style.backgroundColor='#0c4934';el.style.color='#ffffff';el.style.borderColor='#0c4934';el.style.boxShadow='0 2px 7px rgba(12,73,52,.25)';}},true);"
            + "document.addEventListener('click',function(ev){var el=ev.target&&ev.target.closest?ev.target.closest('[data-nq-juz]'):null;if(el){ev.preventDefault();window.nqOpenJuz(el.getAttribute('data-nq-juz'));}},true);"
            + "})();";
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
