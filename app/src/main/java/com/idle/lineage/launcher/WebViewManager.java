package com.idle.lineage.launcher;

import android.content.Context;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;

public class WebViewManager {

    public static WebView createConfiguredWebView(Context context) {

        WebView webView = new WebView(context);

        WebSettings settings = webView.getSettings();

        // JavaScript
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);

        // HTML5
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // 檔案
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // 快取
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 混合內容
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        // 圖片
        settings.setLoadsImagesAutomatically(true);

        // Viewport
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // 縮放
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        // Media
        settings.setMediaPlaybackRequiresUserGesture(false);

        // User-Agent
        String ua = settings.getUserAgentString();
        ua = ua.replace("; wv", "");
        ua = ua.replace("Version/4.0 ", "");
        settings.setUserAgentString(ua);

        // Cookie
        CookieManager cookie = CookieManager.getInstance();
        cookie.setAcceptCookie(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookie.setAcceptThirdPartyCookies(webView, true);
        }

        cookie.flush();

        // 硬體加速
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
        }

        // 初始化 WebView 資料庫
        WebViewDatabase.getInstance(context);

        return webView;
    }
}
