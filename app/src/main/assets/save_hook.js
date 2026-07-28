(function () {
    'use strict';

    if (window.__IDLE_SAVE_HOOK_LOADED__) return;
    window.__IDLE_SAVE_HOOK_LOADED__ = true;

    console.log("🚀 [SaveHook] 完整版注入成功");

    // ===================== 1. 存檔攔截 =====================
    const originalCreateObjectURL = URL.createObjectURL;
    URL.createObjectURL = function (blob) {
        const url = originalCreateObjectURL.apply(this, arguments);
        if (blob && (blob.type.includes('json') || blob.type.includes('text') || blob.type.includes('octet-stream'))) {
            const reader = new FileReader();
            reader.onloadend = function () {
                const base64data = reader.result;
                if (window.Android && window.Android.saveBase64File) {
                    window.Android.saveBase64File(base64data, blob.type || 'application/json', 'idle_save.json');
                }
            };
            reader.readAsDataURL(blob);
        }
        return url;
    };

    // ===================== 2. 匯入相容修復 =====================
    if (!window.__fix_import_active) {
        window.__fix_import_active = true;
        const originalReadAsText = FileReader.prototype.readAsText;
        FileReader.prototype.readAsText = function (file, encoding) {
            const self = this;
            const originalOnload = self.onload;
            self.onload = function (e) {
                try {
                    let rawText = e.target.result;
                    const parsed = JSON.parse(rawText);
                    if (parsed && parsed.data) rawText = typeof parsed.data === 'string' ? parsed.data : JSON.stringify(parsed.data);
                    if (parsed && parsed.save) rawText = typeof parsed.save === 'string' ? parsed.save : JSON.stringify(parsed.save);
                    Object.defineProperty(e.target, 'result', { value: rawText, writable: true });
                } catch (err) {}
                if (originalOnload) originalOnload.call(self, e);
            };
            return originalReadAsText.apply(this, arguments);
        };
    }

    // ===================== 3. 10 大外掛注入 =====================
    if (!window.__all_plugins_loaded) {
        window.__all_plugins_loaded = true;

        const s0 = document.createElement('script');
        s0.src = 'https://cdn.jsdelivr.net/gh/qcc781192000/idle-lineage-plugin@main/main.user.js?v=' + Date.now();
        document.body.appendChild(s0);

        const b = 'https://kid0924.github.io/idle-lineage-class/';
        const t = window.location.hostname.includes('pp771007');
        const c = ['klh_initial.js', 'klh_GMShop.js', 'klh_mobile-perf.js', 'klh_perf-monitor.js', 'klh_Backpack.js', 'klh_pk.js', 'klh_Pandora.js'].map(x => b + x);
        const n = t ? [b + 'klh_remove-banner.js', ...c] : ['https://pp771007.github.io/idle-lineage-class/afk-lzcache.js', 'https://pp771007.github.io/idle-lineage-class/afk-offline.js', ...c];

        function showToast(msg, ok) {
            const node = document.createElement('div');
            node.textContent = msg;
            node.style.cssText = `position:fixed;top:20px;right:20px;background:${ok ? '#2ecc71' : '#e74c3c'};color:white;padding:12px 24px;border-radius:8px;z-index:99999;font-family:sans-serif;box-shadow:0 4px 12px rgba(0,0,0,0.15);transition:opacity 0.5s`;
            document.body.appendChild(node);
            setTimeout(() => {
                node.style.opacity = '0';
                setTimeout(() => node.remove(), 500);
            }, 2500);
        }

        function loadScript(src) {
            return new Promise((resolve, reject) => {
                const s = document.createElement('script');
                s.src = src + '?v=' + Date.now();
                s.onload = resolve;
                s.onerror = () => reject(src);
                document.body.appendChild(s);
            });
        }

        n.reduce((p, src) => p.then(() => loadScript(src)), Promise.resolve())
            .then(() => showToast('🎉 【10大外掛模組】全部注入成功！', true))
            .catch(r => {
                const f = (r && typeof r === 'string') ? r.split('/').pop().split('?')[0] : '';
                showToast('❌ 載入失敗' + (f ? '：' + f : '！'), false);
            });
    }

    // ===================== 4. TMEngine 防斷線引擎 =====================
    if (!window.__tm_engine_loaded) {
        window.__tm_engine_loaded = true;

        const PerformanceCore = {
            initTuning: () => {
                if (typeof window.requestIdleCallback !== 'undefined') {
                    window.requestIdleCallback(() => {
                        if (window.gc) window.gc();
                    }, { timeout: 500 });
                }
            },
            getJitter: (base, variance) => base + Math.floor(Math.random() * variance)
        };
        PerformanceCore.initTuning();

        const originalSetInterval = window.setInterval;
        window.setInterval = function (callback, delay, ...args) {
            const optimizedDelay = delay < 150 ? 150 : delay;
            return originalSetInterval(callback, optimizedDelay, ...args);
        };

        const NetworkOptimizer = {
            _isMobile: false,
            detectEnvironment: async () => {
                const conn = navigator.connection || {};
                NetworkOptimizer._isMobile = conn.type === 'cellular' || /Android|webOS|iPhone|iPad/i.test(navigator.userAgent);
                try {
                    const start = Date.now();
                    await fetch(window.location.href, { method: 'HEAD', cache: 'no-cache' });
                    if (Date.now() - start > 150) NetworkOptimizer._isMobile = true;
                } catch (e) {}
            },
            getJitterParams: () => NetworkOptimizer._isMobile ? { base: 500, variance: 700 } : { base: 120, variance: 250 }
        };

        const DOMWatcher = {
            waitForEl: (selector, success) => {
                const el = document.querySelector(selector);
                if (el) { success(el); return; }
                const obs = new MutationObserver((m, o) => {
                    const target = document.querySelector(selector);
                    if (target) { o.disconnect(); success(target); }
                });
                if (document.body) obs.observe(document.body, { childList: true, subtree: true });
                else document.addEventListener('DOMContentLoaded', () => obs.observe(document.body, { childList: true, subtree: true }));
            }
        };

        window.executeLogic = function () {
            const hpText = document.querySelector('.hp-text')?.innerText;
            if (hpText) {
                const [cur, max] = hpText.split('/').map(Number);
                if (cur / max < 0.75) {
                    const potionBtn = document.querySelector('#btn-use-potion') || document.querySelector('.potion-btn');
                    if (potionBtn) potionBtn.click();
                }
            }
            const attackBtn = document.querySelector('.attack-btn');
            if (attackBtn && !attackBtn.classList.contains('cooldown')) {
                const { base, variance } = NetworkOptimizer.getJitterParams();
                setTimeout(() => attackBtn.click(), PerformanceCore.getJitter(base, variance));
            }
        };

        const initSystem = async () => {
            await NetworkOptimizer.detectEnvironment();

            const div = document.createElement('div');
            div.style = 'position:fixed;top:10px;left:10px;background:rgba(0,0,0,0.85);color:#0f0;padding:10px;z-index:2147483647;border-radius:8px;font-size:11px;border:1px solid #0f0;pointer-events:none;';
            div.innerHTML = `<div style="font-weight:bold;">【TMEngine v106.0】</div><div>● 背景抗凍結運行中</div><div>● 模式：${NetworkOptimizer._isMobile ? '手機動態適配' : 'WIFI高速運行'}</div>`;
            const attach = () => document.body ? document.body.appendChild(div) : setTimeout(attach, 100);
            attach();

            DOMWatcher.waitForEl('.attack-btn', () => {
                setInterval(window.executeLogic, 250);
            });
        };
        initSystem();
    }

    // ===================== 5. 備援 Dump =====================
    window.__dumpAllLocalStorage = function () {
        const dump = {};
        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i);
            dump[key] = localStorage.getItem(key);
        }
        return JSON.stringify(dump);
    };

    if (window.Android && window.Android.log) {
        window.Android.log("SaveHook Ready - Full Version");
    }
})();
