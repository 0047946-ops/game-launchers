package com.idle.lineage.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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


    private final SharedPreferences prefs;



    public AndroidBridge(
            Activity activity,
            WebView webView
    ){

        this.activity = activity;

        this.webView = webView;


        prefs =
                activity.getSharedPreferences(
                        "launcher",
                        Context.MODE_PRIVATE
                );

    }




    /*
        啟動遊戲網址
    */

    @JavascriptInterface
    public void launchGame(String url){


        handler.post(() -> {


            webView.loadUrl(url);


        });


    }





    /*
        儲存外掛網址
    */

    @JavascriptInterface
    public void savePluginUrl(
            String url
    ){


        if(url == null ||
                url.trim().isEmpty()){


            return;

        }



        String old =
                prefs.getString(
                        "plugins",
                        ""
                );



        if(!old.contains(url)){


            String result;


            if(old.isEmpty()){


                result = url;


            }else{


                result =
                        old
                        + "\n"
                        + url;


            }



            prefs.edit()
                    .putString(
                            "plugins",
                            result
                    )
                    .apply();



        }



        handler.post(() -> Toast.makeText(
                activity,
                "✅ 外掛已保存",
                Toast.LENGTH_SHORT
        ).show());


    }





    /*
        提供給 MainActivity 讀取
    */

    @JavascriptInterface
    public String getPluginUrls(){


        return prefs.getString(
                "plugins",
                ""
        );


    }





    /*
        移除全部外掛
    */

    @JavascriptInterface
    public void clearPlugins(){


        prefs.edit()
                .remove("plugins")
                .apply();


    }





    /*
        存檔匯出
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


        });


    }





    /*
        日誌
    */

    @JavascriptInterface
    public void log(
            String msg
    ){


        android.util.Log.d(
                "AndroidBridge",
                msg
        );


    }





    /*
        Toast
    */

    @JavascriptInterface
    public void toast(
            String msg
    ){


        handler.post(() -> Toast.makeText(
                activity,
                msg,
                Toast.LENGTH_SHORT
        ).show());


    }


}
