(function(){

"use strict";


if(window.__native_save_bridge_loaded){

    return;

}


window.__native_save_bridge_loaded = true;



console.log(
    "[NativeSaveBridge] loaded"
);





function sendToAndroid(
    data,
    name
){

    try{


        if(
            window.AndroidBridge
            &&
            AndroidBridge.saveBase64File
        ){


            AndroidBridge.saveBase64File(

                data,

                name ||
                "idle_lineage_save.json"

            );


            console.log(
                "[NativeSaveBridge] sent"
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






/*
    攔截 Blob
*/


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



            reader.readAsDataURL(blob);


        }



    }catch(e){



        console.error(
            e
        );


    }





    return oldCreateObjectURL.apply(
        URL,
        arguments
    );


};








/*
    遊戲主動呼叫

    window.exportNativeSave(data)

*/


window.exportNativeSave =
function(data){


    return sendToAndroid(

        data,

        "idle_lineage_save.json"

    );


};






/*
    攔截下載按鈕

*/


const oldClick =
HTMLAnchorElement.prototype.click;



HTMLAnchorElement.prototype.click =
function(){


    try{


        if(
            this.href
            &&
            (
                this.href.startsWith("blob:")
                ||
                this.href.startsWith("data:")
            )
        ){


            sendToAndroid(

                this.href,

                "idle_lineage_save.json"

            );


        }



    }catch(e){}



    return oldClick.apply(
        this,
        arguments
    );


};






console.log(
    "[NativeSaveBridge] ready"
);



})();
