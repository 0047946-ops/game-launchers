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


    // =========================================================================
    // 測試狀態檢查
    // 僅確認載入狀態，不修改任何功能
    // =========================================================================

    console.log(
        "[NativeSaveBridge TEST]",
        "AndroidBridge:",
        typeof window.AndroidBridge,
        "exportSave:",
        typeof window.exportSave,
        "exportSavePortable:",
        typeof window.exportSavePortable
    );


    // 1. 原生派發助手：僅負責將資料傳給 AndroidBridge.saveJson
    window.dispatchNativeSave = function(json, filename) {

        if (window.AndroidBridge && typeof window.AndroidBridge.saveJson === 'function') {

            window.AndroidBridge.saveJson(
                json,
                filename || 'game_save.json'
            );

        }

    };


    // 2. 僅提供 window.exportSavePortable(slot) 橋接入口
    window.exportSavePortable = function(slot) {

        var rawData = "";


        /*
            TODO:
            下一步：在此處呼叫原作者 Save Engine 真正產生 JSON 出口的函式，
            取得原作者已完成格式化的 rawData。

            禁止：
            - 自行產生 JSON
            - 自行組裝存檔
            - 自行處理 LZ/SIG
            - 讀寫 localStorage
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
