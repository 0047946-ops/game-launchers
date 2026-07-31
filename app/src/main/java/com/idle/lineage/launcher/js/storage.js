// 存檔輔助處理模組
window.SaveStorageModule = {
    saveLocal: function(key, data) {
        localStorage.setItem(key, data);
    },
    getLocal: function(key) {
        return localStorage.getItem(key);
    }
};
