package com.idle.lineage.launcher.plugin;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;


public class PluginRuntime {


    private final Context context;

    private final List<String> pluginUrls =
            new ArrayList<>();


    private final List<String> bookmarkUrls =
            new ArrayList<>();





    public PluginRuntime(Context context){

        this.context = context;

    }





    /*
        新增外掛網址

        玩家自行提供
    */

    public void addPluginUrl(String url){


        if(url == null || url.trim().isEmpty()){

            return;

        }


        if(!pluginUrls.contains(url)){


            pluginUrls.add(url);


        }


    }





    /*
        新增書籤腳本網址
    */

    public void addBookmarkUrl(String url){


        if(url == null || url.trim().isEmpty()){

            return;

        }


        if(!bookmarkUrls.contains(url)){


            bookmarkUrls.add(url);


        }


    }





    /*
        清除
    */

    public void clear(){


        pluginUrls.clear();

        bookmarkUrls.clear();


    }






    /*
        注入全部腳本
    */

    public void inject(WebView webView){



        for(String url : pluginUrls){


            injectScript(
                    webView,
                    url
            );


        }





        for(String url : bookmarkUrls){


            injectScript(
                    webView,
                    url
            );


        }


    }







    private void injectScript(
            WebView webView,
            String url
    ){



        String safe =
                Uri.encode(url);



        String js =

                "(function(){"
              + "var s=document.createElement('script');"
              + "s.src='"+safe+"';"
              + "s.onload=function(){"
              + "console.log('[PluginRuntime] loaded');"
              + "};"
              + "document.body.appendChild(s);"
              + "})();";



        webView.evaluateJavascript(
                js,
                null
        );



    }






    public List<String> getPluginUrls(){


        return pluginUrls;


    }





    public List<String> getBookmarkUrls(){


        return bookmarkUrls;


    }




}
