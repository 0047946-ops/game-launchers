package com.idle.lineage.launcher;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;


public class MainActivity extends Activity {


    private WebView webView;

    private FileChooserManager fileChooserManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);


        PermissionManager.checkAllFilesAccessPermission(this);


        fileChooserManager =
                new FileChooserManager();


        webView =
                WebViewManager.createConfiguredWebView(this);


        setContentView(webView);



        // Android 與 JS 橋接

        webView.addJavascriptInterface(
                new AndroidBridge(
                        this,
                        webView
                ),
                "AndroidBridge"
        );



        initDownloadListener();


        initWebChromeClient();


        initWebViewClient();



        // 啟動器入口

        webView.loadUrl(
                LauncherConfig.DEFAULT_LAUNCHER_URL
        );

    }
    private void initDownloadListener(){


        webView.setDownloadListener(
                (url, userAgent, contentDisposition, mimetype, contentLength) -> {


                    try{


                        if(url.startsWith("blob:")){


                            triggerBlobDownload(url);


                        }else{


                            DownloadManager.Request request =
                                    new DownloadManager.Request(
                                            Uri.parse(url)
                                    );


                            request.setMimeType(
                                    mimetype
                            );


                            request.addRequestHeader(
                                    "User-Agent",
                                    userAgent
                            );


                            request.setNotificationVisibility(
                                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                            );


                            request.setDestinationInExternalPublicDir(
                                    Environment.DIRECTORY_DOWNLOADS,
                                    "idle_lineage_save_"
                                            + System.currentTimeMillis()
                                            + ".json"
                            );


                            DownloadManager manager =
                                    (DownloadManager)
                                    getSystemService(
                                            Context.DOWNLOAD_SERVICE
                                    );


                            manager.enqueue(request);



                            Toast.makeText(
                                    this,
                                    "📥 存檔下載開始",
                                    Toast.LENGTH_SHORT
                            ).show();


                        }


                    }catch(Exception e){


                        Toast.makeText(
                                this,
                                "❌ 下載失敗："+e.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();


                    }


                }
        );


    }





    private void initWebChromeClient(){


        webView.setWebChromeClient(
                new WebChromeClient(){



                    @Override
                    public boolean onShowFileChooser(
                            WebView view,
                            ValueCallback<Uri[]> callback,
                            FileChooserParams params
                    ){


                        return fileChooserManager.handleShowFileChooser(
                                MainActivity.this,
                                callback,
                                params,
                                LauncherConfig.FILE_CHOOSER_RESULT_CODE
                        );


                    }





                    @Override
                    public boolean onJsPrompt(
                            WebView view,
                            String url,
                            String message,
                            String defaultValue,
                            JsPromptResult result
                    ){


                        String data =
                                defaultValue != null
                                && !defaultValue.isEmpty()
                                ?
                                defaultValue
                                :
                                message;



                        if(data != null
                                && data.contains("SIG1:")){


                            SaveManager.processAndSaveFile(
                                    MainActivity.this,
                                    data,
                                    "application/json",
                                    null
                            );


                            result.confirm();


                            return true;


                        }


                        return super.onJsPrompt(
                                view,
                                url,
                                message,
                                defaultValue,
                                result
                        );


                    }




                    @Override
                    public boolean onJsAlert(
                            WebView view,
                            String url,
                            String message,
                            JsResult result
                    ){


                        if(message != null
                                && message.contains("SIG1:")){


                            SaveManager.processAndSaveFile(
                                    MainActivity.this,
                                    message,
                                    "application/json",
                                    null
                            );


                            result.confirm();


                            return true;


                        }


                        return super.onJsAlert(
                                view,
                                url,
                                message,
                                result
                        );


                    }



                }
        );


    }
    private void initWebViewClient(){


        webView.setWebViewClient(
                new WebViewClient(){



                    @Override
                    public boolean shouldOverrideUrlLoading(
                            WebView view,
                            WebResourceRequest request
                    ){


                        String url =
                                request.getUrl()
                                .toString();



                        if(url.startsWith("blob:")){


                            triggerBlobDownload(url);


                            return true;


                        }




                        if(url.startsWith("data:")){


                            SaveManager.processAndSaveFile(
                                    MainActivity.this,
                                    url,
                                    "application/json",
                                    null
                            );


                            return true;


                        }



                        return false;


                    }





                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ){


                        super.onPageFinished(
                                view,
                                url
                        );



                        // 啟動器不注入

                        if(url.equals(
                                LauncherConfig.DEFAULT_LAUNCHER_URL
                        )){


                            return;


                        }





                        String js =


                                "javascript:(function(){"

                                + "if(window.myLineagePluginInjected)return;"

                                + "window.myLineagePluginInjected=true;"



                                + "fetch('https://raw.githubusercontent.com/qcc781192000/idle-lineage-plugin/main/main.user.js')"



                                + ".then(function(r){return r.text();})"



                                + ".then(function(code){"



                                + "var clean="
                                + "code.replace(/\\/\\/\\s*==UserScript==[\\s\\S]*?\\/\\/\\s*==\\/UserScript==/g,'');"



                                + "var s=document.createElement('script');"



                                + "s.textContent=clean;"



                                + "document.documentElement.appendChild(s);"



                                + "console.log('Plugin Loaded');"



                                + "})"



                                + ".catch(function(e){"

                                + "console.log('Plugin Error',e);"

                                + "});"



                                + "})();";



                        view.evaluateJavascript(
                                js,
                                null
                        );


                    }



                }
        );


    }
    private void triggerBlobDownload(String blobUrl){


        String js =


                "javascript:(function(){"

                + "var xhr=new XMLHttpRequest();"

                + "xhr.open('GET','"
                + blobUrl
                + "',true);"



                + "xhr.responseType='blob';"



                + "xhr.onload=function(){"



                + "var reader=new FileReader();"



                + "reader.onloadend=function(){"



                + "AndroidBridge.saveBase64File("

                + "reader.result,"

                + "'idle_lineage_save.json'"

                + ");"



                + "};"



                + "reader.readAsDataURL(xhr.response);"



                + "};"



                + "xhr.send();"



                + "})()";



        webView.evaluateJavascript(
                js,
                null
        );


    }





    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ){


        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );



        if(requestCode ==
                LauncherConfig.FILE_CHOOSER_RESULT_CODE){


            fileChooserManager.handleActivityResult(
                    resultCode,
                    data
            );


        }


    }





    @Override
    public void onBackPressed(){


        if(webView != null
                && webView.canGoBack()){


            webView.goBack();


        }else{


            super.onBackPressed();


        }


    }



}
