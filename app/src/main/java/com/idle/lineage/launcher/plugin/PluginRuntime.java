package com.idle.lineage.launcher.plugin;

public class PluginRuntime {

    public static String buildRuntimeScript() {

        StringBuilder sb = new StringBuilder();

        sb.append("(function(){\n");

        sb.append("if(window.__NativeApiCheckerLoaded) return;\n");
        sb.append("window.__NativeApiCheckerLoaded = true;\n");

        sb.append("console.log('[PluginRuntime] 啟動 Save Engine API 狀態檢查...');\n");

        sb.append("var checkCount = 0;\n");
        sb.append("var timer = setInterval(function(){\n");
        sb.append(" checkCount++;\n");

        sb.append(" var hasExportSave = typeof window.exportSave === 'function';\n");
        sb.append(" var hasImportSave = typeof window.importSave === 'function';\n");
        sb.append(" var hasSaveGame = typeof window.saveGame === 'function';\n");
        sb.append(" var hasExportPortable = typeof window.exportSavePortable === 'function';\n");

        sb.append(" console.log('[Save Engine Check] exportSave:' + hasExportSave + ");
        sb.append("' | importSave:' + hasImportSave + ");
        sb.append("' | saveGame:' + hasSaveGame + ");
        sb.append("' | exportSavePortable:' + hasExportPortable);\n");

        // 完成條件完全恢復原樣
        sb.append(" if(hasExportSave && hasImportSave && hasSaveGame){\n");
        sb.append("  console.log('[PluginRuntime] Save Engine 核心 API Ready');\n");
        sb.append("  clearInterval(timer);\n");
        sb.append(" } else if(checkCount >= 10){\n");
        sb.append("  clearInterval(timer);\n");
        sb.append(" }\n");

        sb.append("}, 2000);\n");

        sb.append("})();");

        return sb.toString();
    }
}
