package com.idle.lineage.launcher;


import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;


import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;



public class SaveManager {


    private static final String TAG =
            "SaveManager";


    public static void processAndSaveFile(
            Context context,
            String data,
            String mimeType,
            String fileName
    ){


        try{


            byte[] bytes =
                    decodeData(data);



            if(fileName == null
                    ||
               fileName.isEmpty()){


                fileName =
                        "idle_lineage_save_"
                        +
                        System.currentTimeMillis()
                        +
                        ".json";


            }



            if(!fileName.endsWith(".json")){


                fileName += ".json";


            }



            boolean result =
                    saveFile(
                            context,
                            bytes,
                            fileName,
                            mimeType
                    );



            Log.d(
                    TAG,
                    "save result = "
                    + result
            );



        }catch(Exception e){


            Log.e(
                    TAG,
                    "save error",
                    e
            );


        }


    }







    private static byte[] decodeData(
            String data
    )
    throws Exception{


        if(data == null){


            return "{}"
                    .getBytes(
                            StandardCharsets.UTF_8
                    );


        }



        data =
                data.trim();





        // SIG1

        if(data.startsWith("SIG1:")){


            return data.getBytes(
                    StandardCharsets.UTF_8
            );


        }





        // JSON

        if(
                data.startsWith("{")
                ||
                data.startsWith("[")
        ){


            return data.getBytes(
                    StandardCharsets.UTF_8
            );


        }







        // Data URL

        if(data.startsWith("data:")){


            int index =
                    data.indexOf(",");



            if(index > 0){


                String header =
                        data.substring(
                                0,
                                index
                        );



                String body =
                        data.substring(
                                index + 1
                        );



                if(
                    header.contains(
                            ";base64"
                    )
                ){


                    return Base64.decode(
                            body,
                            Base64.DEFAULT
                    );


                }
                else{


                    return URLDecoder
                            .decode(
                                    body,
                                    "UTF-8"
                            )
                            .getBytes(
                                    StandardCharsets.UTF_8
                            );


                }


            }


        }







        // Base64

        try{


            return Base64.decode(
                    data,
                    Base64.DEFAULT
            );


        }catch(Exception e){



            return data.getBytes(
                    StandardCharsets.UTF_8
            );


        }



    }








    private static boolean saveFile(
            Context context,
            byte[] bytes,
            String fileName,
            String mimeType
    ){



        try{



            if(Build.VERSION.SDK_INT
                    >=
               Build.VERSION_CODES.Q){



                ContentValues values =
                        new ContentValues();



                values.put(
                        MediaStore.Downloads.DISPLAY_NAME,
                        fileName
                );



                values.put(
                        MediaStore.Downloads.MIME_TYPE,
                        "application/json"
                );



                values.put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS
                );



                Uri uri =
                        context
                        .getContentResolver()
                        .insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                values
                        );



                if(uri == null){


                    return false;


                }



                OutputStream os =
                        context
                        .getContentResolver()
                        .openOutputStream(uri);



                if(os != null){


                    os.write(bytes);

                    os.flush();

                    os.close();


                    return true;


                }



            }



        }catch(Exception e){


            Log.e(
                    TAG,
                    "write error",
                    e
            );


        }



        return false;


    }



}
