(function(){

"use strict";


if(window.__native_save_bridge_loaded){

    return;

}


window.__native_save_bridge_loaded=true;



console.log(
    "[NativeSaveBridge] loaded"
);





function sendToAndroid(data,name){



    try{


        if(window.AndroidDownloader
            &&
           AndroidDownloader.saveBase64File){



            AndroidDownloader.saveBase64File(
                data,
                "application/json",
                name ||
                "idle_lineage_save.json"
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








// 攔截下載 Blob

const oldCreate =
URL.createObjectURL;



URL.createObjectURL =
function(blob){


    try{


        if(blob
            &&
           blob.type
           &&
           blob.type.includes("json")){


            const reader =
                new FileReader();



            reader.onload=function(){


                sendToAndroid(
                    reader.result,
                    "idle_lineage_save.json"
                );


            };



            reader.readAsDataURL(blob);



        }


    }catch(e){}



    return oldCreate.apply(
        URL,
        arguments
    );


};









// 提供給遊戲或其他腳本呼叫


window.exportNativeSave =
function(data){


    return sendToAndroid(
        data,
        "idle_lineage_save.json"
    );


};









// 通知 Android 匯出完成

window.__markExported=function(){


    console.log(
        "[NativeSaveBridge] export finished"
    );


};






})();
