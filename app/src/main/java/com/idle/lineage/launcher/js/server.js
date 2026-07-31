function handleServerChange() {
    const val = document.getElementById("gameUrl").value;
    const customGroup = document.getElementById("customGroup");
    customGroup.style.display = (val === "custom") ? "block" : "none";
}

function startGame() {
    let selectVal = document.getElementById("gameUrl").value;
    let targetUrl = (selectVal === "custom") ? document.getElementById("customUrl").value.trim() : selectVal;
    
    if (!targetUrl) {
        alert("請選擇或輸入有效的遊戲網址！");
        return;
    }

    if (window.AndroidBridge && typeof window.AndroidBridge.launchGame === 'function') {
        window.AndroidBridge.launchGame(targetUrl);
    } else {
        location.href = targetUrl;
    }
}
