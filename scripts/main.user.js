// ==UserScript==    
// @name         Idle Lineage Master Engine    
// @namespace    http://tampermonkey.net/    
// @version      1.0.2    
// @description  全功能外掛總引擎    
// @author       0047946-ops    
// @match        *://*/*    
// @grant        none    
// ==/UserScript==    
    
(function() {    
    
    'use strict';    
    
    
    if (window.__MasterEngineLoaded) {    
        console.log("[MasterEngine] 已存在，停止重複載入");    
        return;    
    }    
    
    
    window.__MasterEngineLoaded = true;    
    
    
    console.log("[MasterEngine] 線上外掛總加載器啟動");    
    
    
    // =========================================================================    
    // Native Save Bridge 載入    
    // 用途：連接 Android WebView 與原作者 Save Engine    
    // 注意：不修改、不取代 13-shop-save.js    
    // =========================================================================    
    
    function loadExternalScript(url, callback) {    
    
        var script = document.createElement("script");    
    
        script.src = url + "?t=" + Date.now();    
    
        script.onload = function() {    
            console.log("[MasterEngine] 載入完成: " + url);    
    
            if (callback) {    
                callback();    
            }    
        };    
    
        script.onerror = function() {    
            console.error("[MasterEngine] 載入失敗: " + url);    
        };    
    
        document.head.appendChild(script);    
    }    
    
    
    loadExternalScript(    
        "https://raw.githubusercontent.com/0047946-ops/game-launchers/character-sync-test/scripts/native-save-bridge.js",    
        function() {    
            console.log("[MasterEngine] Native Save Bridge 已啟動");    
        }    
    );    
    
    
})();
