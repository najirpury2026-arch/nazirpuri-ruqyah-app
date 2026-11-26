package com.nazirpuri.ruqyah;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.media.MediaPlayer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import android.view.View;
import android.speech.tts.TextToSpeech;
import java.util.Locale;
import android.webkit.WebChromeClient;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final String HOME_URL = "https://nazirpuriruqyah.blogspot.com/";
    private static final String WHATSAPP_URL = "https://wa.me/8801614776592";
    private WebView webView;
    private Typeface banglaTypeface;
    private ProgressBar progressBar;
    private LinearLayout root;
    private boolean showingWeb = false;
    private volatile boolean hadithReaderOpen = false;
    private boolean openQuranWhenLoaded = false;
    private MediaPlayer yasinPlayer;
    private MediaPlayer rahmanPlayer;
    private MediaPlayer mulkPlayer;
    private TextToSpeech duaTextToSpeech;
    private boolean duaTtsReady = false;
    private String pendingDuaSpeech = "";

    private android.hardware.SensorManager compassSensorManager;
    private android.hardware.Sensor compassSensor;
    private android.hardware.SensorEventListener compassListener;
    private QiblaCompassView qiblaCompassView;
    private TextView qiblaHeadingText, qiblaBearingText, qiblaLocationText;
    private double qiblaBearing = Double.NaN;
    private double userLatitude = Double.NaN, userLongitude = Double.NaN;
    private static final int QIBLA_LOCATION_REQUEST = 741;
    private static final int MOSQUE_LOCATION_REQUEST = 742;
    private boolean pendingMosqueSearch = false;
    private volatile boolean yasinDownloading = false;
    private volatile boolean rahmanDownloading = false;
    private volatile boolean mulkDownloading = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // One app-wide queue: up to 10 PDF downloads run at the same time;
    // every additional tap is kept in the queue until a worker becomes free.
    private static final java.util.Set<String> ACTIVE_BOOK_DOWNLOADS =
        java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ExecutorService BOOK_DOWNLOAD_EXECUTOR =
        java.util.concurrent.Executors.newFixedThreadPool(10, runnable -> {
            Thread thread = new Thread(runnable, "kitab-pdf-download");
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
    private final String[] titles = {"কুরআন","কুরআন ও তাফসীর","হাদীস","নামাজের সময়সূচী","তিলাওয়াত","অডিও তেলাওয়াত","অনুবাদসহ তেলাওয়াত","রুকইয়া","কিতাব","কুরআন শিক্ষা","দু'আ","কিবলা","তাসবীহ্","মসজিদ খুঁজুন","ইসলামিক নাম","ক্যালেন্ডার","গুরুত্বপূর্ণ দিন","অন্যান্য","আমাদের সেবা","আমাদের ওয়েবসাইট"};
    private final String[] icons = {"📖","📜","📚","🕌","🎧","🔊","🎙️","🤲","📘","🌙","🤲","🧭","📿","🕌","👶","🗓️","🌙","☪️","🤝","🌐"};

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(12,73,52));
        getWindow().setNavigationBarColor(Color.rgb(8,49,36));
        // Load the native font once; WebView pages reference the bundled asset directly.
        try { banglaTypeface = Typeface.createFromAsset(getAssets(), "SolaimanLipi.ttf"); }
        catch (Exception ignored) { }
        showHome();
        checkForAppUpdate();
    }

    private void refreshAppContent() {
        android.widget.Toast.makeText(this, "অ্যাপের তথ্য রিফ্রেশ করা হচ্ছে…", android.widget.Toast.LENGTH_SHORT).show();
        if (webView != null) {
            webView.reload();
        } else {
            showHome();
        }
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
        releaseWebView();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.islamic_background); // Lightweight native drawable; no large bitmap bundled.

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        // Premium emerald header with a warm gold accent.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(dp(20), dp(24), dp(20), dp(24));
        GradientDrawable headerBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(7,54,43), Color.rgb(13,91,65), Color.rgb(22,112,78)});
        headerBg.setCornerRadii(new float[]{0,0,0,0,dp(28),dp(28),dp(28),dp(28)});
        header.setBackground(headerBg);

        LinearLayout headerTop = new LinearLayout(this);
        headerTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView menuButton = text("☰", 25, Color.WHITE, true);
        menuButton.setGravity(Gravity.CENTER); menuButton.setContentDescription("অ্যাপ মেনু");
        GradientDrawable menuBg = new GradientDrawable(); menuBg.setColor(0x337FFFFF); menuBg.setCornerRadius(dp(22));
        menuButton.setBackground(menuBg); menuButton.setOnClickListener(v -> showHomeMenu());
        LinearLayout.LayoutParams menuLp = new LinearLayout.LayoutParams(dp(44),dp(42)); menuLp.setMargins(0,0,dp(7),0);
        headerTop.addView(menuButton, menuLp);
        TextView brandMark = text("۞", 27, Color.rgb(244,211,130), true);
        headerTop.addView(brandMark, new LinearLayout.LayoutParams(0,dp(42),1));
        TextView refresh = text("⟳", 27, Color.WHITE, true);
        refresh.setGravity(Gravity.CENTER);
        refresh.setContentDescription("সবকিছু রিফ্রেশ ও আপডেট পরীক্ষা");
        GradientDrawable refreshBg = new GradientDrawable();
        refreshBg.setColor(0x337FFFFF);
        refreshBg.setCornerRadius(dp(22));
        refresh.setBackground(refreshBg);
        refresh.setOnClickListener(v -> refreshAppContent());
        LinearLayout.LayoutParams refreshLp = new LinearLayout.LayoutParams(dp(44),dp(42));
        refreshLp.setMargins(0,0,dp(7),0);
        headerTop.addView(refresh, refreshLp);

        TextView settings = text("⚙", 24, Color.WHITE, true);
        settings.setGravity(Gravity.CENTER);
        settings.setContentDescription("সেটিংস");
        GradientDrawable settingsBg = new GradientDrawable();
        settingsBg.setColor(0x337FFFFF);
        settingsBg.setCornerRadius(dp(22));
        settings.setBackground(settingsBg);
        settings.setOnClickListener(v -> showSettings());
        headerTop.addView(settings, new LinearLayout.LayoutParams(dp(44),dp(42)));
        header.addView(headerTop, new LinearLayout.LayoutParams(-1,dp(52)));

        TextView brand = text("নাজিরপুরী রুকইয়া ও দাওয়াখানা", 22, Color.WHITE, true);
        brand.setGravity(Gravity.CENTER);
        header.addView(brand);
        TextView tagline = text("কুরআন • সুন্নাহ • ইলম • আমল", 13, Color.rgb(246,224,174), false);
        tagline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tagp = new LinearLayout.LayoutParams(-1,-2);
        tagp.topMargin = dp(7);
        header.addView(tagline, tagp);

        TextView welcome = text("আসসালামু আলাইকুম", 17, Color.WHITE, true);
        welcome.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams welp = new LinearLayout.LayoutParams(-1,-2);
        welp.topMargin = dp(17);
        header.addView(welcome, welp);
        TextView welcomeSub = text("দ্বীনি শিক্ষা, আমল ও প্রয়োজনীয় সেবা—এক জায়গায়", 12, Color.rgb(228,242,233), false);
        welcomeSub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams wsp = new LinearLayout.LayoutParams(-1,-2);
        wsp.topMargin = dp(4);
        header.addView(welcomeSub, wsp);
        content.addView(header);

        LinearLayout sectionTitle = new LinearLayout(this);
        sectionTitle.setGravity(Gravity.CENTER_VERTICAL);
        sectionTitle.setPadding(dp(16),dp(18),dp(16),dp(8));
        TextView sectionHeading = text("দ্বীনি বিভাগসমূহ",18,Color.rgb(24,62,47),true);
        sectionTitle.addView(sectionHeading,new LinearLayout.LayoutParams(0,-2,1));
        TextView sectionHint = text("আপনার পছন্দেরটি বেছে নিন",11,Color.rgb(112,133,119),false);
        sectionTitle.addView(sectionHint);
        content.addView(sectionTitle);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setPadding(dp(9),dp(2),dp(9),dp(14));
        int[][] tilePalette = {
            {235,246,239, 20,112,77}, {250,239,226, 190,112,35},
            {237,242,252, 57,93,170}, {250,235,241, 174,65,105},
            {232,246,249, 20,126,145}, {245,239,252, 117,75,164},
            {250,244,226, 170,126,35}, {238,247,233, 76,125,55},
            {252,238,232, 175,83,51}, {236,241,252, 64,91,159},
            {248,237,247, 151,68,132}, {231,246,246, 35,121,126}
        };
        for (int i=0; i<titles.length; i++) {
            final int index = i;
            LinearLayout tile = new LinearLayout(this);
            tile.setOrientation(LinearLayout.VERTICAL);
            tile.setGravity(Gravity.CENTER);
            tile.setPadding(dp(3),dp(10),dp(3),dp(9));
            GradientDrawable tileBg = new GradientDrawable();
            int[] palette = tilePalette[i % tilePalette.length];
            tileBg.setColor(Color.rgb(palette[0],palette[1],palette[2]));
            tileBg.setCornerRadius(dp(17));
            tileBg.setStroke(dp(1),Color.rgb(Math.max(0,palette[0]-12),Math.max(0,palette[1]-12),Math.max(0,palette[2]-12)));
            tile.setBackground(tileBg);
            tile.setElevation(dp(2));

            SectionIconView icon = new SectionIconView(i, palette);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(56),dp(54));
            tile.addView(icon, iconLp);

            TextView label = text(titles[i],12,Color.rgb(35,66,49),true);
            label.setGravity(Gravity.CENTER);
            label.setMaxLines(2);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,dp(34));
            lp.topMargin = dp(7);
            tile.addView(label,lp);

            GridLayout.LayoutParams gp = new GridLayout.LayoutParams();
            gp.width=0; gp.height=dp(105);
            gp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);
            gp.setMargins(dp(4),dp(4),dp(4),dp(4));
            grid.addView(tile,gp);
            tile.setOnClickListener(v -> {
                tile.setAlpha(0.82f);
                tile.postDelayed(() -> tile.setAlpha(1f), 90);
                openSection(index);
            });
            tile.setClickable(true);
            tile.setFocusable(true);
        }
        content.addView(grid);

        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.VERTICAL);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(16),dp(17),dp(16),dp(18));
        GradientDrawable footerBg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(7,54,43),Color.rgb(14,88,63)});
        footerBg.setCornerRadius(dp(22));
        footer.setBackground(footerBg);
        LinearLayout.LayoutParams footp = new LinearLayout.LayoutParams(-1,-2);
        footp.setMargins(dp(13),dp(3),dp(13),dp(16));
        TextView managed=text("পরিচালনায়",13,Color.rgb(246,218,151),true);
        footer.addView(managed);
        TextView name=text("মুফতী রুহুল আমীন নাজিরপুরী",18,Color.WHITE,true);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
        np.topMargin=dp(5); footer.addView(name,np);
        TextView role=text("মুফতী, মুহাদ্দিস, লেখক এবং বিশিষ্ট রাক্বী ও মুদাব্বির",12,Color.rgb(226,239,230),false);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);
        rp.topMargin=dp(4); footer.addView(role,rp);
        TextView whatsapp=text("হোয়াটসঅ্যাপ: 01614-776592  •  যোগাযোগ করুন",13,Color.rgb(255,225,151),true);
        whatsapp.setPaintFlags(whatsapp.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        whatsapp.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(WHATSAPP_URL))); }
            catch (Exception ignored) { }
        });
        LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);
        wp.topMargin=dp(9); footer.addView(whatsapp,wp);
        content.addView(footer,footp);

        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }


    // Live Qibla compass using the phone's rotation-vector sensor and current location.
    private void showQiblaCompass() {
        showingWeb = false;
        if (webView != null) { webView.destroy(); webView = null; }
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(18), dp(10), dp(18), dp(18));
        root.setBackgroundColor(Color.rgb(244,248,245));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(4),dp(8),dp(4),dp(12));
        TextView back = text("‹  বিভাগসমূহ",16,Color.rgb(12,73,52),true);
        back.setOnClickListener(v -> { stopQiblaSensors(); showHome(); });
        bar.addView(back);
        root.addView(bar,new LinearLayout.LayoutParams(-1,-2));

        TextView heading = text("কিবলা কম্পাস",22,Color.rgb(12,73,52),true);
        heading.setGravity(Gravity.CENTER);
        root.addView(heading);
        TextView sub = text("কাবা শরিফের দিক নির্ণয় করুন",13,Color.rgb(91,116,101),false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2); sp.topMargin=dp(5); root.addView(sub,sp);

        qiblaCompassView = new QiblaCompassView();
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(dp(300),dp(300));
        cp.gravity=Gravity.CENTER;
        cp.topMargin=dp(18); cp.bottomMargin=dp(14);
        root.addView(qiblaCompassView,cp);

        qiblaHeadingText = text("দিক: --°",18,Color.rgb(12,73,52),true);
        qiblaHeadingText.setGravity(Gravity.CENTER);
        root.addView(qiblaHeadingText);
        qiblaBearingText = text("কিবলার দিক নির্ণয় হচ্ছে…",15,Color.rgb(30,92,63),true);
        qiblaBearingText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2); bp.topMargin=dp(8); root.addView(qiblaBearingText,bp);
        qiblaLocationText = text("আপনার অবস্থান নেওয়া হচ্ছে…",12,Color.rgb(100,120,107),false);
        qiblaLocationText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(7); root.addView(qiblaLocationText,lp);

        TextView tip=text("ফোনটি সমতল রাখুন এবং ধাতব বস্তু/চুম্বক থেকে দূরে রাখুন। কম্পাস ঠিকভাবে কাজ না করলে ফোনটি ৮ আকৃতিতে কয়েকবার নাড়িয়ে ক্যালিব্রেট করুন।",12,Color.rgb(112,93,55),false);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(dp(12),dp(12),dp(12),dp(12));
        GradientDrawable tipBg=new GradientDrawable(); tipBg.setColor(Color.rgb(255,248,225)); tipBg.setCornerRadius(dp(14)); tip.setBackground(tipBg);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2); tp.topMargin=dp(16); root.addView(tip,tp);
        setContentView(root);
        startQiblaSensors();
        obtainQiblaLocation();
    }

    private void startQiblaSensors() {
        compassSensorManager=(android.hardware.SensorManager)getSystemService(SENSOR_SERVICE);
        compassSensor=compassSensorManager==null?null:compassSensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR);
        if(compassSensor==null) {
            if(qiblaHeadingText!=null) qiblaHeadingText.setText("এই ফোনে কম্পাস সেন্সর পাওয়া যায়নি");
            return;
        }
        compassListener=new android.hardware.SensorEventListener() {
            public void onSensorChanged(android.hardware.SensorEvent event) {
                float[] rotation=new float[9], orientation=new float[3];
                android.hardware.SensorManager.getRotationMatrixFromVector(rotation,event.values);
                android.hardware.SensorManager.getOrientation(rotation,orientation);
                float azimuth=(float)Math.toDegrees(orientation[0]);
                if(azimuth<0) azimuth+=360f;
                if(qiblaCompassView!=null) qiblaCompassView.setDirection(azimuth,(float)qiblaBearing);
                if(qiblaHeadingText!=null) qiblaHeadingText.setText("ফোনের দিক: "+Math.round(azimuth)+"°");
            }
            public void onAccuracyChanged(android.hardware.Sensor sensor,int accuracy) {}
        };
        compassSensorManager.registerListener(compassListener,compassSensor,android.hardware.SensorManager.SENSOR_DELAY_UI);
    }

    private void obtainQiblaLocation() {
        if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},QIBLA_LOCATION_REQUEST);
            if(qiblaLocationText!=null) qiblaLocationText.setText("কিবলার দিক জানতে অবস্থানের অনুমতি দিন");
            return;
        }
        try {
            android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
            android.location.Location best=null;
            for(String provider:lm.getProviders(true)) {
                try {
                    android.location.Location loc=lm.getLastKnownLocation(provider);
                    if(loc!=null && (best==null || loc.getAccuracy()<best.getAccuracy())) best=loc;
                } catch(SecurityException ignored) {}
            }
            if(best!=null) updateQiblaLocation(best);
            android.location.Criteria criteria=new android.location.Criteria();
            criteria.setAccuracy(android.location.Criteria.ACCURACY_FINE);
            String provider=lm.getBestProvider(criteria,true);
            if(provider!=null) lm.requestSingleUpdate(provider,new android.location.LocationListener() {
                public void onLocationChanged(android.location.Location location){ updateQiblaLocation(location); }
                public void onStatusChanged(String p,int status,android.os.Bundle extras){}
                public void onProviderEnabled(String p){}
                public void onProviderDisabled(String p){}
            },mainHandler.getLooper());
            else if(best==null && qiblaLocationText!=null) qiblaLocationText.setText("অবস্থান পাওয়া যায়নি—GPS চালু করে আবার চেষ্টা করুন");
        } catch(Exception e) {
            if(qiblaLocationText!=null) qiblaLocationText.setText("অবস্থান পাওয়া যায়নি—GPS চালু করুন");
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if(requestCode==QIBLA_LOCATION_REQUEST) {
            if(grantResults.length>0 && grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED) obtainQiblaLocation();
            else if(qiblaLocationText!=null) qiblaLocationText.setText("অবস্থানের অনুমতি ছাড়া কিবলার দিক নির্ণয় করা যাবে না");
        }
        if(requestCode==MOSQUE_LOCATION_REQUEST) {
            if(grantResults.length>0 && grantResults[0]==android.content.pm.PackageManager.PERMISSION_GRANTED) locateAndOpenMosques();
            else {
                pendingMosqueSearch = false;
                new android.app.AlertDialog.Builder(this)
                    .setTitle("অবস্থানের অনুমতি প্রয়োজন")
                    .setMessage("আপনার বর্তমান অবস্থানের আশেপাশের মসজিদ দেখাতে অবস্থানের অনুমতি দিন।")
                    .setPositiveButton("আবার চেষ্টা করুন",(d,w)->findNearbyMosques())
                    .setNegativeButton("বন্ধ",null).show();
            }
        }
    }

    private void updateQiblaLocation(android.location.Location location) {
        userLatitude=location.getLatitude(); userLongitude=location.getLongitude();
        double lat1=Math.toRadians(userLatitude), lat2=Math.toRadians(21.4225);
        double dLon=Math.toRadians(39.8262-userLongitude);
        double y=Math.sin(dLon)*Math.cos(lat2);
        double x=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon);
        qiblaBearing=(Math.toDegrees(Math.atan2(y,x))+360.0)%360.0;
        runOnUiThread(() -> {
            if(qiblaBearingText!=null) qiblaBearingText.setText("কাবা শরিফের দিক: "+Math.round(qiblaBearing)+"° (উত্তর থেকে)");
            if(qiblaLocationText!=null) qiblaLocationText.setText("আপনার অবস্থান পাওয়া গেছে • নির্ভুলতার জন্য GPS ব্যবহার করুন");
            if(qiblaCompassView!=null) qiblaCompassView.invalidate();
        });
    }

    private void stopQiblaSensors() {
        if(compassSensorManager!=null && compassListener!=null) compassSensorManager.unregisterListener(compassListener);
        compassListener=null; compassSensorManager=null; qiblaCompassView=null;
    }

    @Override protected void onPause() {
        super.onPause();
        if(compassSensorManager!=null && compassListener!=null) compassSensorManager.unregisterListener(compassListener);
    }

    @Override protected void onResume() {
        super.onResume();
        if(compassSensorManager!=null && compassSensor!=null && compassListener!=null)
            compassSensorManager.registerListener(compassListener,compassSensor,android.hardware.SensorManager.SENSOR_DELAY_UI);
    }

    private class QiblaCompassView extends View {
        private final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private float azimuth=0, bearing=Float.NaN;
        QiblaCompassView(){ super(MainActivity.this); setLayerType(View.LAYER_TYPE_SOFTWARE,null); }
        void setDirection(float a,float b){ azimuth=a; bearing=b; invalidate(); }
        private void circle(android.graphics.Canvas canvas,float cx,float cy,float r,int color,android.graphics.Paint.Style style,float stroke) {
            paint.setColor(color); paint.setStyle(style); paint.setStrokeWidth(stroke); canvas.drawCircle(cx,cy,r,paint);
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float cx=getWidth()/2f, cy=getHeight()/2f, radius=Math.min(cx,cy)-dp(12);
            // Layered premium dial with a soft outer rim and inner guide rings.
            circle(canvas,cx,cy,radius+dp(5),Color.rgb(221,232,224),android.graphics.Paint.Style.FILL,0);
            circle(canvas,cx,cy,radius,Color.rgb(255,255,255),android.graphics.Paint.Style.FILL,0);
            circle(canvas,cx,cy,radius,Color.rgb(17,105,73),android.graphics.Paint.Style.STROKE,dp(3));
            circle(canvas,cx,cy,radius-dp(8),Color.rgb(222,235,226),android.graphics.Paint.Style.STROKE,dp(1));
            canvas.save(); canvas.rotate(-azimuth,cx,cy);
            // Fine degree marks and stronger marks at each 30-degree point.
            for(int i=0;i<72;i++){
                canvas.save(); canvas.rotate(i*5,cx,cy);
                boolean major=i%6==0, cardinal=i%18==0;
                paint.setColor(cardinal?Color.rgb(12,73,52):(major?Color.rgb(73,130,101):Color.rgb(174,193,180)));
                paint.setStrokeWidth(cardinal?dp(3):(major?dp(2):dp(1)));
                canvas.drawLine(cx,cy-radius+dp(12),cx,cy-radius+(cardinal?dp(31):(major?dp(23):dp(17))),paint);
                canvas.restore();
            }
            // Cardinal directions are written in full, not abbreviated.
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            paint.setTextSize(dp(15));
            paint.setColor(Color.rgb(12,73,52));
            canvas.drawText("উত্তর",cx,cy-radius+dp(54),paint);
            canvas.drawText("পূর্ব",cx+radius-dp(39),cy+dp(6),paint);
            canvas.drawText("দক্ষিণ",cx,cy+radius-dp(28),paint);
            canvas.drawText("পশ্চিম",cx-radius+dp(39),cy+dp(6),paint);
            // Qibla pointer: red arrow aims toward the bearing relative to phone heading.
            if(!Float.isNaN(bearing)){
                canvas.save(); canvas.rotate(bearing,cx,cy);
                paint.setColor(Color.rgb(190,55,43)); paint.setStyle(android.graphics.Paint.Style.FILL);
                android.graphics.Path arrow=new android.graphics.Path();
                arrow.moveTo(cx,cy-radius+dp(42));
                arrow.lineTo(cx-dp(14),cy+dp(18));
                arrow.lineTo(cx,cy+dp(9));
                arrow.lineTo(cx+dp(14),cy+dp(18));
                arrow.close(); canvas.drawPath(arrow,paint);
                canvas.restore();
            }
            canvas.restore();
            // Center hub and a small gold accent.
            circle(canvas,cx,cy,dp(12),Color.rgb(222,174,76),android.graphics.Paint.Style.FILL,0);
            circle(canvas,cx,cy,dp(7),Color.rgb(12,73,52),android.graphics.Paint.Style.FILL,0);
            paint.setColor(Color.rgb(12,73,52)); paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            paint.setTextSize(dp(12)); paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("কিবলা",cx,cy+radius+dp(3),paint);
        }
    }


    // Persistent digital tasbih counter for the home-page Tasbih tile.
    private void showTasbihCounter() {
        showingWeb = false;
        if (webView != null) { webView.destroy(); webView = null; }
        final android.content.SharedPreferences prefs = getSharedPreferences("tasbih_counter", MODE_PRIVATE);
        final int[] count = {prefs.getInt("count", 0)};
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(22));
        root.setBackgroundColor(Color.rgb(244,248,245));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        bar.setPadding(0, 0, 0, 0);
        TextView back = text("‹  হোম পেজে ফিরে যান",16,Color.rgb(12,73,52),true);
        back.setGravity(Gravity.CENTER_VERTICAL | Gravity.LEFT);
        back.setPadding(0,dp(9),dp(12),dp(9));
        back.setOnClickListener(v -> showHome());
        bar.addView(back);
        LinearLayout.LayoutParams backBarLp = new LinearLayout.LayoutParams(-1,-2);
        backBarLp.topMargin = dp(16);
        root.addView(bar,backBarLp);

        TextView title = text("ডিজিটাল তাসবীহ্",23,Color.rgb(12,73,52),true);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1,-2);
        titleLp.topMargin = dp(24);
        root.addView(title,titleLp);
        TextView subtitle = text("প্রতিবার তাসবীহ্ পড়ে নিচের বাটনে চাপ দিন",13,Color.rgb(95,117,102),false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1,-2);
        subLp.topMargin = dp(8);
        root.addView(subtitle,subLp);

        TextView countView = text(String.valueOf(count[0]),48,Color.rgb(12,73,52),true);
        countView.setGravity(Gravity.CENTER);
        TextView countLabel = text("সর্বমোট পড়া হয়েছে",15,Color.rgb(72,99,81),true);
        countLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams countLp = new LinearLayout.LayoutParams(-1,-2);
        countLp.topMargin = dp(25);
        root.addView(countView,countLp);
        root.addView(countLabel,new LinearLayout.LayoutParams(-1,-2));

        TextView tap = text("📿\nতাসবীহ্ পড়েছি",22,Color.WHITE,true);
        tap.setGravity(Gravity.CENTER);
        tap.setLineSpacing(dp(8),1f);
        GradientDrawable tapBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(16,112,77),Color.rgb(7,65,47)});
        tapBg.setShape(GradientDrawable.OVAL);
        tapBg.setStroke(dp(4),Color.rgb(224,185,94));
        tap.setBackground(tapBg);
        tap.setElevation(dp(5));
        LinearLayout.LayoutParams tapLp = new LinearLayout.LayoutParams(dp(220),dp(220));
        tapLp.gravity = Gravity.CENTER;
        tapLp.topMargin = dp(28);
        tapLp.bottomMargin = dp(25);
        root.addView(tap,tapLp);

        TextView reset = text("↻  রিসেট",16,Color.rgb(155,55,45),true);
        reset.setGravity(Gravity.CENTER);
        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setColor(Color.rgb(255,239,235));
        resetBg.setCornerRadius(dp(24));
        resetBg.setStroke(dp(1),Color.rgb(235,199,190));
        reset.setBackground(resetBg);
        reset.setPadding(dp(28),dp(12),dp(28),dp(12));
        root.addView(reset,new LinearLayout.LayoutParams(-2,-2));

        tap.setOnClickListener(v -> {
            if (count[0] < Integer.MAX_VALUE) count[0]++;
            countView.setText(String.valueOf(count[0]));
            prefs.edit().putInt("count",count[0]).apply();
            tap.setScaleX(0.97f); tap.setScaleY(0.97f);
            tap.postDelayed(() -> { tap.setScaleX(1f); tap.setScaleY(1f); },90);
        });
        reset.setOnClickListener(v -> new android.app.AlertDialog.Builder(this)
            .setTitle("তাসবীহ্ রিসেট")
            .setMessage("পড়ার বর্তমান সংখ্যা শূন্য করতে চান?")
            .setNegativeButton("না",null)
            .setPositiveButton("রিসেট",(dialog,which) -> {
                count[0] = 0;
                countView.setText("0");
                prefs.edit().putInt("count",0).apply();
            }).show());

        TextView note = text("আপনার গণনা অ্যাপ বন্ধ করলেও সংরক্ষিত থাকবে।",12,Color.rgb(112,130,117),false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1,-2);
        noteLp.topMargin = dp(18);
        root.addView(note,noteLp);
        setContentView(root);
    }

    // Find mosques around the phone's current GPS position and open the map results.
    private void findNearbyMosques() {
        if(android.os.Build.VERSION.SDK_INT>=23 &&
           checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED &&
           checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendingMosqueSearch = true;
            requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},MOSQUE_LOCATION_REQUEST);
            return;
        }
        locateAndOpenMosques();
    }

    private void locateAndOpenMosques() {
        pendingMosqueSearch = false;
        try {
            android.location.LocationManager lm=(android.location.LocationManager)getSystemService(LOCATION_SERVICE);
            android.location.Location best=null;
            for(String provider:lm.getProviders(true)) {
                try {
                    android.location.Location loc=lm.getLastKnownLocation(provider);
                    if(loc!=null && (best==null || loc.getTime()>best.getTime() || (loc.getTime()==best.getTime() && loc.getAccuracy()<best.getAccuracy()))) best=loc;
                } catch(SecurityException ignored) {}
            }
            if(best!=null && System.currentTimeMillis()-best.getTime()<10*60*1000L) {
                openMosqueMap(best.getLatitude(),best.getLongitude());
                return;
            }
            android.location.Criteria criteria=new android.location.Criteria();
            criteria.setAccuracy(android.location.Criteria.ACCURACY_FINE);
            String provider=lm.getBestProvider(criteria,true);
            if(provider==null) {
                showMosqueLocationError("ফোনের GPS বা অবস্থান সেবা চালু করুন।");
                return;
            }
            final android.app.ProgressDialog progress= new android.app.ProgressDialog(this);
            progress.setMessage("আপনার বর্তমান অবস্থান খোঁজা হচ্ছে…");
            progress.setCancelable(false);
            progress.show();
            final boolean[] completed={false};
            final android.location.LocationListener listener=new android.location.LocationListener() {
                public void onLocationChanged(android.location.Location location) {
                    if(completed[0]) return;
                    completed[0]=true; progress.dismiss();
                    openMosqueMap(location.getLatitude(),location.getLongitude());
                    try { lm.removeUpdates(this); } catch(Exception ignored) {}
                }
                public void onStatusChanged(String p,int status,android.os.Bundle extras){}
                public void onProviderEnabled(String p){}
                public void onProviderDisabled(String p){}
            };
            lm.requestSingleUpdate(provider,listener,mainHandler.getLooper());
            mainHandler.postDelayed(() -> {
                if(!completed[0]) {
                    completed[0]=true; progress.dismiss();
                    try { lm.removeUpdates(listener); } catch(Exception ignored) {}
                    showMosqueLocationError("বর্তমান অবস্থান পাওয়া যায়নি। GPS চালু করে আবার চেষ্টা করুন।");
                }
            },15000);
        } catch(SecurityException e) {
            showMosqueLocationError("অবস্থানের অনুমতি পাওয়া যায়নি।");
        } catch(Exception e) {
            showMosqueLocationError("অবস্থান নির্ণয় করা যায়নি। GPS চালু করে আবার চেষ্টা করুন।");
        }
    }

    private void openMosqueMap(double latitude,double longitude) {
        try {
            String url="https://www.google.com/maps/search/?api=1&query="+Uri.encode("মসজিদ")+"%26center="+latitude+","+longitude;
            // Use a location-centred Maps search so nearby mosque results are shown for this GPS position.
            Uri mapUri=Uri.parse("https://www.google.com/maps/search/মসজিদ/@"+latitude+","+longitude+",15z");
            Intent intent=new Intent(Intent.ACTION_VIEW,mapUri);
            startActivity(intent);
        } catch(Exception e) {
            showMosqueLocationError("ম্যাপ খোলা যায়নি। Google Maps বা ব্রাউজার ইনস্টল আছে কি না দেখুন।");
        }
    }

    private void showMosqueLocationError(String message) {
        new android.app.AlertDialog.Builder(this).setTitle("মসজিদ খুঁজুন")
            .setMessage(message).setPositiveButton("আবার চেষ্টা করুন",(d,w)->findNearbyMosques())
            .setNegativeButton("বন্ধ",null).show();
    }

    private void openSection(int index) {
        if (index == 2) { showWeb("file:///android_asset/hadith.html"); return; }
        if (index == 1) { showWeb("file:///android_asset/tafsir.html"); return; }
        if (index == 8) { showWeb("file:///android_asset/kitab.html"); return; }
        if (index == 0) {
            showLocalQuran();
            return;
        }
        if (index == 3) {
            showWeb("file:///android_asset/prayer_times.html");
            return;
        }
        if (index == 5) {
            showAudioTilawat();
            return;
        }
        if (index == 6) {
            showWeb("file:///android_asset/translation_recitation.html");
            return;
        }
        if (index == 10) {
            showWeb("file:///android_asset/dua.html");
            return;
        }
        if (index == 11) {
            showQiblaCompass();
            return;
        }
        if (index == 12) {
            showTasbihCounter();
            return;
        }
        if (index == 13) {
            findNearbyMosques();
            return;
        }
        if (index == 14) {
            showWeb("file:///android_asset/islamic_names.html");
            return;
        }
        if (index == 15) {
            showWeb("file:///android_asset/calendar.html");
            return;
        }
        if (index == 16) {
            showWeb("file:///android_asset/important_days.html");
            return;
        }
        if (index == 18) {
            showServicesPlaceholder();
            return;
        }
        String url=index==19?HOME_URL:HOME_URL+"search?q="+Uri.encode(titles[index]);
        showWeb(url);
    }


    // Dedicated home-page Audio Tilawat section; separate from the Quran reader.
    private void showAudioTilawat() {
        showingWeb = true;
        openQuranWhenLoaded = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(5), dp(12), dp(5));
        bar.setBackgroundColor(Color.rgb(12,73,52));
        TextView back = text("‹  বিভাগসমূহ",16,Color.WHITE,true);
        back.setPadding(dp(4),dp(8),dp(14),dp(8));
        back.setOnClickListener(v -> {
            if (webView != null) { webView.destroy(); webView = null; }
            showHome();
        });
        bar.addView(back);
        root.addView(bar);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new QuranBridge(), "AndroidQuran");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        root.addView(webView,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        webView.loadUrl("file:///android_asset/audio_tilawat.html");
    }

    private void showLocalQuran() {
        showingWeb = true;
        openQuranWhenLoaded = false;
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(5), dp(12), dp(5));
        bar.setBackgroundColor(Color.rgb(12,73,52));
        TextView back = text("‹  বিভাগসমূহ",16,Color.WHITE,true);
        back.setPadding(dp(4),dp(8),dp(14),dp(8));
        back.setOnClickListener(v -> { if(webView!=null){webView.destroy();webView=null;} showHome(); });
        bar.addView(back);
        root.addView(bar);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        webView.addJavascriptInterface(new QuranBridge(), "AndroidQuran");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        root.addView(webView,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        webView.loadUrl("file:///android_asset/quran.html");
    }

    private volatile boolean quranAudioDownloading = false;

    private final class QuranBridge {
        private File quranAudioDir() {
            File dir = new File(getFilesDir(), "quran_audio");
            if (!dir.exists()) dir.mkdirs();
            return dir;
        }
        private String safeAudioName(String name) {
            return name != null && name.matches("[A-Za-z0-9_-]{1,90}\\.mp3") ? name : null;
        }
        @JavascriptInterface
        public String getCachedAudioPath(String name) {
            String safe = safeAudioName(name);
            if (safe == null) return "";
            File file = new File(quranAudioDir(), safe);
            return file.isFile() && file.length() > 300 ? Uri.fromFile(file).toString() : "";
        }
        @JavascriptInterface
        public void downloadAudio(String audioUrl, String name) {
            final String safe = safeAudioName(name);
            if (safe == null || audioUrl == null || !audioUrl.startsWith("https://everyayah.com/data/")) {
                notifyQuranAudio("nqAudioDownloadError", name, "অডিও ঠিকানা গ্রহণযোগ্য নয়");
                return;
            }
            synchronized (this) {
                if (quranAudioDownloading) {
                    notifyQuranAudio("nqAudioDownloadError", safe, "আগের অডিও ডাউনলোড শেষ হলে আবার চেষ্টা করুন");
                    return;
                }
                quranAudioDownloading = true;
            }
            new Thread(() -> {
                HttpURLConnection connection = null;
                File dir = quranAudioDir();
                File target = new File(dir, safe);
                File temp = new File(dir, safe + ".part");
                try {
                    connection = (HttpURLConnection) new URL(audioUrl).openConnection();
                    connection.setConnectTimeout(20000);
                    connection.setReadTimeout(30000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "NazirpuriRuqyahAndroid/1.0");
                    int response = connection.getResponseCode();
                    if (response != 200) throw new Exception("সার্ভার সাড়া দেয়নি (" + response + ")");
                    // Google Drive may report a compressed/partial response length.
            // Trust Content-Length only for identity encoding; prefer the full
            // object size from Content-Range when Drive provides it.
            long total = connection.getContentLengthLong();
            String encoding = connection.getHeaderField("Content-Encoding");
            if (encoding != null && !encoding.trim().isEmpty() &&
                    !"identity".equalsIgnoreCase(encoding.trim())) total = -1L;
            String contentRange = connection.getHeaderField("Content-Range");
            if (contentRange != null) {
                java.util.regex.Matcher range = java.util.regex.Pattern
                    .compile("/(\\d+)\\s*$").matcher(contentRange.trim());
                if (range.find()) {
                    try { total = Long.parseLong(range.group(1)); }
                    catch (NumberFormatException ignored) { total = -1L; }
                }
            }
                    long received = 0;
                    try (InputStream input = connection.getInputStream();
                         FileOutputStream output = new FileOutputStream(temp)) {
                        byte[] buffer = new byte[32768];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            output.write(buffer, 0, count);
                            received += count;
                            if (total > 0) {
                                final String percent = String.valueOf((int)(received * 100 / total));
                                mainHandler.post(() -> notifyQuranAudio("nqAudioDownloadProgress", safe, percent));
                            }
                        }
                        output.getFD().sync();
                    }
                    if (temp.length() < 300) throw new Exception("অডিও ফাইল অসম্পূর্ণ");
                    if (target.exists()) target.delete();
                    if (!temp.renameTo(target)) throw new Exception("ফোনে অডিও সংরক্ষণ করা যায়নি");
                    mainHandler.post(() -> notifyQuranAudio("nqAudioDownloaded", safe, ""));
                } catch (Exception error) {
                    temp.delete();
                    String message = error.getMessage() == null ? "ডাউনলোড ব্যর্থ" : error.getMessage();
                    mainHandler.post(() -> notifyQuranAudio("nqAudioDownloadError", safe, message));
                } finally {
                    if (connection != null) connection.disconnect();
                    synchronized (QuranBridge.this) { quranAudioDownloading = false; }
                }
            }).start();
        }
        @JavascriptInterface
        public boolean deleteCachedAudio(String name) {
            String safe = safeAudioName(name);
            if (safe == null) return false;
            File file = new File(quranAudioDir(), safe);
            return !file.exists() || file.delete();
        }
        @JavascriptInterface
        public long getCachedAudioBytes() {
            File[] files = quranAudioDir().listFiles();
            long total = 0;
            if (files != null) for (File file : files)
                if (file.isFile() && file.getName().endsWith(".mp3")) total += file.length();
            return total;
        }
        private void notifyQuranAudio(String callback, String name, String detail) {
            if (webView == null) return;
            String script = "window." + callback + " && window." + callback + "("
                + JSONObject.quote(name == null ? "" : name) + ","
                + JSONObject.quote(detail == null ? "" : detail) + ");";
            webView.evaluateJavascript(script, null);
        }
        @JavascriptInterface
        public void close() {
            mainHandler.post(() -> {
                if (webView != null) { webView.destroy(); webView = null; }
                showHome();
            });
        }
    }



    private final class DuaTtsBridge {
        @JavascriptInterface
        public void speak(String text) {
            if (text == null || text.trim().isEmpty()) return;
            final String safeText = text.length() > 4500 ? text.substring(0, 4500) : text;
            mainHandler.post(() -> {
                pendingDuaSpeech = safeText;
                if (duaTextToSpeech == null) {
                    duaTextToSpeech = new TextToSpeech(MainActivity.this, status -> {
                        if (status != TextToSpeech.SUCCESS) {
                            duaTtsReady = false;
                            notifyDuaTts("আরবি কণ্ঠ চালু হয়নি। ফোনের Text-to-Speech সেটিংসে Arabic কণ্ঠ ইনস্টল করুন।");
                            return;
                        }
                        int languageStatus = duaTextToSpeech.setLanguage(Locale.forLanguageTag("ar"));
                        if (languageStatus == TextToSpeech.LANG_MISSING_DATA || languageStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                            duaTtsReady = false;
                            notifyDuaTts("ফোনে আরবি TTS কণ্ঠ নেই। Text-to-Speech সেটিংস থেকে Arabic voice data ইনস্টল করুন।");
                            return;
                        }
                        duaTtsReady = true;
                        speakPendingDua();
                    });
                } else if (duaTtsReady) {
                    speakPendingDua();
                } else {
                    notifyDuaTts("আরবি কণ্ঠ প্রস্তুত হচ্ছে…");
                }
            });
        }
        @JavascriptInterface
        public void stop() {
            mainHandler.post(() -> {
                if (duaTextToSpeech != null) duaTextToSpeech.stop();
            });
        }
    }

    private void speakPendingDua() {
        if (!duaTtsReady || duaTextToSpeech == null || pendingDuaSpeech.isEmpty()) return;
        String text = pendingDuaSpeech;
        pendingDuaSpeech = "";
        int result = duaTextToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dua-" + System.currentTimeMillis());
        if (result == TextToSpeech.ERROR) notifyDuaTts("আরবি কণ্ঠে পড়া যাচ্ছে না। ফোনের Arabic voice data পরীক্ষা করুন।");
        else notifyDuaTts("আরবি কণ্ঠে দুআ পড়া হচ্ছে…");
    }

    private void notifyDuaTts(String message) {
        if (webView == null) return;
        webView.evaluateJavascript("if(window.duaTtsStatus)window.duaTtsStatus(" + JSONObject.quote(message) + ");", null);
    }

    private final class AudioBridge {
        @JavascriptInterface
        public void playYasin() {
            mainHandler.post(() -> {
                if (yasinPlayer != null && yasinPlayer.isPlaying()) {
                    yasinPlayer.pause();
                    audioStatus("বিরতিতে আছে।");
                    return;
                }
                File cached = new File(getFilesDir(), "surah-yasin.mp3");
                if (cached.exists() && cached.length() > 1000000) {
                    startYasinPlayback(cached);
                } else if (!yasinDownloading) {
                    yasinDownloading = true;
                    audioStatus("প্রথমবার অডিও প্রস্তুত করা হচ্ছে…");
                    new Thread(() -> downloadYasin(cached)).start();
                }
            });
        }
        @JavascriptInterface
        public void pauseYasin() {
            mainHandler.post(() -> {
                if (yasinPlayer != null && yasinPlayer.isPlaying()) yasinPlayer.pause();
                audioStatus("বিরতিতে আছে।");
            });
        }
        @JavascriptInterface
        public void seekYasin(int positionMs) {
            mainHandler.post(() -> {
                try {
                    if (yasinPlayer != null && yasinPlayer.isPlaying() || yasinPlayer != null) {
                        int safePosition = Math.max(0, Math.min(positionMs, yasinPlayer.getDuration()));
                        yasinPlayer.seekTo(safePosition);
                    }
                } catch (Exception ignored) { }
            });
        }
        @JavascriptInterface
        public int getYasinPosition() {
            try { return yasinPlayer != null ? yasinPlayer.getCurrentPosition() : 0; }
            catch (Exception ignored) { return 0; }
        }
        @JavascriptInterface
        public int getYasinDuration() {
            try { return yasinPlayer != null ? yasinPlayer.getDuration() : 0; }
            catch (Exception ignored) { return 0; }
        }
        @JavascriptInterface
        public void playMulk() {
            mainHandler.post(() -> {
                if (mulkPlayer != null && mulkPlayer.isPlaying()) {
                    mulkPlayer.pause();
                    audioMulkStatus("বিরতিতে আছে।");
                    return;
                }
                File cached = new File(getFilesDir(), "surah-al-mulk.mp3");
                if (cached.exists() && cached.length() > 1000000) startMulkPlayback(cached);
                else if (!mulkDownloading) {
                    mulkDownloading = true;
                    audioMulkStatus("প্রথমবার সূরা আল-মুলকের অডিও প্রস্তুত করা হচ্ছে…");
                    new Thread(() -> downloadMulk(cached)).start();
                }
            });
        }
        @JavascriptInterface
        public void pauseMulk() {
            mainHandler.post(() -> {
                if (mulkPlayer != null && mulkPlayer.isPlaying()) mulkPlayer.pause();
                audioMulkStatus("বিরতিতে আছে।");
            });
        }
        @JavascriptInterface
        public void seekMulk(int positionMs) {
            mainHandler.post(() -> {
                try {
                    if (mulkPlayer != null) mulkPlayer.seekTo(Math.max(0, Math.min(positionMs, mulkPlayer.getDuration())));
                } catch (Exception ignored) { }
            });
        }
        @JavascriptInterface
        public int getMulkPosition() {
            try { return mulkPlayer != null ? mulkPlayer.getCurrentPosition() : 0; }
            catch (Exception ignored) { return 0; }
        }
        @JavascriptInterface
        public int getMulkDuration() {
            try { return mulkPlayer != null ? mulkPlayer.getDuration() : 0; }
            catch (Exception ignored) { return 0; }
        }
        @JavascriptInterface
        public void playRahman() {
            mainHandler.post(() -> {
                if (rahmanPlayer != null && rahmanPlayer.isPlaying()) {
                    rahmanPlayer.pause();
                    audioRahmanStatus("বিরতিতে আছে।");
                    return;
                }
                File cached = new File(getFilesDir(), "surah-ar-rahman.mp3");
                if (cached.exists() && cached.length() > 1000000) startRahmanPlayback(cached);
                else if (!rahmanDownloading) {
                    rahmanDownloading = true;
                    audioRahmanStatus("প্রথমবার সূরা আর-রহমানের অডিও প্রস্তুত করা হচ্ছে…");
                    new Thread(() -> downloadRahman(cached)).start();
                }
            });
        }
        @JavascriptInterface
        public void pauseRahman() {
            mainHandler.post(() -> {
                if (rahmanPlayer != null && rahmanPlayer.isPlaying()) rahmanPlayer.pause();
                audioRahmanStatus("বিরতিতে আছে।");
            });
        }
        @JavascriptInterface
        public void seekRahman(int positionMs) {
            mainHandler.post(() -> {
                try {
                    if (rahmanPlayer != null) rahmanPlayer.seekTo(Math.max(0, Math.min(positionMs, rahmanPlayer.getDuration())));
                } catch (Exception ignored) { }
            });
        }
        @JavascriptInterface
        public int getRahmanPosition() {
            try { return rahmanPlayer != null ? rahmanPlayer.getCurrentPosition() : 0; }
            catch (Exception ignored) { return 0; }
        }
        @JavascriptInterface
        public int getRahmanDuration() {
            try { return rahmanPlayer != null ? rahmanPlayer.getDuration() : 0; }
            catch (Exception ignored) { return 0; }
        }
    }

    private void audioStatus(String message) {
        if (webView != null) {
            String safe = JSONObject.quote(message);
            webView.evaluateJavascript("window.onYasinStatus && window.onYasinStatus(" + safe + ")", null);
        }
    }
    private void audioRahmanStatus(String message) {
        if (webView != null) {
            String safe = JSONObject.quote(message);
            webView.evaluateJavascript("window.onRahmanStatus && window.onRahmanStatus(" + safe + ")", null);
        }
    }
    private void audioMulkStatus(String message) {
        if (webView != null) {
            String safe = JSONObject.quote(message);
            webView.evaluateJavascript("window.onMulkStatus && window.onMulkStatus(" + safe + ")", null);
        }
    }

    private void downloadMulk(File destination) {
        File temp = new File(getCacheDir(), "surah-al-mulk.download");
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://drive.usercontent.google.com/download?id=1PfD7_skRJ-_-JHkI-NlEAwBuxWA3xb7Y&export=download&confirm=t");
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("ডাউনলোড সার্ভার সাড়া দেয়নি (" + code + ")");
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[262144]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            if (temp.length() < 1000000) throw new Exception("অডিও ফাইলটি সম্পূর্ণ ডাউনলোড হয়নি।");
            if (destination.exists()) destination.delete();
            if (!temp.renameTo(destination)) throw new Exception("অডিও সংরক্ষণ করা যায়নি।");
            mainHandler.post(() -> { mulkDownloading = false; startMulkPlayback(destination); });
        } catch (Exception e) {
            temp.delete();
            mainHandler.post(() -> { mulkDownloading = false; audioMulkStatus("সূরা আল-মুলকের অডিও নামানো যায়নি। Drive ফাইলের অনুমতি ‘যার কাছে লিংক আছে’ করে আবার চেষ্টা করুন।"); });
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void startMulkPlayback(File audioFile) {
        try {
            if (yasinPlayer != null && yasinPlayer.isPlaying()) yasinPlayer.pause();
            if (rahmanPlayer != null && rahmanPlayer.isPlaying()) rahmanPlayer.pause();
            if (mulkPlayer != null) { mulkPlayer.release(); mulkPlayer = null; }
            mulkPlayer = new MediaPlayer();
            mulkPlayer.setDataSource(audioFile.getAbsolutePath());
            mulkPlayer.setOnPreparedListener(player -> { player.start(); audioMulkStatus("সূরা আল-মুলকের তেলাওয়াত চলছে।"); });
            mulkPlayer.setOnCompletionListener(player -> audioMulkStatus("সূরা আল-মুলকের তেলাওয়াত শেষ হয়েছে। আবার শুনতে চাপুন।"));
            mulkPlayer.setOnErrorListener((player, what, extra) -> { audioMulkStatus("অডিও চালানো যায়নি। আবার চেষ্টা করুন।"); return true; });
            mulkPlayer.prepareAsync();
            audioMulkStatus("অডিও চালু হচ্ছে…");
        } catch (Exception e) {
            audioMulkStatus("অডিও চালু করা যায়নি।");
        }
    }

    private void downloadRahman(File destination) {
        File temp = new File(getCacheDir(), "surah-ar-rahman.download");
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://drive.usercontent.google.com/download?id=1rFrvXq6RNwi1pFBqXfHuBpPSq-63-FZ2&export=download&confirm=t");
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("ডাউনলোড সার্ভার সাড়া দেয়নি (" + code + ")");
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            if (temp.length() < 1000000) throw new Exception("অডিও ফাইলটি সম্পূর্ণ ডাউনলোড হয়নি।");
            if (destination.exists()) destination.delete();
            if (!temp.renameTo(destination)) throw new Exception("অডিও সংরক্ষণ করা যায়নি।");
            mainHandler.post(() -> { rahmanDownloading = false; startRahmanPlayback(destination); });
        } catch (Exception e) {
            temp.delete();
            mainHandler.post(() -> { rahmanDownloading = false; audioRahmanStatus("সূরা আর-রহমানের অডিও নামানো যায়নি। Drive ফাইলের অনুমতি ‘যার কাছে লিংক আছে’ করে আবার চেষ্টা করুন।"); });
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void startRahmanPlayback(File audioFile) {
        try {
            if (yasinPlayer != null && yasinPlayer.isPlaying()) yasinPlayer.pause();
            if (rahmanPlayer != null) { rahmanPlayer.release(); rahmanPlayer = null; }
            rahmanPlayer = new MediaPlayer();
            rahmanPlayer.setDataSource(audioFile.getAbsolutePath());
            rahmanPlayer.setOnPreparedListener(player -> { player.start(); audioRahmanStatus("সূরা আর-রহমানের তেলাওয়াত চলছে।"); });
            rahmanPlayer.setOnCompletionListener(player -> audioRahmanStatus("সূরা আর-রহমানের তেলাওয়াত শেষ হয়েছে। আবার শুনতে চাপুন।"));
            rahmanPlayer.setOnErrorListener((player, what, extra) -> { audioRahmanStatus("অডিও চালানো যায়নি। আবার চেষ্টা করুন।"); return true; });
            rahmanPlayer.prepareAsync();
            audioRahmanStatus("অডিও চালু হচ্ছে…");
        } catch (Exception e) {
            audioRahmanStatus("অডিও চালু করা যায়নি।");
        }
    }

    private void downloadYasin(File destination) {
        File temp = new File(getCacheDir(), "surah-yasin.download");
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://drive.usercontent.google.com/download?id=1pWdLu1cTJ3LkyW9wGaOw1o01am9SlfLP&export=download&confirm=t");
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(30000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("ডাউনলোড সার্ভার সাড়া দেয়নি (" + code + ")");
            try (InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(temp)) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            if (temp.length() < 1000000) throw new Exception("অডিও ফাইলটি সম্পূর্ণ ডাউনলোড হয়নি।");
            if (destination.exists()) destination.delete();
            if (!temp.renameTo(destination)) throw new Exception("অডিও সংরক্ষণ করা যায়নি।");
            mainHandler.post(() -> { yasinDownloading = false; startYasinPlayback(destination); });
        } catch (Exception e) {
            temp.delete();
            mainHandler.post(() -> { yasinDownloading = false; audioStatus("অডিও নামানো যায়নি। ইন্টারনেট সংযোগ ও Drive-এর লিংক অনুমতি পরীক্ষা করে আবার চাপুন।"); });
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void startYasinPlayback(File audioFile) {
        try {
            if (yasinPlayer != null) { yasinPlayer.release(); yasinPlayer = null; }
            yasinPlayer = new MediaPlayer();
            yasinPlayer.setDataSource(audioFile.getAbsolutePath());
            yasinPlayer.setOnPreparedListener(player -> { player.start(); audioStatus("সূরা ইয়াসিনের তেলাওয়াত চলছে।"); });
            yasinPlayer.setOnCompletionListener(player -> audioStatus("তেলাওয়াত শেষ হয়েছে। আবার শুনতে চাপুন।"));
            yasinPlayer.setOnErrorListener((player, what, extra) -> { audioStatus("অডিও চালানো যায়নি। আবার চেষ্টা করুন।"); return true; });
            yasinPlayer.prepareAsync();
            audioStatus("অডিও চালু হচ্ছে…");
        } catch (Exception e) {
            audioStatus("অডিও চালু করা যায়নি।");
        }
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
        showingWeb = false;
        releaseWebView();
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(190, 222, 219));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(18), dp(12), dp(18), dp(12));
        toolbar.setBackgroundColor(Color.rgb(0, 77, 66));
        TextView back = text("←", 30, Color.rgb(160, 220, 205), true);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> showHome());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(52), dp(48)));
        TextView heading = text("সেটিংস", 25, Color.WHITE, true);
        heading.setGravity(Gravity.CENTER);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView menu = text("☰", 29, Color.rgb(160, 220, 205), true);
        menu.setGravity(Gravity.CENTER);
        menu.setContentDescription("অ্যাপ মেনু");
        menu.setOnClickListener(v -> showHomeMenu());
        toolbar.addView(menu, new LinearLayout.LayoutParams(dp(52), dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(20), dp(28), dp(20), dp(26));
        GradientDrawable outerBg = new GradientDrawable();
        outerBg.setColor(Color.rgb(218, 244, 240));
        outerBg.setCornerRadius(dp(34));
        outer.setBackground(outerBg);
        outer.setElevation(dp(2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(Color.rgb(218, 244, 240));
        cardBg.setCornerRadius(dp(30));
        cardBg.setStroke(dp(1), Color.rgb(197, 230, 225));
        card.setBackground(cardBg);
        card.setClipToOutline(true);

        android.content.SharedPreferences prefs = getSharedPreferences("app_preferences", MODE_PRIVATE);
        String selectedArabic = prefs.getString("arabic_font", "Noorehuda (Default)");
        String selectedBangla = prefs.getString("bangla_font", "Solaiman Lipi");
        addSettingsRow(card, "ভাষা", "বাংলা", null);
        addSettingsRow(card, "আরবি ফন্ট", selectedArabic, () -> showFontPicker(true));
        addSettingsRow(card, "বাংলা ফন্ট", selectedBangla, () -> showFontPicker(false));
        addFontSizeControl(card, "আরবি লেখার সাইজ", "arabic_text_size", 24, 14, 40);
        addFontSizeControl(card, "বাংলা লেখার সাইজ", "bangla_text_size", 18, 12, 34);

        LinearLayout extra = new LinearLayout(this);
        extra.setOrientation(LinearLayout.VERTICAL);
        extra.setPadding(dp(22), dp(14), dp(22), dp(18));
        TextView website = text("ওয়েবসাইট খুলুন  ›", 16, Color.rgb(0, 92, 76), true);
        website.setPadding(0, dp(8), 0, dp(8));
        website.setOnClickListener(v -> showWeb(HOME_URL));
        extra.addView(website);
        TextView about = text("আমাদের সম্পর্কে জানুন  ›", 16, Color.rgb(0, 92, 76), true);
        about.setPadding(0, dp(8), 0, dp(8));
        about.setOnClickListener(v -> showAboutDialog());
        extra.addView(about);
        card.addView(extra);
        outer.addView(card, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(outer);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private void applySelectedFonts(WebView view) {
        if (view == null) return;
        android.content.SharedPreferences fp=getSharedPreferences("app_preferences",MODE_PRIVATE);
        String bn=fp.getString("bangla_font","Solaiman Lipi");
        String ar=fp.getString("arabic_font","Noorehuda (Default)");
        int bSize=fp.getInt("bangla_text_size",18), aSize=fp.getInt("arabic_text_size",24);
        String banglaFace="Solaiman Lipi".equals(bn)?"SolaimanLipi":"'Noto Sans Bengali'";
        // Named Quran font selections remain selectable; use a dependable Arabic font fallback
        // for pages that do not bundle the selected font file.
        String arabicFace="'Noto Naskh Arabic'";
        String css="@font-face{font-family:SolaimanLipi;src:url('file:///android_asset/SolaimanLipi.ttf') format('truetype');}@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+Bengali:wght@400;600;700&family=Noto+Naskh+Arabic:wght@400;600;700&display=swap');html,body,body *:not(i):not([class*='icon']){font-family:"+banglaFace+",'Noto Sans Bengali',sans-serif !important;font-size:"+bSize+"px !important;}[lang='ar'],.arabic,.arabic-text,[dir='rtl'],.quran,.ayah{font-family:"+arabicFace+",serif !important;font-size:"+aSize+"px !important;}";
        String js="(function(){var st=document.getElementById('nazirpuri-app-fonts');if(!st){st=document.createElement('style');st.id='nazirpuri-app-fonts';document.head.appendChild(st);}st.textContent="+JSONObject.quote(css)+";document.querySelectorAll('body *').forEach(function(el){var t=el.childElementCount===0?(el.textContent||''):'';if(/[\u0600-\u06FF]/.test(t)){el.classList.add('nazirpuri-arabic-font');}});})();";
        js="(function(){var st=document.getElementById('nazirpuri-app-fonts');if(!st){st=document.createElement('style');st.id='nazirpuri-app-fonts';document.head.appendChild(st);}st.textContent="+JSONObject.quote(css)+";document.querySelectorAll('body *').forEach(function(el){if(el.children.length===0&&/[\\u0600-\\u06FF]/.test(el.textContent||''))el.classList.add('nazirpuri-arabic-font');});})();";
        view.evaluateJavascript(js,null);
    }

    private void addFontSizeControl(LinearLayout card, String label, String prefKey, int defaultSize, int min, int max) {
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20),dp(12),dp(20),dp(14));
        TextView title=text(label,17,Color.rgb(28,42,39),true);
        panel.addView(title);
        android.content.SharedPreferences prefs=getSharedPreferences("app_preferences",MODE_PRIVATE);
        int current=prefs.getInt(prefKey,defaultSize);
        TextView value=text(current+" px",15,Color.rgb(0,91,77),true);
        value.setGravity(Gravity.CENTER);
        panel.addView(value,new LinearLayout.LayoutParams(-1,-2));
        SeekBar seek=new SeekBar(this);
        seek.setMax(max-min); seek.setProgress(Math.max(0,Math.min(max-min,current-min)));
        seek.setContentDescription(label+" পরিবর্তন");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                int size=min+progress; value.setText(size+" px");
                if(fromUser){
                    prefs.edit().putInt(prefKey,size).apply();
                    if(webView!=null)applySelectedFonts(webView);
                }
            }
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        panel.addView(seek,new LinearLayout.LayoutParams(-1,dp(38)));
        card.addView(panel,new LinearLayout.LayoutParams(-1,-2));
        View divider=new View(this); divider.setBackgroundColor(Color.rgb(192,225,220));
        card.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private void addSettingsRow(LinearLayout card, String labelText, String valueText, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), dp(22), dp(20), dp(22));
        TextView label = text(labelText, 20, Color.rgb(28, 42, 39), true);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(valueText + (action == null ? "" : "  ⌄"), 17, Color.rgb(0, 91, 77), true);
        value.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        row.addView(value, new LinearLayout.LayoutParams(-2, -2));
        if (action != null) {
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> { action.run(); });
        }
        card.addView(row, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(192, 225, 220));
        card.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }
    private void showFontPicker(boolean arabic) {
        final String[] opts=arabic?new String[]{"Noorehuda (Default)","Al Qalam Quran Majeed","Al Qalam Kolkatta Quran Majeed","Al Mushaf Quran","Al Qalam Quran","Kitab"}:new String[]{"Solaiman Lipi","Siyam Rupali","Noto Sans Bengali"};
        final String key=arabic?"arabic_font":"bangla_font";
        android.content.SharedPreferences p=getSharedPreferences("app_preferences",MODE_PRIVATE);
        String cur=p.getString(key,opts[0]); int checked=0; for(int x=0;x<opts.length;x++)if(opts[x].equals(cur))checked=x;
        new android.app.AlertDialog.Builder(this).setTitle(arabic?"আরবি ফন্ট নির্বাচন":"বাংলা ফন্ট নির্বাচন")
            .setSingleChoiceItems(opts,checked,(d,w)->{
                p.edit().putString(key,opts[w]).apply();
                if(!arabic) {
                    try {
                        if ("Solaiman Lipi".equals(opts[w])) banglaTypeface=Typeface.createFromAsset(getAssets(),"SolaimanLipi.ttf");
                        else if ("Siyam Rupali".equals(opts[w])) banglaTypeface=Typeface.create("sans-serif",Typeface.NORMAL);
                        else banglaTypeface=Typeface.create("sans-serif",Typeface.NORMAL);
                    } catch(Exception e){ banglaTypeface=Typeface.create("sans-serif",Typeface.NORMAL); }
                }
                d.dismiss();
                android.widget.Toast.makeText(this,opts[w]+" ফন্ট প্রয়োগ করা হচ্ছে",android.widget.Toast.LENGTH_SHORT).show();
                if (webView != null) { applySelectedFonts(webView); webView.reload(); }
                showHome();
            }).setNegativeButton("বন্ধ",null).show();
    }
    private void showHomeMenu() {
        new android.app.AlertDialog.Builder(this).setTitle("অ্যাপ মেনু")
            .setItems(new String[]{"অ্যাপটি শেয়ার করুন","আমাদের সম্পর্কে জানুন","যোগাযোগ"},(d,w)->{
                if(w==0){Intent share=new Intent(Intent.ACTION_SEND);share.setType("text/plain");share.putExtra(Intent.EXTRA_TEXT,"নাজিরপুরী রুকইয়া ও দাওয়াখানা অ্যাপ\\n"+HOME_URL);startActivity(Intent.createChooser(share,"অ্যাপটি শেয়ার করুন"));}
                else if(w==1)showAboutDialog();else showContactDialog();
            }).setNegativeButton("বন্ধ",null).show();
    }
    private void showAboutDialog() {
        new android.app.AlertDialog.Builder(this).setTitle("নাজিরপুরী রুকইয়া ও দাওয়াখানা")
            .setMessage("কুরআন • সুন্নাহ • ইলম • আমল\\n\\nমুফতী রুহুল আমীন নাজিরপুরীর তত্ত্বাবধানে পরিচালিত দ্বীনি শিক্ষা ও রুকইয়া সেবা কেন্দ্রের সহযোগী অ্যাপ।")
            .setPositiveButton("ঠিক আছে",null).show();
    }
    private void showContactDialog() {
        new android.app.AlertDialog.Builder(this).setTitle("যোগাযোগ").setMessage("WhatsApp: 01614-776592")
            .setPositiveButton("WhatsApp খুলুন",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(WHATSAPP_URL)));}catch(Exception ignored){}})
            .setNegativeButton("বন্ধ",null).show();
    }

    private void releaseWebView() {
        WebView old = webView;
        webView = null;
        if (old != null) {
            try {
                old.stopLoading();
                old.setWebChromeClient(null);
                old.setWebViewClient(null);
                old.removeAllViews();
                old.destroy();
            } catch (Exception ignored) { }
        }
    }

    private void showWeb(String url) {
        releaseWebView();
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
        webView.addJavascriptInterface(new AudioBridge(), "AndroidAudio");
        webView.addJavascriptInterface(new DuaTtsBridge(), "AndroidDuaTts");
        webView.addJavascriptInterface(new KitabBridge(), "AndroidKitab");
        if (url != null && url.endsWith("/hadith.html")) webView.addJavascriptInterface(new HadithBridge(), "AndroidHadith");
        if (openQuranWhenLoaded) webView.setVisibility(View.INVISIBLE);
        WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setLoadsImagesAutomatically(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setAllowFileAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        webView.setWebViewClient(new WebViewClient(){
            // Route both modern and legacy navigation callbacks into this WebView.
            private boolean openInside(WebView view, String url) {
                if (url == null) return true;
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    view.loadUrl(url);
                    return true;
                }
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openInside(view, url);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return openInside(view, request.getUrl().toString());
            }
            @Override public void onPageFinished(WebView view, String pageUrl){
                super.onPageFinished(view, pageUrl);
                // Some book pages use target=_blank or window.open; force those links
                // to navigate the current WebView instead of creating an unhandled window.
                view.evaluateJavascript("(function(){if(window.__nazirInAppLinks)return;window.__nazirInAppLinks=true;document.addEventListener('click',function(e){var a=e.target&&e.target.closest?e.target.closest('a[href]'):null;if(a&&/^https?:/i.test(a.href)){e.preventDefault();e.stopPropagation();window.location.href=a.href;}},true);var oldOpen=window.open;window.open=function(u){if(u&&/^https?:/i.test(String(u))){window.location.href=String(u);return window;}return oldOpen?oldOpen.apply(window,arguments):null;};})();", null);
                applySelectedFonts(view);
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
                }, 80);
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
        TextView v=new TextView(this); v.setText(value);
        int chosenSize=size;
        if(value.codePoints().anyMatch(c -> c >= 0x0600 && c <= 0x06FF)) chosenSize=getSharedPreferences("app_preferences",MODE_PRIVATE).getInt("arabic_text_size",24);
        else if(value.codePoints().anyMatch(c -> c >= 0x0980 && c <= 0x09FF)) chosenSize=getSharedPreferences("app_preferences",MODE_PRIVATE).getInt("bangla_text_size",18);
        v.setTextSize(chosenSize); v.setTextColor(color);
        boolean hasBangla = value.codePoints().anyMatch(c -> c >= 0x0980 && c <= 0x09FF);
        if (hasBangla) {
            String f=getSharedPreferences("app_preferences",MODE_PRIVATE).getString("bangla_font","Solaiman Lipi");
            Typeface face=banglaTypeface;
            if("Noto Sans Bengali".equals(f))face=Typeface.create("sans-serif",Typeface.NORMAL);
            if(face!=null)v.setTypeface(Typeface.create(face,bold?Typeface.BOLD:Typeface.NORMAL));
        } else if(bold) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        v.setGravity(Gravity.CENTER); return v;
    }
    // Consistent premium icon badges: soft gradient, gold halo and distinct category pictograms.
    private class SectionIconView extends View {
        private final int category;
        private final int[] colors;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rect = new android.graphics.RectF();
        SectionIconView(int category, int[] palette) {
            super(MainActivity.this);
            this.category = category;
            this.colors = palette.clone();
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setContentDescription(titles[category] + " আইকন");
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            float w=getWidth(), h=getHeight(), cx=w/2f, cy=h/2f;
            float r=Math.min(w,h)*.47f;
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setShader(new android.graphics.LinearGradient(0,0,w,h,
                Color.rgb(Math.min(255,colors[0]+18),Math.min(255,colors[1]+18),Math.min(255,colors[2]+18)),
                Color.rgb(colors[0],colors[1],colors[2]),android.graphics.Shader.TileMode.CLAMP));
            rect.set(cx-r,cy-r,cx+r,cy+r);
            paint.setShadowLayer(dp(3),0,dp(2),0x260D3528);
            canvas.drawRoundRect(rect,dp(17),dp(17),paint);
            paint.clearShadowLayer(); paint.setShader(null);
            paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(dp(1)*1.4f);
            paint.setColor(Color.argb(125,255,255,255));
            rect.inset(dp(3),dp(3)); canvas.drawRoundRect(rect,dp(14),dp(14),paint);
            paint.setStyle(android.graphics.Paint.Style.FILL);
            paint.setColor(Color.rgb(colors[3],colors[4],colors[5]));
            paint.setTextAlign(android.graphics.Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.NORMAL));
            paint.setTextSize(dp(27));
            android.graphics.Paint.FontMetrics fm=paint.getFontMetrics();
            canvas.drawText(icons[category],cx,cy-(fm.ascent+fm.descent)/2f,paint);
            paint.setColor(Color.rgb(225,181,91));
            canvas.drawCircle(cx+r*.72f,cy-r*.68f,dp(2)*1.1f,paint);
        }
    }

    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private boolean androidBackCheckPending = false;
    @Override public void onBackPressed(){
        if (!showingWeb || webView == null) { super.onBackPressed(); return; }
        if (androidBackCheckPending) return;
        final WebView current = webView;
        androidBackCheckPending = true;
        try {
            current.evaluateJavascript(
                "(function(){try{return !!(window.nazirHandleAndroidBack && window.nazirHandleAndroidBack());}catch(e){return false;}})()",
                result -> {
                    androidBackCheckPending = false;
                    if (current != webView || !showingWeb) return;
                    if ("true".equals(result)) return;
                    if (current.canGoBack()) current.goBack();
                    else showHome();
                }
            );
        } catch (Exception e) {
            androidBackCheckPending = false;
            if (current == webView && showingWeb) {
                if (current.canGoBack()) current.goBack();
                else showHome();
            }
        }
    }
    @Override protected void onDestroy(){
        if(yasinPlayer!=null){ yasinPlayer.release(); yasinPlayer=null; }
        if(rahmanPlayer!=null){ rahmanPlayer.release(); rahmanPlayer=null; }
        if(mulkPlayer!=null){ mulkPlayer.release(); mulkPlayer=null; }
        if(duaTextToSpeech!=null){duaTextToSpeech.stop();duaTextToSpeech.shutdown();duaTextToSpeech=null;}
        if(webView!=null){webView.destroy();webView=null;} super.onDestroy();
    }

    // Persistent per-URL Hadith JSON cache for full offline access.
    private final class HadithBridge {
        @JavascriptInterface public void openExternalBook(String address) {
            if (address == null) return;
            Uri uri = Uri.parse(address);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null ||
                    !(host.equalsIgnoreCase("hadithbd.com") || host.toLowerCase(Locale.US).endsWith(".hadithbd.com"))) return;
            mainHandler.post(() -> {
                WebView currentView = webView;
                if (currentView != null && showingWeb) currentView.loadUrl(uri.toString());
            });
        }
        @JavascriptInterface public void setReaderOpen(boolean open) { hadithReaderOpen = open; }
        private File cacheDir() {
            File dir = new File(getFilesDir(), "hadith_cache");
            if (!dir.exists()) dir.mkdirs();
            return dir;
        }
        private String key(String value) {
            try {
                java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(value.getBytes("UTF-8"));
                StringBuilder out = new StringBuilder();
                for (byte b : bytes) out.append(String.format(java.util.Locale.US, "%02x", b & 0xff));
                return out.toString();
            } catch (Exception e) { return Integer.toHexString(value.hashCode()); }
        }
        private File jsonFile(String url) { return new File(cacheDir(), key(url) + ".json"); }
        @JavascriptInterface public String getCachedJson(String url) {
            if (url == null || !(url.startsWith("https://cdn.jsdelivr.net/") || url.startsWith("https://raw.githubusercontent.com/"))) return "";
            File file = jsonFile(url);
            if (!file.isFile() || file.length() == 0) return "";
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                InputStream in = new java.io.FileInputStream(file);
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                in.close();
                return out.toString("UTF-8");
            } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public void saveCachedJson(String url, String json) {
            if (url == null || json == null || json.length() < 2 ||
                !(url.startsWith("https://cdn.jsdelivr.net/") || url.startsWith("https://raw.githubusercontent.com/"))) return;
            File target = jsonFile(url), temp = new File(target.getParentFile(), target.getName() + ".part");
            try {
                java.io.FileOutputStream out = new java.io.FileOutputStream(temp);
                out.write(json.getBytes("UTF-8")); out.flush(); out.getFD().sync(); out.close();
                if (target.exists()) target.delete();
                temp.renameTo(target);
            } catch (Exception ignored) { temp.delete(); }
        }
        private File marker(String bookId) {
            String safe = bookId == null ? "" : bookId.replaceAll("[^a-zA-Z0-9_-]", "");
            return new File(cacheDir(), "complete_" + safe + ".flag");
        }
        @JavascriptInterface public boolean isBookComplete(String bookId) { return marker(bookId).isFile(); }
        @JavascriptInterface public void markBookComplete(String bookId, boolean complete) {
            File f = marker(bookId);
            try { if (complete) { java.io.FileOutputStream out = new java.io.FileOutputStream(f); out.write(1); out.close(); } else if (f.exists()) f.delete(); }
            catch (Exception ignored) { }
        }
    }

    // Download books once into private app storage; keep them available offline.
    private final class KitabBridge {
        // Shared app-wide executor and active set prevent separate WebView bridge
        // instances from creating competing pools or duplicate downloads.

        private File bookFile(String name) {
            String safe = name == null ? "" : name.replaceAll("[^a-zA-Z0-9_-]", "");
            return new File(new File(getFilesDir(), "kitab_books"), safe + ".pdf");
        }

        @JavascriptInterface
        public boolean isBookDownloaded(String name) {
            File f = bookFile(name);
            return isPdfFile(f);
        }

        @JavascriptInterface
        public void downloadBook(String name, String address) {
            if (name == null || name.trim().isEmpty() || address == null ||
                !address.startsWith("https://drive.google.com/")) {
                notifyBook(name, false, "কিতাবের ডাউনলোড লিংক সঠিক নয়");
                return;
            }
            File target = bookFile(name);
            if (isPdfFile(target)) {
                openOfflineBook(name);
                return;
            }
            if (!ACTIVE_BOOK_DOWNLOADS.add(name)) {
                notifyBook(name, false, "এই কিতাবটির ডাউনলোড ইতোমধ্যে চলছে।");
                return;
            }
            notifyBookQueued(name);
            try {
            BOOK_DOWNLOAD_EXECUTOR.execute(() -> {
                notifyBookStarted(name);
                File dir = target.getParentFile();
                File temp = new File(dir, target.getName() + ".part");
                try {
                    if (!dir.exists() && !dir.mkdirs()) throw new Exception("ফোল্ডার তৈরি করা যায়নি");
                    Uri source = Uri.parse(address);
                    String id = source.getQueryParameter("id");
                    if (id == null || id.trim().isEmpty()) throw new Exception("Drive ফাইলের ID পাওয়া যায়নি");
                    downloadGoogleDrivePdf(id, temp, name);
                    if (temp.length() < 1000) throw new Exception("ফাইলটি সম্পূর্ণ ডাউনলোড হয়নি");
                    if (!isPdfFile(temp) || !hasPdfEof(temp)) {
                        throw new Exception("Drive থেকে সম্পূর্ণ ও বৈধ PDF ফাইল পাওয়া যায়নি");
                    }
                    if (target.exists()) target.delete();
                    if (!temp.renameTo(target)) throw new Exception("ফাইল সংরক্ষণ করা যায়নি");
                    notifyBookProgress(name, target.length(), target.length());
                    // Do not open the reader automatically: leave the book list visible
                    // so users can start or finish other book downloads independently.
                    notifyBook(name, true, "ডাউনলোড সম্পূর্ণ হয়েছে। অফলাইনে পড়তে কিতাবটিতে আবার চাপ দিন।");
                } catch (Exception e) {
                    temp.delete();
                    notifyBook(name, false, "ডাউনলোড হয়নি: " + e.getMessage());
                } finally {
                    ACTIVE_BOOK_DOWNLOADS.remove(name);
                }
            });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                ACTIVE_BOOK_DOWNLOADS.remove(name);
                notifyBook(name, false, "ডাউনলোডের সারি পূর্ণ। কিছুক্ষণ পর আবার চেষ্টা করুন।");
            }
        }

        @JavascriptInterface
        public void openOfflineBook(String name) {
            File f = bookFile(name);
            if (!isPdfFile(f)) {
                if (f.exists()) f.delete(); // Remove an old incomplete/non-PDF cache so it can be downloaded again.
                notifyBook(name, false, "সংরক্ষিত ফাইলটি অসম্পূর্ণ বা নষ্ট। ইন্টারনেট চালু করে আবার ডাউনলোড করুন।");
                return;
            }
            mainHandler.post(() -> {
                try {
                    Intent intent = new Intent(MainActivity.this, PdfReaderActivity.class);
                    intent.putExtra("pdf_path", f.getAbsolutePath());
                    startActivity(intent);
                } catch (Exception e) {
                    android.widget.Toast.makeText(MainActivity.this,
                        "কিতাবটি খোলা যাচ্ছে না। আবার চেষ্টা করুন।", android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }

        private void notifyBookQueued(String name) {
            mainHandler.post(() -> {
                if (webView != null) {
                    String key = org.json.JSONObject.quote(name == null ? "" : name);
                    webView.evaluateJavascript("if(window.onBookQueued)window.onBookQueued(" + key + ")", null);
                }
            });
        }

        private void notifyBookStarted(String name) {
            mainHandler.post(() -> {
                if (webView != null) {
                    String key = org.json.JSONObject.quote(name == null ? "" : name);
                    webView.evaluateJavascript("if(window.onBookStarted)window.onBookStarted(" + key + ")", null);
                }
            });
        }

        private void notifyBookProgress(String name, long downloaded, long total) {
            mainHandler.post(() -> {
                if (webView != null) {
                    String key = org.json.JSONObject.quote(name == null ? "" : name);
                    webView.evaluateJavascript("if(window.onBookProgress)window.onBookProgress(" + key + "," + downloaded + "," + total + ")", null);
                }
            });
        }

        private void notifyBook(String name, boolean success, String message) {
            mainHandler.post(() -> {
                android.widget.Toast.makeText(MainActivity.this, message, android.widget.Toast.LENGTH_LONG).show();
                if (webView != null) {
                    String key = org.json.JSONObject.quote(name == null ? "" : name);
                    String msg = org.json.JSONObject.quote(message);
                    webView.evaluateJavascript("if(window.onBookDownload)window.onBookDownload(" + key + "," + success + "," + msg + ")", null);
                }
            });
        }

        // Handle Google Drive's confirmation form and cookies before accepting a PDF.
        // Stream PDF bytes directly to disk and report live progress to the book list.
        // If Drive returns HTML, keep it in memory only to parse its confirmation form.
        private byte[] readDriveResponse(HttpURLConnection connection, File temp, String name) throws Exception {
            long total = connection.getContentLengthLong();
            String contentRange = connection.getHeaderField("Content-Range");
            if (contentRange != null) {
                java.util.regex.Matcher range = java.util.regex.Pattern.compile("(?i)bytes\\s+\\d+-\\d+/(\\d+)").matcher(contentRange);
                if (range.find()) {
                    try { total = Long.parseLong(range.group(1)); } catch (NumberFormatException ignored) { }
                }
            }
            try (java.io.InputStream input = new java.io.BufferedInputStream(connection.getInputStream())) {
                // PDF specifications permit the %PDF- marker within the first 1024 bytes.
                // Some Drive octet-stream responses contain a short preamble before the header.
                byte[] header = new byte[1024];
                int got = 0;
                while (got < header.length) {
                    int n = input.read(header, got, header.length - got);
                    if (n == -1) break;
                    got += n;
                    if (got >= 5 && indexOfPdfHeader(header, got) >= 0) break;
                }
                if (indexOfPdfHeader(header, got) >= 0) {
                    long done = 0;
                    try (FileOutputStream out = new FileOutputStream(temp)) {
                        out.write(header, 0, got);
                        done = got;
                        notifyBookProgress(name, done, total);
                        byte[] buffer = new byte[65536];
                        int n;
                        long lastProgressAt = android.os.SystemClock.elapsedRealtime();
                        while ((n = input.read(buffer)) != -1) {
                            out.write(buffer, 0, n);
                            done += n;
                            long now = android.os.SystemClock.elapsedRealtime();
                            if (now - lastProgressAt >= 250L) {
                                notifyBookProgress(name, done, total);
                                lastProgressAt = now;
                            }
                        }
                        out.flush();
                    }
                    // Never report success when the server declared a file size but
                    // the stream ended early (or delivered extra bytes). This prevents
                    // a truncated PDF from being marked complete at e.g. 3%.
                    if (total > 0 && done != total) {
                        notifyBookProgress(name, done, total);
                        throw new Exception("ডাউনলোড অসম্পূর্ণ: " + done + " / " + total + " বাইট এসেছে। ইন্টারনেট স্থিতিশীল হলে আবার চেষ্টা করুন।");
                    }
                    if (!isPdfFile(temp) || !hasPdfEof(temp))
                        throw new Exception("PDF ফাইলটি সম্পূর্ণ আসেনি; শেষ অংশ যাচাই করা যায়নি। আবার ডাউনলোড করুন।");
                    // Unknown server length? Show 100% only after EOF and PDF trailer validation.
                    if (total <= 0) notifyBookProgress(name, done, done);
                    else notifyBookProgress(name, done, total);
                    return null;
                }
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                if (got > 0) out.write(header, 0, got);
                byte[] buffer = new byte[8192];
                int n;
                while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
                return out.toByteArray();
            }
        }

        private void downloadGoogleDrivePdf(String id, File temp, String name) throws Exception {
            String encoded = Uri.encode(id);
            // Google Drive's public-download endpoint has changed over time.
            // Try the current usercontent URL (both confirmation spellings), then
            // the legacy uc endpoint. Each attempt gets a fresh cookie jar.
            String[] urls = {
                "https://drive.usercontent.google.com/download?export=download&id=" + encoded + "&confirm=yes",
                "https://drive.usercontent.google.com/download?export=download&id=" + encoded + "&confirm=t",
                "https://drive.google.com/uc?export=download&id=" + encoded + "&confirm=yes",
                "https://drive.google.com/uc?export=download&id=" + encoded + "&confirm=t",
                "https://drive.google.com/uc?export=download&id=" + encoded
            };
            Exception last = null;
            for (String address : urls) {
                HttpURLConnection c = null;
                java.net.CookieManager cookieJar = new java.net.CookieManager(null, java.net.CookiePolicy.ACCEPT_ALL);
                try {
                    c = openDriveConnection(address, cookieJar, "GET", null, address);
                    int code = c.getResponseCode();
                    if (code < 200 || code >= 300) {
                        String errorType = c.getContentType();
                        String errorText = readDriveError(c);
                        throw new Exception("Drive HTTP " + code + " (" + errorType + "): " + errorText);
                    }
                    byte[] body = readDriveResponse(c, temp, name);
                    if (body == null) return;
                    String html = new String(body, java.nio.charset.StandardCharsets.UTF_8);
                    java.util.regex.Matcher form = java.util.regex.Pattern.compile("(?is)<form\\b([^>]*)>(.*?)</form>").matcher(html);
                    String action = null, method = "GET", fields = "";
                    if (form.find()) {
                        String attrs = form.group(1);
                        action = htmlAttribute(attrs, "action");
                        String m = htmlAttribute(attrs, "method");
                        if (m != null) method = m.trim().toUpperCase(java.util.Locale.US);
                        StringBuilder data = new StringBuilder();
                        java.util.regex.Matcher inputTag = java.util.regex.Pattern.compile("(?is)<input\\b[^>]*>").matcher(form.group(2));
                        while (inputTag.find()) {
                            String tag = inputTag.group();
                            String fieldName = htmlAttribute(tag, "name");
                            String fieldValue = htmlAttribute(tag, "value");
                            if (fieldName != null && fieldValue != null) {
                                if (data.length() > 0) data.append("&");
                                data.append(java.net.URLEncoder.encode(fieldName, "UTF-8"));
                                data.append("=").append(java.net.URLEncoder.encode(fieldValue, "UTF-8"));
                            }
                        }
                        fields = data.toString();
                    } else {
                        java.util.regex.Matcher link = java.util.regex.Pattern.compile("(?is)<a\\b[^>]*id=['\\\"]uc-download-link['\\\"][^>]*>").matcher(html);
                        if (link.find()) action = htmlAttribute(link.group(), "href");
                        if (action == null) {
                            java.util.regex.Matcher confirm = java.util.regex.Pattern.compile("(?i)confirm=([a-zA-Z0-9_-]+)").matcher(html);
                            if (confirm.find()) fields = "confirm=" + java.net.URLEncoder.encode(confirm.group(1), "UTF-8") + "&id=" + encoded;
                        }
                    }
                    // Some Drive responses expose the virus-scan confirmation token only
                    // as a download_warning_* cookie, not as a hidden HTML field.
                    if (action == null && fields.isEmpty()) {
                        String warningToken = null;
                        String uuidToken = null;
                        for (java.net.HttpCookie cookie : cookieJar.getCookieStore().getCookies()) {
                            if (cookie.getName() != null && cookie.getName().startsWith("download_warning")) warningToken = cookie.getValue();
                            if ("uuid".equalsIgnoreCase(cookie.getName())) uuidToken = cookie.getValue();
                        }
                        if (warningToken != null && !warningToken.isEmpty()) {
                            fields = "id=" + encoded + "&export=download&confirm=" +
                                java.net.URLEncoder.encode(warningToken, "UTF-8");
                            if (uuidToken != null && !uuidToken.isEmpty())
                                fields += "&uuid=" + java.net.URLEncoder.encode(uuidToken, "UTF-8");
                            action = "https://drive.usercontent.google.com/download";
                        }
                    }
                    if (action != null) {
                        action = action.replace("&amp;", "&");
                        if (action.startsWith("/")) action = "https://drive.google.com" + action;
                        else if (!action.startsWith("http")) action = "https://drive.google.com/" + action;
                    } else if (!fields.isEmpty()) {
                        action = "https://drive.google.com/uc?export=download";
                    }
                    if (action != null) {
                        HttpURLConnection c2 = null;
                        try {
                            if (!"POST".equals(method) && !fields.isEmpty()) {
                                String separator = action.contains("?") ? "&" : "?";
                                action = action + separator + fields;
                            }
                            c2 = openDriveConnection(action, cookieJar, method, fields, address);
                            int code2 = c2.getResponseCode();
                            if (code2 >= 200 && code2 < 300) {
                                byte[] confirmedBody = readDriveResponse(c2, temp, name);
                                if (confirmedBody == null) return;
                            }
                        } finally { if (c2 != null) c2.disconnect(); }
                    }
                    String contentType = c.getContentType();
                    String finalUrl = c.getURL() == null ? address : c.getURL().toString();
                    String detail = summarizeDriveHtml(html);
                    String disposition = c.getHeaderField("Content-Disposition");
                    String firstBytes = describeBytes(body);
                    last = new Exception("Drive PDF দেয়নি; HTTP " + code + ", " + contentType +
                        ", disposition=" + disposition + ", URL=" + finalUrl +
                        (detail.isEmpty() ? "" : ", উত্তর: " + detail) +
                        (firstBytes.isEmpty() ? "" : ", শুরু: " + firstBytes));
                } catch (Exception e) { last = e; }
                finally { if (c != null) c.disconnect(); }
            }
            throw last == null ? new Exception("Google Drive থেকে PDF পাওয়া যায়নি") : last;
        }

        // Follow redirects manually so Google Drive confirmation cookies survive
        // every hop (HttpURLConnection's automatic redirects can drop this state).
        private HttpURLConnection openDriveConnection(String address, java.net.CookieManager cookieJar,
                String requestMethod, String formFields, String referer) throws Exception {
            URL current = new URL(address);
            String method = requestMethod == null ? "GET" : requestMethod;
            String body = formFields == null ? "" : formFields;
            for (int hop = 0; hop < 10; hop++) {
                HttpURLConnection connection = (HttpURLConnection) current.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(180000);
                connection.setRequestMethod(method);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/125.0 Mobile Safari/537.36");
                connection.setRequestProperty("Accept", "application/pdf,application/octet-stream,text/html,*/*");
                connection.setRequestProperty("Accept-Encoding", "identity");
                if (referer != null) connection.setRequestProperty("Referer", referer);
                java.net.URI uri = current.toURI();
                java.util.Map<String, java.util.List<String>> cookieHeaders = cookieJar.get(uri, java.util.Collections.<String, java.util.List<String>>emptyMap());
                for (java.util.Map.Entry<String, java.util.List<String>> entry : cookieHeaders.entrySet()) {
                    for (String value : entry.getValue()) connection.addRequestProperty(entry.getKey(), value);
                }
                if ("POST".equals(method)) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    byte[] payload = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(payload.length);
                    try (java.io.OutputStream output = connection.getOutputStream()) { output.write(payload); }
                }
                int status = connection.getResponseCode();
                cookieJar.put(uri, connection.getHeaderFields());
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    connection.disconnect();
                    if (location == null || location.trim().isEmpty()) throw new Exception("Drive redirect-এ গন্তব্য URL দেয়নি");
                    current = new URL(current, location);
                    if (status == 303 || ((status == 301 || status == 302) && "POST".equals(method))) {
                        method = "GET";
                        body = "";
                    }
                    continue;
                }
                return connection;
            }
            throw new Exception("Google Drive অতিরিক্ত redirect পাঠিয়েছে");
        }

        private String describeBytes(byte[] bytes) {
            if (bytes == null || bytes.length == 0) return "";
            StringBuilder result = new StringBuilder();
            int limit = Math.min(bytes.length, 24);
            for (int i = 0; i < limit; i++) {
                if (i > 0) result.append(' ');
                result.append(String.format(java.util.Locale.US, "%02X", bytes[i] & 0xff));
            }
            return result.toString();
        }

        private String readDriveError(HttpURLConnection connection) {
            try {
                InputStream error = connection.getErrorStream();
                if (error == null) return "সার্ভার ত্রুটির বিবরণ দেয়নি";
                try (InputStream in = error; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                    byte[] buffer = new byte[2048];
                    int n;
                    while ((n = in.read(buffer)) != -1 && out.size() < 8192) out.write(buffer, 0, n);
                    return summarizeDriveHtml(new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) { return "ত্রুটির বিবরণ পড়া যায়নি"; }
        }

        private String summarizeDriveHtml(String source) {
            if (source == null) return "";
            String value = source.replaceAll("(?is)<script.*?</script>|<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ").replace("&nbsp;", " ")
                .replace("&amp;", "&").replaceAll("\\s+", " ").trim();
            if (value.length() > 240) value = value.substring(0, 240);
            return value;
        }

        private String htmlAttribute(String tag, String attribute) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?is)\\b" + attribute + "\\s*=\\s*(['\"])(.*?)\\1").matcher(tag);
            return m.find() ? m.group(2).replace("&amp;", "&") : null;
        }

        private int indexOfPdfHeader(byte[] bytes, int length) {
            if (bytes == null) return -1;
            int limit = Math.min(length, bytes.length) - 5;
            for (int i = 0; i <= limit; i++) {
                if (bytes[i] == '%' && bytes[i + 1] == 'P' && bytes[i + 2] == 'D' &&
                    bytes[i + 3] == 'F' && bytes[i + 4] == '-') return i;
            }
            return -1;
        }

        private boolean isPdfBytes(byte[] bytes) {
            return bytes != null && indexOfPdfHeader(bytes, bytes.length) >= 0;
        }

        private void writePdfBytes(byte[] bytes, File temp, String name, long total) throws Exception {
            try (FileOutputStream out = new FileOutputStream(temp)) {
                notifyBookProgress(name, 0L, total > 0 ? total : bytes.length);
                out.write(bytes);
                notifyBookProgress(name, bytes.length, total > 0 ? total : bytes.length);
            }
            if (!isPdfFile(temp)) throw new Exception("ডাউনলোড করা ফাইলটি বৈধ PDF নয়");
        }

        private boolean hasPdfEof(File file) {
            if (file == null || !file.isFile() || file.length() < 1000) return false;
            try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(file, "r")) {
                int length = (int)Math.min(4096L, raf.length());
                byte[] tail = new byte[length];
                raf.seek(raf.length() - length);
                raf.readFully(tail);
                String ending = new String(tail, java.nio.charset.StandardCharsets.ISO_8859_1);
                return ending.contains("%%EOF");
            } catch (Exception ignored) { return false; }
        }

        private boolean isPdfFile(File file) {
            if (file == null || !file.isFile() || file.length() < 1000) return false;
            try (InputStream input = new java.io.FileInputStream(file)) {
                byte[] prefix = new byte[1024];
                int count = input.read(prefix);
                return count > 0 && indexOfPdfHeader(prefix, count) >= 0;
            } catch (Exception ignored) { return false; }
        }
    }

}
