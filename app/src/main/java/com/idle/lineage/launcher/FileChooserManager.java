package com.idle.lineage.launcher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;

public class FileChooserManager {
    private ValueCallback<Uri[]> filePathCallback;

    public boolean handleShowFileChooser(Activity activity, ValueCallback<Uri[]> callback, WebChromeClient.FileChooserParams params, int requestCode) {
        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
        }
        filePathCallback = callback;
        Intent intent = params.createIntent();
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            filePathCallback = null;
            return false;
        }
        return true;
    }

    public void handleActivityResult(int resultCode, Intent data) {
        if (filePathCallback == null) return;
        filePathCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
        filePathCallback = null;
    }
}
