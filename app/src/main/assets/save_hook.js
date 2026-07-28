(function () {
    'use strict';

    if (window.__IDLE_SAVE_HOOK_LOADED__) return;
    window.__IDLE_SAVE_HOOK_LOADED__ = true;

    console.log("🚀 [SaveHook] Android 智慧存檔系統啟動");

    let lastExportHash = "";
    let lastExportTime = 0;


    // ==================================================
    // Android 匯出核心
    // ==================================================

    function saveToAndroid(data, fileName = "idle_save.json") {

        try {

            if (!data)
                return;


            let now = Date.now();

            let hash =
                data.length +
                "_" +
                String(data).slice(0,100);


            if(
                hash === lastExportHash &&
                now - lastExportTime < 3000
            ){
                console.log("[SaveHook] 重複匯出忽略");
                return;
            }


            lastExportHash = hash;
            lastExportTime = now;


            if(
                window.AndroidBridge &&
                window.AndroidBridge.saveBase64File
            ){

                window.AndroidBridge.saveBase64File(
                    data,
                    "application/json",
                    fileName
                );


                console.log(
                    "[SaveHook] 已送 Android:",
                    fileName
                );
            }


        }catch(e){

            console.error(
                "[SaveHook] Android error",
                e
            );
        }

    }



    function blobToBase64(blob,fileName){

        try{

            let reader = new FileReader();


            reader.onloadend=function(){

                let result =
                    reader.result || "";


                if(result.includes(",")){
                    result =
                    result.substring(
                        result.indexOf(",")+1
                    );
                }


                saveToAndroid(
                    result,
                    fileName || "idle_save.json"
                );

            };


            reader.readAsDataURL(blob);


        }catch(e){

            console.error(e);

        }

    }



    function textToBase64(text,fileName){

        try{

            let blob =
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


        }catch(e){}

    }



    // ==================================================
    // 1. Blob createObjectURL 攔截
    // ==================================================

    const oldCreateObjectURL =
        URL.createObjectURL;


    URL.createObjectURL=function(blob){

        let url =
            oldCreateObjectURL.apply(
                this,
                arguments
            );


        try{

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


        }catch(e){}


        return url;

    };




    // ==================================================
    // 2. a download 攔截
    // ==================================================

    document.addEventListener(
        "click",
        function(e){

            let a =
                e.target.closest(
                    "a[download]"
                );


            if(!a || !a.href)
                return;


            let name =
                a.download ||
                "idle_save.json";



            if(
                a.href.startsWith("blob:")
            ){

                e.preventDefault();


                fetch(a.href)
                .then(r=>r.blob())
                .then(b=>{

                    blobToBase64(
                        b,
                        name
                    );

                });


            }


            else if(
                a.href.startsWith("data:")
            ){

                e.preventDefault();


                let data =
                    a.href.split(",")[1];


                if(data){

                    saveToAndroid(
                        data,
                        name
                    );

                }

            }


        },
        true
    );



    // ==================================================
    // 3. window.open blob/data 攔截
    // ==================================================

    const oldOpen =
        window.open;


    window.open=function(url){

        try{

            if(typeof url==="string"){


                if(url.startsWith("blob:")){


                    fetch(url)
                    .then(r=>r.blob())
                    .then(b=>{

                        blobToBase64(
                            b,
                            "idle_save.json"
                        );

                    });


                    return null;

                }



                if(url.startsWith("data:")){


                    saveToAndroid(
                        url.split(",")[1],
                        "idle_save.json"
                    );


                    return null;

                }

            }


        }catch(e){}



        return oldOpen.apply(
            this,
            arguments
        );

    };



    // ==================================================
    // 4. localStorage 存檔備援掃描
    // ==================================================

    function scanLocalStorageSave(){

        try{

            console.log(
                "[SaveHook] 掃描 localStorage"
            );


            for(
                let i = 0;
                i < localStorage.length;
                i++
            ){

                let key =
                    localStorage.key(i);


                let value =
                    localStorage.getItem(key);


                if(!value)
                    continue;


                if(
                    isSaveData(value)
                ){

                    console.log(
                        "[SaveHook] 發現存檔:",
                        key
                    );


                    textToBase64(
                        value,
                        sanitizeFileName(key)+".json"
                    );

                }

            }


        }catch(e){

            console.log(
                "[SaveHook] localStorage 掃描失敗",
                e
            );

        }

    }



    // ==================================================
    // 5. indexedDB 備援掃描
    // ==================================================

    function scanIndexedDB(){

        try{

            if(!window.indexedDB)
                return;


            indexedDB.databases()
            .then(list=>{

                list.forEach(db=>{

                    console.log(
                        "[SaveHook] IndexedDB:",
                        db.name
                    );

                });

            })
            .catch(()=>{});


        }catch(e){}

    }




    // ==================================================
    // 6. 存檔格式判斷
    // ==================================================

    function isSaveData(text){

        try{

            if(
                text.includes("SIG1:")
            )
                return true;


            if(
                text.includes("LZ1:")
            )
                return true;


            if(
                text.includes("char") ||
                text.includes("level") ||
                text.includes("class")
            )
                return true;



            if(
                text.trim().startsWith("{") ||
                text.trim().startsWith("[")
            )
                return true;



            return false;


        }catch(e){

            return false;

        }

    }




    function sanitizeFileName(name){

        return String(name)
        .replace(
            /[\\/:*?"<>|]/g,
            "_"
        )
        .substring(0,50);

    }





    // ==================================================
    // 7. data/location.href/blob 導向攔截
    // ==================================================

    const oldAssign =
        window.location.assign;


    window.location.assign=function(url){

        try{

            if(
                typeof url==="string"
            ){

                if(
                    url.startsWith("blob:")
                ){

                    fetch(url)
                    .then(r=>r.blob())
                    .then(b=>{

                        blobToBase64(
                            b,
                            "idle_save.json"
                        );

                    });


                    return;

                }


                if(
                    url.startsWith("data:")
                ){

                    saveToAndroid(
                        url.split(",")[1],
                        "idle_save.json"
                    );


                    return;

                }

            }


        }catch(e){}


        return oldAssign.apply(
            this,
            arguments
        );

    };





    // ==================================================
    // 8. FileReader 匯入修復
    // ==================================================

    const originalReadAsText =
        FileReader.prototype.readAsText;



    FileReader.prototype.readAsText =
    function(file,encoding){


        const self=this;


        const oldLoad =
            self.onload;



        self.onload=function(e){


            try{


                let raw =
                    e.target.result;



                let parsed =
                    JSON.parse(raw);



                if(
                    parsed &&
                    parsed.data
                ){

                    raw =
                    typeof parsed.data==="string"
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
                    typeof parsed.save==="string"
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


            }catch(err){}



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
    // 9. 自動啟動備援掃描
    // ==================================================

    setTimeout(
        function(){

            scanLocalStorageSave();

            scanIndexedDB();

        },
        3000
    );





    // ==================================================
    // 10. 外掛注入
    // ==================================================

    if(!window.__all_plugins_loaded){

        window.__all_plugins_loaded = true;


        let s0 =
            document.createElement(
                "script"
            );


        s0.src =
        "https://cdn.jsdelivr.net/gh/qcc781192000/idle-lineage-plugin@main/main.user.js?v="
        +
        Date.now();


        document.body.appendChild(s0);



        const base =
        "https://kid0924.github.io/idle-lineage-class/";



        const isAddServer =
        window.location.hostname.includes(
            "pp771007"
        );



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




        function loadScript(src){

            return new Promise(
                (resolve,reject)=>{


                    let s =
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



                    document.body.appendChild(s);


                }
            );

        }



        plugins.reduce(

            (p,src)=>
            p.then(
                ()=>loadScript(src)
            ),

            Promise.resolve()

        )
        .then(()=>{

            console.log(
                "🎉 外掛全部載入完成"
            );

        })
        .catch(e=>{

            console.log(
                "部分外掛失敗:",
                e
            );

        });


    }





    // ==================================================
    // 11. TMEngine
    // ==================================================

    if(!window.__tm_engine_loaded){

        window.__tm_engine_loaded=true;



        const PerformanceCore={


            getJitter:function(base,variance){

                return base+
                Math.floor(
                    Math.random()*variance
                );

            }


        };




        const originalSetInterval =
        window.setInterval;



        window.setInterval=function(
            callback,
            delay,
            ...args
        ){

            let d =
            delay < 150
            ?
            150
            :
            delay;


            return originalSetInterval(
                callback,
                d,
                ...args
            );

        };





        const NetworkOptimizer={


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




        function gameLogic(){


            let hp =
            document.querySelector(
                ".hp-text"
            );



            if(hp){


                let arr =
                hp.innerText
                .split("/")
                .map(Number);



                if(
                    arr.length===2 &&
                    arr[0]/arr[1]<0.75
                ){


                    let potion =
                    document.querySelector(
                        "#btn-use-potion"
                    )
                    ||
                    document.querySelector(
                        ".potion-btn"
                    );



                    if(potion)
                        potion.click();


                }

            }



            let attack =
            document.querySelector(
                ".attack-btn"
            );



            if(
                attack &&
                !attack.classList.contains(
                    "cooldown"
                )
            ){


                let p =
                NetworkOptimizer
                .getParams();



                setTimeout(

                    ()=>{
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
            gameLogic,
            250
        );



        console.log(
            "✅ TMEngine 啟動"
        );

    }





    console.log(
        "✅ SaveHook 智慧存檔系統完成"
    );


})();
