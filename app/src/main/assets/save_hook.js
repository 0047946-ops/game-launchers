(function() {
    'use strict';

    if (window.__save_hook_loaded) return;
    window.__save_hook_loaded = true;


    console.log("🚀 SaveHook loaded");


    function sendToAndroid(data, name) {

        try {

            if (
                window.AndroidBridge &&
                typeof window.AndroidBridge.saveBase64File === "function"
            ) {

                console.log(
                    "📤 send save",
                    name,
                    data.length
                );


                window.AndroidBridge.saveBase64File(
                    data,
                    "application/json",
                    name || "idle_lineage_save.json"
                );

                return true;
            }


            console.warn(
                "AndroidBridge missing"
            );


        } catch(e) {

            console.error(
                e
            );

        }


        return false;

    }





    function blobToBase64(blob,name){

        const reader = new FileReader();


        reader.onloadend=function(){

            let result =
                reader.result;


            if(result.includes(",")){
                result =
                result.substring(
                    result.indexOf(",")+1
                );
            }


            sendToAndroid(
                result,
                name
            );

        };


        reader.readAsDataURL(blob);

    }





    const oldCreate =
        URL.createObjectURL;



    URL.createObjectURL =
    function(blob){


        const url =
        oldCreate.apply(
            URL,
            arguments
        );


        try {

            if(blob instanceof Blob){

                console.log(
                    "Blob:",
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







    document.addEventListener(
        "click",
        function(e){


            const a =
            e.target.closest &&
            e.target.closest(
                "a[download]"
            );


            if(!a) return;



            if(
                a.href.startsWith("blob:")
            ){

                console.log(
                    "capture download",
                    a.download
                );


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


})();
