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
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
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
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<Intent> createDocumentLauncher;

    private byte[] pendingSaveBytes = null;
    private String pendingSaveFileName = null;
    private String saveHookJs = null;

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

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        AndroidBridge bridge = new AndroidBridge();
        webView.addJavascriptInterface(bridge, "Android");
        webView.addJavascriptInterface(bridge, "AndroidBridge");
        webView.addJavascriptInterface(bridge, "AndroidDownloader");

        webView.setWebChromeClient(new WebChromeClient() {
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
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue,
                                      android.webkit.JsPromptResult result) {
                String content = (defaultValue != null && !defaultValue.isEmpty()) ? defaultValue : message;
                if (content != null && content.contains("SIG1:")) {
                    processAndSaveFile(content, "application/json", null);
                    result.confirm();
                    return true;
                }
                return super.onJsPrompt(view, url, message, defaultValue, result);
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, android.webkit.JsResult result) {
                if (message != null && message.contains("SIG1:")) {
                    processAndSaveFile(message, "application/json", null);
                    result.confirm();
                    return true;
                }
                return super.onJsAlert(view, url, message, result);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            private void injectSaveHook(WebView view) {
                String js = loadSaveHookJs();
                if (js != null && !js.isEmpty()) {
                    view.evaluateJavascript(js, null);
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                injectSaveHook(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectSaveHook(view);

                if (url != null && url.startsWith("http")) {
                    injectPlugins(view);
                    injectTMEngine(view);
                    injectFixImport(view);
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
                    Log.d(TAG, "攔到 blob:，交由 save_hook 處理");
                    return true;
                }
                return false;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                Toast.makeText(MainActivity.this, "⚠️ 連線失敗：" + description, Toast.LENGTH_LONG).show();
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            if (url.startsWith("blob:")) return;
            String suggested = null;
            try {
                suggested = URLUtil.guessFileName(url, contentDisposition, mimetype);
            } catch (Exception ignored) {}
            final String hint = suggested;
            runOnUiThread(() -> processAndSaveFile(url, mimetype != null ? mimetype : "application/json", hint));
        });
    }

    private void loadNativeLauncherHtml() {
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "body{background:#121212;color:#fff;font-family:sans-serif;padding:20px;display:flex;justify-content:center;align-items:center;min-height:90vh;margin:0;}" +
                ".card{background:#1e1e1e;border-radius:16px;padding:24px;width:100%;max-width:380px;text-align:center;box-sizing:border-box;}" +
                "h2{font-size:20px;margin-bottom:4px;}" +
                ".subtitle{color:#8e8e93;font-size:13px;margin-bottom:24px;}" +
                ".label{color:#4cd964;font-size:14px;text-align:left;margin-bottom:8px;font-weight:bold;}" +
                "select{width:100%;padding:12px;background:#2c2c2e;color:#fff;border:1px solid #3a3a3c;border-radius:8px;font-size:15px;margin-bottom:24px;}" +
                ".btn-start{width:100%;padding:14px;background:#28a745;color:#fff;border:none;border-radius:8px;font-size:16px;font-weight:bold;}" +
                "</style></head><body>" +
                "<div class='card'>" +
                "<h2>🎮 放置天堂 旗艦版啟動器</h2>" +
                "<div class='subtitle'>10大外掛模組 + TMEngine 防斷線引擎</div>" +
                "<div class='label'>選擇遊戲伺服器：</div>" +
                "<select id='serverSelect'>" +
                "<option value='https://pp771007.github.io/idle-lineage-class/'>伺服器一 (pp771007)</option>" +
                "<option value='https://shines871.github.io/idle-lineage-class/'>伺服器二 (shines871)</option>" +
                "</select>" +
                "<button class='btn-start' onclick='launchGame()'>🚀 啟動遊戲與防斷線外掛</button>" +
                "</div>" +
                "<script>function launchGame(){location.href=document.getElementById('serverSelect').value;}</script>" +
                "</body></html>";
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
                "const c=['klh_initial.js','klh_GMShop.js','klh_mobile-perf.js','klh_perf-monitor.js','klh_Backpack.js','klh_pk.js','klh_Pandora.js'].map(x=>b+x);" +
                "const n=t?[...[b+'klh_remove-banner.js'],...c]:[...['https://pp771007.github.io/idle-lineage-class/afk-lzcache.js','https://pp771007.github.io/idle-lineage-class/afk-offline.js'],...c];" +
                "function s(e,ok){const n=document.createElement('div');n.textContent=e;n.style.cssText='position:fixed;top:20px;right:20px;background:'+(ok?'#2ecc71':'#e74c3c')+';color:#fff;padding:12px 24px;border-radius:8px;z-index:99999;font-family:sans-serif;box-shadow:0 4px 12px rgba(0,0,0,.15);transition:opacity .5s';document.body.appendChild(n);setTimeout(()=>{n.style.opacity='0';setTimeout(()=>n.remove(),500);},2500);}" +
                "function l(src){return new Promise((res,rej)=>{const o=document.createElement('script');o.src=src+'?v='+Date.now();o.onload=res;o.onerror=()=>rej(src);document.body.appendChild(o);});}" +
                "n.reduce((p,src)=>p.then(()=>l(src)),Promise.resolve()).then(()=>s('🎉 【10大外掛模組】全部注入成功！',true)).catch(r=>{const f=(r&&typeof r==='string')?r.split('/').pop().split('?')[0]:'';s('❌ 載入失敗'+(f?'：'+f:'！'),false);});" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    private void injectTMEngine(WebView view) {
        String tmEngineJs = "(function() {" +
                "'use strict';" +
                "if(window.__tm_engine_loaded) return;" +
                "window.__tm_engine_loaded = true;" +

                "const PerformanceCore = {" +
                "    initTuning: () => {" +
                "        if (typeof window.requestIdleCallback !== 'undefined') {" +
                "            window.requestIdleCallback(() => {" +
                "                if (window.gc) window.gc();" +
                "                console.log('【TMEngine】低功耗模式與記憶體最佳化執行完畢。');" +
                "            }, { timeout: 500 });" +
                "        }" +
                "    }," +
                "    getJitter: (base, variance) => base + Math.floor(Math.random() * variance)" +
                "};" +

                "PerformanceCore.initTuning();" +

                "const originalSetInterval = window.setInterval;" +
                "window.setInterval = function(callback, delay, ...args) {" +
                "    const optimizedDelay = delay < 150 ? 150 : delay;" +
                "    return originalSetInterval(callback, optimizedDelay, ...args);" +
                "};" +

                "const NetworkOptimizer = {" +
                "    _isMobile: false," +
                "    detectEnvironment: async () => {" +
                "        const conn = navigator.connection || {};" +
                "        NetworkOptimizer._isMobile = conn.type === 'cellular' || /Android|webOS|iPhone|iPad/i.test(navigator.userAgent);" +
                "        try {" +
                "            const start = Date.now();" +
                "            await fetch(window.location.href, { method: 'HEAD', cache: 'no-cache' });" +
                "            const rtt = Date.now() - start;" +
                "            if (rtt > 150) NetworkOptimizer._isMobile = true;" +
                "        } catch (e) {}" +
                "    }," +
                "    getJitterParams: () => {" +
                "        return NetworkOptimizer._isMobile ? { base: 500, variance: 700 } : { base: 120, variance: 250 };" +
                "    }" +
                "};" +

                "const DOMWatcher = {" +
                "    waitForEl: (selector, success) => {" +
                "        const el = document.querySelector(selector);" +
                "        if (el) { success(el); return; }" +
                "        const obs = new MutationObserver((mutations, obs) => {" +
                "            const target = document.querySelector(selector);" +
                "            if (target) { obs.disconnect(); success(target); }" +
                "        });" +
                "        if (document.body) {" +
                "            obs.observe(document.body, { childList: true, subtree: true });" +
                "        } else {" +
                "            document.addEventListener('DOMContentLoaded', () => {" +
                "                obs.observe(document.body, { childList: true, subtree: true });" +
                "            });" +
                "        }" +
                "    }" +
                "};" +

                "const GuildInterfaceOptimizer = {" +
                "    isGuildActive: () => {" +
                "        const guildPanel = document.querySelector('.guild-interface, .blood-pledge-panel, [data-view=\"guild\"]');" +
                "        return guildPanel !== null && guildPanel.offsetParent !== null;" +
                "    }," +
                "    executeGuildLogic: () => {" +
                "        if (!GuildInterfaceOptimizer.isGuildActive()) return;" +
                "        const checkInBtn = document.querySelector('.guild-checkin-btn:not(.completed)');" +
                "        if (checkInBtn) {" +
                "            setTimeout(() => checkInBtn.click(), PerformanceCore.getJitter(500, 1000));" +
                "        }" +
                "        const donateBtn = document.querySelector('.guild-donate-confirm');" +
                "        if (donateBtn && Math.random() > 0.95) {" +
                "            setTimeout(() => donateBtn.click(), PerformanceCore.getJitter(800, 1500));" +
                "        }" +
                "    }" +
                "};" +

                "window.executeLogic = function() {" +
                "    if (GuildInterfaceOptimizer.isGuildActive()) {" +
                "        GuildInterfaceOptimizer.executeGuildLogic();" +
                "        return;" +
                "    }" +
                "    const hpText = document.querySelector('.hp-text')?.innerText;" +
                "    if (hpText) {" +
                "        const [cur, max] = hpText.split('/').map(Number);" +
                "        if (cur / max < 0.75) {" +
                "            const potionBtn = document.querySelector('#btn-use-potion') || document.querySelector('.potion-btn');" +
                "            if (potionBtn) potionBtn.click();" +
                "        }" +
                "    }" +
                "    const attackBtn = document.querySelector('.attack-btn');" +
                "    if (attackBtn && !attackBtn.classList.contains('cooldown')) {" +
                "        const { base, variance } = NetworkOptimizer.getJitterParams();" +
                "        setTimeout(() => attackBtn.click(), PerformanceCore.getJitter(base, variance));" +
                "    }" +
                "    const buffs = [" +
                "        { selector: '.status-haste', btn: '#btn-use-haste-potion' }," +
                "        { selector: '.status-shield', btn: '#btn-use-shield' }," +
                "        { selector: '.status-holy-weapon', btn: '#btn-use-holy-weapon' }," +
                "        { selector: '.status-berserk', btn: '#btn-use-berserk' }" +
                "    ];" +
                "    buffs.forEach(buff => {" +
                "        if (document.querySelector(buff.selector) === null) {" +
                "            const targetBtn = document.querySelector(buff.btn);" +
                "            if (targetBtn && Math.random() > 0.8) {" +
                "                setTimeout(() => targetBtn.click(), PerformanceCore.getJitter(400, 800));" +
                "            }" +
                "        }" +
                "    });" +
                "    if (Math.random() > 0.995) {" +
                "        const sellBtn = document.querySelector('#btn-sell-all-waste');" +
                "        if (sellBtn && sellBtn.offsetParent !== null) sellBtn.click();" +
                "    }" +
                "};" +

                "const PageVisibilityModule = {" +
                "    init: () => {" +
                "        document.addEventListener('visibilitychange', () => {" +
                "            if (!document.hidden && typeof window.executeLogic === 'function') {" +
                "                window.executeLogic();" +
                "            }" +
                "        });" +
                "    }" +
                "};" +

                "const HeartbeatModule = {" +
                "    sendKeepAliveSignal: () => {" +
                "        if (window.socket && window.socket.readyState === WebSocket.OPEN) {" +
                "            window.socket.send(JSON.stringify({ type: 'heartbeat', timestamp: Date.now() }));" +
                "        } else {" +
                "            fetch(window.location.href, { method: 'HEAD', cache: 'no-cache', keepalive: true }).catch(() => {});" +
                "        }" +
                "    }" +
                "};" +

                "const WebWorkerModule = {" +
                "    init: () => {" +
                "        if (!window.Worker) return;" +
                "        const workerCode = `let intervalId = null;" +
                "        self.onmessage = function(e) {" +
                "            if (e.data === 'start') {" +
                "                if (intervalId) clearInterval(intervalId);" +
                "                intervalId = setInterval(() => { self.postMessage('ping'); }, 1000);" +
                "            } else if (e.data === 'stop') {" +
                "                if (intervalId) clearInterval(intervalId);" +
                "            }" +
                "        };`;" +
                "        const blob = new Blob([workerCode], { type: 'application/javascript' });" +
                "        const workerUrl = URL.createObjectURL(blob);" +
                "        const worker = new Worker(workerUrl);" +
                "        worker.postMessage('start');" +
                "        worker.onmessage = function(e) {" +
                "            if (e.data === 'ping') HeartbeatModule.sendKeepAliveSignal();" +
                "        };" +
                "    }" +
                "};" +

                "const AudioKeepAliveModule = {" +
                "    silentAudioCtx: null," +
                "    init: () => {" +
                "        try {" +
                "            const AudioContext = window.AudioContext || window.webkitAudioContext;" +
                "            if (!AudioContext) return;" +
                "            AudioKeepAliveModule.silentAudioCtx = new AudioContext();" +
                "            const oscillator = AudioKeepAliveModule.silentAudioCtx.createOscillator();" +
                "            const gainNode = AudioKeepAliveModule.silentAudioCtx.createGain();" +
                "            gainNode.gain.value = 0.0001;" +
                "            oscillator.connect(gainNode);" +
                "            gainNode.connect(AudioKeepAliveModule.silentAudioCtx.destination);" +
                "            oscillator.start();" +
                "            document.addEventListener('visibilitychange', () => {" +
                "                if (AudioKeepAliveModule.silentAudioCtx && AudioKeepAliveModule.silentAudioCtx.state === 'suspended') {" +
                "                    AudioKeepAliveModule.silentAudioCtx.resume();" +
                "                }" +
                "            });" +
                "        } catch (e) {}" +
                "    }" +
                "};" +

                "const initSystem = async () => {" +
                "    await NetworkOptimizer.detectEnvironment();" +
                "    PageVisibilityModule.init();" +
                "    WebWorkerModule.init();" +
                "    AudioKeepAliveModule.init();" +

                "    const div = document.createElement('div');" +
                "    div.style = 'position:fixed; top:10px; left:10px; background:rgba(0,0,0,0.85); color:#0f0; padding:10px; z-index:2147483647; border-radius:8px; font-size:11px; border:1px solid #0f0; pointer-events:none;';" +
                "    div.innerHTML = `<div style=\"font-weight:bold;\">【TMEngine v106.0】全域極致整合防斷線版</div><div>● 背景抗凍結：四大模組運行中</div><div>● 模式：${NetworkOptimizer._isMobile ? '手機動態適配' : 'WIFI高速運行'}</div>`;" +

                "    const attachUI = () => {" +
                "        if (document.body) document.body.appendChild(div);" +
                "        else setTimeout(attachUI, 100);" +
                "    };" +
                "    attachUI();" +

                "    DOMWatcher.waitForEl('.attack-btn', () => {" +
                "        setInterval(window.executeLogic, 250);" +
                "    });" +
                "};" +
                "initSystem();" +
                "})();";
        view.evaluateJavascript(tmEngineJs, null);
    }

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

    private void processAndSaveFile(String dataUrlOrBase64, String mimeType, String fileName) {
        if (dataUrlOrBase64 == null || dataUrlOrBase64.isEmpty()) return;
        if (dataUrlOrBase64.startsWith("blob:")) {
            Log.w(TAG, "收到 blob:，Java 端無法直接讀取，略過");
            return;
        }

        try {
            byte[] bytes;

            if (dataUrlOrBase64.contains("SIG1:")) {
                String sigData = dataUrlOrBase64.substring(dataUrlOrBase64.indexOf("SIG1:")).trim();
                bytes = sigData.getBytes(StandardCharsets.UTF_8);
            } else if (dataUrlOrBase64.trim().startsWith("{") || dataUrlOrBase64.trim().startsWith("[")) {
                bytes = dataUrlOrBase64.trim().getBytes(StandardCharsets.UTF_8);
            } else if (dataUrlOrBase64.startsWith("data:")) {
                int commaIndex = dataUrlOrBase64.indexOf(",");
                if (commaIndex != -1) {
                    String header = dataUrlOrBase64.substring(0, commaIndex);
                    String content = dataUrlOrBase64.substring(commaIndex + 1);
                    if (header.contains(";base64")) {
                        bytes = Base64.decode(content, Base64.DEFAULT);
                    } else {
                        bytes = URLDecoder.decode(content, "UTF-8").getBytes(StandardCharsets.UTF_8);
                    }
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
            } else {
                saveViaSAF(bytes, fileName);
            }
        } catch (Exception e) {
            Log.e(TAG, "存檔解析失敗", e);
            Toast.makeText(this, "❌ 匯出失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
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
                Log.e(TAG, "MediaStore 寫入失敗", e);
            }
            return false;
        }

        try {
            File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!dir.exists()) dir.mkdirs();
            try (FileOutputStream fos = new FileOutputStream(new File(dir, fileName))) {
                fos.write(bytes);
                fos.flush();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "直接寫入失敗", e);
            return false;
        }
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
                fos.flush();
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cacheFile);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "儲存遊戲存檔: " + fileName));
        } catch (Exception e) {
            Toast.makeText(this, "分享失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String buildSaveFileName(String rawName, byte[] bytes) {
        String base = rawName == null ? "" : rawName.trim();
        base = base.replaceAll("(?i)\\.(json|txt|sav|dat|bin)$", "").trim();

        if (base.matches("(?i)(idle[_-]?lineage[_-]?save|save|savefile|download|downloadfile|export|progress|存檔|下載|進度|未命名|idle_save)?")) {
            base = "";
        }
        if (base.isEmpty()) {
            base = extractCharInfo(bytes);
        }
        if (base.isEmpty()) {
            base = "存檔_" + timestamp();
        }
        if (!SAVE_NAME_PREFIX.isEmpty() && !base.startsWith(SAVE_NAME_PREFIX)) {
            base = SAVE_NAME_PREFIX + "_" + base;
        }
        return sanitizeFileName(base) + ".json";
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
                    "charName", "characterName", "playerName", "nickName", "nickname", "cname", "charname", "name"});
        } catch (Exception e) {
            return "";
        }
    }

    private String mapClass(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String v = raw.trim();
        if (v.matches(".*[\\u4e00-\\u9fff].*")) {
            return v.length() > 6 ? v.substring(0, 6) : v;
        }
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
                .replaceAll("_{2,}", "_")
                .replaceAll("^[._]+", "")
                .trim();
        if (out.length() > 80) out = out.substring(0, 80);
        return out.isEmpty() ? "存檔" : out;
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.TAIWAN).format(new Date());
    }

    private String loadSaveHookJs() {
        if (saveHookJs != null) return saveHookJs;
        try (InputStream is = getAssets().open("save_hook.js");
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            saveHookJs = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "讀取 assets/save_hook.js 失敗，使用內嵌版本", e);
            saveHookJs = getEmbeddedSaveHook();
        }
        return saveHookJs;
    }

    private String getEmbeddedSaveHook() {
        return "(function() {\n" +
                "    'use strict';\n" +
                "    if (window.__IDLE_SAVE_HOOK_LOADED__) return;\n" +
                "    window.__IDLE_SAVE_HOOK_LOADED__ = true;\n" +
                "    console.log('🚀 [SaveHook] 線上雙向存檔腳本注入成功！');\n" +
                "    const originalCreateObjectURL = URL.createObjectURL;\n" +
                "    URL.createObjectURL = function(blob) {\n" +
                "        const url = originalCreateObjectURL.apply(this, arguments);\n" +
                "        if (blob && (blob.type.includes('json') || blob.type.includes('text') || blob.type.includes('octet-stream'))) {\n" +
                "            const reader = new FileReader();\n" +
                "            reader.onloadend = function() {\n" +
                "                const base64data = reader.result;\n" +
                "                if (window.Android && window.Android.saveBase64File) {\n" +
                "                    window.Android.saveBase64File(base64data, blob.type || 'application/json', 'idle_save.json');\n" +
                "                }\n" +
                "            };\n" +
                "            reader.readAsDataURL(blob);\n" +
                "        }\n" +
                "        return url;\n" +
                "    };\n" +
                "    window.__dumpAllLocalStorage = function() {\n" +
                "        let dump = {};\n" +
                "        for (let i = 0; i < localStorage.length; i++) {\n" +
                "            let key = localStorage.key(i);\n" +
                "            dump[key] = localStorage.getItem(key);\n" +
                "        }\n" +
                "        return JSON.stringify(dump);\n" +
                "    };\n" +
                "    if (window.Android && window.Android.log) {\n" +
                "        window.Android.log('SaveHook Ready');\n" +
                "    }\n" +
                "})();";
    }

    private void initFileChooserLauncher() {
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (filePathCallback == null) return;
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        if (result.getData().getData() != null) {
                            results = new Uri[]{result.getData().getData()};
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
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && pendingSaveBytes != null) {
                        try (OutputStream os = getContentResolver().openOutputStream(result.getData().getData())) {
                            if (os != null) {
                                os.write(pendingSaveBytes);
                                os.flush();
                                Toast.makeText(this, "✅ 檔案已成功儲存！", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "寫入失敗：" + e.getMessage(), Toast.LENGTH_LONG).show();
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
        public void saveBase64File(String dataUrlOrBase64, String mimeType, String fileName) {
            processAndSaveFile(dataUrlOrBase64, mimeType, fileName);
        }

        @JavascriptInterface
        public void saveBase64File(String base64Data, String fileName) {
            processAndSaveFile(base64Data, "application/json", fileName);
        }

        @JavascriptInterface
        public void log(String message) {
            Log.d(TAG, "[JS] " + message);
        }

        @JavascriptInterface
        public void toast(String message) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
