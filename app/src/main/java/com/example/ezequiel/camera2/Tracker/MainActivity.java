package com.example.ezequiel.camera2.Tracker;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ezequiel.camera2.R;
import com.example.ezequiel.camera2.others.Camera2Source;
import com.example.ezequiel.camera2.others.CameraSource;
import com.example.ezequiel.camera2.utils.Utils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.MultiDetector;
import com.google.android.gms.vision.MultiProcessor;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

public class MainActivity extends AppCompatActivity  implements BarcodeGraphic.BarcodeUpdateListener,OcrGraphic.OcrUpdateListener{
    private String TAG = this.getClass().getSimpleName();
    private Context context;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_STORAGE_PERMISSION = 201;
    private TextView cameraVersion;
    private TextView tv_barcodeResult;
    private ImageView ivAutoFocus;

    // CAMERA VERSION ONE DECLARATIONS
    private CameraSource mCameraSource = null;

    // CAMERA VERSION TWO DECLARATIONS
    private Camera2Source mCamera2Source = null;

    // COMMON TO BOTH CAMERAS
    private CameraSourcePreview mPreview;
    private GraphicOverlay mGraphicOverlay;
    private boolean wasActivityResumed = false;
    private boolean isRecordingVideo = false;
    private Button takePictureButton;
    private Button videoButton;

    // MUST BE CAREFUL USING THIS VARIABLE.
    // ANY ATTEMPT TO START CAMERA2 ON API < 21 WILL CRASH.
    private boolean useCamera2 = true;
    private Size stream1Size ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        context = getApplicationContext();

        takePictureButton = (Button) findViewById(R.id.btn_takepicture);
        videoButton = (Button) findViewById(R.id.btn_video);
        mPreview = (CameraSourcePreview) findViewById(R.id.preview);
        mGraphicOverlay = (GraphicOverlay) findViewById(R.id.faceOverlay);
        cameraVersion = (TextView) findViewById(R.id.cameraVersion);
        ivAutoFocus = (ImageView) findViewById(R.id.ivAutoFocus);
        tv_barcodeResult = (TextView) findViewById(R.id.tv_barcode_result);


        //init stream size
        stream1Size = new Size(3840,2160);

        if(checkGooglePlayAvailability()) {
            requestPermissionThenOpenCamera();


            takePictureButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    videoButton.setEnabled(false);
                    takePictureButton.setEnabled(false);
                    if(useCamera2) {
                        if(mCamera2Source != null)mCamera2Source.takePicture(camera2SourceShutterCallback, camera2SourcePictureCallback);
                    } else {
                        if(mCameraSource != null)mCameraSource.takePicture(cameraSourceShutterCallback, cameraSourcePictureCallback);
                    }
                }
            });

            videoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    takePictureButton.setEnabled(false);
                    videoButton.setEnabled(false);
                    if(isRecordingVideo) {
                        if(useCamera2) {
                            if(mCamera2Source != null)mCamera2Source.stopVideo();
                        } else {
                            if(mCameraSource != null)mCameraSource.stopVideo();
                        }
                    } else {
                        if(useCamera2){
                            if(mCamera2Source != null)mCamera2Source.recordVideo(camera2SourceVideoStartCallback, camera2SourceVideoStopCallback, camera2SourceVideoErrorCallback);
                        } else {
                            if(mCameraSource != null)mCameraSource.recordVideo(cameraSourceVideoStartCallback, cameraSourceVideoStopCallback, cameraSourceVideoErrorCallback);
                        }
                    }
                }
            });

            mPreview.setOnTouchListener(CameraPreviewTouchListener);
        }
    }


    final CameraSource.ShutterCallback cameraSourceShutterCallback = new CameraSource.ShutterCallback() {@Override public void onShutter() {Log.d(TAG, "Shutter Callback!");}};
    final CameraSource.PictureCallback cameraSourcePictureCallback = new CameraSource.PictureCallback() {
        @Override
        public void onPictureTaken(Bitmap picture) {
            Log.d(TAG, "Taken picture is here!");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    videoButton.setEnabled(true);
                    takePictureButton.setEnabled(true);
                }
            });
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "/camera_picture.png"));
                picture.compress(Bitmap.CompressFormat.JPEG, 95, out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };
    final CameraSource.VideoStartCallback cameraSourceVideoStartCallback = new CameraSource.VideoStartCallback() {
        @Override
        public void onVideoStart() {
            isRecordingVideo = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    videoButton.setEnabled(true);
                    videoButton.setText(getString(R.string.stop_video));
                }
            });
            Toast.makeText(context, "Video STARTED!", Toast.LENGTH_SHORT).show();
        }
    };
    final CameraSource.VideoStopCallback cameraSourceVideoStopCallback = new CameraSource.VideoStopCallback() {
        @Override
        public void onVideoStop(String videoFile) {
            isRecordingVideo = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    takePictureButton.setEnabled(true);
                    videoButton.setEnabled(true);
                    videoButton.setText(getString(R.string.record_video));
                }
            });
            Toast.makeText(context, "Video STOPPED!", Toast.LENGTH_SHORT).show();
        }
    };
    final CameraSource.VideoErrorCallback cameraSourceVideoErrorCallback = new CameraSource.VideoErrorCallback() {
        @Override
        public void onVideoError(String error) {
            isRecordingVideo = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    takePictureButton.setEnabled(true);
                    videoButton.setEnabled(true);
                    videoButton.setText(getString(R.string.record_video));
                }
            });
            Toast.makeText(context, "Video Error: "+error, Toast.LENGTH_LONG).show();
        }
    };
    final Camera2Source.VideoStartCallback camera2SourceVideoStartCallback = new Camera2Source.VideoStartCallback() {
        @Override
        public void onVideoStart() {
            isRecordingVideo = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    videoButton.setEnabled(true);
                    videoButton.setText(getString(R.string.stop_video));
                }
            });
            Toast.makeText(context, "Video STARTED!", Toast.LENGTH_SHORT).show();
        }
    };
    final Camera2Source.VideoStopCallback camera2SourceVideoStopCallback = new Camera2Source.VideoStopCallback() {
        @Override
        public void onVideoStop(String videoFile) {
            isRecordingVideo = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    takePictureButton.setEnabled(true);
                    videoButton.setEnabled(true);
                    videoButton.setText(getString(R.string.record_video));
                }
            });
            Toast.makeText(context, "Video STOPPED!", Toast.LENGTH_SHORT).show();
        }
    };
    final Camera2Source.VideoErrorCallback camera2SourceVideoErrorCallback = new Camera2Source.VideoErrorCallback() {
        @Override
        public void onVideoError(String error) {
            isRecordingVideo = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    takePictureButton.setEnabled(true);
                    videoButton.setEnabled(true);
                    videoButton.setText(getString(R.string.record_video));
                }
            });
            Toast.makeText(context, "Video Error: "+error, Toast.LENGTH_LONG).show();
        }
    };

    final Camera2Source.ShutterCallback camera2SourceShutterCallback = new Camera2Source.ShutterCallback() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onShutter() {Log.d(TAG, "Shutter Callback for CAMERA2");}
    };

    final Camera2Source.PictureCallback camera2SourcePictureCallback = new Camera2Source.PictureCallback() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onPictureTaken(Image image) {
            Log.d(TAG, "Taken picture is here!");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    videoButton.setEnabled(true);
                    takePictureButton.setEnabled(true);
                }
            });
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            Bitmap picture = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(new File(Environment.getExternalStorageDirectory(), "/camera2_picture.png"));
                picture.compress(Bitmap.CompressFormat.JPEG, 95, out);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private boolean checkGooglePlayAvailability() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context);
        if(resultCode == ConnectionResult.SUCCESS) {
            return true;
        } else {
            if(googleApiAvailability.isUserResolvableError(resultCode)) {
                googleApiAvailability.getErrorDialog(MainActivity.this, resultCode, 2404).show();
            }
        }
        return false;
    }

    private void requestPermissionThenOpenCamera() {
        if(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                useCamera2 = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
//                createCameraSourceFront();
                createCameraSourceBack();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }



    private void createCameraSourceBack() {


        FaceDetector faceDetector = new FaceDetector.Builder(context).build();
        FaceTrackerFactory faceFactory = new FaceTrackerFactory(mGraphicOverlay);
        faceDetector.setProcessor(
                new MultiProcessor.Builder<>(faceFactory).build());


        // A barcode detector is created to track barcodes.  An associated multi-processor instance
        // is set to receive the barcode detection results, track the barcodes, and maintain
        // graphics for each barcode on screen.  The factory is used by the multi-processor to
        // create a separate tracker instance for each barcode.
        BarcodeDetector barcodeDetector = new BarcodeDetector.Builder(context).build();
        BarcodeTrackerFactory barcodeFactory = new BarcodeTrackerFactory(mGraphicOverlay,MainActivity.this);
        barcodeDetector.setProcessor(new MultiProcessor.Builder<>(barcodeFactory).build());


        //ocr
        TextRecognizer textRecognizer = new TextRecognizer.Builder(context).build();
        OcrTrackerFactory ocrTrackerFactory = new OcrTrackerFactory(mGraphicOverlay,MainActivity.this);
        textRecognizer.setProcessor(new MultiProcessor.Builder<>(ocrTrackerFactory).build());


        if(barcodeDetector.isOperational()) {
            Log.d(TAG,"AAA_barcodeDetector isOperational ");
        }else{
            Log.e(TAG,"AAA_barcodeDetector is fail ");
        }


        if(textRecognizer.isOperational()){
            Log.d(TAG,"AAA_textRecognizer isOperational ");
        }else{
            Log.e(TAG,"AAA_textRecognizer is fail ");
        }


        MultiDetector multiDetector = new MultiDetector.Builder()
                .add(faceDetector)
                .add(barcodeDetector)
                .add(textRecognizer)
                .build();


        if(multiDetector.isOperational()) {
            Log.d(TAG,"AAA_multiDetector isOperational ");
        } else {
            Toast.makeText(context, "FACE DETECTION NOT AVAILABLE", Toast.LENGTH_SHORT).show();
        }





        if(useCamera2) {
            mCamera2Source = new Camera2Source.Builder(context, multiDetector)
                    .setFocusMode(Camera2Source.CAMERA_AF_CONTINUOUS_PICTURE)
                    .setFlashMode(Camera2Source.CAMERA_FLASH_AUTO)
                    .setFacing(Camera2Source.CAMERA_FACING_BACK)
                    .setStreamSize(stream1Size)
                    .build();

            startCameraSource();
        }
    }

    private void startCameraSource() {
        if(useCamera2) {
            if(mCamera2Source != null) {
                cameraVersion.setText("Camera 2");
                try {mPreview.start(mCamera2Source, mGraphicOverlay);
                } catch (IOException e) {
                    Log.e(TAG, "Unable to start camera source 2.", e);
                    mCamera2Source.release();
                    mCamera2Source = null;
                }
            }
        } else {
            if (mCameraSource != null) {
                cameraVersion.setText("Camera 1");
                try {mPreview.start(mCameraSource, mGraphicOverlay);
                } catch (IOException e) {
                    Log.e(TAG, "Unable to start camera source.", e);
                    mCameraSource.release();
                    mCameraSource = null;
                }
            }
        }
    }

    private void stopCameraSource() {
        mPreview.stop();
    }


    private final CameraSourcePreview.OnTouchListener CameraPreviewTouchListener = new CameraSourcePreview.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent pEvent) {
            v.onTouchEvent(pEvent);
            if (pEvent.getAction() == MotionEvent.ACTION_DOWN) {
                int autoFocusX = (int) (pEvent.getX() - Utils.dpToPx(60)/2);
                int autoFocusY = (int) (pEvent.getY() - Utils.dpToPx(60)/2);
                ivAutoFocus.setTranslationX(autoFocusX);
                ivAutoFocus.setTranslationY(autoFocusY);
                ivAutoFocus.setVisibility(View.VISIBLE);
                ivAutoFocus.bringToFront();
                if(useCamera2) {
                    if(mCamera2Source != null) {
                        mCamera2Source.autoFocus(new Camera2Source.AutoFocusCallback() {
                            @Override
                            public void onAutoFocus(boolean success) {
                                Log.d(TAG,"AAA_onAutoFocus success : "+success);
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {
                                        ivAutoFocus.setVisibility(View.GONE);}
                                });
                            }
                        }, pEvent, v.getWidth(), v.getHeight());
                    } else {
                        ivAutoFocus.setVisibility(View.GONE);
                    }
                } else {
                    if(mCameraSource != null) {
                        mCameraSource.autoFocus(new CameraSource.AutoFocusCallback() {
                            @Override
                            public void onAutoFocus(boolean success) {
                                runOnUiThread(new Runnable() {
                                    @Override public void run() {ivAutoFocus.setVisibility(View.GONE);}
                                });
                            }
                        });
                    } else {
                        ivAutoFocus.setVisibility(View.GONE);
                    }
                }
            }
            return false;
        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissionThenOpenCamera();
            } else {
                Toast.makeText(MainActivity.this, "CAMERA PERMISSION REQUIRED", Toast.LENGTH_LONG).show();
                finish();
            }
        }
        if(requestCode == REQUEST_STORAGE_PERMISSION) {
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                requestPermissionThenOpenCamera();
            } else {
                Toast.makeText(MainActivity.this, "STORAGE PERMISSION REQUIRED", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(wasActivityResumed)
        	//If the CAMERA2 is paused then resumed, it won't start again unless creating the whole camera again.
        	if(useCamera2) {
                createCameraSourceBack();
        	} else {
        		startCameraSource();
        	}
    }

    @Override
    protected void onPause() {
        super.onPause();
        wasActivityResumed = true;
        stopCameraSource();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopCameraSource();
    }

    @Override
    public void onBarcodeDetected(Barcode barcode) {
        final  String  barcodeResult = barcode.rawValue;
        Log.d(TAG,"AAA_barcode : "+barcodeResult);

        if(!TextUtils.isEmpty(barcodeResult)){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    tv_barcodeResult.setText("辨識結果: "+barcodeResult);
                }
            });
        }
    }

    @Override
    public void onOcrDetected(TextBlock mText) {
        if(mText!=null){
            List<? extends Text> textComponents = mText.getComponents();
            for(Text currentText : textComponents) {
                Log.d(TAG,"AAA_onOcrDetected : "+mText.getValue());
            }
        }
    }
}
