// save_hook.js — 放置天堂 APK 存檔攔截
(function () {
  'use strict';
  if (window.__IDLE_SAVE_HOOK_LOADED__) return;
  window.__IDLE_SAVE_HOOK_LOADED__ = true;

  function bridgeLog(msg) {
    try {
      if (window.AndroidDownloader && AndroidDownloader.log) AndroidDownloader.log(msg);
      else if (window.AndroidBridge && AndroidBridge.log) AndroidBridge.log(msg);
      else console.log('[SaveHook] ' + msg);
    } catch (e) {}
  }

  function bridgeToast(msg) {
    try {
      if (window.AndroidDownloader && AndroidDownloader.toast) AndroidDownloader.toast(msg);
      else if (window.AndroidBridge && AndroidBridge.toast) AndroidBridge.toast(msg);
    } catch (e) {}
  }

  function sendToAndroid(data, mime, fileName) {
    mime = mime || 'application/json';
    fileName = fileName || 'idle_save.json';
    try {
      if (window.AndroidDownloader && AndroidDownloader.saveBase64File) {
        AndroidDownloader.saveBase64File(data, mime, fileName);
        return true;
      }
      if (window.AndroidBridge && AndroidBridge.saveBase64File) {
        // 相容兩種簽名
        try {
          AndroidBridge.saveBase64File(data, mime, fileName);
        } catch (e) {
          AndroidBridge.saveBase64File(data, fileName);
        }
        return true;
      }
    } catch (e) {
      bridgeLog('sendToAndroid error: ' + e);
    }
    return false;
  }

  function blobToDataUrl(blob, cb) {
    var r = new FileReader();
    r.onloadend = function () { cb(r.result); };
    r.onerror = function () { bridgeLog('FileReader error'); };
    r.readAsDataURL(blob);
  }

  // —— 攔截 URL.createObjectURL（多數匯出會走這裡）——
  var origCreateObjectURL = URL.createObjectURL;
  URL.createObjectURL = function (blob) {
    var url = origCreateObjectURL.apply(this, arguments);
    try {
      if (blob && blob.size > 0) {
        var t = (blob.type || '').toLowerCase();
        if (
          t.indexOf('json') >= 0 ||
          t.indexOf('text') >= 0 ||
          t.indexOf('octet') >= 0 ||
          t === '' ||
          t === 'application/octet-stream'
        ) {
          bridgeLog('createObjectURL intercepted, size=' + blob.size + ' type=' + t);
          blobToDataUrl(blob, function (dataUrl) {
            sendToAndroid(dataUrl, 'application/json', guessNameFromPage());
          });
        }
      }
    } catch (e) {
      bridgeLog('createObjectURL hook error: ' + e);
    }
    return url;
  };

  // —— 攔截 <a download> 點擊 ——
  document.addEventListener(
    'click',
    function (e) {
      var a = e.target;
      while (a && a.tagName !== 'A') a = a.parentElement;
      if (!a || !a.hasAttribute('download')) return;
      var href = a.getAttribute('href') || '';
      if (href.indexOf('blob:') === 0 || href.indexOf('data:') === 0) {
        e.preventDefault();
        e.stopPropagation();
        bridgeLog('download link intercepted: ' + href.slice(0, 40));
        if (href.indexOf('data:') === 0) {
          sendToAndroid(href, 'application/json', a.getAttribute('download') || guessNameFromPage());
        } else {
          fetch(href)
            .then(function (res) { return res.blob(); })
            .then(function (blob) {
              blobToDataUrl(blob, function (dataUrl) {
                sendToAndroid(dataUrl, 'application/json', a.getAttribute('download') || guessNameFromPage());
              });
            })
            .catch(function (err) {
              bridgeLog('fetch blob failed: ' + err);
              bridgeToast('匯出讀取失敗');
            });
        }
      }
    },
    true
  );

  // —— 簡易檔名（等級／職業留給 Java 再解析）——
  function guessNameFromPage() {
    try {
      var t = document.title || '';
      if (t && t.length < 40) return t.replace(/[\\/:*?"<>|]/g, '_') + '.json';
    } catch (e) {}
    return 'idle_save.json';
  }

  // —— 給 Android 呼叫：匯出指定 localStorage key ——
  window.__exportSlotByKey = function (key) {
    try {
      var val = localStorage.getItem(key);
      if (val == null) {
        bridgeToast('找不到欄位: ' + key);
        return;
      }
      var payload = val;
      // 若已是物件字串就原樣；否則包一層方便辨識
      sendToAndroid(
        typeof payload === 'string' && (payload.indexOf('{') === 0 || payload.indexOf('SIG1:') === 0 || payload.indexOf('data:') === 0)
          ? (payload.indexOf('data:') === 0 ? payload : 'data:application/json;base64,' + btoa(unescape(encodeURIComponent(payload))))
          : 'data:application/json;base64,' + btoa(unescape(encodeURIComponent(String(payload)))),
        'application/json',
        (key || 'slot') + '.json'
      );
    } catch (e) {
      bridgeLog('__exportSlotByKey error: ' + e);
      bridgeToast('匯出失敗');
    }
  };

  // —— 列出可能的存檔欄位（給選單用）——
  window.__listSaveSlots = function () {
    var slots = [];
    try {
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        var v = localStorage.getItem(k) || '';
        if (
          k.indexOf('save') >= 0 ||
          k.indexOf('char') >= 0 ||
          k.indexOf('slot') >= 0 ||
          k.indexOf('SIG1') >= 0 ||
          v.indexOf('SIG1:') >= 0 ||
          v.indexOf('LZ1:') >= 0 ||
          (v.length > 200 && (v.indexOf('{') === 0 || v.indexOf('SIG1:') === 0))
        ) {
          slots.push({ key: k, label: k + ' (' + Math.round(v.length / 1024) + 'KB)' });
        }
      }
    } catch (e) {}
    return JSON.stringify(slots);
  };

  // —— 診斷 ——
  window.__dumpStorage = function () {
    var dump = {};
    try {
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        var v = localStorage.getItem(k);
        dump[k] = v && v.length > 500 ? v.slice(0, 500) + '…(len=' + v.length + ')' : v;
      }
    } catch (e) {
      dump.error = String(e);
    }
    return JSON.stringify(dump, null, 2);
  };

  window.__markExported = function () {
    bridgeLog('marked exported');
  };

  // 長按標題列可觸發「選欄位匯出」（備援）
  // Android 也可直接 call AndroidDownloader.pickSaveSlot(__listSaveSlots())
  window.__offerExportMenu = function () {
    try {
      var json = window.__listSaveSlots();
      if (window.AndroidDownloader && AndroidDownloader.pickSaveSlot) {
        AndroidDownloader.pickSaveSlot(json);
      } else if (window.AndroidBridge && AndroidBridge.pickSaveSlot) {
        AndroidBridge.pickSaveSlot(json);
      }
    } catch (e) {
      bridgeLog('offerExportMenu: ' + e);
    }
  };

  bridgeLog('SaveHook ready');
})();
