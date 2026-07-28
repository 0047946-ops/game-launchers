(function () {
  if (window.__launcherHookInstalled) return;
  window.__launcherHookInstalled = true;

  function log(msg) {
    try {
      if (window.AndroidBridge && AndroidBridge.log) AndroidBridge.log(String(msg));
    } catch (e) {}
  }

  function toast(msg) {
    try {
      if (window.AndroidBridge && AndroidBridge.toast) AndroidBridge.toast(String(msg));
    } catch (e) {}
  }

  function sendSave(payload, mimeType, fileName) {
    try {
      if (window.AndroidBridge && AndroidBridge.saveBase64File) {
        AndroidBridge.saveBase64File(String(payload || ""), String(mimeType || "application/json"), String(fileName || ""));
        return true;
      }
    } catch (e) {}
    return false;
  }

  function exportFromLocalStorage() {
    try {
      var keys = Object.keys(localStorage || {});
      var slots = [];

      keys.forEach(function (k) {
        var v = localStorage.getItem(k);
        if (!v) return;
        if (typeof v === "string" && (v.indexOf("{") === 0 || v.indexOf("[") === 0 || v.indexOf("SIG1:") >= 0)) {
          slots.push({
            key: k,
            label: k
          });
        }
      });

      if (window.AndroidBridge && AndroidBridge.pickSaveSlot) {
        AndroidBridge.pickSaveSlot(JSON.stringify(slots));
      } else {
        toast("找不到原生橋接");
      }
    } catch (e) {
      log("exportFromLocalStorage error: " + e);
    }
  }

  window.__exportSlotByKey = function (key) {
    try {
      var value = localStorage.getItem(key);
      if (!value) {
        toast("找不到存檔：" + key);
        return;
      }
      sendSave(value, "application/json", key + ".json");
    } catch (e) {
      log("exportSlotByKey error: " + e);
    }
  };

  window.__markExported = function () {
    log("exported");
  };

  window.__dumpStorage = function () {
    try {
      return JSON.stringify(localStorage, null, 2);
    } catch (e) {
      return String(e);
    }
  };

  function hookDownloads() {
    try {
      var origCreateElement = document.createElement.bind(document);
      document.createElement = function (tagName) {
        var el = origCreateElement(tagName);
        try {
          if (String(tagName).toLowerCase() === "a") {
            var origClick = el.click;
            el.click = function () {
              try {
                var href = el.href || "";
                if (href.indexOf("data:") === 0 || href.indexOf("blob:") === 0) {
                  log("intercept anchor download: " + href.substring(0, 50));
                }
              } catch (e) {}
              return origClick.apply(el, arguments);
            };
          }
        } catch (e) {}
        return el;
      };
    } catch (e) {
      log("hookDownloads error: " + e);
    }
  }

  hookDownloads();
  exportFromLocalStorage();
  log("save_hook loaded");
})();
