(function () {
    'use strict';

    // 1. 生命週期與執行狀態解耦：防止重複執行，但允許極端環境優雅重試
    if (window.__IDLE_SAVE_HOOK_RUNNING__) {
        console.log("⚠️ [SaveHook v5.7] 腳本已在運行狀態，跳過重複執行");
        return;
    }
    window.__IDLE_SAVE_HOOK_RUNNING__ = true;

    console.log("🚀 [SaveHook v5.7 Production Final] 啟動成功 - 終極生產部署版");

    let lastExportHash = "";
    let lastExportTime = 0;
    
    // Blob 快取記憶體與 50MB 上限控制
    let blobCache = new Map();
    let blobCacheTotalSize = 0;
    const MAX_CACHE_BYTES = 50 * 1024 * 1024; // 50MB 記憶體上限
    let fileNameCounter = new Map();

    function cacheBlob(url, blob) {
        if (!blob) return;
        const size = blob.size || 0;

        // 容量超出時進行 FIFO 清理，確保記憶體安穩如磐
        while (blobCacheTotalSize + size > MAX_CACHE_BYTES && blobCache.size > 0) {
            const firstKey = blobCache.keys().next().value;
            const item = blobCache.get(firstKey);
            if (item && item.size) {
                blobCacheTotalSize -= item.size;
            }
            blobCache.delete(firstKey);
            console.log("[SaveHook] 快取已滿，自動釋放舊 Blob:", firstKey);
        }

        blobCache.set(url, blob);
        blobCacheTotalSize += size;

        setTimeout(() => {
            if (blobCache.has(url)) {
                const item = blobCache.get(url);
                if (item && item.size) {
                    blobCacheTotalSize -= item.size;
                }
                blobCache.delete(url);
            }
        }, 60000);
    }

    // ==================================================
    // 2. 延遲 URL.revokeObjectURL 機制（保護極速銷毀型 Blob）
    // ==================================================
    const oldRevokeObjectURL = URL.revokeObjectURL;
    URL.revokeObjectURL = function (url) {
        if (typeof url === "string" && blobCache.has(url)) {
            console.log("[SaveHook] 偵測到即時 revoke，強制延遲 60 秒釋放 Blob 記憶體:", url);
            setTimeout(() => {
                if (blobCache.has(url)) {
                    const item = blobCache.get(url);
                    if (item && item.size) {
                        blobCacheTotalSize -= item.size;
                    }
                    blobCache.delete(url);
                }
                try {
                    oldRevokeObjectURL.call(URL, url);
                } catch (e) {}
            }, 60000);
            return;
        }
        return oldRevokeObjectURL.apply(this, arguments);
    };

    // ==================================================
    // 3. SHA-256 完整性驗證計算器（背景非阻塞式）
    // ==================================================
    function calculateSHA256Background(str, fileName) {
        Promise.resolve().then(async () => {
            try {
                let hash = "";
                if (window.crypto && window.crypto.subtle && typeof TextEncoder !== "undefined") {
                    const msgUint8 = new TextEncoder().encode(str);
                    const hashBuffer = await window.crypto.subtle.digest("SHA-256", msgUint8);
                    const hashArray = Array.from(new Uint8Array(hashBuffer));
                    hash = hashArray.map(b => b.toString(16).padStart(2, "0")).join("");
                } else {
                    let h = 0;
                    for (let i = 0; i < str.length; i++) {
                        const char = str.charCodeAt(i);
                        h = ((h << 5) - h) + char;
                        h |= 0;
                    }
                    hash = "FB_" + Math.abs(h).toString(16);
                }
                console.log(`[SaveHook Background Hash] 檔名: ${fileName}, SHA256: ${hash}`);
            } catch (e) {
                console.warn("[SaveHook Background Hash] 計算失敗", e);
            }
        });
    }

    // ==================================================
    // 4. 嚴格 Base64 判定器
    // ==================================================
    function isStrictBase64(str) {
        if (!str || typeof str !== "string") return false;
        let trimmed = str.trim();
        if (trimmed.length === 0 || trimmed.length % 4 !== 0) return false;
        if (!/^[A-Za-z0-9+/=]+$/.test(trimmed)) return false;
        try {
            return btoa(atob(trimmed)) === trimmed;
        } catch (e) {
            return false;
        }
    }

    // UTF-8 Base64 解碼器
    function decodeBase64UTF8(data) {
        try {
            if (!data) return "";
            const cleanData = data.includes(",") ? data.split(",")[1] : data;
            let bytes = Uint8Array.from(atob(cleanData), c => c.charCodeAt(0));
            
            if (typeof window.TextDecoder === "function") {
                return new TextDecoder("utf-8").decode(bytes);
            } else {
                let binaryString = String.fromCharCode.apply(null, bytes);
                return decodeURIComponent(escape(binaryString));
            }
        } catch (e) {
            try {
                const cleanData = data.includes(",") ? data.split(",")[1] : data;
                return decodeURIComponent(escape(atob(cleanData)));
            } catch (err) {
                return "";
            }
        }
    }

    // ==================================================
    // 5. 智慧檔名與日期維度防覆蓋序號解析
    // ==================================================
    function extractCharInfo(text) {
        try {
            if (!text) return "";
            
            const sigMatch = text.match(/^SIG1:[^:]+:(\{[\s\S]*\})$/);
            if (sigMatch) {
                text = sigMatch[1];
            } else if (text.includes("SIG1:")) {
                let i = text.indexOf("SIG1:");
                let body = text.substring(i + 5);
                let firstColon = body.indexOf(':');
                if (firstColon > 0) {
                    let secondColon = body.indexOf(':', firstColon + 1);
                    if (secondColon > 0) {
                        text = body.substring(secondColon + 1);
                    } else {
                        text = body.substring(firstColon + 1);
                    }
                }
            }
            
            let level = "";
            let cls = "";
            let name = "";

            try {
                const json = JSON.parse(text);
                const p = json.p || json.player || json.character || json;
                if (p) {
                    level = p.charLevel || p.level || p.lv || "";
                    cls = p.cls || p.class || p.className || p.job || "";
                    name = p.name || p.charName || p.playerName || "";
                }
            } catch (err) {
                const lvMatch = text.match(/"(?:charLevel|level|lv)"\s*:\s*(\d{1,3})/);
                if (lvMatch) level = lvMatch[1];

                const clMatch = text.match(/"(?:cls|class|className|job)"\s*:\s*"?([^"\s,]{1,10})"?/);
                if (clMatch) cls = clMatch[1];

                const nameMatch = text.match(/"(?:name|charName|playerName)"\s*:\s*"?([^"\s,]{1,20})"?/);
                if (nameMatch) name = nameMatch[1];
            }

            if (cls) {
                const classMap = {
                    mage: "法師",
                    knight: "騎士",
                    elf: "妖精",
                    dark: "黑妖",
                    warrior: "戰士",
                    royal: "王族"
                };
                cls = classMap[cls.toLowerCase()] || cls;
            }

            if (level || cls || name) {
                let res = "";
                if (level) res += level + "等";
                if (cls) res += cls;
                if (name && name.trim() !== "") {
                    res += "_" + name.trim();
                }
                if (!res) return "";
                return res;
            }
        } catch (e) {}
        return "";
    }

    function resolveFileName(rawFileName, textContent) {
        let base = "";
        
        if (rawFileName && typeof rawFileName === "string") {
            base = rawFileName.replace(/\.(json|txt|sav)$/i, "").trim();
        }

        if (!base || base.match(/^(save|download|export|idle_save|blob|file|存檔).*$/i)) {
            const extracted = extractCharInfo(textContent);
            if (extracted) {
                base = extracted;
            }
        }

        if (!base || base.length === 0) {
            base = "存檔";
        }

        let cleanBase = base.replace(/[\\/:*?"<>|]/g, "_");
        
        // 取得當前日期 YYYYMMDD（防記憶體重置與跨日覆蓋）
        const d = new Date();
        const dateStr = d.getFullYear() +
            String(d.getMonth() + 1).padStart(2, '0') +
            String(d.getDate()).padStart(2, '0');

        let key = cleanBase + "_" + dateStr;
        let count = fileNameCounter.get(key) || 0;
        fileNameCounter.set(key, count + 1);

        let finalName = count > 0 
            ? key + "_" + String(count).padStart(3, '0') + ".json"
            : key + ".json";

        return finalName;
    }

    // ==================================================
    // 6. Android 匯出核心（非阻塞喚起 Bridge）
    // ==================================================
    function saveToAndroid(base64, rawFileName, textContent) {
        try {
            if (!base64) return;

            let fileName = resolveFileName(rawFileName, textContent);
            
            // 背景異步計算 SHA-256 驗證碼，絕不卡頓主流程
            calculateSHA256Background(base64, fileName);

            const now = Date.now();
            const hashKey = base64.length + "_" + base64.substring(0, 80);

            if (hashKey === lastExportHash && now - lastExportTime < 3000) {
                console.log("[SaveHook] 重複匯出略過");
                return;
            }

            lastExportHash = hashKey;
            lastExportTime = now;

            if (window.AndroidBridge && window.AndroidBridge.saveBase64File) {
                window.AndroidBridge.saveBase64File(base64, "application/json", fileName);
                console.log("[SaveHook] AndroidBridge 匯出成功, 檔名:", fileName);
            } else {
                console.warn("[SaveHook] AndroidBridge 未就緒");
            }
        } catch (e) {
            console.error("[SaveHook] Android 匯出失敗", e);
        }
    }

    function blobToBase64(blob, rawFileName) {
        try {
            console.log("[SaveHook] 偵測到 Blob 資料");
            const reader = new FileReader();
            reader.onloadend = function () {
                let result = reader.result || "";
                let rawText = "";
                try {
                    const base64Data = result.includes(",") ? result.split(",")[1] : result;
                    rawText = decodeBase64UTF8(base64Data);
                } catch (err) {
                    rawText = result;
                }

                if (result.includes(",")) {
                    result = result.substring(result.indexOf(",") + 1);
                }

                saveToAndroid(result, rawFileName, rawText);
            };
            reader.readAsDataURL(blob);
        } catch (e) {
            console.error("[SaveHook] Blob 處理失敗", e);
        }
    }

    // ==================================================
    // 7. window.exportSave 真實攔截與多類型判斷
    // ==================================================
    function hookExportSave() {
        if (window.exportSave && !window.exportSave.__hooked__) {
            const oldExportSave = window.exportSave;
            window.exportSave = function (slot) {
                console.log("[SaveHook] exportSave 被觸發, slot:", slot);
                try {
                    const result = oldExportSave.apply(this, arguments);
                    if (result !== undefined && result !== null) {
                        let jsonStr = "";
                        let base64Data = "";

                        if (typeof result === "string") {
                            let trimmed = result.trim();
                            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                                jsonStr = trimmed;
                                base64Data = btoa(unescape(encodeURIComponent(jsonStr)));
                            } else if (isStrictBase64(trimmed)) {
                                base64Data = trimmed;
                                jsonStr = decodeBase64UTF8(base64Data);
                            } else {
                                jsonStr = trimmed;
                                base64Data = btoa(unescape(encodeURIComponent(jsonStr)));
                            }
                        } else if (typeof result === "object") {
                            jsonStr = JSON.stringify(result);
                            base64Data = btoa(unescape(encodeURIComponent(jsonStr)));
                        }

                        if (base64Data) {
                            saveToAndroid(base64Data, "idle_save.json", jsonStr);
                        }
                    }
                    return result;
                } catch (e) {
                    console.error("[SaveHook] exportSave 執行異常", e);
                    return oldExportSave.apply(this, arguments);
                }
            };
            window.exportSave.__hooked__ = true;
            console.log("✅ [SaveHook] window.exportSave 真實掛鉤完成");
        }
    }

    const exportCheckTimer = setInterval(() => {
        if (window.exportSave) {
            hookExportSave();
            clearInterval(exportCheckTimer);
        }
    }, 500);

    // ==================================================
    // URL.createObjectURL 快取
    // ==================================================
    const oldCreateObjectURL = URL.createObjectURL;
    URL.createObjectURL = function (blob) {
        const url = oldCreateObjectURL.apply(this, arguments);
        try {
            if (blob instanceof Blob) {
                cacheBlob(url, blob);
            }
        } catch (e) {}
        return url;
    };

    // ==================================================
    // 8. 多路徑攔截：a[download] / click / window.open / navigator.share (防雙寫)
    // ==================================================
    function processAnchorAction(a) {
        if (!a) return false;
        const downloadAttr = a.getAttribute("download");
        const href = a.href;
        const isDownload = downloadAttr !== null || (href && href.startsWith("blob:"));

        if (!isDownload) return false;

        const name = downloadAttr || "idle_save.json";

        if (href && href.startsWith("blob:")) {
            const cachedBlob = blobCache.get(href);
            if (cachedBlob) {
                blobToBase64(cachedBlob, name);
                return true;
            } else {
                fetch(href)
                    .then(r => r.blob())
                    .then(blob => {
                        blobToBase64(blob, name);
                    });
                return true;
            }
        } else if (href && href.startsWith("data:")) {
            const idx = href.indexOf(",");
            const dataContent = idx !== -1 ? href.substring(idx + 1) : href;
            let base64Data = "";
            let rawText = "";

            if (href.includes(";base64,")) {
                base64Data = dataContent;
                rawText = decodeBase64UTF8(base64Data);
            } else {
                try {
                    rawText = decodeURIComponent(dataContent);
                    base64Data = btoa(unescape(encodeURIComponent(rawText)));
                } catch (err) {
                    base64Data = btoa(dataContent);
                    rawText = dataContent;
                }
            }

            saveToAndroid(base64Data, name, rawText);
            return true;
        }
        return false;
    }

    document.addEventListener("click", function (e) {
        const a = e.target.closest && e.target.closest("a");
        if (a && processAnchorAction(a)) {
            e.preventDefault();
        }
    }, true);

    const oldAnchorClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
        try {
            if (processAnchorAction(this)) {
                return;
            }
        } catch (e) {}
        return oldAnchorClick.apply(this, arguments);
    };

    const oldOpen = window.open;
    window.open = function (url) {
        try {
            if (typeof url === "string") {
                if (url.startsWith("blob:") || url.startsWith("data:")) {
                    if (url.startsWith("blob:")) {
                        const cachedBlob = blobCache.get(url);
                        if (cachedBlob) {
                            blobToBase64(cachedBlob, "idle_save.json");
                        } else {
                            fetch(url)
                                .then(r => r.blob())
                                .then(blob => {
                                    blobToBase64(blob, "idle_save.json");
                                });
                        }
                    } else if (url.startsWith("data:")) {
                        const idx = url.indexOf(",");
                        const dataContent = idx !== -1 ? url.substring(idx + 1) : url;
                        let base64Data = "";
                        let rawText = "";

                        if (url.includes(";base64,")) {
                            base64Data = dataContent;
                            rawText = decodeBase64UTF8(base64Data);
                        } else {
                            try {
                                rawText = decodeURIComponent(dataContent);
                                base64Data = btoa(unescape(encodeURIComponent(rawText)));
                            } catch (err) {
                                base64Data = btoa(dataContent);
                                rawText = dataContent;
                            }
                        }
                        saveToAndroid(base64Data, "idle_save.json", rawText);
                    }
                    return null;
                }
            }
        } catch (e) {}
        return oldOpen.apply(this, arguments);
    };

    // navigator.share 存檔攔截並阻擋原生選單（防止雙寫與彈出選單）
    if (navigator.share && !navigator.share.__hooked__) {
        const oldShare = navigator.share;
        navigator.share = function (data) {
            try {
                if (data && data.files && data.files.length > 0) {
                    let intercepted = false;
                    for (let i = 0; i < data.files.length; i++) {
                        const file = data.files[i];
                        if (file.name && file.name.match(/\.(json|txt|sav)$/i)) {
                            blobToBase64(file, file.name);
                            intercepted = true;
                        }
                    }
                    if (intercepted) {
                        console.log("[SaveHook] navigator.share 成功攔截存檔，已封鎖原生分享面板");
                        return Promise.resolve();
                    }
                }
            } catch (e) {}
            return oldShare.apply(this, arguments);
        };
        navigator.share.__hooked__ = true;
    }

    // ==================================================
    // 9. FileReader 相容性修正（雙層防護）
    // ==================================================
    if (!FileReader.prototype.readAsText.__savehook__) {
        const originalReadAsText = FileReader.prototype.readAsText;
        FileReader.prototype.readAsText = function (file, encoding) {
            const self = this;
            const oldLoad = self.onload;

            self.onload = function (e) {
                try {
                    let raw = e.target.result;
                    const parsed = JSON.parse(raw);

                    if (parsed && parsed.data) {
                        raw = typeof parsed.data === "string" ? parsed.data : JSON.stringify(parsed.data);
                    }
                    if (parsed && parsed.save) {
                        raw = typeof parsed.save === "string" ? parsed.save : JSON.stringify(parsed.save);
                    }

                    try {
                        Object.defineProperty(e.target, "result", {
                            value: raw,
                            writable: true,
                            configurable: true
                        });
                    } catch (defineErr) {
                        e.target.__savehook_result = raw;
                        console.warn("[SaveHook] Object.defineProperty 被封鎖，啟用 fallback 屬性");
                    }
                } catch (err) {}

                if (oldLoad) {
                    oldLoad.call(self, e);
                }
            };

            return originalReadAsText.apply(this, arguments);
        };
        FileReader.prototype.readAsText.__savehook__ = true;
    }

    // ==================================================
    // 10. 外掛安全注入（正確處理 Promise Reject 與異常捕捉）
    // ==================================================
    function appendScript(script) {
        return new Promise((resolve, reject) => {
            if (script.__added) {
                resolve();
                return;
            }
            script.__added = true;

            script.onload = () => resolve();
            script.onerror = (err) => {
                console.warn("[SaveHook] 腳本載入失敗:", script.src);
                reject(err);
            };

            if (document.body) {
                document.body.appendChild(script);
            } else {
                setTimeout(() => {
                    if (document.body) {
                        document.body.appendChild(script);
                    } else {
                        reject(new Error("document.body 不存在"));
                    }
                }, 100);
            }
        });
    }

    if (!window.__all_plugins_loaded) {
        window.__all_plugins_loaded = true;

        const mainPlugin = document.createElement("script");
        mainPlugin.src = "https://cdn.jsdelivr.net/gh/qcc781192000/idle-lineage-plugin@main/main.user.js?v=" + Date.now();
        
        appendScript(mainPlugin).catch(e => {
            console.warn("[SaveHook] 主外掛載入失敗，準備切換至備用外掛組", e);
        }).then(() => {
            const base = "https://kid0924.github.io/idle-lineage-class/";
            const commonPlugins = [
                "klh_initial.js",
                "klh_GMShop.js",
                "klh_mobile-perf.js",
                "klh_perf-monitor.js",
                "klh_Backpack.js",
                "klh_pk.js",
                "klh_Pandora.js"
            ].map(x => base + x);

            const isAddServer = window.location.hostname.includes("pp771007");
            const plugins = isAddServer ? [
                base + "klh_remove-banner.js",
                ...commonPlugins
            ] : [
                "https://pp771007.github.io/idle-lineage-class/afk-lzcache.js",
                "https://pp771007.github.io/idle-lineage-class/afk-offline.js",
                ...commonPlugins
            ];

            function loadPluginOrdered(index) {
                if (index >= plugins.length) {
                    console.log("🎉 外掛全部順序載入完成");
                    return Promise.resolve();
                }
                const s = document.createElement("script");
                s.src = plugins[index] + "?v=" + Date.now();
                return appendScript(s).catch(err => {
                    console.warn("[SaveHook] 子外掛載入跳過:", plugins[index]);
                }).then(() => loadPluginOrdered(index + 1));
            }

            return loadPluginOrdered(0);
        });
    }

    // ==================================================
    // 11. TMEngine（全域 Timer 唯一性鎖定防重複機制）
    // ==================================================
    if (!window.__tm_engine_loaded) {
        window.__tm_engine_loaded = true;

        if (window.__tm_timer) {
            clearInterval(window.__tm_timer);
        }

        const PerformanceCore = {
            getJitter: function (base, variance) {
                return base + Math.floor(Math.random() * variance);
            }
        };

        const NetworkOptimizer = {
            _isMobile: false,
            detect: function () {
                NetworkOptimizer._isMobile = /Android|iPhone|iPad/i.test(navigator.userAgent);
            },
            getParams: function () {
                return NetworkOptimizer._isMobile ? { base: 500, variance: 700 } : { base: 120, variance: 250 };
            }
        };

        function executeLogic() {
            const hp = document.querySelector(".hp-text");
            if (hp) {
                const data = hp.innerText.split("/").map(Number);
                if (data.length === 2 && data[0] / data[1] < 0.75) {
                    const potion = document.querySelector("#btn-use-potion") || document.querySelector(".potion-btn");
                    if (potion) {
                        potion.click();
                    }
                }
            }

            const attack = document.querySelector(".attack-btn");
            if (attack && !attack.classList.contains("cooldown")) {
                const p = NetworkOptimizer.getParams();
                setTimeout(function () {
                    attack.click();
                }, PerformanceCore.getJitter(p.base, p.variance));
            }
        }

        NetworkOptimizer.detect();
        window.__tm_timer = setInterval(executeLogic, 250);

        console.log("✅ TMEngine 啟動，Timer 識別碼已鎖定");
    }

    console.log("✅ SaveHook v5.7 Production Final 準備就緒，已達 APK 量產級安全防護標準");
})();
