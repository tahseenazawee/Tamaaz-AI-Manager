package com.tamaaz.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int REQ_LOCATION = 1001;
    private static final int REQ_AUDIO = 1002;
    private WebView webView;
    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;
    private String pendingExternalType;
    private String pendingExternalPayload;
    private String pendingExternalLabel;
    private boolean pageReady = false;

    private static final Pattern COORDS = Pattern.compile("(-?\\d{1,3}\\.\\d+)\\s*[, ]\\s*(-?\\d{1,3}\\.\\d+)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");

    @SuppressLint({"SetJavaScriptEnabled", "AllowFileAccessFromFileURLs"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setOffscreenPreRaster(true);
        s.setUserAgentString(s.getUserAgentString() + " TAMAAZAndroid/1.2");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageReady = true;
                dispatchPendingExternal();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                geoOrigin = origin;
                geoCallback = callback;
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                            REQ_LOCATION);
                }
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> {
                    if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.getResources());
                    } else {
                        ActivityCompat.requestPermissions(MainActivity.this,
                                new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
                        request.deny();
                    }
                });
            }
        });

        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        handleIncomingIntent(getIntent());
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(intent.getType())) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null && !text.trim().isEmpty()) {
                Matcher url = URL_PATTERN.matcher(text);
                if (url.find()) {
                    processLocationText(url.group());
                } else {
                    processLocationText(text);
                }
            }
            return;
        }

        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            processLocationText(intent.getData().toString());
        }
    }

    private void processLocationText(String raw) {
        if (raw == null) return;
        final String value = raw.trim();

        if (value.startsWith("https://maps.app.goo.gl/") || value.startsWith("https://goo.gl/maps/")) {
            new Thread(() -> {
                String resolved = resolveRedirect(value);
                runOnUiThread(() -> parseResolvedLocation(resolved != null ? resolved : value));
            }).start();
        } else {
            parseResolvedLocation(value);
        }
    }

    private String resolveRedirect(String input) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(input);
            for (int i = 0; i < 6; i++) {
                conn = (HttpURLConnection) url.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 TAMAAZ");
                int code = conn.getResponseCode();
                String location = conn.getHeaderField("Location");
                conn.disconnect();
                conn = null;
                if (code >= 300 && code < 400 && location != null) {
                    url = new URL(url, location);
                } else {
                    return url.toString();
                }
            }
            return url.toString();
        } catch (Exception e) {
            if (conn != null) conn.disconnect();
            return input;
        }
    }

    private void parseResolvedLocation(String raw) {
        try {
            Uri uri = Uri.parse(raw);
            String scheme = uri.getScheme();

            if ("geo".equalsIgnoreCase(scheme)) {
                String ssp = uri.getSchemeSpecificPart();
                Matcher direct = COORDS.matcher(ssp);
                if (direct.find() && !(direct.group(1).equals("0") && direct.group(2).equals("0"))) {
                    sendCoordinates(direct.group(1), direct.group(2), "موقع من واتساب");
                    return;
                }
                String q = uri.getQueryParameter("q");
                if (q != null) {
                    Matcher qm = COORDS.matcher(q);
                    if (qm.find()) {
                        sendCoordinates(qm.group(1), qm.group(2), "موقع من واتساب");
                    } else {
                        sendSearch(q);
                    }
                    return;
                }
            }

            if ("google.navigation".equalsIgnoreCase(scheme)) {
                String q = uri.getQueryParameter("q");
                if (q != null) {
                    Matcher qm = COORDS.matcher(q);
                    if (qm.find()) sendCoordinates(qm.group(1), qm.group(2), "وجهة من واتساب");
                    else sendSearch(q);
                    return;
                }
            }

            Matcher anyCoords = COORDS.matcher(raw);
            if (anyCoords.find()) {
                double a = Double.parseDouble(anyCoords.group(1));
                double b = Double.parseDouble(anyCoords.group(2));
                if (Math.abs(a) <= 90 && Math.abs(b) <= 180) {
                    sendCoordinates(anyCoords.group(1), anyCoords.group(2), "موقع من واتساب");
                    return;
                }
            }

            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                String q = uri.getQueryParameter("q");
                if (q == null) q = uri.getQueryParameter("query");
                if (q == null) q = uri.getQueryParameter("destination");
                if (q != null && !q.trim().isEmpty()) {
                    Matcher qm = COORDS.matcher(q);
                    if (qm.find()) sendCoordinates(qm.group(1), qm.group(2), "موقع من واتساب");
                    else sendSearch(q);
                    return;
                }

                String path = uri.getPath();
                if (path != null && path.contains("/place/")) {
                    String place = path.substring(path.indexOf("/place/") + 7).split("/")[0];
                    place = URLDecoder.decode(place.replace("+", " "), StandardCharsets.UTF_8.name());
                    if (!place.isEmpty()) {
                        sendSearch(place);
                        return;
                    }
                }
            }

            if (!raw.startsWith("http")) sendSearch(raw);
        } catch (Exception ignored) {}
    }

    private void sendCoordinates(String lat, String lon, String label) {
        pendingExternalType = "coords";
        pendingExternalPayload = lat + "," + lon;
        pendingExternalLabel = label;
        dispatchPendingExternal();
    }

    private void sendSearch(String query) {
        pendingExternalType = "search";
        pendingExternalPayload = query;
        pendingExternalLabel = "وجهة من واتساب";
        dispatchPendingExternal();
    }

    private void dispatchPendingExternal() {
        if (!pageReady || webView == null || pendingExternalType == null || pendingExternalPayload == null) return;
        String js = "window.acceptExternalLocation(" +
                JSONObject.quote(pendingExternalType) + "," +
                JSONObject.quote(pendingExternalPayload) + "," +
                JSONObject.quote(pendingExternalLabel == null ? "" : pendingExternalLabel) + ");";
        webView.evaluateJavascript(js, null);
        pendingExternalType = null;
        pendingExternalPayload = null;
        pendingExternalLabel = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && geoCallback != null) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            geoCallback.invoke(geoOrigin, granted, false);
            geoCallback = null;
            geoOrigin = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
