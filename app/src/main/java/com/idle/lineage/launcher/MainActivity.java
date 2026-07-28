package com.yourpackage.game_launcher;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog.Builder;
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

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "GameLauncher";
    private static final String PREFS_NAME = "LauncherPrefs";
    private static final String KEY_LAST_URL = "last_url";
    private static final String SAVE_NAME_PREFIX = "";

    private static final String[] PRESET_URLS = new String[] {
            "https://example.com/",
            "https://example.org/"
    };

    private WebView webView;
    private RelativeLayout rootContainer;
    private LinearLayout homePanel;
    private RelativeLayout loadingOverlay;
    private ProgressBar progressBar;
    private TextView tvLoadingStatus;
    private EditText etUrl;

    private SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ValueCallback<Uri[]> filePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<Intent> createDocumentLauncher;

    private byte[] pendingSaveBytes = null;
    private String pendingSaveFileName = null;
    private String saveHookJs = "";

    @Keep
    public class WebAppInterface {
        @JavascriptInterface
        @Keep
        public void saveBase64File(String dataUrlOrBase64, String mimeType, String fileName) {
            runOnUiThread(() -> processAndSaveFile(dataUrlOrBase64, mimeType, fileName));
        }

        @JavascriptInterface
        @Keep
        public void pickSaveSlot(String slotsJson) {
            runOnUiThread(() -> showSlotChooser(slotsJson));
        }

        @JavascriptInterface
        @Keep
        public void log(String message) {
            Log.d(TAG, "[JS] " + message);
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

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        buildUi();
        initFileChooserLauncher();
        initCreateDocumentLauncher();
        setupWebView();

        String lastUrl = prefs.getString(KEY_LAST_URL, PRESET_URLS[0]);
        etUrl.setText(lastUrl);
    }

    private void buildUi() {
        rootContainer = new RelativeLayout(this);
        rootContainer.setLayoutParams(new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        ));

        webView = new WebView(this);

        RelativeLayout.LayoutParams webLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );
        webView.setLayoutParams(webLp);

        homePanel = new LinearLayout(this);
        homePanel.setOrientation(LinearLayout.VERTICAL);
        homePanel.setPadding(48, 48, 48, 48);
        homePanel.setGravity(Gravity.CENTER_HORIZONTAL);

        RelativeLayout.LayoutParams homeLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );
        homePanel.setLayoutParams(homeLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(32, 32, 32, 32);

        etUrl = new EditText(this);
        etUrl.setHint("輸入遊戲網址");
        etUrl.setSingleLine(true);
        etUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        etUrl.setImeOptions(EditorInfo.IME_ACTION_GO);
        card.addView(etUrl);

        Button btnOpen = new Button(this);
        btnOpen.setText("開啟網址");
        btnOpen.setOnClickListener(v -> openCurrentUrl());
        card.addView(btnOpen);

        Button btnSave = new Button(this);
        btnSave.setText("儲存目前網址");
        btnSave.setOnClickListener(v -> {
            String url = normalizeUrl(etUrl.getText().toString().trim());
            if (url.isEmpty()) {
                Toast.makeText(this, "請輸入網址", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(KEY_LAST_URL, url).apply();
            etUrl.setText(url);
            Toast.makeText(this, "已儲存", Toast.LENGTH_SHORT).show();
        });
        card.addView(btnSave);

        Button btnPreset = new Button(this);
        btnPreset.setText("選擇內建網址");
        btnPreset.setOnClickListener(v -> showPresetPicker());
        card.addView(btnPreset);

        Button btnBackHome = new Button(this);
        btnBackHome.setText("回到首頁");
        btnBackHome.setOnClickListener(v -> showHome());
        card.addView(btnBackHome);

        Button btnReloadHook = new Button(this);
        btnReloadHook.setText("重新注入外掛");
        btnReloadHook.setOnClickListener(v -> injectHookJs());
        card.addView(btnReloadHook);

        scroll.addView(card);
        homePanel.addView(scroll);

        loadingOverlay = new RelativeLayout(this);
        loadingOverlay.setVisibility(View.GONE);
        loadingOverlay.setBackgroundColor(0xCC000000);

        RelativeLayout.LayoutParams overlayLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.MATCH_PARENT
        );
        loadingOverlay.setLayoutParams(overlayLp);

        LinearLayout loadingBox = new LinearLayout(this);
        loadingBox.setOrientation(LinearLayout.VERTICAL);
        loadingBox.setPadding(48, 48, 48, 48);

        RelativeLayout.LayoutParams boxLp = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT
        );
        boxLp.addRule(RelativeLayout.CENTER_IN_PARENT);
        boxLp.setMargins(48, 48, 48, 48);
        loadingBox.setLayoutParams(boxLp);

        tvLoadingStatus = new TextView(this);
        tvLoadingStatus.setTextColor(0xFFFFFFFF);
        tvLoadingStatus.setTextSize(16f);
        tvLoadingStatus.setText("載入中...");
        loadingBox.addView(tvLoadingStatus);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setMax(100);
        loadingBox.addView(progressBar);

        loadingOverlay.addView(loadingBox);

        rootContainer.addView(webView);
        rootContainer.addView(homePanel);
        rootContainer.addView(loadingOverlay);

        setContentView(rootContainer);
        showHome();
    }

    private void setupWebView() {
        WebView.setWebContentsDebuggingEnabled(true);

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
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "JS: " + consoleMessage.message());
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams fileChooserParams) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;

                Intent intent = fileChooserParams.createIntent();
                try {
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    filePathCallback = null;
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
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                showLoadingUI("載入頁面中...");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectHookJs();
                hideLoadingUI();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            String suggested = guessFileName(contentDisposition, url);
            runOnUiThread(() -> processAndSaveFile(url, mimetype, suggested));
        });
    }

    private void injectHookJs() {
        String js = loadSaveHookJs();
        if (js == null || js.isEmpty()) return;
        webView.evaluateJavascript(js, null);
    }

    private String loadSaveHookJs() {
        if (saveHookJs != null && !saveHookJs.isEmpty()) return saveHookJs;
        try (InputStream is = getAssets().open("save_hook.js");
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                bos.write(buffer, 0, read);
            }
            saveHookJs = new String(bos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "讀取 save_hook.js 失敗", e);
            saveHookJs = "";
        }
        return saveHookJs;
    }

    private void openCurrentUrl() {
        String url = normalizeUrl(etUrl.getText().toString().trim());
        if (url.isEmpty()) {
            Toast.makeText(this, "請輸入網址", Toast.LENGTH_SHORT).show();
            return;
        }
        prefs.edit().putString(KEY_LAST_URL, url).apply();
        showWebView();
        webView.loadUrl(url);
    }

    private void showPresetPicker() {
        new AlertDialog.Builder(this)
                .setTitle("選擇內建網址")
                .setItems(PRESET_URLS, (dialog, which) -> {
                    etUrl.setText(PRESET_URLS[which]);
                    prefs.edit().putString(KEY_LAST_URL, PRESET_URLS[which]).apply();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHome() {
        homePanel.setVisibility(View.VISIBLE);
        webView.setVisibility(View.GONE);
        loadingOverlay.setVisibility(View.GONE);
    }

    private void showWebView() {
        homePanel.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
    }

    private void showLoadingUI(String statusText) {
        loadingOverlay.setVisibility(View.VISIBLE);
        tvLoadingStatus.setText(statusText);
        progressBar.setIndeterminate(true);
    }

    private void hideLoadingUI() {
        loadingOverlay.setVisibility(View.GONE);
    }

    private void processAndSaveFile(String dataUrlOrBase64, String mimeType, String fileName) {
        if (dataUrlOrBase64 == null || dataUrlOrBase64.isEmpty()) return;
        if (dataUrlOrBase64.startsWith("blob:")) return;

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
                        String decodedText = URLDecoder.decode(content, "UTF-8");
                        bytes = decodedText.getBytes(StandardCharsets.UTF_8);
                    }
                } else {
                    bytes = dataUrlOrBase64.getBytes(StandardCharsets.UTF_8);
                }
            } else if (dataUrlOrBase64.matches("[A-Za-z0-9+/=\\r\
]{16,}")) {
                try {
                    bytes = Base64.decode(dataUrlOrBase64, Base64.DEFAULT);
                } catch (Exception e) {
                    bytes = dataUrlOrBase64.getBytes(StandardCharsets.UTF_8);
                }
            } else {
                bytes = dataUrlOrBase64.getBytes(StandardCharsets.UTF_8);
            }

            fileName = buildSaveFileName(fileName, bytes);

            if (!writeToDownloads(bytes, fileName, mimeType != null ? mimeType : "application/json")) {
                saveViaSAF(bytes, fileName);
            } else {
                Toast.makeText(MainActivity.this, "✅ 已匯出：" + fileName, Toast.LENGTH_LONG).show();
                notifyJsExported();
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
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) downloadsDir.mkdirs();
            File file = new File(downloadsDir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(bytes);
                fos.flush();
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Direct Write 寫入失敗", e);
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

            Uri contentUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", cacheFile);

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, "儲存遊戲存檔: " + fileName));
        } catch (Exception e) {
            showDebugDialog("❌ Share 分享選單失敗", e.getMessage());
        }
    }

    private void notifyJsExported() {
        mainHandler.post(() -> {
            try {
                webView.evaluateJavascript("window.__markExported && window.__markExported();", null);
            } catch (Exception ignored) {
            }
        });
    }

    private void showSlotChooser(String slotsJson) {
        try {
            JSONArray arr = new JSONArray(slotsJson);
            if (arr.length() == 0) {
                new AlertDialog.Builder(this)
                        .setTitle("找不到任何存檔")
                        .setMessage("網頁端沒有回報任何存檔欄位。")
                        .setPositiveButton("關閉", null)
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
                        String js = "window.__exportSlotByKey && window.__exportSlotByKey(" + JSONObject.quote(keys[which]) + ");";
                        webView.evaluateJavascript(js, null);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Exception e) {
            showDebugDialog("❌ 讀取存檔清單失敗", e.toString());
        }
    }

    private String buildSaveFileName(String rawName, byte[] bytes) {
        String base = rawName == null ? "" : rawName.trim();
        base = base.replaceAll("(?i)\\.(json|txt|sav|dat|bin)$", "").trim();

        if (base.matches("(?i)(idle[_-]?lineage[_-]?save|save|savefile|download|downloadfile|export|progress|存檔|下載|進度|未命名)?")) {
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

            String level = firstNumber(probe, new String[]{"charLevel", "level", "lv", "lvl"});
            String rawClass = firstMatch(probe, new String[]{"cls", "class", "charClass", "className", "job", "career"});
            String cls = mapClass(rawClass);

            String out = "";
            if (!level.isEmpty()) out += level + "等";
            if (!cls.isEmpty()) out += cls;
            if (!out.isEmpty()) return out;

            return firstMatch(probe, new String[]{"charName", "characterName", "playerName", "nickName", "nickname", "cname", "charname", "name"});
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
            case "prince":
            case "royal":
            case "king":
            case "royalty":
                return "王子";
            case "knight":
            case "kn":
                return "騎士";
            case "mage":
            case "wizard":
            case "wiz":
                return "法師";
            case "elf":
                return "妖精";
            case "darkelf":
            case "de":
                return "黑暗妖精";
            case "illusionist":
            case "illusion":
            case "il":
                return "幻術士";
            case "dragonknight":
            case "dk":
                return "龍騎士";
            case "warrior":
            case "fighter":
            case "wa":
                return "戰士";
            default:
                return "";
        }
    }

    private String firstMatch(String text, String[] keys) {
        for (String key : keys) {
            try {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(""" + key + ""\\s*:\\s*"([^"\\\\]{1,24})"").matcher(text);
                if (m.find()) return m.group(1).trim();
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private String firstNumber(String text, String[] keys) {
        for (String key : keys) {
            try {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile(""" + key + ""\\s*:\\s*(\\d{1,3})").matcher(text);
                if (m.find()) return m.group(1);
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private String sanitizeFileName(String name) {
        String out = name.replaceAll("[\\\\/:*?"<>|\\r\
\\t\\x00-\\x1f]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^[._]+", "")
                .trim();
        if (out.length() > 80) out = out.substring(0, 80);
        return out.isEmpty() ? "存檔" : out;
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.TAIWAN).format(new Date());
    }

    private void initFileChooserLauncher() {
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (filePathCallback == null) return;
                    Uri[] results = null;

                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent dataIntent = result.getData();
                        if (dataIntent.getData() != null) {
                            results = new Uri[]{dataIntent.getData()};
                        } else if (dataIntent.getClipData() != null) {
                            int count = dataIntent.getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = dataIntent.getClipData().getItemAt(i).getUri();
                            }
                        }
                    }

                    filePathCallback.onReceiveValue(results);
                    filePathCallback = null;
                }
        );
    }

    private void initCreateDocumentLauncher() {
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK
                            && result.getData() != null
                            && result.getData().getData() != null) {
                        Uri uri = result.getData().getData();
                        if (pendingSaveBytes != null) {
                            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                                if (os != null) {
                                    os.write(pendingSaveBytes);
                                    os.flush();
                                    Toast.makeText(MainActivity.this, "✅ 檔案已成功儲存！", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                showDebugDialog("❌ SAF 寫入失敗", e.getMessage());
                            } finally {
                                pendingSaveBytes = null;
                                pendingSaveFileName = null;
                            }
                        }
                    } else {
                        pendingSaveBytes = null;
                        pendingSaveFileName = null;
                    }
                }
        );
    }

    private void checkAllFilesAccessPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }
    }

    private String guessFileName(String contentDisposition, String url) {
        try {
            if (contentDisposition != null && contentDisposition.contains("filename=")) {
                return contentDisposition.split("filename=")[1].replace(""", "").trim();
            }
        } catch (Exception ignored) {
        }

        try {
            if (url != null && url.contains("/")) {
                String last = url.substring(url.lastIndexOf('/') + 1);
                if (last.length() > 0 && last.length() < 100) return last;
            }
        } catch (Exception ignored) {
        }

        return "save_" + timestamp() + ".json";
    }

    private void showDebugDialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("確定", null)
                .show();
    }

    private boolean isAllowedUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private String normalizeUrl(String raw) {
        if (raw == null) return "";
        raw = raw.trim();
        if (raw.isEmpty()) return "";
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            raw = "https://" + raw;
        }
        return raw;
    }

    @Override
    public void onBackPressed() {
        if (homePanel.getVisibility() == View.VISIBLE) {
            super.onBackPressed();
            return;
        }
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            showHome();
        }
    }
}
