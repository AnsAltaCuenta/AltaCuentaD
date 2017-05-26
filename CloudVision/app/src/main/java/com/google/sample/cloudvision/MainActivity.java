/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sample.cloudvision;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequest;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.FaceAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class MainActivity extends AppCompatActivity {
    private static final String CLOUD_VISION_API_KEY = "AIzaSyA081GZ_FetOvIH4lCfDSgZHU-Hn-MIM6E";
    public static final String FILE_NAME = "temp.jpg";
    private static final String ANDROID_CERT_HEADER = "X-Android-Cert";
    private static final String ANDROID_PACKAGE_HEADER = "X-Android-Package";

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int GALLERY_PERMISSIONS_REQUEST = 0;
    private static final int GALLERY_IMAGE_REQUEST = 1;
    public static final int CAMERA_PERMISSIONS_REQUEST = 2;
    public static final int CAMERA_IMAGE_REQUEST = 3;

    public TextView mImageDetails;
    private ImageView mMainImage;
    public EditText mList;
    public TextView mMensaje;

    //comentarioss
//Inicializa componentes de interfaz de usuario a Activity Main
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Proceso que realiza botón para elegir imagen
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mList.setVisibility(View.INVISIBLE);
                mList.setText("");
                mMensaje.setText("");
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder
                        .setMessage(R.string.dialog_select_prompt)
                        .setPositiveButton(R.string.dialog_select_gallery, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startGalleryChooser();
                            }
                        })
                        .setNegativeButton(R.string.dialog_select_camera, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                startCamera();
                            }
                        });
                builder.create().show();
            }
        });
        // asocia los componentes de interfaz de usuario a la clase Main Activity
        mImageDetails = (TextView) findViewById(R.id.image_details);
        mMainImage = (ImageView) findViewById(R.id.main_image);
        mList = (EditText) findViewById(R.id.editText);
        mMensaje = (TextView) findViewById(R.id.mensajes);
    }

    //Método si elige una imagen de galería
    public void startGalleryChooser() {
        if (PermissionUtils.requestPermission(this, GALLERY_PERMISSIONS_REQUEST, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            startActivityForResult(Intent.createChooser(intent, "Selecciona una foto "),
                    GALLERY_IMAGE_REQUEST);
        }
    }

    //Método si elige tomar una foto desde cámara
    public void startCamera() {
        if (PermissionUtils.requestPermission(
                this,
                CAMERA_PERMISSIONS_REQUEST,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA)) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(intent, CAMERA_IMAGE_REQUEST);
        }
    }

    //método que obtiene la imagen de origen
    public File getCameraFile() {
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        return new File(dir, FILE_NAME);
    }

    //Método prepara imagen para procesamiento
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GALLERY_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            uploadImage(data.getData());
        } else if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            Uri photoUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", getCameraFile());
            uploadImage(photoUri);
        }
    }

    //método solicita permisos para ejecución de imagen
    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CAMERA_PERMISSIONS_REQUEST:
                if (PermissionUtils.permissionGranted(requestCode, CAMERA_PERMISSIONS_REQUEST, grantResults)) {
                    startCamera();
                }
                break;
            case GALLERY_PERMISSIONS_REQUEST:
                if (PermissionUtils.permissionGranted(requestCode, GALLERY_PERMISSIONS_REQUEST, grantResults)) {
                    startGalleryChooser();
                }
                break;
        }
    }

    //Carga y da formato a la imagen para su procesamiento,
    public void uploadImage(Uri uri) {
        if (uri != null) {
            try {
                // scale the image to save on bandwidth
                Bitmap bitmap =
                        scaleBitmapDown(
                                MediaStore.Images.Media.getBitmap(getContentResolver(), uri),
                                1200);

                callCloudVision(bitmap);
                mMainImage.setImageBitmap(bitmap);

            } catch (IOException e) {
                Log.d(TAG, "Image picking failed because " + e.getMessage());
                Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
            }
        } else {
            Log.d(TAG, "Image picker gave us a null image.");
            Toast.makeText(this, R.string.image_picker_error, Toast.LENGTH_LONG).show();
        }
    }

    //Método que realiza el llamado a Cloud Vision, arma request y lo envía para recibir los resultados
    private void callCloudVision(final Bitmap bitmap) throws IOException {
        // Switch text to loading
        mImageDetails.setText(R.string.loading_message);

        // Do the real work in an async task, because we need to use the network anyway
        new AsyncTask<Object, Void, String>() {
            @Override
            protected String doInBackground(Object... params) {
                try {
                    HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
                    JsonFactory jsonFactory = GsonFactory.getDefaultInstance();

                    VisionRequestInitializer requestInitializer =
                            new VisionRequestInitializer(CLOUD_VISION_API_KEY) {
                                /**
                                 * We override this so we can inject important identifying fields into the HTTP
                                 * headers. This enables use of a restricted cloud platform API key.
                                 */
                                @Override
                                protected void initializeVisionRequest(VisionRequest<?> visionRequest)
                                        throws IOException {
                                    super.initializeVisionRequest(visionRequest);

                                    String packageName = getPackageName();
                                    visionRequest.getRequestHeaders().set(ANDROID_PACKAGE_HEADER, packageName);

                                    String sig = PackageManagerUtils.getSignature(getPackageManager(), packageName);

                                    visionRequest.getRequestHeaders().set(ANDROID_CERT_HEADER, sig);
                                }
                            };

                    Vision.Builder builder = new Vision.Builder(httpTransport, jsonFactory, null);
                    builder.setVisionRequestInitializer(requestInitializer);

                    Vision vision = builder.build();

                    BatchAnnotateImagesRequest batchAnnotateImagesRequest =
                            new BatchAnnotateImagesRequest();
                    batchAnnotateImagesRequest.setRequests(new ArrayList<AnnotateImageRequest>() {{
                        AnnotateImageRequest annotateImageRequest = new AnnotateImageRequest();


                        // Add the image
                        Image base64EncodedImage = new Image();
                        // Convert the bitmap to a JPEG
                        // Just in case it's a format that Android understands but Cloud Vision
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream);
                        byte[] imageBytes = byteArrayOutputStream.toByteArray();

                        // Base64 encode the JPEG
                        base64EncodedImage.encodeContent(imageBytes);
                        annotateImageRequest.setImage(base64EncodedImage);

                        // add the features we want
                        annotateImageRequest.setFeatures(new ArrayList<Feature>() {{
                            Feature documentText = new Feature();
                            //Feature document = new Feature();
                            Feature facialDetection = new Feature();
                            //
                            documentText.setType("DOCUMENT_TEXT_DETECTION");
                            //document.setType("TEXT_DETECTION");
                            facialDetection.setType("FACE_DETECTION");
                            //
                           // annotateImageRequest.setFeatures(Collections.singletonList(documentText));
                            add(documentText);
                      //      Log.d(TAG, "FEATURE DOCUMENT TEXT "+ documentText);
                            //add(document);
                            add(facialDetection);
                        //    Log.d(TAG, "FEATURE FACIAL DETECTION "+ facialDetection);
                        }});

                        // Add the list of one thing to the request
                        add(annotateImageRequest);
                        //Log.d(TAG, "Annotate image request: " + annotateImageRequest);
                    }});

                    Vision.Images.Annotate annotateRequest =
                            vision.images().annotate(batchAnnotateImagesRequest);
                    // Due to a bug: requests to Vision API containing large images fail when GZipped.
                    annotateRequest.setDisableGZipContent(true);
                    Log.d(TAG, "created Cloud Vision request object, sending request");
                    //Log.d(TAG, "SEGÚN ESTE ES EL REQUEST: " + batchAnnotateImagesRequest);


                    BatchAnnotateImagesResponse response = annotateRequest.execute();
                    return convertResponseToString(response);



                } catch (GoogleJsonResponseException e) {
                    Log.d(TAG, "failed to make API request because " + e.getContent());
                } catch (IOException e) {
                    Log.d(TAG, "failed to make API request because of other IOException " +
                            e.getMessage());
                }
                return "Asegurate de tener conexión a internet.";
            }
            protected void onPostExecute(String result){
                if (result!=null){
                    String res = "Resultado:\n";
                    Log.d(TAG,"Recupera datos final de método onPost-- INICIO"+result);
                    mImageDetails.setText(R.string.final_message);
                   // mList.setVisibility(View.VISIBLE);
                    //mList.setText(result);
                    res+=result;
                    mMensaje.setText(res);
                    Log.d(TAG,"resultado"+res);
                }else{
                    mImageDetails.setText(R.string.final_message);
                    mMensaje.setText(R.string.image_picker_error);
                }
            }
        }.execute();
    }

    public Bitmap scaleBitmapDown(Bitmap bitmap, int maxDimension) {

        int originalWidth = bitmap.getWidth();
        int originalHeight = bitmap.getHeight();
        int resizedWidth = maxDimension;
        int resizedHeight = maxDimension;

        if (originalHeight > originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = (int) (resizedHeight * (float) originalWidth / (float) originalHeight);
        } else if (originalWidth > originalHeight) {
            resizedWidth = maxDimension;
            resizedHeight = (int) (resizedWidth * (float) originalHeight / (float) originalWidth);
        } else if (originalHeight == originalWidth) {
            resizedHeight = maxDimension;
            resizedWidth = maxDimension;
        }
        return Bitmap.createScaledBitmap(bitmap, resizedWidth, resizedHeight, false);
    }

    //método que da formato al resultado de la imagen procesada por Vision Cloud
    public String convertResponseToString(BatchAnnotateImagesResponse response) {
        String message = "I found these things:\n\n";
        String nombreString ="";
        String domString  ="";
        String curpString  ="";
        String fin="";

        Log.d(TAG,"Valida los tipos de response");
        List<EntityAnnotation> texts = response.getResponses().get(0).getTextAnnotations();
        List<FaceAnnotation> facialDetections = response.getResponses().get(0).getFaceAnnotations();

        if (texts != null) {
            int i = 0;
            for (EntityAnnotation text : texts) {
                message +=text.getDescription();
                i++;
               Log.d(TAG,"Numero: " + i + "Response de TEXT DETECTION: " + text );
            }
            if (message.contains("NOMBRE")&&message.contains("DOMICILIO")&&message.contains("CURP")||
                    message.contains("FOLIO")&&message.contains("CLAVE")&&message.contains("ESTA")) {
           // if (message.contains("NOMBRE")||message.contains("DIRECCION")){
                int pocisionNombre = message.indexOf("NOMBRE");
                int posDomi = message.indexOf("DOMICILIO");
                int posicionClave = message.indexOf("CLAVE");

                Log.d(TAG, "pocisionNombre: " + pocisionNombre + "posDomi: " + posDomi);

                int posFolio = message.indexOf("FOLIO");
                int posCurp = message.indexOf("CURP");
                int posAnio = message.indexOf("ANO");
                int posEstado = message.indexOf("ESTA");

                //obtiene nombre
                nombreString += message.substring(pocisionNombre, posDomi);
                //StringTokenizer tkn = new StringTokenizer(nombreString," ");
                //String pri = tkn.nextToken();
                Log.d(TAG, "rango " + nombreString);

                //obtiene domicilio
                if (message.contains("FOLIO")) {
                        domString += message.substring(posDomi, posFolio);
                        Log.d(TAG, "domicilio " + domString);
                } else {
                    domString = message.substring(posDomi, posicionClave);
                    Log.d(TAG, "Domicilio 1 " + domString);
                }
                //obtiene curp
                if (message.contains("CURP")) {
                    if (message.contains("FOLIO")) {
                            curpString = message.substring(posCurp, posEstado);
                            Log.d(TAG, "CURP1 " + curpString);
                    } else {
                        curpString += message.substring(posCurp, posAnio);
                        Log.d(TAG, "CURP2 " + curpString);
                    }
                }
            }//fin de if que valida entrada
             else{
                fin+="Algo sucedió con la imagen. Por favor, vuelve a capturar.";
                Log.d(TAG,"Error con la imagen, baja resolución");
            }
        } else {
            message += "nothing";
            fin+="Algo sucedió con la imagen. Selecciona otra.";
            Log.d(TAG,"Error con la imagen");
           // mList.setText(R.string.image_picker_error);
        }
      fin += nombreString + domString + curpString;
        Log.d(TAG,"final " + fin);
        return fin;

    }
}
