package com.idle.lineage.launcher.plugin;

public class PluginRuntime {

    public static String buildRuntimeScript() {

        StringBuilder sb = new StringBuilder();

        sb.append("(function(){\n");

        sb.append("if(window.__DiscoveryLoaded)return;\n");
        sb.append("window.__DiscoveryLoaded=true;\n");

        sb.append("function log(msg){\n");
        sb.append(" console.log('[Discovery] '+msg);\n");
        sb.append(" if(window.AndroidBridge && window.AndroidBridge.logFromJS){\n");
        sb.append("  window.AndroidBridge.logFromJS(msg);\n");
        sb.append(" }\n");
        sb.append("}\n");

        sb.append("log('12.1 Character Discovery Started');\n");

        sb.append("var keys=[\n");
        sb.append("'player',");
        sb.append("'character',");
        sb.append("'gameData',");
        sb.append("'saveData',");
        sb.append("'GameState',");
        sb.append("'save',");
        sb.append("'g_save',");
        sb.append("'game',");
        sb.append("'account',");
        sb.append("'user',");
        sb.append("'role',");
        sb.append("'hero',");
        sb.append("'playerData',");
        sb.append("'characterData'");
        sb.append("];\n");

        sb.append("var checked={};\n");

        sb.append("var timer=setInterval(function(){\n");

        sb.append(" for(var i=0;i<keys.length;i++){\n");

        sb.append("  var k=keys[i];\n");

        sb.append("  if(window[k]!==undefined && !checked['window.'+k]){\n");

        sb.append("   checked['window.'+k]=true;\n");

        sb.append("   try{\n");
        sb.append("    log('[FOUND] window.'+k);\n");
        sb.append("    log(JSON.stringify(window[k]).substring(0,300));\n");
        sb.append("   }catch(e){\n");
        sb.append("    log('[ERROR] '+k);\n");
        sb.append("   }\n");

        sb.append("  }\n");

        sb.append(" }\n");


        sb.append(" try{\n");

        sb.append("  for(var j=0;j<localStorage.length;j++){\n");

        sb.append("   var key=localStorage.key(j);\n");
        sb.append("   var value=localStorage.getItem(key);\n");

        sb.append("   if(value && value.length>20){\n");

        sb.append("    var label='localStorage['+key+']';\n");

        sb.append("    if(!checked[label]){\n");

        sb.append("     checked[label]=true;\n");

        sb.append("     log('[FOUND] '+label);\n");

        sb.append("     log(value.substring(0,300));\n");

        sb.append("    }\n");

        sb.append("   }\n");

        sb.append("  }\n");

        sb.append(" }catch(e){\n");
        sb.append("  log('[localStorage ERROR]');\n");
        sb.append(" }\n");


        sb.append("},2000);\n");


        sb.append("setTimeout(function(){\n");

        sb.append(" clearInterval(timer);\n");

        sb.append(" log('Discovery finished');\n");

        sb.append("},40000);\n");


        sb.append("})();");

        return sb.toString();
    }
}
