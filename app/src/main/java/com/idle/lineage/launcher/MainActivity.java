package com.idle.lineage.launcher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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

import com.idle.lineage.launcher.plugin.PluginRuntime;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private WebView webView;
    private LinearLayout launcherLayout;
    private ProgressBar progressBar;
    private TextView statusText;

    private ValueCallback<Uri[]> uploadMessage;
    private ActivityResultLauncher<Intent> fileChooserLauncher;
    private ActivityResultLauncher<Intent> exportFileLauncher;

    private String pendingExportData = null;
    private String pendingExportFilename = null;

    private final String URL_SERVER_A = "https://shines871.github.io/idle-lineage-class/";
    private final String URL_SERVER_B = "https://pp771007.github.io/idle-lineage-class/";
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
        title.setTextSize(22);
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

        root.addView(launcherLayout);

        webView = new WebView(context);
        webView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        webView.setVisibility(View.GONE);
        root.addView(webView);

        return root;
    }

    private void showLauncher() {
        if (webView != null) webView.setVisibility(View.GONE);
        if (launcherLayout != null) launcherLayout.setVisibility(View.VISIBLE);
    }

    private void launchGame(String url) {
        if (launcherLayout != null) launcherLayout.setVisibility(View.GONE);
        if (webView != null) {
            webView.setVisibility(View.VISIBLE);
            if (webView.getUrl() == null || !webView.getUrl().equals(url)) {
                webView.loadUrl(url);
            }
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
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                injectScripts(view);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectScripts(view);
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
                if (newProgress > 30) {
                    injectScripts(view);
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
                                    Toast.makeText(this, "存檔匯出成功！", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "寫入檔案失敗: " + e.getMessage(), e);
                                Toast.makeText(this, "寫入檔案失敗", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                    pendingExportData = null;
                    pendingExportFilename = null;
                }
        );
    }

    private void injectScripts(WebView view) {
        String initScript = "javascript:(function() {" +
                "if (window.__NATIVE_CONTAINER_INITIALIZED__) return;" +
                "window.__NATIVE_CONTAINER_INITIALIZED__ = true;" +

                "function loadExternalScript(url, cb) {" +
                "  var s = document.createElement('script');" +
                "  s.src = url + (url.indexOf('?') >= 0 ? '&' : '?') + 't=' + Date.now();" +
                "  s.onload = function() { if (cb) cb(); };" +
                "  s.onerror = function() { if (cb) cb(); };" +
                "  document.head.appendChild(s);" +
                "}" +

                "loadExternalScript('" + URL_MAIN_USER_JS + "', function() {" +
                "  console.log('[NativeContainer] 主腳本載入完成');" +
                "});" +

                "})();";

        view.evaluateJavascript(initScript, null);

        view.postDelayed(() -> {
            view.evaluateJavascript(
                    PluginRuntime.buildRuntimeScript(),
                    null
            );
        }, 2000);
    }

    // 正確的 public 宣告，允許原生外部呼叫，只通知執行 window.exportSavePortable(slot)
    public void requestPortableExport(int slot) {
        if (webView != null) {
            webView.evaluateJavascript(
                "window.exportSavePortable && window.exportSavePortable(" + slot + ");", 
                null
            );
        }
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void saveJson(String json, String filename) {
            if (json == null || json.isEmpty()) return;
            pendingExportData = json;
            pendingExportFilename = (filename != null && !filename.isEmpty()) ? filename : "game_save.json";

            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, pendingExportFilename);
                exportFileLauncher.launch(intent);
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
