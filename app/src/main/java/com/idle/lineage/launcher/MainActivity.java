package com.idle.lineage.launcher;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
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

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<Intent> createDocumentLauncher;

    private byte[] pendingSaveBytes = null;
    private String saveHookJs = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initFileChooserLauncher();
        initCreateDocumentLauncher();

        webView = new WebView(this);
        setContentView(webView);
        setupWebView();
        loadLauncher();
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);

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
                    return false;
                }
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // 只注入 save_hook.js，其餘邏輯都在裡面
                String js = loadSaveHookJs();
                if (js != null && !js.isEmpty()) {
                    view.evaluateJavascript(js, null);
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
                    return true; // 交由 save_hook.js 處理
                }
                return false;
            }
        });
    }

    private void loadLauncher() {
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>" +
                "body{background:#121212;color:#fff;font-family:sans-serif;padding:20px;display:flex;justify-content:center;align-items:center;min-height:90vh;margin:0;}" +
                ".card{background:#1e1e1e;border-radius:16px;padding:24px;width:100%;max-width:380px;text-align:center;}" +
                "h2{font-size:20px;margin-bottom:8px;}" +
                ".subtitle{color:#8e8e93;font-size:13px;margin-bottom:24px;}" +
                "select{width:100%;padding:12px;background:#2c2c2e;color:#fff;border:1px solid #3a3a3c;border-radius:8px;font-size:15px;margin-bottom:24px;}" +
                ".btn{width:100%;padding:14px;background:#28a745;color:#fff;border:none;border-radius:8px;font-size:16px;font-weight:bold;}" +
                "</style></head><body>" +
                "<div class='card'>" +
                "<h2>放置天堂啟動器</h2>" +
                "<div class='subtitle'>選擇伺服器後自動注入外掛與存檔支援</div>" +
                "<select id='serverSelect'>" +
                "<option value='https://pp771007.github.io/idle-lineage-class/'>伺服器一 (加掛版)</option>" +
                "<option value='https://shines871.github.io/idle-lineage-class/'>伺服器二 (原版)</option>" +
                "</select>" +
                "<button class='btn' onclick='location.href=document.getElementById(\"serverSelect\").value'>啟動遊戲</button>" +
                "</div></body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    /* ==================== 存檔寫入核心 ==================== */

    private void processAndSaveFile(String data, String mimeType, String fileName) {
        if (data == null || data.isEmpty() || data.startsWith("blob:")) return;

        try {
            byte[] bytes;
            if (data.contains("SIG1:")) {
                bytes = data.substring(data.indexOf("SIG1:")).getBytes(StandardCharsets.UTF_8);
            } else if (data.trim().startsWith("{") || data.trim().startsWith("[")) {
                bytes = data.trim().getBytes(StandardCharsets.UTF_8);
            } else if (data.startsWith("data:")) {
                int idx = data.indexOf(",");
                if (idx != -1) {
                    String header = data.substring(0, idx);
                    String content = data.substring(idx + 1);
                    bytes = header.contains("base64") ?
                            Base64.decode(content, Base64.DEFAULT) :
                            URLDecoder.decode(content, "UTF-8").getBytes(StandardCharsets.UTF_8);
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
            } else {
                saveViaSAF(bytes, fileName);
            }
        } catch (Exception e) {
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
                Log.e(TAG, "MediaStore 失敗", e);
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

    private void saveViaSAF(byte[] bytes, String fileName) {
        pendingSaveBytes = bytes;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        try {
            createDocumentLauncher.launch(intent);
        } catch (Exception e) {
            shareFile(bytes, fileName);
        }
    }

    private void shareFile(byte[] data, String fileName) {
        try {
            File cache = new File(getCacheDir(), fileName);
            try (FileOutputStream fos = new FileOutputStream(cache)) {
                fos.write(data);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cache);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("application/json");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "儲存存檔"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失敗", Toast.LENGTH_SHORT).show();
        }
    }

    private String buildSaveFileName(String raw, byte[] bytes) {
        String base = raw == null ? "" : raw.replaceAll("(?i)\\.(json|txt|sav)$", "").trim();
        if (base.isEmpty() || base.matches("(?i)(save|download|export|idle_save|存檔).*")) {
            base = extractCharInfo(bytes);
        }
        if (base.isEmpty()) {
            base = "存檔_" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.TAIWAN).format(new Date());
        }
        return base.replaceAll("[\\\\/:*?\"<>|]", "_") + ".json";
    }

    private String extractCharInfo(byte[] bytes) {
        try {
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (text.contains("SIG1:")) {
                int i = text.indexOf("SIG1:");
                String body = text.substring(i + 5);
                int colon = body.indexOf(':');
                if (colon > 0) body = body.substring(colon + 1);
                if (body.startsWith("{")) text = body;
            }
            Matcher lv = Pattern.compile("\"(?:charLevel|level|lv)\"\\s*:\\s*(\\d{1,3})").matcher(text);
            String level = lv.find() ? lv.group(1) : "";
            Matcher cl = Pattern.compile("\"(?:cls|class|className|job)\"\\s*:\\s*\"?([^\"\\s,]{1,10})\"?").matcher(text);
            String cls = cl.find() ? cl.group(1) : "";
            if (!level.isEmpty() || !cls.isEmpty()) {
                return (level.isEmpty() ? "" : level + "等") + cls;
            }
        } catch (Exception ignored) {}
        return "";
    }

    private String loadSaveHookJs() {
        if (saveHookJs != null) return saveHookJs;
        try (InputStream is = getAssets().open("save_hook.js");
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = is.read(buf)) != -1) bos.write(buf, 0, len);
            saveHookJs = bos.toString("UTF-8");
        } catch (Exception e) {
            Log.e(TAG, "讀取 save_hook.js 失敗", e);
            saveHookJs = "";
        }
        return saveHookJs;
    }

    private void initFileChooserLauncher() {
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (filePathCallback == null) return;
                    Uri[] uris = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                        uris = new Uri[]{result.getData().getData()};
                    }
                    filePathCallback.onReceiveValue(uris);
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
                                Toast.makeText(this, "✅ 檔案已儲存", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "寫入失敗", Toast.LENGTH_SHORT).show();
                        }
                    }
                    pendingSaveBytes = null;
                });
    }

    @Keep
    public class AndroidBridge {
        @JavascriptInterface
        public void saveBase64File(String data, String mime, String fileName) {
            processAndSaveFile(data, mime, fileName);
        }

        @JavascriptInterface
        public void saveBase64File(String data, String fileName) {
            processAndSaveFile(data, "application/json", fileName);
        }

        @JavascriptInterface
        public void log(String msg) {
            Log.d(TAG, "[JS] " + msg);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
