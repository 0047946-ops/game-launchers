package com.idle.lineage.launcher;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView webView;
    private ValueCallback<Uri[]> filePathCallback;
    private final static int FILE_CHOOSER_RESULT_CODE = 10001;

    private ActivityResultLauncher<Intent> createDocumentLauncher;
    private byte[] pendingSaveBytes = null;
    private String pendingSaveFileName = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        checkAllFilesAccessPermission();

        webView = new WebView(this);
        setContentView(webView);

        setupWebView();
        initCreateDocumentLauncher();

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

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
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                try {
                    startActivityForResult(params.createIntent(), FILE_CHOOSER_RESULT_CODE);
                } catch (Exception e) {
                    filePathCallback = null;
                }
                return true;
            }
        });
    }

    private void loadNativeLauncherHtml() {
        String html = "<!DOCTYPE html><html><head><meta charset='utf-8'>" +
                "<meta name='viewport' content='width=device-width, initial-scale=1.0'>" +
                "<style>body{background:#121212;color:#fff;padding:20px;display:flex;justify-content:center;align-items:center;min-height:90vh;margin:0;}" +
                ".card{background:#1e1e1e;border-radius:16px;padding:24px;width:100%;max-width:380px;text-align:center;}</style></head><body>" +
                "<div class='card'>" +
                "  <h2>🎮 放置天堂 啟動器</h2>" +
                "  <select id='server' style='width:100%;padding:12px;margin:15px 0;border-radius:8px;'>" +
                "    <option value='https://pp771007.github.io/idle-lineage-class/'>伺服器一 (pp771007)</option>" +
                "    <option value='https://shines871.github.io/idle-lineage-class/'>伺服器二 (shines871)</option>" +
                "  </select>" +
                "  <button onclick='launch()' style='width:100%;padding:14px;background:#28a745;color:white;border:none;border-radius:8px;font-size:16px;'>🚀 啟動遊戲</button>" +
                "</div>" +
                "<script>function launch(){ location.href = document.getElementById('server').value; }</script>" +
                "</body></html>";

        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
    }

    private void initCreateDocumentLauncher() {
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && pendingSaveBytes != null) {
                        Uri uri = result.getData().getData();
                        try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                            os.write(pendingSaveBytes);
                            Toast.makeText(this, "✅ 存檔已成功儲存！", Toast.LENGTH_LONG).show();
                        } catch (Exception e) {
                            Toast.makeText(this, "❌ 儲存失敗", Toast.LENGTH_SHORT).show();
                        }
                    }
                    pendingSaveBytes = null;
                });
    }

    // ===================== AndroidBridge（核心） =====================
    public class AndroidBridge {
        @android.webkit.JavascriptInterface
        public void saveBase64File(String base64Data, String fileName) {
            runOnUiThread(() -> {
                try {
                    byte[] bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT);
                    String smartName = buildSmartFileName(fileName);

                    if (!writeToDownloads(bytes, smartName)) {
                        saveViaSAF(bytes, smartName);
                    } else {
                        Toast.makeText(MainActivity.this, "✅ 已匯出：\n" + smartName, Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "❌ 匯出失敗", Toast.LENGTH_LONG).show();
                }
            });
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
                        os.write(bytes);
                    }
                    values.put(MediaStore.Downloads.IS_PENDING, 0);
                    getContentResolver().update(uri, values, null, null);
                    return true;
                }
            } catch (Exception ignored) {}
        }

        // 舊版 Android 直接寫入
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
        pendingSaveFileName = fileName;

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        createDocumentLauncher.launch(intent);
    }

    private String buildSmartFileName(String rawName) {
        String name = (rawName != null && !rawName.isEmpty()) ? rawName : "idle_save";
        name = name.replaceAll("\\.(json|sav)$", "").trim();
        if (name.isEmpty() || name.toLowerCase().contains("save")) {
            name = "idle_save_" + System.currentTimeMillis();
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".json";
    }

    private void checkAllFilesAccessPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_RESULT_CODE && filePathCallback != null) {
            filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            filePathCallback = null;
        }
    }
}
