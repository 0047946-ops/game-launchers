// ============================================================================
// 檔案名稱：native-save-bridge.js
// 檔案用途：僅建立 Android Native 與原作者 Save Engine 的橋接入口
// 嚴格規範：不自行產生 JSON、不組裝存檔、不處理 LZ/SIG、不讀寫 localStorage
// ============================================================================

(function() {

    'use strict';

    if (window.__NativeSaveBridgeLoaded) {
        return;
    }

    window.__NativeSaveBridgeLoaded = true;

    // 1. 原生派發助手：僅負責將資料傳給 AndroidBridge.saveJson
    window.dispatchNativeSave = function(json, filename) {
        if (window.AndroidBridge && typeof window.AndroidBridge.saveJson === 'function') {
            window.AndroidBridge.saveJson(json, filename || 'game_save.json');
        }
    };

    // 2. 僅提供 window.exportSavePortable(slot) 橋接入口
    window.exportSavePortable = function(slot) {
        var rawData = "";

        /*
            TODO:
            下一步：在此處呼叫原作者 Save Engine 真正產生 JSON 出口的函式，取得原作者已完成格式化的 rawData。
        */

        if (
            rawData &&
            window.AndroidBridge &&
            typeof window.AndroidBridge.saveJson === "function"
        ) {
            window.dispatchNativeSave(
                rawData,
                "fable5_save_" + (slot || 1) + ".json"
            );
        }

        return rawData;
    };

})();
