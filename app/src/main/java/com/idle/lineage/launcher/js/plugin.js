function addPluginUrl() {
    const url = document.getElementById("pluginUrl").value.trim();
    if (!url) { alert("請輸入外掛網址"); return; }
    if (window.AndroidBridge && typeof window.AndroidBridge.injectPluginUrl === 'function') {
        window.AndroidBridge.injectPluginUrl(url);
    } else {
        const s = document.createElement('script');
        s.src = url + '?v=' + Date.now();
        document.body.appendChild(s);
        alert("已透過網頁端動態載入外掛網址");
    }
}

function uploadPluginFile(event) {
    const file = event.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = function(e) {
        const code = e.target.result;
        try {
            const s = document.createElement('script');
            s.textContent = code;
            document.body.appendChild(s);
            alert("🎉 成功上傳並注入外掛檔案：" + file.name);
        } catch (err) {
            alert("❌ 外掛檔案執行失敗：" + err.message);
        }
    };
    reader.readAsText(file);
}
