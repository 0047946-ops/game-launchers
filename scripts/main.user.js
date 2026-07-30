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


})();
