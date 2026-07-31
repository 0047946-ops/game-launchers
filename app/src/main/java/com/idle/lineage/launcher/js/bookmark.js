function executeBookmark() {
    let js = document.getElementById("bookmarkJs").value.trim();
    if (!js) { alert("請輸入書籤腳本"); return; }
    if (!js.toLowerCase().startsWith("javascript:")) {
        js = "javascript:" + js;
    }
    try {
        eval(js.replace("javascript:", ""));
        alert("書籤腳本執行成功");
    } catch (e) {
        alert("書籤執行失敗：" + e.message);
    }
}
