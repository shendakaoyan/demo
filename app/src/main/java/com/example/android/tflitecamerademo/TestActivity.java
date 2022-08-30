package com.example.android.tflitecamerademo;

import static android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v13.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class TestActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "TestActivity";
    
    private AppCompatButton vBtn15Fps;
    private AppCompatButton vBtn30Fps;
    private AutoFitTextureView vTextureView;

    private ImageReader mImageReader;

    /**
     * This is the output file for our picture.
     */
    private File mPictureFile;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    private Handler mClassifyHandler;
    private HandlerThread mClassifyThread;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    /**
     * The {@link Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * ID of the current {@link CameraDevice}.
     */
    private String mCameraId = String.valueOf(0);

    /**
     * Whether the current camera device supports Flash or not.
     */
    private boolean mFlashSupported;
    
    
    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;
    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private static final int PERMISSIONS_REQUEST_CODE = 1;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #captureCallback
     */
    private int mState = STATE_PREVIEW;

    private long mLastTimeMills = -1L;

    private boolean mPhoneStable = false;

    private int mLastFpx = -1;

    private int mStableTime = -1;

    private double maxDelta = -1;

    private final Object mLockObject = new Object();
    private boolean mRunClassifier = false;


    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a {@link
     * TextureView}.
     */
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {

                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                    openCamera(width, height);
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
                    configureTransform(width, height);
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                    return true;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture texture) {
                }
            };

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback stateCallback =
            new CameraDevice.StateCallback() {

                @RequiresApi(api = Build.VERSION_CODES.M)
                @Override
                public void onOpened(@NonNull CameraDevice currentCameraDevice) {
                    // This method is called when the camera is opened.  We start camera preview here.
                    mCameraOpenCloseLock.release();
                    mCameraDevice = currentCameraDevice;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice currentCameraDevice) {
                    mCameraOpenCloseLock.release();
                    currentCameraDevice.close();
                    mCameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice currentCameraDevice, int error) {
                    mCameraOpenCloseLock.release();
                    currentCameraDevice.close();
                    mCameraDevice = null;
                    Activity activity = TestActivity.this;
                    if (null != activity) {
                        activity.finish();
                    }
                }
            };

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to capture.
     */
    private CameraCaptureSession.CaptureCallback captureCallback =
            new CameraCaptureSession.CaptureCallback() {

                private void process(CaptureResult result) {
                    switch (mState) {
                        case STATE_PREVIEW: {

                            // We have nothing to do when the camera preview is working normally.
                            break;
                        }
                        case STATE_WAITING_LOCK: {
                            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                            if (afState == null) {
                                captureStillPicture();
                            } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                                    CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                                // CONTROL_AE_STATE can be null on some devices
                                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                                if (aeState == null ||
                                        aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                                    mState = STATE_PICTURE_TAKEN;
                                    captureStillPicture();
                                } else {
                                    runPrecaptureSequence();
                                }
                            }
                            break;
                        }
                        case STATE_WAITING_PRECAPTURE: {
                            // CONTROL_AE_STATE can be null on some devices
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            if (aeState == null ||
                                    aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                                    aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                                mState = STATE_WAITING_NON_PRECAPTURE;
                            }
                            break;
                        }
                        case STATE_WAITING_NON_PRECAPTURE: {
                            // CONTROL_AE_STATE can be null on some devices
                            Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                            if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                                mState = STATE_PICTURE_TAKEN;
                                captureStillPicture();
                            }
                            break;
                        }
                    }
                }


                @Override
                public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                                @NonNull CaptureRequest request,
                                                @NonNull CaptureResult partialResult) {
//                    Log.d(TAG, "onCaptureProgressed called");
                    process(partialResult);
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
//                    Log.d(TAG, "onCaptureCompleted called");
                    process(result);
//              SimpleDateFormat sdf=new SimpleDateFormat("HH:mm:ss SSS");
//              String str=sdf.format(new Date());
//              Log.e(TAG, "time:"+str);
                }

                @Override
                public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
//                    Log.d(TAG, "onCaptureStarted: frameNumber = " + frameNumber + " time = " + timestamp);
                }

            };

    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {

            DecimalFormat df = new DecimalFormat("######0.000");
            @SuppressLint("SimpleDateFormat") 
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss SSS");
            String str = sdf.format(new Date());
            String message = "Take picture:  " + str + "\n";
            mPictureFile = new File(getExternalFilesDir(null), str + ".jpg");
            mBackgroundHandler.post(new Camera2BasicFragment.ImageSaver(reader.acquireNextImage(), mPictureFile));
        }

    };
    private boolean mCheckedPermissions;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private final Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * A reference to the opened {@link CameraDevice}.
     */
    private CameraDevice mCameraDevice;
    private SurfaceTexture mTexture;
    private Surface mSurface;

    /**
     * A {@link CameraCaptureSession } for camera preview.
     */
    private CameraCaptureSession mCaptureSession;

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    private CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    private CaptureRequest mPreviewRequest;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    private SensorManager mSensorManager;


    /**
     * Configures the necessary {@link Matrix} transformation to `textureView`. This
     * method should be called after the camera preview size is determined in setUpCameraOutputs and
     * also the size of `textureView` is fixed.
     *
     * @param viewWidth The width of `textureView`
     * @param viewHeight The height of `textureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = this;
        if (null == vTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale =
                    Math.max(
                            (float) viewHeight / mPreviewSize.getHeight(),
                            (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        vTextureView.setTransform(matrix);
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #captureCallback} from both {@link #lockFocus()}.
     */
    private void captureStillPicture() {
        try {
            final Activity activity = this;
            if (null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            setAutoFlash(captureBuilder);

            // Orientation
            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    showToast("Saved");
//          Log.d(TAG, pictureFile.toString());
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #captureCallback} from {@link #lockFocus()}.
     */
    private void runPrecaptureSequence() {
        Log.d(TAG, "->>>>>>>>>>>>>>>>>>> runPrecaptureSequence called");
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), captureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private void lockFocus() {
        Log.d(TAG, "->>>>>>>>>>>>>>>>>>> lockFocus called");
        try {
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_START);
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), captureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private void unlockFocus() {
        Log.d(TAG, "->>>>>>>>>>>>>>>>>>> unlockFocus called");
        try {
            // Reset the auto-focus trigger
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), captureCallback,
                    mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);

        mSensorManager = (SensorManager) getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            Sensor mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        }

        vBtn15Fps = findViewById(R.id.vBtn15dps);
        vBtn30Fps = findViewById(R.id.vBtn30dps);

        vBtn15Fps.setOnClickListener((v) -> {
            changeFpsTo(15);
        });

        vBtn30Fps.setOnClickListener((v) -> {
            changeFpsTo(30);
        });

        initTextureViews();

    }

    private void initClassifier() {
        // Get UI information before delegating to background
        mClassifyHandler.post(() -> {
            // Disable classifier while updating
            if (mClassifier != null) {
                mClassifier.close();
                mClassifier = null;
            }

            // Try to load model.
            try {
                mClassifier = new ImageClassifierFloatMobileNet(this);
            } catch (IOException e) {
                Log.d(TAG, "Failed to load", e);
                mClassifier = null;
            }

            // Customize the interpreter to the type of device we want to use.
            if (mClassifier == null) {
                return;
            }
            mClassifier.setNumThreads(1);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();

        initClassifier();

        if (vTextureView.isAvailable()) {
            openCamera(vTextureView.getWidth(), vTextureView.getHeight());
        } else {
            vTextureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopBackgroundThread();
    }

    private void initTextureViews() {
        vTextureView = findViewById(R.id.vTextureView);
    }

    private void changeFpsTo(int fpx) {
        if (mCaptureSession == null || mLastFpx == fpx) return;
        //            mCaptureSession.abortCaptures();
        // Reset the auto-focus trigger
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
        mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(fpx, fpx));

        mPreviewRequest = mPreviewRequestBuilder.build();
//            mCaptureSession.capture(mPreviewRequest, captureCallback,
//                    mBackgroundHandler);
        mBackgroundHandler.postDelayed(() -> {
            try {
                mCaptureSession.setRepeatingRequest(mPreviewRequest, captureCallback,
                        mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }, 10);
        mLastFpx = fpx;
    }


    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    private void createCameraPreviewSession() {
        try {
            mTexture = vTextureView.getSurfaceTexture();
            assert mTexture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            mTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            mSurface = new Surface(mTexture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mSurface);

            Activity activity = TestActivity.this;
            CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics
                    = manager.getCameraCharacteristics(mCameraId);

            // Here, we create a CameraCaptureSession for camera preview.
            mTexture.setDefaultBufferSize(vTextureView.getWidth(), vTextureView.getHeight());
            StreamConfigurationMap configurationMap = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP);
            if (configurationMap != null) {
                final int[] inputFormats = configurationMap.getInputFormats();
                if (inputFormats == null || inputFormats.length == 0) return;
//                mCameraDevice.createCaptureSession(
//                        Arrays.asList(mSurface, mImageReader.getSurface()),
//                        new CameraCaptureSession.StateCallback() {
//
//                            @Override
//                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
//                                // The camera is already closed
//                                if (null == mCameraDevice) {
//                                    return;
//                                }
//
//                                // When the session is ready, we start displaying the preview.
//                                mCaptureSession = cameraCaptureSession;
//                                try {
//                                    // Auto focus should be continuous for camera preview.
//                                    mPreviewRequestBuilder.set(
//                                            CaptureRequest.CONTROL_AF_MODE,
//                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//
//                                    setAutoFlash(mPreviewRequestBuilder);
//
//                                    // Finally, we start displaying the camera preview.
//                                    mPreviewRequest = mPreviewRequestBuilder.build();
//                                    mCaptureSession.setRepeatingRequest(
//                                            mPreviewRequest, captureCallback, mBackgroundHandler);
//                                } catch (CameraAccessException e) {
//                                    Log.e(TAG, "Failed to set up config to capture Camera", e);
//                                }
//                            }
//
//                            @Override
//                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
//                                showToast("Failed");
//                            }
//                        },
//                        null);

                int format = inputFormats[0];
                Size[] sizes = configurationMap.getInputSizes(format);
                if (sizes == null || sizes.length == 0) return;
                mCameraDevice.createReprocessableCaptureSession(
                        new InputConfiguration(sizes[0].getWidth(), sizes[0].getHeight(), format),
                        Arrays.asList(mSurface, mImageReader.getSurface()),
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                                // The camera is already closed
                                if (null == mCameraDevice) {
                                    return;
                                }

                                if (cameraCaptureSession.isReprocessable()) {
                                    Log.d(TAG, "reprocessable = " + true);
                                }

                                // When the session is ready, we start displaying the preview.
                                mCaptureSession = cameraCaptureSession;
                                try {
                                    // Auto focus should be continuous for camera preview.
                                    mPreviewRequestBuilder.set(
                                            CaptureRequest.CONTROL_AF_MODE,
                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                    setAutoFlash(mPreviewRequestBuilder);

                                    // Finally, we start displaying the camera preview.
                                    mPreviewRequest = mPreviewRequestBuilder.build();
                                    mCaptureSession.setRepeatingRequest(
                                            mPreviewRequest, captureCallback, mBackgroundHandler);
                                } catch (CameraAccessException e) {
                                    Log.e(TAG, "Failed to set up config to capture Camera", e);
                                }
                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                                showToast("Failed");
                            }
                        },
                        null);
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to preview Camera", e);
        }
    }

    private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
        if (mFlashSupported) {
            requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        }
    }

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    private void showToast(final String text) {
        final Activity activity = TestActivity.this;
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
            }
        });
    }

        /**
     * Opens the camera specified by {@link TestActivity#mCameraId}.
     */
    private void openCamera(int width, int height) {
        if (!mCheckedPermissions && !allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
            return;
        } else {
            mCheckedPermissions = true;
        }
        setUpCameraOutputs(width, height);
        configureTransform(width, height);
        vTextureView.post(() -> {
            /*
             * Carry out the camera opening in a background thread so it does not cause lag
             * to any potential running animation.
             */
            mBackgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    Activity activity = TestActivity.this;
                    CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
                    try {
                        if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                            throw new RuntimeException("Time out waiting to lock camera opening.");
                        }
                        manager.openCamera(mCameraId, stateCallback, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
                    }
                }
            });
        });

    }

    private boolean allPermissionsGranted() {
        for (String permission : getRequiredPermissions()) {
            if (this.checkPermission(permission, Process.myPid(), Process.myUid())
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] getRequiredPermissions() {
        Activity activity = this;
        try {
            PackageInfo info =
                    activity
                            .getPackageManager()
                            .getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] ps = info.requestedPermissions;
            if (ps != null && ps.length > 0) {
                return ps;
            } else {
                return new String[0];
            }
        } catch (Exception e) {
            return new String[0];
        }
    }

    private void setUpCameraOutputs(int width, int height) {
        Activity activity = this;
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map =
                        characteristics.get(SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size largest =
                        Collections.max(
                                Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new Camera2BasicFragment.CompareSizesByArea());
                mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                        ImageFormat.JPEG, /*maxImages*/ 1);
                mImageReader.setOnImageAvailableListener(
                        mOnImageAvailableListener, mBackgroundHandler);
                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
                // noinspection ConstantConditions
                /* Orientation of the camera sensor */
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                mPreviewSize =
                        chooseOptimalSize(
                                map.getOutputSizes(SurfaceTexture.class),
                                rotatedPreviewWidth,
                                rotatedPreviewHeight,
                                maxPreviewWidth,
                                maxPreviewHeight,
                                largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
//        Log.i(TAG, Arrays.toString(map.getOutputSizes(SurfaceTexture.class)));
//         previewSize =  map.getOutputSizes(SurfaceTexture.class)[15];
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    vTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    vTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                mFlashSupported = available != null && available;

                this.mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to access Camera", e);
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
//      ErrorDialog.newInstance(getString(R.string.camera_error))
//              .show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("frame_background", -20);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        mClassifyThread = new HandlerThread("classify", -20);
        mClassifyThread.start();
        mClassifyHandler = new Handler(mClassifyThread.getLooper());
    }


    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        mClassifyThread.quitSafely();
        try {
            mBackgroundThread.join();
            mClassifyThread.join();
            mBackgroundHandler = null;
            mClassifyHandler = null;
            mBackgroundThread = null;
            mClassifyThread = null;
            synchronized (mLockObject) {
                mRunClassifier = false;
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted when stopping background thread", e);
        }
    }
    
    /**
     * Resizes image.
     *
     * Attempting to use too large a preview size could  exceed the camera bus' bandwidth limitation,
     * resulting in gorgeous previews but the storage of garbage capture data.
     *
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that is
     * at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size, and
     * whose aspect ratio matches with the specified value.
     *
     * @param choices The list of sizes that the camera supports for the intended output class
     * @param textureViewWidth The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth The maximum width that can be chosen
     * @param maxHeight The maximum height that can be chosen
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(
            Size[] choices,
            int textureViewWidth,
            int textureViewHeight,
            int maxWidth,
            int maxHeight,
            Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth
                    && option.getHeight() <= maxHeight
                    && option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth && option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            Log.e(TAG, " find a big suitable preview size");
            return Collections.min(bigEnough, new Camera2BasicFragment.CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            Log.e(TAG, " find a small suitable preview size");
            return Collections.max(notBigEnough, new Camera2BasicFragment.CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    @SuppressWarnings("DuplicateExpressions")
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (mLastTimeMills < 0) {
            mLastTimeMills = System.currentTimeMillis();
            return;
        }


        float a = 0.08f;
        int fpx = 15;
        long currentTimeMills = System.currentTimeMillis();
        if (currentTimeMills - mLastTimeMills < 500) {
            return;
        }

        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
        float X = event.values[0];
        float Y = event.values[1];
        float Z = event.values[2];
        Z = Z * 0.3f;
        double delta = Math.sqrt(X * X + Y * Y + Z * Z);
        Log.d(TAG, "delta = " + delta);

        maxDelta = Math.max(delta, maxDelta);

        if (mPhoneStable) {
            if (delta > 0.25) {
                // 从稳定到不稳定，立刻更新帧率
                fpx = (int) Math.round(30 * (Math.pow(Math.E, -1 * delta * a)));
                mPhoneStable = false;
                mStableTime = 0;
                changeFpsTo(fpx);
            }
        } else {
            // 不稳定状态下，每50ms更新一次帧率
            if (currentTimeMills - 50 >= mLastTimeMills) {
                if (delta > 0.25) {
                    // 依旧不稳定
                    fpx = (int) Math.round(30 * (Math.pow(Math.E, -1 * delta * a)));
                    Log.d(TAG, "fpx = " + fpx + " last = " + mLastFpx);
                    mPhoneStable = false;
                    mStableTime = 0;
                    changeFpsTo(fpx);
                } else {
                    // 出现稳定迹象，稳定超过0.5s就进行一次图像识别
                    mStableTime += 1;
                    checkIfNeedClassify();
                }
            }
        }
    }

    mLastTimeMills = currentTimeMills;
    }

    /**
     * Takes photos and classify them periodically.
     */
    private final Runnable periodicClassify = () -> {
        synchronized (mLockObject) {
            if (mRunClassifier) {
                classifyFrame();
                mRunClassifier = false;
            }
        }
    };

    private void checkIfNeedClassify() {
        if (mStableTime == 10) {
            mStableTime = 0;
            mPhoneStable = true;
            synchronized (mLockObject) {
                mRunClassifier = true;
            }
            mClassifyHandler.post(periodicClassify);
        }
    }

    private ImageClassifier mClassifier;

    /**
     * Classifies a frame from the preview stream.
     */
    private void classifyFrame() {
        long startTime = System.currentTimeMillis();
        if (mClassifier == null || mCameraDevice == null) {
            // It's important to not call showToast every frame, or else the app will starve and
            // hang. updateActiveModel() already puts an error message up with showToast.
            // showToast("Uninitialized Classifier or invalid context.");
            return;
        }
        SpannableStringBuilder textToShow = new SpannableStringBuilder();
        Bitmap bitmap = vTextureView.getBitmap(mClassifier.getImageSizeX(), mClassifier.getImageSizeY());
        mClassifier.classifyFrame(bitmap, textToShow);
        long endTime = System.currentTimeMillis();
        Log.d(TAG, "time for classifyFrame = " + (endTime - startTime));
        bitmap.recycle();
        String classifyResult = textToShow.toString();

        if (classifyResult.contains("animal") || classifyResult.contains("manyperson") || classifyResult.contains(
                "oneperson")) {
            changeFpsTo(30);
        } else {
            // 在手机 stable 状态下再调
            boolean phoneState = mPhoneStable;
            if (phoneState) {
                changeFpsTo(10);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "onAccuracyChanged accuracy = " + accuracy);
    }
}