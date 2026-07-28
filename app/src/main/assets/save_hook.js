(function () {
    'use strict';

    if (window.__IDLE_SAVE_HOOK_LOADED__) return;
    window.__IDLE_SAVE_HOOK_LOADED__ = true;

    console.log("🚀 [SaveHook] 修正版啟動");

    let lastExportHash = "";
    let lastExportTime = 0;


    // ==================================================
    // Android 匯出核心
    // 相容 2參數 / 3參數 Java Bridge
    // ==================================================

    function saveToAndroid(base64, fileName = "idle_save.json") {

        try {

            if (!base64)
                return;


            const now = Date.now();

            const hash =
                base64.length +
                "_" +
                base64.substring(0,80);


            if (
                hash === lastExportHash &&
                now - lastExportTime < 3000
            ) {
                console.log("[SaveHook] 重複匯出略過");
                return;
            }


            lastExportHash = hash;
            lastExportTime = now;


            if (
                window.AndroidBridge &&
                window.AndroidBridge.saveBase64File
            ) {

                // Java 三參數版本
                if (
                    window.AndroidBridge.saveBase64File.length >= 3
                ) {

                    window.AndroidBridge.saveBase64File(
                        base64,
                        "application/json",
                        fileName
                    );

                }

                // Java 兩參數版本
                else {

                    window.AndroidBridge.saveBase64File(
                        base64,
                        fileName
                    );

                }


                console.log(
                    "✅ [SaveHook] 已送出:",
                    fileName
                );

            } else {

                console.warn(
                    "[SaveHook] 找不到 AndroidBridge"
                );

            }


        } catch(e) {

            console.error(
                "[SaveHook] Android export error",
                e
            );

        }

    }



    function blobToBase64(blob,fileName) {

        try {

            const reader = new FileReader();


            reader.onloadend = function(){

                let result = reader.result || "";


                if(result.includes(",")){

                    result =
                    result.substring(
                        result.indexOf(",") + 1
                    );

                }


                saveToAndroid(
                    result,
                    fileName || "idle_save.json"
                );

            };


            reader.readAsDataURL(blob);


        } catch(e){

            console.error(
                "[SaveHook] blob error",
                e
            );

        }

    }




    function textToBase64(text,fileName){

        try {

            const blob =
            new Blob(
                [text],
                {
                    type:"application/json"
                }
            );


            blobToBase64(
                blob,
                fileName
            );


        } catch(e){}

    }




    // ==================================================
    // 1. createObjectURL 攔截
    // ==================================================

    const oldCreateObjectURL =
    URL.createObjectURL;


    URL.createObjectURL =
    function(blob){

        const url =
        oldCreateObjectURL.apply(
            this,
            arguments
        );


        try {

            if(blob instanceof Blob){

                if(
                    blob.type.includes("json") ||
                    blob.type.includes("text") ||
                    blob.size > 200
                ){

                    blobToBase64(
                        blob,
                        "idle_save.json"
                    );

                }

            }

        } catch(e){}


        return url;

    };




    // ==================================================
    // 2. <a download> 攔截
    // ==================================================

    document.addEventListener(
        "click",
        function(e){

            const a =
            e.target.closest &&
            e.target.closest(
                "a[download]"
            );


            if(!a || !a.href)
                return;


            const name =
            a.download ||
            "idle_save.json";



            if(
                a.href.startsWith("blob:")
            ){

                e.preventDefault();


                fetch(a.href)
                .then(r=>r.blob())
                .then(blob=>{

                    blobToBase64(
                        blob,
                        name
                    );

                });


            }
            else if(
                a.href.startsWith("data:")
            ){

                e.preventDefault();


                const data =
                a.href.split(",")[1];


                saveToAndroid(
                    data,
                    name
                );

            }


        },
        true
    );



    // ==================================================
    // 3. window.open blob/data 攔截
    // ==================================================

    const oldOpen =
    window.open;


    window.open =
    function(url){

        try {

            if(typeof url === "string"){


                if(
                    url.startsWith("blob:")
                ){

                    fetch(url)
                    .then(r=>r.blob())
                    .then(blob=>{

                        blobToBase64(
                            blob,
                            "idle_save.json"
                        );

                    });


                    return null;

                }



                if(
                    url.startsWith("data:")
                ){

                    saveToAndroid(
                        url.split(",")[1],
                        "idle_save.json"
                    );


                    return null;

                }

            }


        } catch(e){}



        return oldOpen.apply(
            this,
            arguments
        );

    };




    // ==================================================
    // 4. FileReader 匯入修復
    // ==================================================

    const originalReadAsText =
    FileReader.prototype.readAsText;



    FileReader.prototype.readAsText =
    function(file,encoding){

        const self = this;


        const oldLoad =
        self.onload;



        self.onload =
        function(e){

            try {

                let raw =
                e.target.result;


                const parsed =
                JSON.parse(raw);



                if(
                    parsed &&
                    parsed.data
                ){

                    raw =
                    typeof parsed.data === "string"
                    ?
                    parsed.data
                    :
                    JSON.stringify(
                        parsed.data
                    );

                }



                if(
                    parsed &&
                    parsed.save
                ){

                    raw =
                    typeof parsed.save === "string"
                    ?
                    parsed.save
                    :
                    JSON.stringify(
                        parsed.save
                    );

                }



                Object.defineProperty(
                    e.target,
                    "result",
                    {
                        value:raw,
                        writable:true
                    }
                );


            } catch(err){}



            if(oldLoad){

                oldLoad.call(
                    self,
                    e
                );

            }


        };


        return originalReadAsText.apply(
            this,
            arguments
        );

    };






    // ==================================================
    // 5. 外掛注入
    // ==================================================

    function appendScript(script){

        if(document.body){

            document.body.appendChild(script);

        }
        else {

            setTimeout(
                ()=>appendScript(script),
                100
            );

        }

    }




    if(!window.__all_plugins_loaded){

        window.__all_plugins_loaded = true;



        const mainPlugin =
        document.createElement(
            "script"
        );


        mainPlugin.src =
        "https://cdn.jsdelivr.net/gh/qcc781192000/idle-lineage-plugin@main/main.user.js?v="
        +
        Date.now();


        appendScript(
            mainPlugin
        );




        const base =
        "https://kid0924.github.io/idle-lineage-class/";



        const commonPlugins = [

            "klh_initial.js",
            "klh_GMShop.js",
            "klh_mobile-perf.js",
            "klh_perf-monitor.js",
            "klh_Backpack.js",
            "klh_pk.js",
            "klh_Pandora.js"

        ].map(
            x=>base+x
        );



        const isAddServer =
        window.location.hostname.includes(
            "pp771007"
        );



        const plugins =
        isAddServer
        ?
        [
            base+"klh_remove-banner.js",
            ...commonPlugins
        ]
        :
        [
            "https://pp771007.github.io/idle-lineage-class/afk-lzcache.js",
            "https://pp771007.github.io/idle-lineage-class/afk-offline.js",
            ...commonPlugins
        ];



        function loadPlugin(src){

            return new Promise(
                (resolve,reject)=>{


                    const s =
                    document.createElement(
                        "script"
                    );


                    s.src =
                    src+
                    "?v="+
                    Date.now();



                    s.onload =
                    resolve;


                    s.onerror =
                    ()=>reject(src);



                    appendScript(
                        s
                    );

                }
            );

        }



        plugins.reduce(
            (p,src)=>
            p.then(
                ()=>loadPlugin(src)
            ),
            Promise.resolve()
        )
        .then(()=>{

            console.log(
                "🎉 外掛全部載入完成"
            );

        })
        .catch(e=>{

            console.error(
                "❌ 外掛載入失敗",
                e
            );

        });

    }





    // ==================================================
    // 6. TMEngine
    // ==================================================

    if(!window.__tm_engine_loaded){

        window.__tm_engine_loaded = true;



        const PerformanceCore = {

            getJitter:function(base,variance){

                return base +
                Math.floor(
                    Math.random()*variance
                );

            }

        };




        const oldSetInterval =
        window.setInterval;



        window.setInterval =
        function(callback,delay,...args){


            const newDelay =
            delay < 150
            ?
            150
            :
            delay;



            return oldSetInterval(
                callback,
                newDelay,
                ...args
            );

        };






        const NetworkOptimizer = {


            _isMobile:false,



            detect:function(){


                NetworkOptimizer._isMobile =
                /Android|iPhone|iPad/i
                .test(
                    navigator.userAgent
                );


            },



            getParams:function(){


                return NetworkOptimizer._isMobile

                ?

                {
                    base:500,
                    variance:700
                }

                :

                {
                    base:120,
                    variance:250
                };


            }


        };






        function executeLogic(){


            const hp =
            document.querySelector(
                ".hp-text"
            );



            if(hp){


                const data =
                hp.innerText
                .split("/")
                .map(Number);



                if(
                    data.length === 2 &&
                    data[0] / data[1] < 0.75
                ){


                    const potion =
                    document.querySelector(
                        "#btn-use-potion"
                    )
                    ||
                    document.querySelector(
                        ".potion-btn"
                    );



                    if(potion){

                        potion.click();

                    }


                }


            }





            const attack =
            document.querySelector(
                ".attack-btn"
            );



            if(
                attack &&
                !attack.classList.contains(
                    "cooldown"
                )
            ){



                const p =
                NetworkOptimizer
                .getParams();



                setTimeout(

                    function(){

                        attack.click();

                    },

                    PerformanceCore.getJitter(
                        p.base,
                        p.variance
                    )

                );


            }



        }






        NetworkOptimizer.detect();




        setInterval(
            executeLogic,
            250
        );



        console.log(
            "✅ TMEngine 啟動"
        );


    }





    console.log(
        "✅ SaveHook 修正版全部載入完成"
    );


})();
