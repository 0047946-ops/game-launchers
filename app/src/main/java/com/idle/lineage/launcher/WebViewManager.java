package com.idle.lineage.launcher;

import android.content.Context;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;



public class WebViewManager {


    public static WebView createConfiguredWebView(
            Context context
    ){


        WebView webView =
                new WebView(context);



        WebSettings settings =
                webView.getSettings();




        // JavaScript

        settings.setJavaScriptEnabled(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);




        // HTML5 儲存

        settings.setDomStorageEnabled(true);

        settings.setDatabaseEnabled(true);




        // Cache

        settings.setCacheMode(
                WebSettings.LOAD_DEFAULT
        );




        // 檔案權限

        settings.setAllowFileAccess(true);

        settings.setAllowContentAccess(true);

        settings.setAllowFileAccessFromFileURLs(true);

        settings.setAllowUniversalAccessFromFileURLs(true);




        // 混合內容

        if(Build.VERSION.SDK_INT
                >=
           Build.VERSION_CODES.LOLLIPOP){


            settings.setMixedContentMode(
                    WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            );


        }




        // 顯示

        settings.setLoadsImagesAutomatically(true);

        settings.setUseWideViewPort(true);

        settings.setLoadWithOverviewMode(true);




        // 遊戲音效

        settings.setMediaPlaybackRequiresUserGesture(false);




        // User Agent 偽裝 Chrome

        String ua =
                settings.getUserAgentString();


        ua =
                ua.replace(
                        "; wv",
                        ""
                );


        ua =
                ua.replace(
                        "Version/4.0 ",
                        ""
                );


        settings.setUserAgentString(ua);






        // Cookie

        CookieManager cookie =
                CookieManager.getInstance();



        cookie.setAcceptCookie(true);



        if(Build.VERSION.SDK_INT
                >=
           Build.VERSION_CODES.LOLLIPOP){


            cookie.setAcceptThirdPartyCookies(
                    webView,
                    true
            );


        }



        cookie.flush();






        // WebStorage 初始化

        WebStorage
                .getInstance();






        // 硬體加速

        if(Build.VERSION.SDK_INT
                >=
           Build.VERSION_CODES.KITKAT){


            webView.setLayerType(
                    WebView.LAYER_TYPE_HARDWARE,
                    null
            );


        }



        return webView;


    }


}
