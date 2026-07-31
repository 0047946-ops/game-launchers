package com.idle.lineage.launcher;

import android.webkit.WebView;

public class PluginRuntime {
    public static void injectPluginUrl(WebView webView, String url) {
        String js = "(function(){var s=document.createElement('script');s.src='" + url + "?v='+Date.now();document.body.appendChild(s);})();";
        webView.evaluateJavascript(js, null);
    }

    public static void executeBookmark(WebView webView, String bookmarkJs) {
        if (!bookmarkJs.toLowerCase().startsWith("javascript:")) {
            bookmarkJs = "javascript:" + bookmarkJs;
        }
        webView.evaluateJavascript(bookmarkJs, null);
    }
}
