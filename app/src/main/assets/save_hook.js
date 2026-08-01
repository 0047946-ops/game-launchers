(function () {

    'use strict';


    if (window.__save_hook_loaded) {
        return;
    }

    window.__save_hook_loaded = true;


    console.log(
        "🚀 APK SaveHook 啟動"
    );



    function sendToAndroid(
        data,
        fileName
    ) {


        try {


            if (!data) {

                console.warn(
                    "Save data empty"
                );

                return;

            }



            if (
                window.AndroidBridge &&
                typeof window.AndroidBridge.saveBase64File === "function"
            ) {


                console.log(
                    "📤 傳送 Android:",
                    fileName,
                    data.length
                );



                // 固定三參數

                window.AndroidBridge.saveBase64File(
                    data,
                    "application/json",
                    fileName ||
                    "idle_lineage_save.json"
                );



                console.log(
                    "✅ AndroidBridge 呼叫完成"
                );


            }
            else {


                console.warn(
                    "❌ 找不到 AndroidBridge"
                );


            }



        }
        catch(e) {


            console.error(
                "AndroidBridge error",
                e
            );


        }


    }







    function blobToBase64(
        blob,
        fileName
    ) {


        try {


            console.log(
                "📦 Blob:",
                blob.type,
                blob.size
            );



            const reader =
                new FileReader();



            reader.onloadend =
            function () {


                let result =
                    reader.result;



                if (
                    result &&
                    result.indexOf(",") >= 0
                ) {


                    result =
                    result.substring(
                        result.indexOf(",") + 1
                    );


                }



                sendToAndroid(
                    result,
                    fileName
                );


            };



            reader.readAsDataURL(
                blob
            );


        }
        catch(e) {


            console.error(
                "Blob convert error",
                e
            );


        }


    }








    // ==================================================
    // 1. 攔截 URL.createObjectURL
    // ==================================================


    const oldCreateObjectURL =
        URL.createObjectURL;



    URL.createObjectURL =
    function(blob) {


        const url =
            oldCreateObjectURL.apply(
                URL,
                arguments
            );



        try {


            if (
                blob instanceof Blob
            ) {



                console.log(
                    "📦 createObjectURL",
                    blob.type,
                    blob.size
                );



                if (
                    blob.type.includes("json") ||
                    blob.size > 100
                ) {


                    blobToBase64(
                        blob,
                        "idle_lineage_save.json"
                    );


                }


            }


        }
        catch(e) {



        }



        return url;


    };









    // ==================================================
    // 2. 攔截 <a download>
    // ==================================================


    document.addEventListener(
        "click",
        function(e) {



            try {



                const a =
                    e.target.closest &&
                    e.target.closest(
                        "a[download]"
                    );



                if (!a) {

                    return;

                }




                console.log(
                    "⬇️ 捕捉下載",
                    a.download,
                    a.href
                );





                if (
                    a.href &&
                    a.href.startsWith("blob:")
                ) {



                    e.preventDefault();



                    fetch(
                        a.href
                    )

                    .then(
                        function(r){

                            return r.blob();

                        }
                    )

                    .then(
                        function(blob){


                            blobToBase64(
                                blob,
                                a.download ||
                                "idle_lineage_save.json"
                            );


                        }
                    );


                }



                else if (
                    a.href &&
                    a.href.startsWith("data:")
                ) {



                    e.preventDefault();



                    sendToAndroid(
                        a.href.split(",")[1],
                        a.download ||
                        "idle_lineage_save.json"
                    );


                }



            }
            catch(err) {


                console.error(
                    "download hook error",
                    err
                );


            }


        },
        true
    );








    // ==================================================
    // 3. 攔截 window.open
    // ==================================================


    const oldOpen =
        window.open;



    window.open =
    function(url) {



        try {


            if (
                typeof url === "string"
            ) {



                if (
                    url.startsWith("blob:")
                ) {



                    fetch(url)

                    .then(
                        r=>r.blob()
                    )

                    .then(
                        blob=>{

                            blobToBase64(
                                blob,
                                "idle_lineage_save.json"
                            );

                        }
                    );



                    return null;


                }




                if (
                    url.startsWith("data:")
                ) {


                    sendToAndroid(
                        url.split(",")[1],
                        "idle_lineage_save.json"
                    );


                    return null;


                }



            }



        }
        catch(e) {



        }



        return oldOpen.apply(
            window,
            arguments
        );


    };








    console.log(
        "✅ APK SaveHook 完成"
    );


})();
