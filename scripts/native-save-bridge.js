(function(){

"use strict";


if(window.__NativeSaveBridgeLoaded){

    return;

}


window.__NativeSaveBridgeLoaded = true;



console.log(
    "[NativeSaveBridge] loaded"
);





function sendToAndroid(
    data,
    fileName
){


    try{


        if(
            window.AndroidBridge
            &&
            typeof window.AndroidBridge.saveBase64File
            ===
            "function"
        ){


            window.AndroidBridge.saveBase64File(
                data,
                fileName ||
                "idle_lineage_save.json"
            );


            console.log(
                "[NativeSaveBridge] send success"
            );


            return true;

        }


    }catch(e){


        console.error(
            "[NativeSaveBridge]",
            e
        );


    }


    return false;


}








// =============================
// 攔截 Blob 匯出
// =============================


const oldCreateObjectURL =
    URL.createObjectURL;



URL.createObjectURL =
function(blob){



    try{


        if(
            blob
            &&
            blob.type
            &&
            (
                blob.type.includes("json")
                ||
                blob.type.includes("text")
            )
        ){



            let reader =
                new FileReader();



            reader.onload =
            function(){


                sendToAndroid(
                    reader.result,
                    "idle_lineage_save.json"
                );


            };



            reader.readAsDataURL(
                blob
            );



        }


    }catch(e){


        console.error(e);


    }



    return oldCreateObjectURL.apply(
        URL,
        arguments
    );


};








// =============================
// 攔截 download link
// =============================


document.addEventListener(
"click",
function(e){


    let target =
        e.target;



    if(
        target
        &&
        target.tagName
        ===
        "A"
    ){


        let href =
            target.href;



        if(
            href
            &&
            (
                href.startsWith("blob:")
                ||
                href.startsWith("data:")
            )
        ){


            console.log(
                "[NativeSaveBridge] download detected"
            );


        }


    }



},
true);








// =============================
// 提供遊戲呼叫
// =============================


window.exportNativeSave =
function(data){


    return sendToAndroid(
        data,
        "idle_lineage_save.json"
    );


};








console.log(
    "[NativeSaveBridge] ready"
);



})();
