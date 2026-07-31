package com.idle.lineage.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.DownloadManager;
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

        fileChooserManager = new FileChooserManager();
        webView = WebViewManager.createConfiguredWebView(this);
        setContentView(webView);

        webView.addJavascriptInterface(new AndroidBridge(this, webView), "AndroidBridge");

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            try {
                if (url.startsWith("blob:")) {
                    triggerBlobDownload(url);
                } else {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimetype);
                    request.addRequestHeader("User-Agent", userAgent);
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    String fileName = "fable5_save_" + System.currentTimeMillis() + ".json";
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    dm.enqueue(request);
                    Toast.makeText(this, "📥 已開始下載存檔", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(this, "❌ 下載失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return fileChooserManager.handleShowFileChooser(MainActivity.this, callback, params, LauncherConfig.FILE_CHOOSER_RESULT_CODE);
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                String contentToCheck = (defaultValue != null && !defaultValue.isEmpty()) ? defaultValue : message;
                if (contentToCheck != null && contentToCheck.contains("SIG1:")) {
                    SaveManager.processAndSaveFile(MainActivity.this, contentToCheck, "application/json", null);
                    result.confirm();
                    return true;
                }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                if (message != null && message.contains("SIG1:")) {
                    SaveManager.processAndSaveFile(MainActivity.this, message, "application/json", null);
                    result.confirm();
                    return true;
                }
                return super.onJsAlert(view, url, message, result);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("blob:") || url.startsWith("data:")) {
                    SaveManager.processAndSaveFile(MainActivity.this, url, "application/json", null);
                    return true;
                }
                return false;
            }
        });

        webView.loadUrl(LauncherConfig.DEFAULT_LAUNCHER_URL);
    }

    private void triggerBlobDownload(String blobUrl) {
        String js = "javascript:(function(){" +
                "var xhr=new XMLHttpRequest();" +
                "xhr.open('GET','" + blobUrl + "',true);" +
                "xhr.responseType='blob';" +
                "xhr.onload=function(){" +
                " var reader=new FileReader();" +
                " reader.onloadend=function(){" +
                " var base64=reader.result.split(',')[1];" +
                " AndroidBridge.saveBase64File(base64,'fable5_save_" + System.currentTimeMillis() + ".json');" +
                " };" +
                " reader.readAsDataURL(xhr.response);" +
                "};" +
                "xhr.send();" +
                "})()";
        webView.evaluateJavascript(js, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LauncherConfig.FILE_CHOOSER_RESULT_CODE) {
            fileChooserManager.handleActivityResult(resultCode, data);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
