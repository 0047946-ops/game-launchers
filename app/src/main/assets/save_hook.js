(function() {
    'use strict';
    if (window.__save_hook_loaded) return;
    window.__save_hook_loaded = true;

    // 攔截原生下載或儲存行為，透過 AndroidBridge 傳回原生
    const originalSave = window.saveDataToNative || function(data, name) {
        if (window.AndroidBridge && typeof window.AndroidBridge.saveBase64File === 'function') {
            window.AndroidBridge.saveBase64File(data, name);
        }
    };
    window.saveDataToNative = originalSave;
})();
