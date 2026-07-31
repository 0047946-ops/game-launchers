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
            new Handler(Looper.getMainLooper());



    public AndroidBridge(
            Activity activity,
            WebView webView
    ){

        this.activity = activity;

        this.webView = webView;

    }





    /*
        啟動遊戲網址
    */

    @JavascriptInterface
    public void launchGame(
            String url
    ){

        handler.post(() -> {

            webView.loadUrl(url);

        });

    }





    /*
        外掛網址注入
    */

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
                    "🔌 外掛已載入",
                    Toast.LENGTH_SHORT
            ).show();


        });

    }





    /*
        存檔匯出入口

        支援:
        data:
        base64
        SIG1
        JSON
    */

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
                    "📥 存檔匯出完成",
                    Toast.LENGTH_SHORT
            ).show();


        });


    }





    /*
        取得 WebView LocalStorage

        用於人物資料同步
    */

    @JavascriptInterface
    public void getLocalStorage(){

        handler.post(() -> {


            webView.evaluateJavascript(

                    "(function(){"
                    +"return JSON.stringify(localStorage);"
                    +"})()",


                    value -> {


                        Toast.makeText(
                                activity,
                                "LocalStorage 已讀取",
                                Toast.LENGTH_SHORT
                        ).show();


                    }

            );


        });

    }





    /*
        寫入 LocalStorage

        匯入角色資料用
    */

    @JavascriptInterface
    public void setLocalStorage(
            String key,
            String value
    ){

        handler.post(() -> {


            String js =

                    "localStorage.setItem("
                    +"'"
                    + key.replace("'","\\'")
                    +"',"
                    +"'"
                    + value.replace("'","\\'")
                    +"'"
                    +");";



            webView.evaluateJavascript(
                    js,
                    null
            );


        });


    }





    /*
        訊息
    */

    @JavascriptInterface
    public void toast(
            String message
    ){

        handler.post(() -> {


            Toast.makeText(
                    activity,
                    message,
                    Toast.LENGTH_SHORT
            ).show();


        });


    }





    /*
        Log
    */

    @JavascriptInterface
    public void log(
            String message
    ){

        android.util.Log.d(
                "AndroidBridge",
                message
        );


    }


}
