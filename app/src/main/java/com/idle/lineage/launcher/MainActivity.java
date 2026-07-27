package com.idle.lineage.launcher;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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

    private WebView webView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<Intent> createDocumentLauncher;

    private byte[] pendingSaveBytes = null;
    private String pendingSaveFileName = null;

    private boolean injectPluginsEnabled = true;
    private String saveHookJs = null;

    @Keep
    public class AndroidBridge {
        @JavascriptInterface
        @Keep
        public void setPluginMode(boolean enabled) {
            injectPluginsEnabled = enabled;
            Log.d(TAG, "外掛模式 = " + enabled);
        }

        @JavascriptInterface
        @Keep
        public void saveBase64File(String dataUrlOrBase64, String mimeType, String fileName) {
            Log.d(TAG, "[JS 導出] " + fileName + " len=" + (dataUrlOrBase64 != null ? dataUrlOrBase64.length() : 0));
            runOnUiThread(() -> processAndSaveFile(dataUrlOrBase64, mimeType, fileName));
        }

        @JavascriptInterface
        @Keep
        public void saveBase64FileLegacy(String base64Data, String fileName) {
            runOnUiThread(() -> processAndSaveFile(base64Data, "application/json", fileName));
        }

        @JavascriptInterface
        @Keep
        public void pickSaveSlot(String slotsJson) {
            runOnUiThread(() -> showSlotChooser(slotsJson));
        }

        @JavascriptInterface
        @Keep
        public void log(String message) {
            Log.d(TAG, "[SaveHook] " + message);
        }

        @JavascriptInterface
        @Keep
        public void toast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkAllFilesAccessPermission();
        initFileChooserLauncher();
        initCreateDocumentLauncher();

        webView = new WebView(this);
        setContentView(webView);
        setupWebView();
        loadNativeLauncherHtml();
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

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, true);

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
            private void injectSaveHooks(WebView view) {
                injectFixImport(view);
                String hook = loadSaveHookJs();
                if (!hook.isEmpty()) {
                    view.evaluateJavascript(hook, null);
                } else {
                    injectBuiltinSaveHook(view);
                }
                injectExportFab(view);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                injectSaveHooks(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectSaveHooks(view);

                if (url != null && url.startsWith("http") && !url.contains("android_asset")) {
                    if (injectPluginsEnabled) {
                        injectPlugins(view);
                        injectTMEngine(view);
                        mainHandler.postDelayed(() -> injectSaveHooks(view), 1500);
                    } else {
                        Log.d(TAG, "純淨模式：跳過外掛與 TMEngine");
                    }
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith("data:")) {
                    processAndSaveFile(url, "application/json", null);
                    return true;
                }
                if (url.startsWith("blob:")) {
                    triggerBlobExport(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Toast.makeText(MainActivity.this,
                        "⚠️ 連線失敗 (" + errorCode + ")，請確認網路", Toast.LENGTH_LONG).show();
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url != null && url.startsWith("blob:")) {
                triggerBlobExport(url);
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

    private void triggerBlobExport(String blobUrl) {
        runOnUiThread(() -> Toast.makeText(this, "正在處理匯出…", Toast.LENGTH_SHORT).show());
        String js =
                "(function(){" +
                "try{" +
                "var x=new XMLHttpRequest();" +
                "x.open('GET'," + JSONObject.quote(blobUrl) + ",true);" +
                "x.responseType='blob';" +
                "x.onload=function(){" +
                "  var r=new FileReader();" +
                "  r.onloadend=function(){" +
                "    var data=r.result;" +
                "    if(window.AndroidDownloader&&AndroidDownloader.saveBase64File){" +
                "      AndroidDownloader.saveBase64File(data,'application/json','idle_save.json');" +
                "    }else if(window.AndroidBridge&&AndroidBridge.saveBase64File){" +
                "      AndroidBridge.saveBase64File(data,'application/json','idle_save.json');" +
                "    }" +
                "  };" +
                "  r.readAsDataURL(x.response);" +
                "};" +
                "x.onerror=function(){" +
                "  if(window.AndroidBridge&&AndroidBridge.toast)AndroidBridge.toast('讀取 blob 失敗');" +
                "};" +
                "x.send();" +
                "}catch(e){" +
                "  if(window.AndroidBridge&&AndroidBridge.toast)AndroidBridge.toast('匯出例外:'+e);" +
                "}" +
                "})();";
        webView.evaluateJavascript(js, null);
    }

    /** 選角／遊戲內備援：右下角綠色「匯出存檔」 */
    private void injectExportFab(WebView view) {
        String js =
                "(function(){" +
                "if(window.__export_fab_added)return;" +
                "window.__export_fab_added=true;" +
                "function send(data,name){" +
                "  name=name||'idle_save.json';" +
                "  if(window.AndroidDownloader&&AndroidDownloader.saveBase64File)" +
                "    AndroidDownloader.saveBase64File(data,'application/json',name);" +
                "  else if(window.AndroidBridge&&AndroidBridge.saveBase64File)" +
                "    AndroidBridge.saveBase64File(data,'application/json',name);" +
                "}" +
                "function listSlots(){" +
                "  var s=[];" +
                "  try{" +
                "    for(var i=0;i<localStorage.length;i++){" +
                "      var k=localStorage.key(i),v=localStorage.getItem(k)||'';" +
                "      if(v.length<80)continue;" +
                "      if(k.indexOf('save')>=0||k.indexOf('char')>=0||k.indexOf('slot')>=0" +
                "        ||k.indexOf('progress')>=0||k.indexOf('idle')>=0||k.indexOf('player')>=0" +
                "        ||v.indexOf('SIG1:')===0||v.indexOf('LZ1:')===0" +
                "        ||v.charAt(0)==='{'||v.charAt(0)==='['){" +
                "        s.push({key:k,label:k+' ('+Math.round(v.length/1024)+'KB)',len:v.length});" +
                "      }" +
                "    }" +
                "    s.sort(function(a,b){return b.len-a.len;});" +
                "  }catch(e){}" +
                "  return s;" +
                "}" +
                "function doExport(){" +
                "  var slots=listSlots();" +
                "  if(!slots.length){" +
                "    if(window.AndroidBridge&&AndroidBridge.toast)AndroidBridge.toast('找不到存檔欄位');" +
                "    if(window.AndroidDownloader&&AndroidDownloader.pickSaveSlot)" +
                "      AndroidDownloader.pickSaveSlot('[]');" +
                "    else if(window.AndroidBridge&&AndroidBridge.pickSaveSlot)" +
                "      AndroidBridge.pickSaveSlot('[]');" +
                "    return;" +
                "  }" +
                "  if(window.AndroidDownloader&&AndroidDownloader.pickSaveSlot){" +
                "    AndroidDownloader.pickSaveSlot(JSON.stringify(slots));" +
                "    return;" +
                "  }" +
                "  if(window.AndroidBridge&&AndroidBridge.pickSaveSlot){" +
                "    AndroidBridge.pickSaveSlot(JSON.stringify(slots));" +
                "    return;" +
                "  }" +
                "  var key=slots[0].key;" +
                "  var val=localStorage.getItem(key);" +
                "  var data='data:application/json;base64,'+btoa(unescape(encodeURIComponent(val)));" +
                "  send(data,key+'.json');" +
                "}" +
                "window.__exportSlotByKey=function(key){" +
                "  try{" +
                "    var val=localStorage.getItem(key);" +
                "    if(val==null){if(window.AndroidBridge)AndroidBridge.toast('找不到:'+key);return;}" +
                "    var data='data:application/json;base64,'+btoa(unescape(encodeURIComponent(val)));" +
                "    send(data,key+'.json');" +
                "  }catch(e){if(window.AndroidBridge)AndroidBridge.toast('匯出失敗');}" +
                "};" +
                "window.__listSaveSlots=function(){return JSON.stringify(listSlots());};" +
                "window.__dumpStorage=function(){var d={};try{for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);var v=localStorage.getItem(k);d[k]=v&&v.length>500?v.slice(0,500)+'…(len='+v.length+')':v;}}catch(e){d.error=String(e);}return JSON.stringify(d,null,2);};" +
                "window.__markExported=function(){};" +
                "var btn=document.createElement('button');" +
                "btn.textContent='匯出存檔';" +
                "btn.style.cssText='position:fixed;right:12px;bottom:80px;z-index:2147483647;" +
                "padding:10px 14px;border:none;border-radius:20px;background:#28a745;color:#fff;" +
                "font-size:14px;font-weight:bold;box-shadow:0 4px 12px rgba(0,0,0,.35);';" +
                "btn.onclick=function(e){e.preventDefault();e.stopPropagation();doExport();};" +
                "function attach(){if(document.body)document.body.appendChild(btn);else setTimeout(attach,200);}" +
                "attach();" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    private void loadNativeLauncherHtml() {
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "body{background:#121212;color:#fff;font-family:sans-serif;padding:20px;" +
                "display:flex;justify-content:center;align-items:center;min-height:90vh;margin:0;}" +
                ".card{background:#1e1e1e;border-radius:16px;padding:24px;width:100%;max-width:380px;text-align:center;}" +
                "h2{font-size:20px;margin-bottom:8px;}" +
                ".subtitle{color:#8e8e93;font-size:13px;margin-bottom:20px;}" +
                "select{width:100%;padding:12px;background:#2c2c2e;color:#fff;border:1px solid #3a3a3c;" +
                "border-radius:8px;font-size:15px;margin-bottom:16px;}" +
                ".btn{width:100%;padding:14px;background:#28a745;color:#fff;border:none;" +
                "border-radius:8px;font-size:16px;font-weight:bold;margin-top:8px;}" +
                ".btn-pure{background:#6c757d;}" +
                ".hint{color:#aaa;font-size:12px;margin-top:14px;line-height:1.4;}" +
                "</style></head><body>" +
                "<div class='card'>" +
                "<h2>放置天堂啟動器</h2>" +
                "<div class='subtitle'>多伺服器 · 可選外掛 · 強化存檔</div>" +
                "<select id='serverSelect'>" +
                "<option value='https://pp771007.github.io/idle-lineage-class/'>伺服器一（加掛版 pp771007）</option>" +
                "<option value='https://shines871.github.io/idle-lineage-class/'>伺服器二（原版 shines871）</option>" +
                "</select>" +
                "<button class='btn' onclick='start(true)'>🚀 啟動（含外掛 + 防斷線）</button>" +
                "<button class='btn btn-pure' onclick='start(false)'>純淨啟動（無外掛）</button>" +
                "<div class='hint'>兩種模式都支援存檔匯出／匯入。<br>選角畫面右下角有綠色「匯出存檔」按鈕。</div>" +
                "</div>" +
                "<script>" +
                "function start(withPlugin){" +
                "  if(window.AndroidBridge&&AndroidBridge.setPluginMode)AndroidBridge.setPluginMode(withPlugin);" +
                "  location.href=document.getElementById('serverSelect').value;" +
                "}" +
                "</script></body></html>";
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    }

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
                "function toast(msg,ok){var n=document.createElement('div');n.textContent=msg;" +
                "n.style.cssText='position:fixed;top:20px;right:20px;background:'+(ok?'#2ecc71':'#e74c3c')+" +
                "';color:#fff;padding:12px 24px;border-radius:8px;z-index:99999;';document.body.appendChild(n);" +
                "setTimeout(function(){n.style.opacity='0';setTimeout(function(){n.remove();},500);},2500);}" +
                "function load(src){return new Promise(function(res,rej){var o=document.createElement('script');" +
                "o.src=src+'?v='+Date.now();o.onload=res;o.onerror=function(){rej(src);};document.body.appendChild(o);});}" +
                "n.reduce(function(p,src){return p.then(function(){return load(src);});},Promise.resolve())" +
                ".then(function(){toast('🎉 外掛模組注入完成',true);})" +
                ".catch(function(r){var f=(typeof r==='string')?r.split('/').pop().split('?')[0]:'';" +
                "toast('❌ 載入失敗'+(f?'：'+f:''),false);});" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    private void injectTMEngine(WebView view) {
        String tm = "(function(){" +
                "'use strict';if(window.__tm_engine_loaded)return;window.__tm_engine_loaded=true;" +
                "const PerformanceCore={initTuning:()=>{if(window.requestIdleCallback){window.requestIdleCallback(()=>{if(window.gc)window.gc();},{timeout:500});}}," +
                "getJitter:(b,v)=>b+Math.floor(Math.random()*v)};PerformanceCore.initTuning();" +
                "const _si=window.setInterval;window.setInterval=function(cb,d,...a){return _si(cb,d<150?150:d,...a);};" +
                "const NetworkOptimizer={_isMobile:false,detectEnvironment:async()=>{const c=navigator.connection||{};" +
                "NetworkOptimizer._isMobile=c.type==='cellular'||/Android|iPhone|iPad/i.test(navigator.userAgent);" +
                "try{const t=Date.now();await fetch(location.href,{method:'HEAD',cache:'no-cache'});if(Date.now()-t>150)NetworkOptimizer._isMobile=true;}catch(e){}}," +
                "getJitterParams:()=>NetworkOptimizer._isMobile?{base:500,variance:700}:{base:120,variance:250}};" +
                "const DOMWatcher={waitForEl:(sel,fn)=>{const e=document.querySelector(sel);if(e){fn(e);return;}" +
                "const o=new MutationObserver((m,obs)=>{const t=document.querySelector(sel);if(t){obs.disconnect();fn(t);}});" +
                "if(document.body)o.observe(document.body,{childList:true,subtree:true});" +
                "else document.addEventListener('DOMContentLoaded',()=>o.observe(document.body,{childList:true,subtree:true}));}};" +
                "window.executeLogic=function(){const hp=document.querySelector('.hp-text')?.innerText;" +
                "if(hp){const[cur,max]=hp.split('/').map(Number);if(cur/max<0.75){const b=document.querySelector('#btn-use-potion')||document.querySelector('.potion-btn');if(b)b.click();}}" +
                "const atk=document.querySelector('.attack-btn');if(atk&&!atk.classList.contains('cooldown')){" +
                "const{base,variance}=NetworkOptimizer.getJitterParams();setTimeout(()=>atk.click(),PerformanceCore.getJitter(base,variance));}};" +
                "document.addEventListener('visibilitychange',()=>{if(!document.hidden&&window.executeLogic)window.executeLogic();});" +
                "const Heartbeat={send:()=>{if(window.socket&&window.socket.readyState===1)window.socket.send(JSON.stringify({type:'heartbeat',timestamp:Date.now()}));" +
                "else fetch(location.href,{method:'HEAD',cache:'no-cache',keepalive:true}).catch(()=>{});}};" +
                "if(window.Worker){const code=`let id=null;self.onmessage=e=>{if(e.data==='start'){if(id)clearInterval(id);id=setInterval(()=>self.postMessage('ping'),1000);}" +
                "else if(e.data==='stop'&&id)clearInterval(id);};`;" +
                "const w=new Worker(URL.createObjectURL(new Blob([code],{type:'application/javascript'})));w.postMessage('start');" +
                "w.onmessage=e=>{if(e.data==='ping')Heartbeat.send();};}" +
                "try{const AC=window.AudioContext||window.webkitAudioContext;if(AC){const ctx=new AC();const o=ctx.createOscillator();const g=ctx.createGain();g.gain.value=0.0001;o.connect(g);g.connect(ctx.destination);o.start();" +
                "document.addEventListener('visibilitychange',()=>{if(ctx.state==='suspended')ctx.resume();});}}catch(e){}" +
                "(async()=>{await NetworkOptimizer.detectEnvironment();" +
                "const d=document.createElement('div');d.style='position:fixed;top:10px;left:10px;background:rgba(0,0,0,0.85);color:#0f0;padding:8px 10px;z-index:2147483647;border-radius:8px;font-size:11px;border:1px solid #0f0;pointer-events:none';" +
                "d.textContent='【TMEngine】防斷線 · '+(NetworkOptimizer._isMobile?'手機':'WIFI');" +
                "const attach=()=>{if(document.body)document.body.appendChild(d);else setTimeout(attach,100);};attach();" +
                "DOMWatcher.waitForEl('.attack-btn',()=>setInterval(window.executeLogic,250));})();" +
                "})();";
        view.evaluateJavascript(tm, null);
    }

    private void injectFixImport(WebView view) {
        String js = "(function(){if(window.__fix_import_active)return;window.__fix_import_active=true;" +
                "var orig=FileReader.prototype.readAsText;FileReader.prototype.readAsText=function(file,enc){" +
                "var self=this,ol=self.onload;self.onload=function(e){try{var t=e.target.result;var p=JSON.parse(t);" +
                "if(p&&p.data)t=typeof p.data==='string'?p.data:JSON.stringify(p.data);" +
                "if(p&&p.save)t=typeof p.save==='string'?p.save:JSON.stringify(p.save);" +
                "Object.defineProperty(e.target,'result',{value:t,writable:true});}catch(err){}" +
                "if(ol)ol.call(self,e);};return orig.apply(this,arguments);};})();";
        view.evaluateJavascript(js, null);
    }

    private void injectBuiltinSaveHook(WebView view) {
        String js = "(function(){" +
                "if(window.__IDLE_SAVE_HOOK_LOADED__)return;" +
                "window.__IDLE_SAVE_HOOK_LOADED__=true;" +
                "console.log('[SaveHook] builtin loaded');" +
                "function send(data,name){" +
                "  name=name||'idle_save.json';" +
                "  try{" +
                "    if(window.AndroidDownloader&&AndroidDownloader.saveBase64File)" +
                "      AndroidDownloader.saveBase64File(data,'application/json',name);" +
                "    else if(window.AndroidBridge&&AndroidBridge.saveBase64File)" +
                "      AndroidBridge.saveBase64File(data,'application/json',name);" +
                "  }catch(e){console.error(e);}" +
                "}" +
                "var orig=URL.createObjectURL;" +
                "URL.createObjectURL=function(blob){" +
                "  var url=orig.apply(this,arguments);" +
                "  try{" +
                "    if(blob&&blob.size>0){" +
                "      var t=(blob.type||'').toLowerCase();" +
                "      if(t.indexOf('json')>=0||t.indexOf('text')>=0||t.indexOf('octet')>=0||t===''||t==='application/octet-stream'){" +
                "        var r=new FileReader();" +
                "        r.onloadend=function(){send(r.result,'idle_save.json');};" +
                "        r.readAsDataURL(blob);" +
                "      }" +
                "    }" +
                "  }catch(e){}" +
                "  return url;" +
                "};" +
                "document.addEventListener('click',function(e){" +
                "  var a=e.target;while(a&&a.tagName!=='A')a=a.parentElement;" +
                "  if(!a||!a.hasAttribute('download'))return;" +
                "  var href=a.getAttribute('href')||'';" +
                "  if(href.indexOf('blob:')===0||href.indexOf('data:')===0){" +
                "    e.preventDefault();e.stopPropagation();" +
                "    var fname=a.getAttribute('download')||'idle_save.json';" +
                "    if(href.indexOf('data:')===0){send(href,fname);}" +
                "    else{fetch(href).then(function(res){return res.blob();}).then(function(blob){" +
                "      var r=new FileReader();r.onloadend=function(){send(r.result,fname);};r.readAsDataURL(blob);" +
                "    }).catch(function(){});}" +
                "  }" +
                "},true);" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    private String loadSaveHookJs() {
        if (saveHookJs != null) return saveHookJs;
        try (InputStream is = getAssets().open("save_hook.js");
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
            saveHookJs = new String(bos.toByteArray(), StandardCharsets.UTF_8);
            Log.d(TAG, "已載入 assets/save_hook.js");
        } catch (Exception e) {
            Log.w(TAG, "assets/save_hook.js 不存在，使用內建 Hook");
            saveHookJs = "";
        }
        return saveHookJs;
    }

    private void processAndSaveFile(String dataUrlOrBase64, String mimeType, String fileName) {
        if (dataUrlOrBase64 == null || dataUrlOrBase64.isEmpty()) return;
        if (dataUrlOrBase64.startsWith("blob:")) {
            triggerBlobExport(dataUrlOrBase64);
            return;
        }

        try {
            byte[] bytes;
            if (dataUrlOrBase64.contains("SIG1:")) {
                String sig = dataUrlOrBase64.substring(dataUrlOrBase64.indexOf("SIG1:")).trim();
                bytes = sig.getBytes(StandardCharsets.UTF_8);
            } else if (dataUrlOrBase64.trim().startsWith("{") || dataUrlOrBase64.trim().startsWith("[")) {
                bytes = dataUrlOrBase64.trim().getBytes(StandardCharsets.UTF_8);
            } else if (dataUrlOrBase64.startsWith("data:")) {
                int comma = dataUrlOrBase64.indexOf(",");
                if (comma != -1) {
                    String header = dataUrlOrBase64.substring(0, comma);
                    String content = dataUrlOrBase64.substring(comma + 1);
                    bytes = header.contains(";base64")
                            ? Base64.decode(content, Base64.DEFAULT)
                            : URLDecoder.decode(content, "UTF-8").getBytes(StandardCharsets.UTF_8);
                } else {
                    bytes = dataUrlOrBase64.getBytes(StandardCharsets.UTF_8);
                }
            } else if (dataUrlOrBase64.matches("[A-Za-z0-9+/=\\r\\n]{16,}")) {
                try {
                    bytes = Base64.decode(dataUrlOrBase64, Base64.DEFAULT);
                } catch (Exception e) {
                    bytes = dataUrlOrBase64.getBytes(StandardCharsets.UTF_8);
                }
            } else {
                bytes = dataUrlOrBase64.getBytes(StandardCharsets.UTF_8);
            }

            fileName = buildSaveFileName(fileName, bytes);
            Log.d(TAG, "最終檔名: " + fileName);

            if (writeToDownloads(bytes, fileName, "application/json")) {
                Toast.makeText(this, "✅ 已匯出：" + fileName, Toast.LENGTH_LONG).show();
                notifyJsExported();
            } else {
                saveViaSAF(bytes, fileName);
            }
        } catch (Exception e) {
            showDebugDialog("❌ 資料解析異常", e.toString());
        }
    }

    private boolean writeToDownloads(byte[] bytes, String fileName, String mimeType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
                values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    boolean ok = false;
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os != null) {
                            os.write(bytes);
                            os.flush();
                            ok = true;
                        }
                    }
                    values.clear();
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                    return ok;
                }
            } catch (Exception e) {
                Log.e(TAG, "MediaStore 失敗: " + e.getMessage());
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
        mainHandler.post(() -> {
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
            startActivity(Intent.createChooser(share, "儲存遊戲存檔: " + fileName));
        } catch (Exception e) {
            showDebugDialog("❌ Share 失敗", e.getMessage());
        }
    }

    private String buildSaveFileName(String rawName, byte[] bytes) {
        String base = rawName == null ? "" : rawName.trim();
        base = base.replaceAll("(?i)\\.(json|txt|sav|dat|bin)$", "").trim();
        if (base.matches("(?i)(idle[_-]?lineage[_-]?save|save|savefile|download|downloadfile|export|progress|存檔|下載|進度|未命名|fable5_save_\\d+)?")) {
            base = "";
        }
        if (base.isEmpty()) base = extractCharInfo(bytes);
        if (base.isEmpty()) base = "存檔_" + timestamp();
        if (!SAVE_NAME_PREFIX.isEmpty() && !base.startsWith(SAVE_NAME_PREFIX)) {
            base = SAVE_NAME_PREFIX + "_" + base;
        }
        return sanitizeFileName(base) + ".json";
    }

    private String mapClass(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String v = raw.trim();
        if (v.matches(".*[\u4e00-\u9fff].*")) return v.length() > 6 ? v.substring(0, 6) : v;
        String[] order = {"王子", "騎士", "法師", "妖精", "黑暗妖精", "幻術士", "龍騎士", "戰士"};
        if (v.matches("\\d{1,2}")) {
            int i = Integer.parseInt(v);
            if (i == 0) return order[0];
            return (i <= order.length) ? order[i - 1] : "";
        }
        String k = v.toLowerCase().replaceAll("[\\s_\\-]", "");
        switch (k) {
            case "prince": case "royal": case "king": case "royalty": return "王子";
            case "knight": case "kn": return "騎士";
            case "mage": case "wizard": case "wiz": return "法師";
            case "elf": return "妖精";
            case "darkelf": case "de": return "黑暗妖精";
            case "illusionist": case "illusion": case "il": return "幻術士";
            case "dragonknight": case "dk": return "龍騎士";
            case "warrior": case "fighter": case "wa": return "戰士";
            default: return "";
        }
    }

    private String extractCharInfo(byte[] bytes) {
        try {
            String text = new String(bytes, StandardCharsets.UTF_8);
            String probe = text;
            int i = text.indexOf("SIG1:");
            if (i >= 0) {
                String body = text.substring(i + 5).trim();
                int colon = body.indexOf(':');
                if (colon >= 0) body = body.substring(colon + 1).trim();
                if (body.startsWith("{") || body.startsWith("[")) {
                    probe = body;
                } else {
                    String b64 = body.split("[.|,;\\s]")[0];
                    try {
                        String decoded = new String(Base64.decode(b64, Base64.DEFAULT), StandardCharsets.UTF_8);
                        if (decoded.contains("{")) probe = decoded;
                    } catch (Exception ignored) {}
                }
            }
            if (probe.startsWith("LZ1:")) return "";
            String scope = probe;
            Matcher pm = Pattern.compile("\"p\"\\s*:\\s*\\{").matcher(probe);
            if (pm.find()) {
                int from = pm.start();
                scope = probe.substring(from, Math.min(from + 3000, probe.length()));
            }
            String level = firstNumber(scope, new String[]{"charLevel", "level", "lv", "lvl"});
            if (level.isEmpty()) level = firstNumber(probe, new String[]{"charLevel", "level", "lv", "lvl"});
            String rawClass = firstMatch(scope, new String[]{"cls", "class", "charClass", "className", "job", "career"});
            if (rawClass.isEmpty()) rawClass = firstNumber(scope, new String[]{"cls", "class", "classId", "job"});
            String cls = mapClass(rawClass);
            if (cls.isEmpty()) {
                String avatar = firstMatch(scope, new String[]{"avatar"});
                if (!avatar.isEmpty()) {
                    cls = avatar.replaceAll("^[男女]", "");
                    if (cls.length() > 6) cls = cls.substring(0, 6);
                }
            }
            String out = "";
            if (!level.isEmpty()) out += level + "等";
            if (!cls.isEmpty()) out += cls;
            if (!out.isEmpty()) return out;
            return firstMatch(scope, new String[]{
                    "charName", "characterName", "playerName", "nickName", "nickname", "cname", "name"});
        } catch (Exception e) {
            return "";
        }
    }

    private String firstMatch(String text, String[] keys) {
        for (String key : keys) {
            try {
                Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"\\\\]{1,24})\"").matcher(text);
                if (m.find()) {
                    String v = m.group(1).trim();
                    if (!v.isEmpty()) return v;
                }
            } catch (Exception ignored) {}
        }
        return "";
    }

    private String firstNumber(String text, String[] keys) {
        for (String key : keys) {
            try {
                Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d{1,3})").matcher(text);
                if (m.find()) return m.group(1);
            } catch (Exception ignored) {}
        }
        return "";
    }

    private String sanitizeFileName(String name) {
        String out = name.replaceAll("[\\\\/:*?\"<>|\\r\\n\\t\\x00-\\x1f]", "_")
                .replaceAll("_{2,}", "_").replaceAll("^[._]+", "").trim();
        if (out.length() > 80) out = out.substring(0, 80);
        return out.isEmpty() ? "存檔" : out;
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.TAIWAN).format(new Date());
    }

    private void showSlotChooser(String slotsJson) {
        try {
            JSONArray arr = new JSONArray(slotsJson);
            if (arr.length() == 0) {
                new AlertDialog.Builder(this)
                        .setTitle("找不到任何存檔")
                        .setMessage("localStorage 沒有符合的存檔欄位。可按「診斷」倒出內容。")
                        .setPositiveButton("關閉", null)
                        .setNeutralButton("🔍 診斷", (d, w) -> dumpStorageDiagnostics())
                        .show();
                return;
            }
            final String[] keys = new String[arr.length()];
            final String[] labels = new String[arr.length()];
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                keys[i] = o.optString("key");
                String label = o.optString("label");
                labels[i] = label.isEmpty() ? keys[i] : label;
            }
            new AlertDialog.Builder(this)
                    .setTitle("要匯出哪一個角色？")
                    .setItems(labels, (dialog, which) -> {
                        String js = "window.__exportSlotByKey && window.__exportSlotByKey("
                                + JSONObject.quote(keys[which]) + ");";
                        webView.evaluateJavascript(js, null);
                    })
                    .setNegativeButton("取消", null)
                    .setNeutralButton("🔍 診斷", (d, w) -> dumpStorageDiagnostics())
                    .show();
        } catch (Exception e) {
            showDebugDialog("❌ 讀取存檔清單失敗", e.toString());
        }
    }

    private void dumpStorageDiagnostics() {
        webView.evaluateJavascript("window.__dumpStorage ? window.__dumpStorage() : 'no hook'", value -> {
            String text;
            try {
                Object parsed = new org.json.JSONTokener(value).nextValue();
                text = String.valueOf(parsed);
            } catch (Exception e) {
                text = value;
            }
            String name = "存檔診斷_" + timestamp() + ".txt";
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            boolean ok = writeToDownloads(bytes, name, "text/plain");
            String head = text.length() > 1500 ? text.substring(0, 1500) + "\n…" : text;
            showDebugDialog(ok ? "診斷已存成 " + name : "診斷（寫檔失敗）", head);
        });
    }

    private void initFileChooserLauncher() {
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (filePathCallback == null) return;
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getData() != null) {
                            results = new Uri[]{data.getData()};
                        } else if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = data.getClipData().getItemAt(i).getUri();
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
                            showDebugDialog("❌ SAF 寫入失敗", e.getMessage());
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

    private void showDebugDialog(String title, String message) {
        runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("確定", null)
                .show());
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
