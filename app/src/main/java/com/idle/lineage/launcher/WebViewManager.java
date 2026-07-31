package com.idle.lineage.launcher;

import android.content.Context;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class WebViewManager {
    public static WebView createConfiguredWebView(Context context) {
        WebView webView = new WebView(context);
        WebSettings settings = webView.getSettings();

        // 1. 基礎互動與渲染效能
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // 2. 檔案與跨網域存取
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);

        // 3. 網路與安全性相容性（允許混合內容）
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 4. 【核心關鍵】自訂 User-Agent，去除 Android WebView 特徵，
        // 讓遊戲伺服器將其識別為標準 Chrome 瀏覽器，解決人物資料空白與登入限制！
        String originUserAgent = settings.getUserAgentString();
        String cleanedUserAgent = originUserAgent.replaceAll("; wv", "").replace("Version/4.0 ", "");
        settings.setUserAgentString(cleanedUserAgent);

        // 5. 啟用硬體加速以提升遊戲畫面流暢度
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null);
        }

        // 6. Cookie 完整授權
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        return webView;
    }
}
