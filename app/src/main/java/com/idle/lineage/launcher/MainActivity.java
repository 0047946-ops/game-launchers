package com.idlelineage.container;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private LinearLayout launcherLayout;
    private ProgressBar progressBar;
    private TextView statusText;

    private ValueCallback<Uri[]> uploadMessage;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<Intent> exportFileLauncher;
    private ActivityResultLauncher<Intent> importFileLauncher;

    private String pendingExportData = null;
    private String pendingExportFilename = null;

    private final String URL_SERVER_A = "https://shines871.github.io/idle-lineage-class/";
    private final String URL_SERVER_B = "https://pp771007.github.io/idle-lineage-class/";

    private final String URL_SAVE_HOOK = "https://raw.githubusercontent.com/0047946-ops/game-launchers/main/app/src/main/assets/save_hook.js";
    private final String URL_MAIN_USER_JS = "https://raw.githubusercontent.com/0047946-ops/game-launchers/main/scripts/main.user.js";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createUI(this));

        initActivityResultLaunchers();
        initWebViewSettings();
        showLauncher();
    }

    private View createUI(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 8));
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        root.addView(progressBar);

        launcherLayout = new LinearLayout(context);
        launcherLayout.setOrientation(LinearLayout.VERTICAL);
        launcherLayout.setGravity(android.view.Gravity.CENTER);
        launcherLayout.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        launcherLayout.setPadding(64, 64, 64, 64);

        TextView title = new TextView(context);
        title.setText("Idle Lineage Container");
        title.setTextSize(24);
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, 48);
        launcherLayout.addView(title);

        statusText = new TextView(context);
        statusText.setText("請選擇遊戲伺服器");
        statusText.setTextSize(16);
        statusText.setGravity(android.view.Gravity.CENTER);
        statusText.setPadding(0, 0, 0, 32);
        launcherLayout.addView(statusText);

        Button btnServerA = new Button(context);
        btnServerA.setText("一服：原作者伺服器");
        btnServerA.setOnClickListener(v -> launchGame(URL_SERVER_A));
        launcherLayout.addView(btnServerA);

        Button btnServerB = new Button(context);
        btnServerB.setText("二服：加掛者伺服器");
        btnServerB.setOnClickListener(v -> launchGame(URL_SERVER_B));
        launcherLayout.addView(btnServerB);

        // 新增：Launcher 手動匯出按鈕
        Button btnExport = new Button(context);
        btnExport.setText("手動匯出存檔");
        btnExport.setOnClickListener(v -> {
            if (webView != null && webView.getVisibility() == View.VISIBLE) {
                webView.evaluateJavascript("if(window.SaveEngine && typeof window.SaveEngine.export === 'function'){ window.SaveEngine.export(); }else{ alert('請先進入遊戲再執行匯出'); }", null);
            } else {
                Toast.makeText(this, "請先啟動遊戲進入伺服器", Toast.LENGTH_SHORT).show();
            }
        });
        launcherLayout.addView(btnExport);

        // 新增：Launcher 手動匯入按鈕
        Button btnImport = new Button(context);
        btnImport.setText("手動匯入存檔");
        btnImport.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            importFileLauncher.launch(intent);
        });
        launcherLayout.addView(btnImport);

        root.addView(launcherLayout);

        webView = new WebView(context);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setVisibility(View.GONE);
        root.addView(webView);

        return root;
    }

    private void showLauncher() {
        if (webView != null) {
            webView.setVisibility(View.GONE);
        }
        if (launcherLayout != null) {
            launcherLayout.setVisibility(View.VISIBLE);
        }
    }

    private void launchGame(String url) {
        if (launcherLayout != null) {
            launcherLayout.setVisibility(View.GONE);
        }
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
            webView.loadUrl(url);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebViewSettings() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectAllScripts(view, url);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progressBar != null) {
                    if (newProgress == 100) {
                        progressBar.setVisibility(View.GONE);
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                        progressBar.setProgress(newProgress);
                    }
                }
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                }
                uploadMessage = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    uploadMessage = null;
                    return false;
                }
                return true;
            }
        });
    }

    private void initActivityResultLaunchers() {
        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (uploadMessage == null) return;
                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String dataString = result.getData().getDataString();
                        if (dataString != null) {
                            results = new Uri[]{Uri.parse(dataString)};
                        } else if (result.getData().getClipData() != null) {
                            int count = result.getData().getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = result.getData().getClipData().getItemAt(i).getUri();
                            }
                        }
                    }
                    uploadMessage.onReceiveValue(results);
                    uploadMessage = null;
                }
        );

        exportFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null && pendingExportData != null) {
                            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                                if (os != null) {
                                    os.write(pendingExportData.getBytes(StandardCharsets.UTF_8));
                                    os.flush();
                                    Toast.makeText(this, "存檔匯出成功", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Toast.makeText(this, "存檔匯出失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                    pendingExportData = null;
                    pendingExportFilename = null;
                }
        );

        importFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))) {
                                StringBuilder stringBuilder = new StringBuilder();
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    stringBuilder.append(line);
                                }
                                String jsonContent = stringBuilder.toString();
                                
                                // 支援多重驗證特徵 (SIG1 / SIG2 / IDLE_LINEAGE_SAVE / afk_backup_bundle_v1)
                                boolean isValid = jsonContent.contains("IDLE_LINEAGE_SAVE") || 
                                                  jsonContent.contains("afk_backup_bundle_v1") || 
                                                  jsonContent.contains("SIG1") || 
                                                  jsonContent.contains("SIG2");
                                if (!isValid) {
                                    Toast.makeText(this, "錯誤：不是有效存檔", Toast.LENGTH_SHORT).show();
                                    return;
                                }

                                String base64Data = Base64.encodeToString(jsonContent.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                                String js = "if (window.SaveEngine && typeof window.SaveEngine.restoreBase64 === 'function') { window.SaveEngine.restoreBase64('" + base64Data + "'); } else { alert('SaveEngine 尚未載入'); }";
                                webView.evaluateJavascript(js, null);
                            } catch (Exception e) {
                                Toast.makeText(this, "讀取匯入檔案失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
        );
    }

    private void injectAllScripts(WebView view, String currentUrl) {
        String initScript = "javascript:(function() {" +
                "if (window.__PLUGIN_URL__ === '" + currentUrl + "') return;" +
                "window.__PLUGIN_URL__ = '" + currentUrl + "';" +
                
                // 強化 SaveEngine v2：完整備份 localStorage，支援 SIG1 / SIG2、afk_backup_bundle_v1 與防特殊字元編碼
                "window.SaveEngine = {" +
                "  export: function() {" +
                "    var dataObj = {};" +
                "    for (var i = 0; i < localStorage.length; i++) {" +
                "      var k = localStorage.key(i);" +
                "      if (k) {" +
                "        dataObj[k] = localStorage.getItem(k);" +
                "      }" +
                "    }" +
                "    var pkg = {" +
                "      type: 'IDLE_LINEAGE_SAVE'," +
                "      version: 2," +
                "      sig1: true," +
                "      sig2: true," +
                "      bundle: 'afk_backup_bundle_v1'," +
                "      time: new Date().toISOString()," +
                "      source: 'AndroidContainer'," +
                "      data: dataObj" +
                "    };" +
                "    var jsonStr = JSON.stringify(pkg);" +
                "    if (window.AndroidBridge && typeof window.AndroidBridge.exportGameSave === 'function') {" +
                "      window.AndroidBridge.exportGameSave(jsonStr, 'idle_lineage_save_' + Date.now() + '.json');" +
                "    }" +
                "  }," +
                "  restoreBase64: function(base64Str) {" +
                "    try {" +
                "      var binString = window.atob(base64Str);" +
                "      var bytes = Uint8Array.from(binString, (m) => m.codePointAt(0));" +
                "      var jsonStr = new TextDecoder().decode(bytes);" +
                "      var pkg = JSON.parse(jsonStr);" +
                "      var isValid = pkg.type === 'IDLE_LINEAGE_SAVE' || pkg.bundle === 'afk_backup_bundle_v1' || pkg.sig1 || pkg.sig2 || jsonStr.indexOf('SIG1') >= 0 || jsonStr.indexOf('SIG2') >= 0;" +
                "      if (!isValid) { alert('不是有效存檔'); return; }" +
                "      var dataObj = pkg.data || pkg;" +
                "      if (typeof dataObj === 'object' && dataObj !== null) {" +
                "        for (var k in dataObj) {" +
                "          if (dataObj.hasOwnProperty(k)) {" +
                "            localStorage.setItem(k, dataObj[k]);" +
                "          }" +
                "        }" +
                "      }" +
                "      alert('【還原成功】存檔已寫入，網頁即將重新整理！');" +
                "      window.location.reload();" +
                "    } catch (e) {" +
                "      alert('還原失敗: ' + e.message);" +
                "    }" +
                "  }" +
                "};" +

                // 外掛載入順序：SaveEngine -> save_hook.js -> main.user.js (帶防快取參數)
                "function loadExternalScript(url, callback) {" +
                "  var s = document.createElement('script');" +
                "  s.src = url + (url.indexOf('?') >= 0 ? '&' : '?') + 't=' + Date.now();" +
                "  s.onload = function() { if (callback) callback(); };" +
                "  s.onerror = function() { console.log('Failed to load script: ' + url); if (callback) callback(); };" +
                "  document.head.appendChild(s);" +
                "}" +

                "loadExternalScript('" + URL_SAVE_HOOK + "', function() {" +
                "  loadExternalScript('" + URL_MAIN_USER_JS + "', function() {" +
                "    console.log('All hooks and scripts sequence initialized with anti-cache and v2 engine.');" +
                "  });" +
                "});" +

                "})();";

        view.evaluateJavascript(initScript, null);
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void exportGameSave(String json, String filename) {
            pendingExportData = json;
            pendingExportFilename = filename != null ? filename : "idle_lineage_save.json";
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, pendingExportFilename);
                exportFileLauncher.launch(intent);
            });
        }

        @JavascriptInterface
        public void importGameSave(String json) {
            runOnUiThread(() -> {
                boolean isValid = json.contains("IDLE_LINEAGE_SAVE") || 
                                  json.contains("afk_backup_bundle_v1") || 
                                  json.contains("SIG1") || 
                                  json.contains("SIG2");
                if (!isValid) {
                    Toast.makeText(MainActivity.this, "錯誤：不是有效存檔", Toast.LENGTH_SHORT).show();
                    return;
                }
                String base64Data = Base64.encodeToString(json.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
                String js = "if (window.SaveEngine && typeof window.SaveEngine.restoreBase64 === 'function') { window.SaveEngine.restoreBase64('" + base64Data + "'); }";
                webView.evaluateJavascript(js, null);
            });
        }

        @JavascriptInterface
        public void triggerImportFilePicker() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                importFileLauncher.launch(intent);
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack() && webView.getVisibility() == View.VISIBLE) {
            webView.goBack();
        } else if (webView != null && webView.getVisibility() == View.VISIBLE) {
            showLauncher();
        } else {
            super.onBackPressed();
        }
    }
}
