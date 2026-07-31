package com.idle.lineage.launcher;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

public class AndroidBridge {
    private final Activity activity;
    private final WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public AndroidBridge(Activity activity, WebView webView) {
        this.activity = activity;
        this.webView = webView;
    }

    @JavascriptInterface
    public void launchGame(String url) {
        mainHandler.post(() -> webView.loadUrl(url));
    }

    @JavascriptInterface
    public void injectPluginUrl(String url) {
        mainHandler.post(() -> {
            PluginRuntime.injectPluginUrl(webView, url);
            Toast.makeText(activity, "🔗 外掛網址已注入", Toast.LENGTH_SHORT).show();
        });
    }

    @JavascriptInterface
    public void saveBase64File(String dataUrlOrBase64, String fileName) {
        mainHandler.post(() -> SaveManager.processAndSaveFile(activity, dataUrlOrBase64, "application/json", fileName));
    }
}
