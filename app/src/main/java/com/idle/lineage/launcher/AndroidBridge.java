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


    private final Handler handler =
            new Handler(
                    Looper.getMainLooper()
            );



    public AndroidBridge(
            Activity activity,
            WebView webView
    ){

        this.activity = activity;

        this.webView = webView;

    }




    // 啟動遊戲網址

    @JavascriptInterface
    public void launchGame(
            String url
    ){


        handler.post(() -> {


            webView.loadUrl(url);


        });


    }





    // 外掛網址注入

    @JavascriptInterface
    public void injectPluginUrl(
            String url
    ){


        handler.post(() -> {


            PluginRuntime.injectPluginUrl(
                    webView,
                    url
            );


            Toast.makeText(
                    activity,
                    "🔌 外掛載入完成",
                    Toast.LENGTH_SHORT
            ).show();


        });


    }






    // 核心：匯出存檔

    @JavascriptInterface
    public void saveBase64File(
            String data,
            String fileName
    ){


        handler.post(() -> {


            SaveManager.processAndSaveFile(
                    activity,
                    data,
                    "application/json",
                    fileName
            );


            Toast.makeText(
                    activity,
                    "✅ 存檔匯出完成",
                    Toast.LENGTH_SHORT
            ).show();



        });


    }





    // 測試用

    @JavascriptInterface
    public void toast(
            String msg
    ){


        handler.post(() -> {


            Toast.makeText(
                    activity,
                    msg,
                    Toast.LENGTH_SHORT
            ).show();


        });


    }





    @JavascriptInterface
    public void log(
            String msg
    ){


        android.util.Log.d(
                "AndroidBridge",
                msg
        );


    }


}
