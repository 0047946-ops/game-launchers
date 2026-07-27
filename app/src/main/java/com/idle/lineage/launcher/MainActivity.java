package com.idle.lineage.launcher;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Keep;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GameLauncher";
    private static final String SAVE_NAME_PREFIX = "";

    /** 目前是否注入外掛（由啟動頁選擇決定） */
    private boolean injectPluginsEnabled = true;

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<Intent> createDocumentLauncher;

    private byte[] pendingSaveBytes = null;
    private String pendingSaveFileName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkAllFilesAccessPermission();
        initFileChooserLauncher();
        initCreateDocumentLauncher();

        webView = new WebView(this);
        setContentView(webView);
        setupWebView();
        loadLauncherPage();
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidDownloader");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "[JS] " + consoleMessage.message());
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                try {
                    fileChooserLauncher.launch(fileChooserParams.createIntent());
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    Toast.makeText(MainActivity.this, "無法開啟檔案選擇器", Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            }

            /** 攔截 prompt 裡的 SIG1 存檔訊號 */
            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                String content = (defaultValue != null && !defaultValue.isEmpty()) ? defaultValue : message;
                if (content != null && content.contains("SIG1:")) {
                    processAndSaveFile(content, "application/json", null);
                    result.confirm();
                    return true;
                }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }

            /** 攔截 alert 裡的 SIG1 存檔訊號 */
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                if (message != null && message.contains("SIG1:")) {
                    processAndSaveFile(message, "application/json", null);
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
                if (url.startsWith("data:")) {
                    processAndSaveFile(url, "application/json", null);
                    return true;
                }
                if (url.startsWith("blob:")) {
                    triggerBlobDownload(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (url == null) return;

                // 所有頁面都注入：存檔修復 + 存檔攔截
                injectFixImport(view);
                injectSaveHook(view);

                // 只有真正的遊戲頁才注入外掛 / 防斷線
                if (url.startsWith("http") && !url.contains("android_asset")) {
                    if (injectPluginsEnabled) {
                        injectPlugins(view);
                        injectTMEngine(view);
                    } else {
                        Log.d(TAG, "純淨模式：不注入外掛");
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Toast.makeText(MainActivity.this,
                        "⚠️ 連線失敗 (" + errorCode + ")，請確認網路", Toast.LENGTH_LONG).show();
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url.startsWith("blob:")) {
                triggerBlobDownload(url);
                return;
            }
            String suggested = null;
            try {
                suggested = URLUtil.guessFileName(url, contentDisposition, mimetype);
            } catch (Exception ignored) {}
            final String hint = suggested;
            runOnUiThread(() -> processAndSaveFile(url,
                    mimetype != null ? mimetype : "application/json", hint));
        });
    }

    /* ==================== 啟動器頁面（含純淨版） ==================== */

    private void loadLauncherPage() {
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "body{background:#121212;color:#fff;font-family:sans-serif;padding:20px;" +
                "display:flex;justify-content:center;align-items:center;min-height:90vh;margin:0;}" +
                ".card{background:#1e1e1e;border-radius:16px;padding:24px;width:100%;max-width:380px;text-align:center;}" +
                "h2{font-size:20px;margin-bottom:8px;}" +
                ".subtitle{color:#8e8e93;font-size:13px;margin-bottom:24px;}" +
                "select{width:100%;padding:12px;background:#2c2c2e;color:#fff;border:1px solid #3a3a3c;" +
                "border-radius:8px;font-size:15px;margin-bottom:16px;}" +
                ".btn{width:100%;padding:14px;background:#28a745;color:#fff;border:none;" +
                "border-radius:8px;font-size:16px;font-weight:bold;margin-top:8px;}" +
                ".btn-pure{background:#6c757d;}" +
                ".hint{color:#aaa;font-size:12px;margin-top:12px;line-height:1.4;}" +
                "</style></head><body>" +
                "<div class='card'>" +
                "<h2>放置天堂啟動器</h2>" +
                "<div class='subtitle'>多伺服器 + 可選外掛注入</div>" +
                "<select id='serverSelect'>" +
                "<option value='https://pp771007.github.io/idle-lineage-class/'>伺服器一（加掛版）</option>" +
                "<option value='https://shines871.github.io/idle-lineage-class/'>伺服器二（原版）</option>" +
                "</select>" +
                "<button class='btn' onclick='start(true)'>🚀 啟動（含外掛）</button>" +
                "<button class='btn btn-pure' onclick='start(false)'>純淨啟動（無外掛）</button>" +
                "<div class='hint'>純淨模式只開遊戲本體，不注入任何外掛與防斷線引擎。<br>存檔匯出／匯入功能兩種模式都可用。</div>" +
                "</div>" +
                "<script>" +
                "function start(withPlugin){" +
                "  var url=document.getElementById('serverSelect').value;" +
                "  if(window.AndroidBridge && AndroidBridge.setPluginMode){" +
                "    AndroidBridge.setPluginMode(withPlugin);" +
                "  }" +
                "  location.href=url;" +
                "}" +
                "</script></body></html>";
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    }

    /* ==================== 外掛注入 ==================== */

    private void injectPlugins(WebView view) {
        String js = "(function(){" +
                "if(window.__all_plugins_loaded)return;" +
                "window.__all_plugins_loaded=true;" +
                "var s0=document.createElement('script');" +
                "s0.src='https://cdn.jsdelivr.net/gh/qcc781192000/idle-lineage-plugin@main/main.user.js?v='+Date.now();" +
                "document.body.appendChild(s0);" +
                "const b='https://kid0924.github.io/idle-lineage-class/';" +
                "const t=window.location.hostname.includes('pp771007');" +
                "const c=['klh_initial.js','klh_GMShop.js','klh_mobile-perf.js','klh_perf-monitor.js'," +
                "'klh_Backpack.js','klh_pk.js','klh_Pandora.js'].map(x=>b+x);" +
                "const n=t?[...[b+'klh_remove-banner.js'],...c]:" +
                "[...['https://pp771007.github.io/idle-lineage-class/afk-lzcache.js'," +
                "'https://pp771007.github.io/idle-lineage-class/afk-offline.js'],...c];" +
                "function load(src){return new Promise((res,rej)=>{" +
                "var o=document.createElement('script');o.src=src+'?v='+Date.now();" +
                "o.onload=res;o.onerror=()=>rej(src);document.body.appendChild(o);});}" +
                "n.reduce((p,src)=>p.then(()=>load(src)),Promise.resolve())" +
                ".then(()=>console.log('[Launcher] 外掛注入完成'))" +
                ".catch(e=>console.error('[Launcher] 外掛載入失敗',e));" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    /** TMEngine 防斷線引擎（完整版，從舊專案移植） */
    private void injectTMEngine(WebView view) {
        String tmEngineJs = "(function(){" +
                "'use strict';" +
                "if(window.__tm_engine_loaded)return;" +
                "window.__tm_engine_loaded=true;" +
                "console.log('[TMEngine] 啟動');" +

                "const PerformanceCore={" +
                "initTuning:()=>{" +
                "if(typeof window.requestIdleCallback!=='undefined'){" +
                "window.requestIdleCallback(()=>{if(window.gc)window.gc();},{timeout:500});" +
                "}}," +
                "getJitter:(base,variance)=>base+Math.floor(Math.random()*variance)" +
                "};" +
                "PerformanceCore.initTuning();" +

                "const originalSetInterval=window.setInterval;" +
                "window.setInterval=function(callback,delay,...args){" +
                "const optimizedDelay=delay<150?150:delay;" +
                "return originalSetInterval(callback,optimizedDelay,...args);" +
                "};" +

                "const NetworkOptimizer={" +
                "_isMobile:false," +
                "detectEnvironment:async()=>{" +
                "const conn=navigator.connection||{};" +
                "NetworkOptimizer._isMobile=conn.type==='cellular'||/Android|webOS|iPhone|iPad/i.test(navigator.userAgent);" +
                "try{const start=Date.now();" +
                "await fetch(window.location.href,{method:'HEAD',cache:'no-cache'});" +
                "if(Date.now()-start>150)NetworkOptimizer._isMobile=true;}catch(e){}" +
                "}," +
                "getJitterParams:()=>NetworkOptimizer._isMobile?{base:500,variance:700}:{base:120,variance:250}" +
                "};" +

                "const DOMWatcher={" +
                "waitForEl:(selector,success)=>{" +
                "const el=document.querySelector(selector);" +
                "if(el){success(el);return;}" +
                "const obs=new MutationObserver((m,o)=>{" +
                "const t=document.querySelector(selector);if(t){o.disconnect();success(t);}});" +
                "if(document.body)obs.observe(document.body,{childList:true,subtree:true});" +
                "else document.addEventListener('DOMContentLoaded',()=>obs.observe(document.body,{childList:true,subtree:true}));" +
                "}};" +

                "window.executeLogic=function(){" +
                "const hpText=document.querySelector('.hp-text')?.innerText;" +
                "if(hpText){const [cur,max]=hpText.split('/').map(Number);" +
                "if(cur/max<0.75){const potionBtn=document.querySelector('#btn-use-potion')||document.querySelector('.potion-btn');" +
                "if(potionBtn)potionBtn.click();}}" +
                "const attackBtn=document.querySelector('.attack-btn');" +
                "if(attackBtn&&!attackBtn.classList.contains('cooldown')){" +
                "const {base,variance}=NetworkOptimizer.getJitterParams();" +
                "setTimeout(()=>attackBtn.click(),PerformanceCore.getJitter(base,variance));}" +
                "};" +

                "const PageVisibilityModule={init:()=>{" +
                "document.addEventListener('visibilitychange',()=>{" +
                "if(!document.hidden&&typeof window.executeLogic==='function')window.executeLogic();});}};" +

                "const HeartbeatModule={sendKeepAliveSignal:()=>{" +
                "if(window.socket&&window.socket.readyState===WebSocket.OPEN){" +
                "window.socket.send(JSON.stringify({type:'heartbeat',timestamp:Date.now()}));" +
                "}else{fetch(window.location.href,{method:'HEAD',cache:'no-cache',keepalive:true}).catch(()=>{});}}};" +

                "const WebWorkerModule={init:()=>{" +
                "if(!window.Worker)return;" +
                "const workerCode=`let intervalId=null;self.onmessage=function(e){" +
                "if(e.data==='start'){if(intervalId)clearInterval(intervalId);" +
                "intervalId=setInterval(()=>self.postMessage('ping'),1000);}" +
                "else if(e.data==='stop'){if(intervalId)clearInterval(intervalId);}};`;" +
                "const blob=new Blob([workerCode],{type:'application/javascript'});" +
                "const worker=new Worker(URL.createObjectURL(blob));" +
                "worker.postMessage('start');" +
                "worker.onmessage=function(e){if(e.data==='ping')HeartbeatModule.sendKeepAliveSignal();};}};" +

                "const AudioKeepAliveModule={silentAudioCtx:null,init:()=>{" +
                "try{const AC=window.AudioContext||window.webkitAudioContext;if(!AC)return;" +
                "AudioKeepAliveModule.silentAudioCtx=new AC();" +
                "const osc=AudioKeepAliveModule.silentAudioCtx.createOscillator();" +
                "const gain=AudioKeepAliveModule.silentAudioCtx.createGain();gain.gain.value=0.0001;" +
                "osc.connect(gain);gain.connect(AudioKeepAliveModule.silentAudioCtx.destination);osc.start();" +
                "document.addEventListener('visibilitychange',()=>{" +
                "if(AudioKeepAliveModule.silentAudioCtx&&AudioKeepAliveModule.silentAudioCtx.state==='suspended')" +
                "AudioKeepAliveModule.silentAudioCtx.resume();});}catch(e){}}};" +

                "const initSystem=async()=>{" +
                "await NetworkOptimizer.detectEnvironment();" +
                "PageVisibilityModule.init();WebWorkerModule.init();AudioKeepAliveModule.init();" +
                "const div=document.createElement('div');" +
                "div.style='position:fixed;top:10px;left:10px;background:rgba(0,0,0,0.85);color:#0f0;" +
                "padding:8px 10px;z-index:2147483647;border-radius:8px;font-size:11px;border:1px solid #0f0;pointer-events:none;';" +
                "div.innerHTML='【TMEngine】防斷線運行中 · '+(NetworkOptimizer._isMobile?'手機模式':'WIFI模式');" +
                "const attach=()=>{if(document.body)document.body.appendChild(div);else setTimeout(attach,100);};" +
                "attach();" +
                "DOMWatcher.waitForEl('.attack-btn',()=>{setInterval(window.executeLogic,250);});" +
                "};" +
                "initSystem();" +
                "})();";
        view.evaluateJavascript(tmEngineJs, null);
    }

    /** 存檔匯入相容修復 */
    private void injectFixImport(WebView view) {
        String js = "(function(){" +
                "if(window.__fix_import_active)return;" +
                "window.__fix_import_active=true;" +
                "var orig=FileReader.prototype.readAsText;" +
                "FileReader.prototype.readAsText=function(file,enc){" +
                "var self=this,origOnload=self.onload;" +
                "self.onload=function(e){" +
                "try{var t=e.target.result;var p=JSON.parse(t);" +
                "if(p&&p.data)t=typeof p.data==='string'?p.data:JSON.stringify(p.data);" +
                "if(p&&p.save)t=typeof p.save==='string'?p.save:JSON.stringify(p.save);" +
                "Object.defineProperty(e.target,'result',{value:t,writable:true});}catch(err){}" +
                "if(origOnload)origOnload.call(self,e);};" +
                "return orig.apply(this,arguments);};" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    /** 存檔攔截 Hook（攔截 Blob / createObjectURL） */
    private void injectSaveHook(WebView view) {
        String js = "(function(){" +
                "if(window.__IDLE_SAVE_HOOK_LOADED__)return;" +
                "window.__IDLE_SAVE_HOOK_LOADED__=true;" +
                "console.log('[SaveHook] 已注入');" +
                "var origCreate=URL.createObjectURL;" +
                "URL.createObjectURL=function(blob){" +
                "var url=origCreate.apply(this,arguments);" +
                "if(blob&&(blob.type.indexOf('json')>=0||blob.type.indexOf('text')>=0||blob.type.indexOf('octet-stream')>=0)){" +
                "var reader=new FileReader();" +
                "reader.onloadend=function(){" +
                "if(window.AndroidBridge&&AndroidBridge.saveBase64File){" +
                "AndroidBridge.saveBase64File(reader.result,'application/json','idle_save.json');" +
                "}};" +
                "reader.readAsDataURL(blob);" +
                "}" +
                "return url;" +
                "};" +
                "window.__dumpAllLocalStorage=function(){" +
                "var dump={};for(var i=0;i<localStorage.length;i++){" +
                "var k=localStorage.key(i);dump[k]=localStorage.getItem(k);}" +
                "return JSON.stringify(dump);};" +
                "window.__markExported=function(){console.log('[SaveHook] 已標記匯出完成');};" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    /* ==================== 存檔核心 ==================== */

    private void processAndSaveFile(String data, String mimeType, String fileName) {
        if (data == null || data.isEmpty() || data.startsWith("blob:")) return;

        try {
            byte[] bytes;
            if (data.contains("SIG1:")) {
                bytes = data.substring(data.indexOf("SIG1:")).trim().getBytes(StandardCharsets.UTF_8);
            } else if (data.trim().startsWith("{") || data.trim().startsWith("[")) {
                bytes = data.trim().getBytes(StandardCharsets.UTF_8);
            } else if (data.startsWith("data:")) {
                int idx = data.indexOf(",");
                if (idx != -1) {
                    String header = data.substring(0, idx);
                    String content = data.substring(idx + 1);
                    bytes = header.contains(";base64")
                            ? Base64.decode(content, Base64.DEFAULT)
                            : URLDecoder.decode(content, "UTF-8").getBytes(StandardCharsets.UTF_8);
                } else {
                    bytes = data.getBytes(StandardCharsets.UTF_8);
                }
            } else if (data.matches("[A-Za-z0-9+/=\\r\\n]{16,}")) {
                try {
                    bytes = Base64.decode(data, Base64.DEFAULT);
                } catch (Exception e) {
                    bytes = data.getBytes(StandardCharsets.UTF_8);
                }
            } else {
                bytes = data.getBytes(StandardCharsets.UTF_8);
            }

            fileName = buildSaveFileName(fileName, bytes);
            if (writeToDownloads(bytes, fileName)) {
                Toast.makeText(this, "✅ 已匯出：" + fileName, Toast.LENGTH_LONG).show();
                notifyJsExported();
            } else {
                saveViaSAF(bytes, fileName);
            }
        } catch (Exception e) {
            Log.e(TAG, "存檔解析失敗", e);
            Toast.makeText(this, "❌ 匯出失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean writeToDownloads(byte[] bytes, String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) {
                            os.write(bytes);
                            os.flush();
                        }
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "MediaStore 寫入失敗", e);
            }
            return false;
        }
        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
                fos.write(bytes);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void notifyJsExported() {
        runOnUiThread(() -> {
            try {
                webView.evaluateJavascript("window.__markExported && window.__markExported();", null);
            } catch (Exception ignored) {}
        });
    }

    private void saveViaSAF(byte[] bytes, String fileName) {
        pendingSaveBytes = bytes;
        pendingSaveFileName = fileName;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        try {
            createDocumentLauncher.launch(intent);
        } catch (Exception e) {
            shareSaveFile(bytes, fileName);
        }
    }

    private void shareSaveFile(byte[] data, String fileName) {
        try {
            File cacheFile = new File(getCacheDir(), fileName);
            try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                fos.write(data);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cacheFile);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "儲存存檔"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失敗", Toast.LENGTH_SHORT).show();
        }
    }

    private String buildSaveFileName(String rawName, byte[] bytes) {
        String base = rawName == null ? "" : rawName.replaceAll("(?i)\\.(json|txt|sav)$", "").trim();
        if (base.isEmpty() || base.matches("(?i)(save|download|export|存檔|下載|idle[_-]?lineage).*")) {
            base = extractCharInfo(bytes);
        }
        if (base.isEmpty()) {
            base = "存檔_" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.TAIWAN).format(new Date());
        }
        if (!SAVE_NAME_PREFIX.isEmpty()) base = SAVE_NAME_PREFIX + "_" + base;
        return base.replaceAll("[\\\\/:*?\"<>|]", "_") + ".json";
    }

    private String extractCharInfo(byte[] bytes) {
        try {
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.contains("SIG1:")) {
                int i = text.indexOf("SIG1:");
                String body = text.substring(i + 5).trim();
                int colon = body.indexOf(':');
                if (colon >= 0) body = body.substring(colon + 1).trim();
                if (body.startsWith("{")) text = body;
            }
            Matcher levelM = Pattern.compile("\"(?:charLevel|level|lv)\"\\s*:\\s*(\\d{1,3})").matcher(text);
            String level = levelM.find() ? levelM.group(1) : "";
            Matcher classM = Pattern.compile("\"(?:cls|class|className|job)\"\\s*:\\s*\"?([^\"\\s,]{1,12})\"?").matcher(text);
            String cls = classM.find() ? classM.group(1) : "";
            if (!level.isEmpty() || !cls.isEmpty()) {
                return (level.isEmpty() ? "" : level + "等") + cls;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private void triggerBlobDownload(String blobUrl) {
        String js = "(function(){var x=new XMLHttpRequest();x.open('GET','" + blobUrl + "',true);x.responseType='blob';" +
                "x.onload=function(){var r=new FileReader();r.onloadend=function(){" +
                "if(window.AndroidBridge)AndroidBridge.saveBase64File(r.result,'application/json','save_'+Date.now()+'.json');" +
                "};r.readAsDataURL(x.response);};x.send();})();";
        webView.evaluateJavascript(js, null);
    }

    /* ==================== 輔助 ==================== */

    private void initFileChooserLauncher() {
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (filePathCallback == null) return;
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        if (result.getData().getData() != null) {
                            results = new Uri[]{result.getData().getData()};
                        } else if (result.getData().getClipData() != null) {
                            int count = result.getData().getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = result.getData().getClipData().getItemAt(i).getUri();
                            }
                        }
                    }
                    filePathCallback.onReceiveValue(results);
                    filePathCallback = null;
                });
    }

    private void initCreateDocumentLauncher() {
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null
                            && result.getData().getData() != null && pendingSaveBytes != null) {
                        try (OutputStream os = getContentResolver().openOutputStream(result.getData().getData())) {
                            if (os != null) {
                                os.write(pendingSaveBytes);
                                Toast.makeText(this, "✅ 檔案已儲存", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "寫入失敗", Toast.LENGTH_SHORT).show();
                        }
                    }
                    pendingSaveBytes = null;
                    pendingSaveFileName = null;
                });
    }

    private void checkAllFilesAccessPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            }
        }
    }

    @Keep
    public class AndroidBridge {
        @JavascriptInterface
        public void setPluginMode(boolean enabled) {
            injectPluginsEnabled = enabled;
            Log.d(TAG, "外掛模式 = " + enabled);
        }

        @JavascriptInterface
        public void saveBase64File(String base64, String fileName) {
            runOnUiThread(() -> processAndSaveFile(base64, "application/json", fileName));
        }

        @JavascriptInterface
        public void saveBase64File(String data, String mime, String fileName) {
            runOnUiThread(() -> processAndSaveFile(data, mime, fileName));
        }

        @JavascriptInterface
        public void log(String message) {
            Log.d(TAG, "[Bridge] " + message);
        }

        @JavascriptInterface
        public void toast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
