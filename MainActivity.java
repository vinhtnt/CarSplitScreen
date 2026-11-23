package com.carsplit.dashboard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.URLEncoder;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_LOCATION = 101;
    private static final String DEFAULT_CITY = "Hải Phòng";

    private FrameLayout rootFrame;
    private LinearLayout leftPanel;
    private LinearLayout rightPanel;
    private View dividerView;

    private WebView leftWebView;
    private WebView rightWebView;

    private EditText leftSearchInput;
    private EditText rightSearchInput;
    private TextView leftModeBtn;
    private TextView darkMapBtn;
    private TextView leftExpandBtn;
    private TextView rightExpandBtn;

    // Fullscreen video container cho YouTube
    private View customVideoView;
    private WebChromeClient.CustomViewCallback customViewCallback;

    // Trang thai hien thi
    private boolean leftIsMapMode = false; // false = YouTube, true = Ban do phu (OpenStreetMap)
    private boolean isDarkMapEnabled = true;
    private boolean isSatelliteMap = false;
    private int expandState = 0; // 0 = Chia doi 50/50, 1 = Full trai, 2 = Full phai
    private String lastMapQuery = DEFAULT_CITY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Giu man hinh luon sang khi lai xe & che do toan man hinh gon gang
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        enableImmersiveMode();

        buildUi();
        requestLocationPermissionIfNeeded();
        loadInitialPages();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            enableImmersiveMode();
        }
    }

    private void enableImmersiveMode() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
    }

    private void buildUi() {
        rootFrame = new FrameLayout(this);
        rootFrame.setBackgroundColor(Color.parseColor("#0B0E14"));

        LinearLayout mainRow = new LinearLayout(this);
        mainRow.setOrientation(LinearLayout.HORIZONTAL);
        mainRow.setPadding(dp(8), dp(8), dp(8), dp(8));

        // ===== KHUNG BEN TRAI (YouTube / Ban do phu) =====
        leftPanel = createCardPanel();
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        leftPanel.setLayoutParams(leftParams);

        leftPanel.addView(buildLeftHeader());
        leftWebView = createConfiguredWebView(false);
        leftPanel.addView(leftWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Thanh phan cach o giua
        dividerView = new View(this);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                dp(8), ViewGroup.LayoutParams.MATCH_PARENT);
        dividerView.setLayoutParams(divParams);

        // ===== KHUNG BEN PHAI (Google Maps) =====
        rightPanel = createCardPanel();
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rightPanel.setLayoutParams(rightParams);

        rightPanel.addView(buildRightHeader());
        rightPanel.addView(buildMapQuickBar());
        rightWebView = createConfiguredWebView(true);
        rightPanel.addView(rightWebView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mainRow.addView(leftPanel);
        mainRow.addView(dividerView);
        mainRow.addView(rightPanel);

        rootFrame.addView(mainRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(rootFrame);
    }

    private LinearLayout createCardPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#141923"));
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.parseColor("#242C3D"));
        panel.setBackground(bg);
        panel.setClipToOutline(true);
        return panel;
    }

    private LinearLayout buildLeftHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setBackgroundColor(Color.parseColor("#141923"));

        // Nut chuyen doi nhanh giua YouTube va Ban do phu (Map 2)
        leftModeBtn = createBadgeButton("YT", "#FF2E4D");
        leftModeBtn.setOnClickListener(v -> toggleLeftMode());
        bar.addView(leftModeBtn);

        // O tim kiem / dan link
        leftSearchInput = createSearchBox("Tìm YouTube hoặc dán link...");
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        inputLp.setMargins(dp(6), 0, dp(4), 0);
        leftSearchInput.setLayoutParams(inputLp);
        leftSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                handleLeftSearch(leftSearchInput.getText().toString().trim());
                hideKeyboard(leftSearchInput);
                return true;
            }
            return false;
        });
        bar.addView(leftSearchInput);

        // Nut Quay lai
        TextView backBtn = createSmallBtn("<", "#1E2536");
        backBtn.setOnClickListener(v -> {
            if (leftWebView.canGoBack()) {
                leftWebView.goBack();
            }
        });
        bar.addView(backBtn);

        // Nut Trang chu
        TextView homeBtn = createSmallBtn("Home", "#1E2536");
        homeBtn.setOnClickListener(v -> loadLeftHome());
        bar.addView(homeBtn);

        // Nut Phong to / Chia doi
        leftExpandBtn = createSmallBtn("Full", "#1E2536");
        leftExpandBtn.setOnClickListener(v -> toggleExpand(1));
        bar.addView(leftExpandBtn);

        return bar;
    }

    private LinearLayout buildRightHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setBackgroundColor(Color.parseColor("#141923"));

        // Bieu tuong Maps xanh
        TextView mapBadge = createBadgeButton("MAP", "#10B981");
        mapBadge.setOnClickListener(v -> loadGoogleMapQuery(DEFAULT_CITY, false));
        bar.addView(mapBadge);

        // O tim kiem dia diem
        rightSearchInput = createSearchBox("Tìm điểm đến Google Maps...");
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(0, dp(36), 1f);
        inputLp.setMargins(dp(6), 0, dp(4), 0);
        rightSearchInput.setLayoutParams(inputLp);
        rightSearchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String q = rightSearchInput.getText().toString().trim();
                if (!q.isEmpty()) {
                    loadGoogleMapQuery(q, isSatelliteMap);
                }
                hideKeyboard(rightSearchInput);
                return true;
            }
            return false;
        });
        bar.addView(rightSearchInput);

        // Nut dinh vi GPS
        TextView gpsBtn = createSmallBtn("GPS", "#1E2536");
        gpsBtn.setOnClickListener(v -> centerOnCurrentGps());
        bar.addView(gpsBtn);

        // Nut bat/tat ban do nen toi
        darkMapBtn = createSmallBtn("Tối", "#283552");
        darkMapBtn.setOnClickListener(v -> {
            isDarkMapEnabled = !isDarkMapEnabled;
            darkMapBtn.setText(isDarkMapEnabled ? "Tối" : "Sáng");
            darkMapBtn.setBackground(makeRoundedBg(isDarkMapEnabled ? "#283552" : "#1E2536", 8));
            applyDarkModeToRightMap();
        });
        bar.addView(darkMapBtn);

        // Nut Phong to / Chia doi
        rightExpandBtn = createSmallBtn("Full", "#1E2536");
        rightExpandBtn.setOnClickListener(v -> toggleExpand(2));
        bar.addView(rightExpandBtn);

        return bar;
    }

    private HorizontalScrollView buildMapQuickBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setBackgroundColor(Color.parseColor("#141923"));
        scroll.setPadding(dp(8), 0, dp(8), dp(6));

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);

        chips.addView(createChip("Bản đồ", "#3B82F6", v -> {
            isSatelliteMap = false;
            loadGoogleMapQuery(lastMapQuery, false);
        }));
        chips.addView(createChip("Vệ tinh", "#1E2536", v -> {
            isSatelliteMap = true;
            loadGoogleMapQuery(lastMapQuery, true);
        }));
        chips.addView(createChip("Trạm xăng", "#1E2536", v -> searchNearby("trạm xăng")));
        chips.addView(createChip("Trạm sạc", "#1E2536", v -> searchNearby("trạm sạc ô tô")));
        chips.addView(createChip("Bãi đỗ", "#1E2536", v -> searchNearby("bãi đỗ xe")));
        chips.addView(createChip("Mở Maps gốc", "#065F46", v -> openNativeGoogleMaps(lastMapQuery)));

        scroll.addView(chips);
        return scroll;
    }

    private TextView createBadgeButton(String text, String colorHex) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(makeRoundedBg(colorHex, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(34));
        tv.setLayoutParams(lp);
        return tv;
    }

    private EditText createSearchBox(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Color.parseColor("#8A94A6"));
        et.setTextColor(Color.parseColor("#F3F4F6"));
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        et.setSingleLine(true);
        et.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        et.setPadding(dp(10), dp(4), dp(10), dp(4));
        et.setBackground(makeRoundedBg("#1E2536", 10));
        return et;
    }

    private TextView createSmallBtn(String label, String bgHex) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#E5E7EB"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(8), 0, dp(8), 0);
        tv.setMinWidth(dp(34));
        tv.setBackground(makeRoundedBg(bgHex, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
        lp.setMargins(dp(4), 0, 0, 0);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView createChip(String label, String bgHex, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#F3F4F6"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setPadding(dp(12), dp(5), dp(12), dp(5));
        tv.setBackground(makeRoundedBg(bgHex, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        tv.setLayoutParams(lp);
        tv.setOnClickListener(listener);
        return tv;
    }

    private GradientDrawable makeRoundedBg(String hex, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(hex));
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createConfiguredWebView(boolean isRightMap) {
        WebView wv = new WebView(this);
        wv.setBackgroundColor(Color.parseColor("#0F131C"));

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        String ua = s.getUserAgentString();
        if (ua != null) {
            s.setUserAgentString(ua.replace("; wv", ""));
        }

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();
                if (scheme == null) return false;

                if (!scheme.equals("http") && !scheme.equals("https")) {
                    if (isRightMap && (scheme.equals("geo") || uri.toString().contains("maps"))) {
                        openNativeGoogleMaps(lastMapQuery);
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (isRightMap) {
                    applyDarkModeToRightMap();
                } else if (!leftIsMapMode) {
                    view.evaluateJavascript(
                            "document.documentElement.setAttribute('dark', 'true');",
                            null
                    );
                }
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customVideoView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customVideoView = view;
                customViewCallback = callback;
                rootFrame.addView(customVideoView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                enableImmersiveMode();
            }

            @Override
            public void onHideCustomView() {
                if (customVideoView != null) {
                    rootFrame.removeView(customVideoView);
                    customVideoView = null;
                }
                if (customViewCallback != null) {
                    customViewCallback.onCustomViewHidden();
                    customViewCallback = null;
                }
                enableImmersiveMode();
            }
        });

        return wv;
    }

    private void loadInitialPages() {
        loadLeftHome();
        loadGoogleMapQuery(DEFAULT_CITY, false);
    }

    private void toggleLeftMode() {
        leftIsMapMode = !leftIsMapMode;
        if (leftIsMapMode) {
            leftModeBtn.setText("MAP");
            leftModeBtn.setBackground(makeRoundedBg("#3B82F6", 10));
            leftSearchInput.setHint("Tìm trên bản đồ phụ hoặc dán URL...");
            Toast.makeText(this, "Bên trái: Bản đồ phụ (OpenStreetMap)", Toast.LENGTH_SHORT).show();
        } else {
            leftModeBtn.setText("YT");
            leftModeBtn.setBackground(makeRoundedBg("#FF2E4D", 10));
            leftSearchInput.setHint("Tìm YouTube hoặc dán link...");
            Toast.makeText(this, "Bên trái: YouTube", Toast.LENGTH_SHORT).show();
        }
        loadLeftHome();
    }

    private void loadLeftHome() {
        if (leftIsMapMode) {
            leftWebView.loadUrl("https://www.openstreetmap.org/#map=13/20.8449/106.6881");
        } else {
            leftWebView.loadUrl("https://m.youtube.com");
        }
    }

    private void handleLeftSearch(String input) {
        if (input.isEmpty()) return;

        if (input.startsWith("http://") || input.startsWith("https://")) {
            leftWebView.loadUrl(input);
            return;
        }
        if (input.contains("youtu.be/") || input.contains("youtube.com/")) {
            leftWebView.loadUrl("https://" + input);
            return;
        }

        String encoded = urlEncode(input);
        if (leftIsMapMode) {
            leftWebView.loadUrl("https://www.openstreetmap.org/search?query=" + encoded);
        } else {
            leftWebView.loadUrl("https://m.youtube.com/results?search_query=" + encoded);
        }
    }

    private void loadGoogleMapQuery(String query, boolean satellite) {
        lastMapQuery = TextUtils.isEmpty(query) ? DEFAULT_CITY : query;
        String encoded = urlEncode(lastMapQuery);
        String mapType = satellite ? "k" : "m";
        String url = "https://maps.google.com/maps?q=" + encoded + "&hl=vi&t=" + mapType + "&z=14&output=embed";
        rightWebView.loadUrl(url);
    }

    private void searchNearby(String keyword) {
        Location loc = getLastKnownLocation();
        if (loc != null) {
            String q = keyword + " gần " + loc.getLatitude() + "," + loc.getLongitude();
            loadGoogleMapQuery(q, isSatelliteMap);
        } else {
            loadGoogleMapQuery(keyword + " gần đây", isSatelliteMap);
        }
    }

    private void centerOnCurrentGps() {
        Location loc = getLastKnownLocation();
        if (loc != null) {
            String coords = loc.getLatitude() + "," + loc.getLongitude();
            lastMapQuery = coords;
            String mapType = isSatelliteMap ? "k" : "m";
            String url = "https://maps.google.com/maps?q=" + coords + "&ll=" + coords
                    + "&hl=vi&t=" + mapType + "&z=16&output=embed";
            rightWebView.loadUrl(url);
            Toast.makeText(this, "Đã định vị GPS hiện tại", Toast.LENGTH_SHORT).show();
        } else {
            requestLocationPermissionIfNeeded();
            Toast.makeText(this, "Chưa có tín hiệu GPS, đang mở Google Maps...", Toast.LENGTH_SHORT).show();
            rightWebView.loadUrl("https://www.google.com/maps");
        }
    }

    private void applyDarkModeToRightMap() {
        boolean shouldInvert = isDarkMapEnabled && !isSatelliteMap;
        String css = shouldInvert
                ? "html { filter: invert(90%) hue-rotate(180deg) brightness(95%) contrast(92%) !important; }"
                : "html { filter: none !important; }";

        String js = "(function(){"
                + "var id='car-dark-map';"
                + "var el=document.getElementById(id);"
                + "if(!el){ el=document.createElement('style'); el.id=id; document.head.appendChild(el); }"
                + "el.innerHTML='" + css + "';"
                + "})();";
        rightWebView.evaluateJavascript(js, null);
    }

    private void openNativeGoogleMaps(String query) {
        try {
            String q = TextUtils.isEmpty(query) ? "" : urlEncode(query);
            Uri uri = Uri.parse("geo:0,0?q=" + q);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.google.android.apps.maps");
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Không tìm thấy ứng dụng Google Maps trên máy", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleExpand(int target) {
        if (expandState == target) {
            expandState = 0;
        } else {
            expandState = target;
        }

        if (expandState == 0) {
            leftPanel.setVisibility(View.VISIBLE);
            dividerView.setVisibility(View.VISIBLE);
            rightPanel.setVisibility(View.VISIBLE);
            leftExpandBtn.setText("Full");
            rightExpandBtn.setText("Full");
        } else if (expandState == 1) {
            leftPanel.setVisibility(View.VISIBLE);
            dividerView.setVisibility(View.GONE);
            rightPanel.setVisibility(View.GONE);
            leftExpandBtn.setText("50/50");
        } else {
            leftPanel.setVisibility(View.GONE);
            dividerView.setVisibility(View.GONE);
            rightPanel.setVisibility(View.VISIBLE);
            rightExpandBtn.setText("50/50");
        }
    }

    private void requestLocationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQ_LOCATION);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private Location getLastKnownLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            List<String> providers = lm.getProviders(true);
            Location best = null;
            for (String p : providers) {
                Location l = lm.getLastKnownLocation(p);
                if (l == null) continue;
                if (best == null || l.getAccuracy() < best.getAccuracy()) {
                    best = l;
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s.replace(" ", "+");
        }
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
        enableImmersiveMode();
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    @Override
    public void onBackPressed() {
        if (customVideoView != null) {
            if (customViewCallback != null) customViewCallback.onCustomViewHidden();
            rootFrame.removeView(customVideoView);
            customVideoView = null;
            return;
        }
        if (expandState != 0) {
            toggleExpand(0);
            return;
        }
        if (leftWebView != null && leftWebView.canGoBack()) {
            leftWebView.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (leftWebView != null) leftWebView.destroy();
        if (rightWebView != null) rightWebView.destroy();
        super.onDestroy();
    }
}
