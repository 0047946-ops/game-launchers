(function () {
    'use strict';

    if (window.__IDLE_APK_SAVE_HOOK__) return;
    window.__IDLE_APK_SAVE_HOOK__ = true;

    console.log("🚀 IDLE APK SaveHook 啟動");


    function sendToAndroid(base64, fileName) {

        try {

            if (!base64) return;


            if (
                window.AndroidBridge &&
                typeof window.AndroidBridge.saveBase64File === "function"
            ) {


                console.log(
                    "📤 傳送 Android:",
                    fileName,
                    base64.length
                );


                window.AndroidBridge.saveBase64File(
                    base64,
                    "application/json",
                    fileName || "idle_save.json"
                );


                return true;

            }


            console.warn(
                "❌ 找不到 AndroidBridge"
            );


        } catch(e) {

            console.error(
                "Android save error",
                e
            );

        }


        return false;

    }





    function blobToBase64(blob,fileName){


        try{


            const reader =
                new FileReader();


            reader.onloadend =
            function(){


                let result =
                    reader.result;


                if(
                    result &&
                    result.includes(",")
                ){

                    result =
                    result.split(",")[1];

                }


                sendToAndroid(
                    result,
                    fileName
                );


            };


            reader.readAsDataURL(blob);



        }catch(e){

            console.error(
                "blob error",
                e
            );

        }


    }







    // ==========================================
    // 攔截 Blob 產生
    // ==========================================


    const oldCreateObjectURL =
        URL.createObjectURL;



    URL.createObjectURL =
    function(blob){


        const url =
        oldCreateObjectURL.apply(
            URL,
            arguments
        );



        try{


            if(blob instanceof Blob){


                console.log(
                    "📦 捕捉 Blob:",
                    blob.type,
                    blob.size
                );


                if(
                    blob.type.includes("json") ||
                    blob.size > 100
                ){

                    blobToBase64(
                        blob,
                        "idle_lineage_save.json"
                    );

                }


            }


        }catch(e){}



        return url;

    };








    // ==========================================
    // 攔截下載按鈕
    // ==========================================


    document.addEventListener(
        "click",
        function(e){


            let a =
            e.target.closest &&
            e.target.closest(
                "a[download]"
            );



            if(!a) return;



            console.log(
                "⬇️ 捕捉下載",
                a.download,
                a.href
            );



            if(
                a.href.startsWith("blob:")
            ){


                e.preventDefault();



                fetch(a.href)

                .then(
                    r=>r.blob()
                )

                .then(
                    blob=>{

                        blobToBase64(
                            blob,
                            a.download ||
                            "idle_lineage_save.json"
                        );


                    }
                );


            }


        },
        true
    );








    // ==========================================
    // data URL
    // ==========================================


    document.addEventListener(
        "click",
        function(e){


            let a =
            e.target.closest &&
            e.target.closest(
                "a"
            );



            if(
                a &&
                a.href &&
                a.href.startsWith("data:")
            ){


                e.preventDefault();


                sendToAndroid(
                    a.href.split(",")[1],
                    a.download ||
                    "idle_lineage_save.json"
                );


            }


        },
        true
    );



    console.log(
        "✅ IDLE APK SaveHook 完成"
    );


})();
