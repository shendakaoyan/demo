/* Copyright 2017 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.example.android.tflitecamerademo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
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
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v13.app.FragmentCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.io.FileInputStream;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Basic fragments for the Camera. */
public class Camera2BasicFragment extends Fragment
        implements  FragmentCompat.OnRequestPermissionsResultCallback, SensorEventListener {

  /** Tag for the {@link Log}. */
  private static final String TAG = "TfLiteCameraDemo";

  private static final String FRAGMENT_DIALOG = "dialog";

  private static final String HANDLE_THREAD_NAME = "CameraBackground";

  private static final int PERMISSIONS_REQUEST_CODE = 1;
  private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

  static {
    ORIENTATIONS.append(Surface.ROTATION_0, 90);
    ORIENTATIONS.append(Surface.ROTATION_90, 0);
    ORIENTATIONS.append(Surface.ROTATION_180, 270);
    ORIENTATIONS.append(Surface.ROTATION_270, 180);
  }

  private final Object lock = new Object();
  private boolean runClassifier = false;
  private boolean phonestable = false;
  private boolean checkedPermissions = false;
  private ImageClassifier classifier;

//  private long time = 0;

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
  /** Max preview width that is guaranteed by Camera2 API */
  private static final int MAX_PREVIEW_WIDTH = 1920;

  /** Max preview height that is guaranteed by Camera2 API */
  private static final int MAX_PREVIEW_HEIGHT = 1080;

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
            public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
          };



  /** ID of the current {@link CameraDevice}. */
  private String cameraId = String.valueOf(0);

  /** An {@link AutoFitTextureView} for camera preview. */
  private AutoFitTextureView textureView;

  /** A {@link CameraCaptureSession } for camera preview. */
  private CameraCaptureSession captureSession;

  /** A reference to the opened {@link CameraDevice}. */
  private CameraDevice cameraDevice;

  /** The {@link Size} of camera preview. */
  private Size previewSize;

  private int max_Brightness;

  private  TextView textViewMax;
  //  private  AccelPlot ref;
  private SurfaceTexture texture;
  Surface surface;
  Range<Integer> fps[];
  Range<Integer>[] FPS;

  //  String message = new String();
  DecimalFormat df = new DecimalFormat("######0.000");
  SimpleDateFormat sdf=new SimpleDateFormat("HH:mm:ss SSS");
  /** {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state. */
  private final CameraDevice.StateCallback stateCallback =
          new CameraDevice.StateCallback() {

            @Override
            public void onOpened(@NonNull CameraDevice currentCameraDevice) {
              // This method is called when the camera is opened.  We start camera preview here.
              cameraOpenCloseLock.release();
              cameraDevice = currentCameraDevice;
              createCameraPreviewSession();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice currentCameraDevice) {
              cameraOpenCloseLock.release();
              currentCameraDevice.close();
              cameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice currentCameraDevice, int error) {
              cameraOpenCloseLock.release();
              currentCameraDevice.close();
              cameraDevice = null;
              Activity activity = getActivity();
              if (null != activity) {
                activity.finish();
              }
            }
          };

//  private ArrayList<String> deviceStrings = new ArrayList<String>();
//  private ArrayList<String> modelStrings = new ArrayList<String>();

  /** Current indices of device and model. */
//  int currentDevice = -1;
//
//  int currentModel = -1;
//
//  int currentNumThreads = -1;

  /** An additional thread for running tasks that shouldn't block the UI. */
  private HandlerThread backgroundThread;

  /** A {@link Handler} for running tasks in the background. */
  private Handler backgroundHandler;

  /** An {@link ImageReader} that handles image capture. */
  private ImageReader imageReader;

  /**
   * This is the output file for our picture.
   */
  private File pictureFile;


  private SensorManager mSensorManager;
  private double max = 0;
  private double max2 = 0;
  private double a = 0.001;
  private double b = 0.001;
  private  int stablecnt = 0;
  private int x_luminance;
  private  int min_brightness;
  private int lastFps = 30 ;
  private  int xFps = 0;
  //  private int id = 0;
  private int totalCnt = 0;
  private boolean isHold = true;
  private  boolean firstIn = true;
  private boolean isChanged = false;
  private  List<Integer> list = new ArrayList<>();
  private  int filename = 0;
  StringBuilder stringBuilder = new StringBuilder();
  StringBuilder stringBuilderBright = new StringBuilder();
  TextView t1;
  TextView t2;

  /**
   * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
   * still image is ready to be saved.
   */
  private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
          = new ImageReader.OnImageAvailableListener() {

    @Override
    public void onImageAvailable(ImageReader reader) {
      //对mfile进行处理，尾部加入拍摄时间
//             mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");

      String str = sdf.format(new Date());
      String message = "Take picture:  "+str+"\n";
//      saveFile(message,filename);
      stringBuilder.append(message);
      pictureFile = new File(getActivity().getExternalFilesDir(null), str + ".jpg");
      backgroundHandler.post(new ImageSaver(reader.acquireNextImage(), pictureFile));
    }

  };

  /** {@link CaptureRequest.Builder} for the camera preview */
  private CaptureRequest.Builder previewRequestBuilder;

  /** {@link CaptureRequest} generated by {@link #previewRequestBuilder} */
  private CaptureRequest previewRequest;

  /**
   * The current state of camera state for taking pictures.
   *
   * @see #captureCallback
   */
  private int mState = STATE_PREVIEW;
  /** A {@link Semaphore} to prevent the app from exiting before closing the camera. */
  private Semaphore cameraOpenCloseLock = new Semaphore(1);

  /**
   * Whether the current camera device supports Flash or not.
   */
  private boolean mFlashSupported;

  /**
   * Orientation of the camera sensor
   */
  private int mSensorOrientation;

  private long lastTime;
  private long currentTime;

  /** A {@link CameraCaptureSession.CaptureCallback} that handles events related to capture. */
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
              process(partialResult);
            }

            @Override
            public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                           @NonNull CaptureRequest request,
                                           @NonNull TotalCaptureResult result) {
              process(result);
//              SimpleDateFormat sdf=new SimpleDateFormat("HH:mm:ss SSS");
//              String str=sdf.format(new Date());
//              Log.e(TAG, "time:"+str);
            }
          };

  /**
   * Shows a {@link Toast} on the UI thread.
   *
   * @param text The message to show
   */
  private void showToast(final String text) {
    final Activity activity = getActivity();
    if (activity != null) {
      activity.runOnUiThread(new Runnable() {
        @Override
        public void run() {
          Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
        }
      });
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
      return Collections.min(bigEnough, new CompareSizesByArea());
    } else if (notBigEnough.size() > 0) {
      Log.e(TAG, " find a small suitable preview size");
      return Collections.max(notBigEnough, new CompareSizesByArea());
    } else {
      Log.e(TAG, "Couldn't find any suitable preview size");
      return choices[0];
    }
  }






  public static Camera2BasicFragment newInstance() {
    return new Camera2BasicFragment();
  }

  /** Layout the preview and buttons. */
  @Override
  public View onCreateView(
          LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    return inflater.inflate(R.layout.fragment_camera2_basic, container, false);
  }

  private void updateActiveModel() {
    // Get UI information before delegating to background


    backgroundHandler.post(
            () -> {

              // Disable classifier while updating
              if (classifier != null) {
                classifier.close();
                classifier = null;
              }

              // Try to load model.
              try {
                classifier = new ImageClassifierFloatMobileNet(getActivity());
              }  catch (IOException e) {
                Log.d(TAG, "Failed to load", e);
                classifier = null;
              }

              // Customize the interpreter to the type of device we want to use.
              if (classifier == null) {
                return;
              }
              classifier.setNumThreads(1);
            });
  }

  /** Connect the buttons to their event handler. */
  @Override
  public void onViewCreated(final View view, Bundle savedInstanceState) {


    list.add(1);
    list.add(2);
    list.add(3);
    java.util.Collections.shuffle(list);

    String str = sdf.format(new Date());
    String message1 = "openTime:  "+str+"   "+list;
//    saveFile(message,filename);
//    saveFile(list.toString(),filename);
    stringBuilder.append(message1);
    textViewMax = (TextView)view.findViewById(R.id.maxbrightness);
    textureView = (AutoFitTextureView) view.findViewById(R.id.texture);
    t1 = (TextView)view.findViewById(R.id.I);

    t2 = (TextView)view.findViewById(R.id.XFPS);

    Button b1 = (Button) view.findViewById(R.id.picture);
    b1.setOnClickListener(view12 -> takePicture()
    );
    Spinner spinnerFps = (Spinner)view.findViewById(R.id.spinnerFps);
    ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getActivity().getApplicationContext(),
            R.array.a_array, android.R.layout.simple_spinner_item);
// Specify the layout to use when the list of choices appears
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
// Apply the adapter to the spinner
    spinnerFps.setAdapter(adapter);


//    为spinner绑定监听器，这里我们使用匿名内部类的方式实现监听器
    spinnerFps.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      @Override
      public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

        String ps = adapter.getItem(position).toString();
        saveFile(stringBuilder.toString(),"fps",filename);
        saveFile(stringBuilderBright.toString(),"bright",filename);
        stringBuilder.delete( 0, stringBuilder.length() );
        stringBuilderBright.delete( 0, stringBuilder.length() );
        switch (ps) {
          case "scene1":
            switch(list.get(0)){
              case 1:
                a=0.08;
                b=0.01;
                filename = 1;
                isChanged = true;
                break;
              case 2:
                a=0.20;
                b=0.40;
                filename = 2;
                isChanged = true;
                break;
              case 3:
                a=0.001;
                b=0.001;
                filename = 3;
                isChanged = false;
                break;

            };
            totalCnt = 0;
            x_luminance = max_Brightness;
            setBrightness(getActivity(), x_luminance);
//            textViewX.setText("亮度"+x_luminance);
            setFps(30);
            lastFps = 30;
//            textViewFPS.setText("帧率："+xFps);
            isHold =true;
            break;
          case "scene2":
            switch(list.get(1)){
              case 1:
                a=0.08;
                b=0.01;
                filename = 1;
                isChanged = true;
                break;
              case 2:
                a=0.20;
                b=0.40;
                filename = 2;
                isChanged = true;
                break;
              case 3:
                a=0.001;
                b=0.001;
                filename = 3;
                isChanged = false;
                break;

            };
            totalCnt = 0;
            x_luminance = max_Brightness;
            setBrightness(getActivity(), x_luminance);
            setFps(30);
            lastFps = 30;
            isHold = true;
            break;
          case "scene3":
            switch(list.get(2)){
              case 1:
                a=0.08;
                b=0.01;
                filename = 1;
                isChanged = true;
                break;
              case 2:
                a=0.20;
                b=0.40;
                filename = 2;
                isChanged = true;
                break;
              case 3:
                a=0.001;
                b=0.001;
                filename = 3;
                isChanged = false;
                break;

            };
            totalCnt = 0;
            x_luminance = max_Brightness;
            setBrightness(getActivity(), x_luminance);
            setFps(30);
            lastFps = 30;
            isHold = true;
            break;
          default:
            isChanged = false;
            break;
        }


      }
      @Override
      public void onNothingSelected(AdapterView<?> parent) {
      }
    });

    max_Brightness = getScreenBrightness(getActivity().getApplicationContext());;
    textViewMax.setText("finaltest");
    x_luminance =  max_Brightness;
    min_brightness = (int)(max_Brightness * 0.7);
  }

  /** Load the model and labels. */
  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
//    mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");
    mSensorManager =(SensorManager) getActivity().getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
    Sensor mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
    lastTime = System.currentTimeMillis();
    startBackgroundThread();
    updateActiveModel();

  }

  @Override
  public void onResume() {
    super.onResume();
    startBackgroundThread();

    // When the screen is turned off and turned back on, the SurfaceTexture is already
    // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
    // a camera and start preview from here (otherwise, we wait until the surface is ready in
    // the SurfaceTextureListener).
    if (textureView.isAvailable()) {
      openCamera(textureView.getWidth(), textureView.getHeight());
    } else {
      textureView.setSurfaceTextureListener(surfaceTextureListener);
    }
  }

  @Override
  public void onPause() {
    closeCamera();
    stopBackgroundThread();
    mSensorManager.unregisterListener(this);
    super.onPause();
  }



  @Override
  public void onRequestPermissionsResult(
          int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  private String[] getRequiredPermissions() {
    Activity activity = getActivity();
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



  private boolean allPermissionsGranted() {
    for (String permission : getRequiredPermissions()) {
      if (getActivity().checkPermission(permission, Process.myPid(), Process.myUid())
              != PackageManager.PERMISSION_GRANTED) {
        return false;
      }
    }
    return true;
  }




  /**
   * Sets up member variables related to camera.
   *
   * @param width The width of available size for camera preview
   * @param height The height of available size for camera preview
   */
  private void setUpCameraOutputs(int width, int height) {
    Activity activity = getActivity();
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
                characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) {
          continue;
        }

        // // For still image captures, we use the largest available size.
        Size largest =
                Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
        imageReader =
                ImageReader.newInstance(
                        largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/ 1);
        imageReader.setOnImageAvailableListener(
                mOnImageAvailableListener, backgroundHandler);
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

        previewSize =
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
          textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
        } else {
          textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
        }

        // Check if the flash is supported.
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        mFlashSupported = available == null ? false : available;

        this.cameraId = cameraId;
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


  /** Opens the camera specified by {@link Camera2BasicFragment#cameraId}. */
  private void openCamera(int width, int height) {
    if (!checkedPermissions && !allPermissionsGranted()) {
      FragmentCompat.requestPermissions(this, getRequiredPermissions(), PERMISSIONS_REQUEST_CODE);
      return;
    } else {
      checkedPermissions = true;
    }
    setUpCameraOutputs(width, height);
    configureTransform(width, height);
    textureView.post(new Runnable() {
                       @Override
                       public void run() {
                         /*
                          * Carry out the camera opening in a background thread so it does not cause lag
                          * to any potential running animation.
                          */
                         backgroundHandler.post(new Runnable() {
                           @Override
                           public void run() {
                             Activity activity = getActivity();
                             CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
                             try {
                               if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                                 throw new RuntimeException("Time out waiting to lock camera opening.");
                               }
                               manager.openCamera(cameraId, stateCallback, backgroundHandler);
                             } catch (CameraAccessException e) {
                               e.printStackTrace();
                             } catch (InterruptedException e) {
                               throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
                             }
                           }
                         });
                       }
                     }
    );

  }

  @Override
  public void onDestroy() {
    if (classifier != null) {
      classifier.close();
    }
    super.onDestroy();
  }

  /** Closes the current {@link CameraDevice}. */
  private void closeCamera() {
    try {
      cameraOpenCloseLock.acquire();
      if (null != captureSession) {
        captureSession.close();
        captureSession = null;
      }
      if (null != cameraDevice) {
        cameraDevice.close();
        cameraDevice = null;
      }
      if (null != imageReader) {
        imageReader.close();
        imageReader = null;
      }
    } catch (InterruptedException e) {
      throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
    } finally {
      cameraOpenCloseLock.release();
    }
  }

  /** Starts a background thread and its {@link Handler}. */
  private void startBackgroundThread() {
    backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
    // Start the classification train & load an initial model.
//    synchronized (lock) {
//      runClassifier = true;
//    }
//    backgroundHandler.post(periodicClassify);
//    updateActiveModel();
  }

  /** Stops the background thread and its {@link Handler}. */
  private void stopBackgroundThread() {
    backgroundThread.quitSafely();
    try {
      backgroundThread.join();
      backgroundThread = null;
      backgroundHandler = null;
      synchronized (lock) {
        runClassifier = false;
      }
    } catch (InterruptedException e) {
      Log.e(TAG, "Interrupted when stopping background thread", e);
    }
  }

  /** Takes photos and classify them periodically. */
  private Runnable periodicClassify =
          new Runnable() {
            @Override
            public void run() {
              synchronized (lock) {
                if (runClassifier) {
//                  long startTime = SystemClock.uptimeMillis();
                  classifyFrame();
//                  long endTime = SystemClock.uptimeMillis();
                  runClassifier = false;
//                  Log.d(TAG, "Timecost to run classification: " + (endTime - startTime));
                }
              }
              //   backgroundHandler.post(periodicClassify);
            }
          };








  /** Creates a new {@link CameraCaptureSession} for camera preview. */
  private void createCameraPreviewSession() {
    try {
      texture = textureView.getSurfaceTexture();
      assert texture != null;

      // We configure the size of default buffer to be the size of camera preview we want.
      texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

      // This is the output Surface we need to start preview.
      surface = new Surface(texture);

      // We set up a CaptureRequest.Builder with the output Surface.
      previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
      previewRequestBuilder.addTarget(surface);

      Activity activity = getActivity();
      CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
      CameraCharacteristics characteristics
              = manager.getCameraCharacteristics(cameraId);

      fps = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
      Log.i("FPS", "SYNC_MAX_LATENCY_PER_FRAME_CONTROL: " + Arrays.toString(fps));
      FPS = new Range[16];
      for(int i=0;i<=15;i++){
        FPS[i] = Range.create(15+i,15+i);
      }

      // Here, we create a CameraCaptureSession for camera preview.
      cameraDevice.createCaptureSession(
              Arrays.asList(surface,imageReader.getSurface()),
              new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                  // The camera is already closed
                  if (null == cameraDevice) {
                    return;
                  }

                  // When the session is ready, we start displaying the preview.
                  captureSession = cameraCaptureSession;
                  try {
                    // Auto focus should be continuous for camera preview.
                    previewRequestBuilder.set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                    setAutoFlash(previewRequestBuilder);

                    // Finally, we start displaying the camera preview.
                    previewRequest = previewRequestBuilder.build();
                    captureSession.setRepeatingRequest(
                            previewRequest, captureCallback, backgroundHandler);
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
    } catch (CameraAccessException e) {
      Log.e(TAG, "Failed to preview Camera", e);
    }
  }

  /**
   * Configures the necessary {@link Matrix} transformation to `textureView`. This
   * method should be called after the camera preview size is determined in setUpCameraOutputs and
   * also the size of `textureView` is fixed.
   *
   * @param viewWidth The width of `textureView`
   * @param viewHeight The height of `textureView`
   */
  private void configureTransform(int viewWidth, int viewHeight) {
    Activity activity = getActivity();
    if (null == textureView || null == previewSize || null == activity) {
      return;
    }
    int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
    Matrix matrix = new Matrix();
    RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
    RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
    float centerX = viewRect.centerX();
    float centerY = viewRect.centerY();
    if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
      bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
      matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
      float scale =
              Math.max(
                      (float) viewHeight / previewSize.getHeight(),
                      (float) viewWidth / previewSize.getWidth());
      matrix.postScale(scale, scale, centerX, centerY);
      matrix.postRotate(90 * (rotation - 2), centerX, centerY);
    } else if (Surface.ROTATION_180 == rotation) {
      matrix.postRotate(180, centerX, centerY);
    }
    textureView.setTransform(matrix);
  }


  private void takePicture() {
    lockFocus();
  }

  /**
   * Lock the focus as the first step for a still image capture.
   */
  private void lockFocus() {
    try {
      // This is how to tell the camera to lock focus.
      previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
              CameraMetadata.CONTROL_AF_TRIGGER_START);
      // Tell #mCaptureCallback to wait for the lock.
      mState = STATE_WAITING_LOCK;
      captureSession.capture(previewRequestBuilder.build(), captureCallback,
              backgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }


  /**
   * Run the precapture sequence for capturing a still image. This method should be called when
   * we get a response in {@link #captureCallback} from {@link #lockFocus()}.
   */
  private void runPrecaptureSequence() {
    try {
      // This is how to tell the camera to trigger.
      previewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
              CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
      // Tell #mCaptureCallback to wait for the precapture sequence to be set.
      mState = STATE_WAITING_PRECAPTURE;
      captureSession.capture(previewRequestBuilder.build(), captureCallback,
              backgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * Capture a still picture. This method should be called when we get a response in
   * {@link #captureCallback} from both {@link #lockFocus()}.
   *      */

  private void captureStillPicture() {
    try {
      final Activity activity = getActivity();
      if (null == activity || null == cameraDevice) {
        return;
      }
      // This is the CaptureRequest.Builder that we use to take a picture.
      final CaptureRequest.Builder captureBuilder =
              cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
      captureBuilder.addTarget(imageReader.getSurface());

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
          showToast("Saved" );
//          Log.d(TAG, pictureFile.toString());
          unlockFocus();
        }
      };

      captureSession.stopRepeating();
      captureSession.abortCaptures();
      captureSession.capture(captureBuilder.build(), CaptureCallback, null);
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



  /**
   * Unlock the focus. This method should be called when still image capture sequence is
   * finished.
   */
  private void unlockFocus() {
    try {
      // Reset the auto-focus trigger
      previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
              CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
      setAutoFlash(previewRequestBuilder);
      captureSession.capture(previewRequestBuilder.build(), captureCallback,
              backgroundHandler);
      // After this, the camera will go back to the normal state of preview.
      mState = STATE_PREVIEW;
      captureSession.setRepeatingRequest(previewRequest, captureCallback,
              backgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }



  private void setAutoFlash(CaptureRequest.Builder requestBuilder) {
    if (mFlashSupported) {
      requestBuilder.set(CaptureRequest.CONTROL_AE_MODE,
              CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
    }
  }




  /** Classifies a frame from the preview stream. */
  private void classifyFrame() {
    if (classifier == null || getActivity() == null || cameraDevice == null) {
      // It's important to not call showToast every frame, or else the app will starve and
      // hang. updateActiveModel() already puts an error message up with showToast.
      // showToast("Uninitialized Classifier or invalid context.");
      return;
    }
    SpannableStringBuilder textToShow = new SpannableStringBuilder();
    Bitmap bitmap = textureView.getBitmap(classifier.getImageSizeX(), classifier.getImageSizeY());
    classifier.classifyFrame(bitmap, textToShow);
    bitmap.recycle();
    String classifyResult = textToShow.toString();

//    saveFile("classifyResult:  "+textToShow.toString(),filename);
    stringBuilder.append(classifyResult);
    if(classifyResult.contains("animal")||classifyResult.contains("manyperson")||classifyResult.contains("oneperson")){
      setFps(30);
      lastFps = 30;

    }else{
      setFps(20);
      lastFps = 20;
    }
    xFps = lastFps;
//    t2.setText("fps"+lastFps);
  }

  @Override
  public void onSensorChanged(SensorEvent event) {


    if (isChanged && event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
      float X = event.values[0];
      float Y = event.values[1];
      float Z = event.values[2];
      double i = Math.sqrt(X * X + Y * Y + Z * Z);
      if (i > max) {
        max = i;
      }
      if (i > max2) {
        max2 = i;
      }
      currentTime = System.currentTimeMillis();
      if (!firstIn || currentTime - lastTime > 500) {
//        long startTime1=System.currentTimeMillis();   //获取开始时间
        if (phonestable == false) {//手机处于运动状态时进行的处理。
          if (currentTime - lastTime > 50) {
            lastTime = currentTime;
            if (max > 0.5) {
              xFps = (int) Math.round(30 * (Math.pow(Math.E, -1 * max * a)));//保守和激进时可以改变a的值
              if (xFps != lastFps) {
//              setFps(xFps);
                if (xFps < 15) {
                  xFps = 15;
                }
                previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[xFps - 15]);
                try {
                  // Finally, we start displaying the camera preview.
                  previewRequest = previewRequestBuilder.build();
                  captureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);
                } catch (CameraAccessException e) {
                  Log.e(TAG, "Failed to set up config to capture Camera", e);
                }
                lastFps = xFps;
//                t2.setText("fps" + lastFps);
              }
              totalCnt += 1;
              stablecnt = 0;
              stringBuilder.append( totalCnt + "  fps  " + xFps +  "  I  " + df.format(max) +" "+stablecnt+'\n');
              max = 0;
            } else {
              stablecnt += 1;
              if (stablecnt == 10) {                //稳定超过0.5秒进行一次判断，并根据结果调整预览界面的帧率，且只有stablecnt为2的时候才执行一次类型识别
                synchronized (lock) {
                  runClassifier = true;
                }
                backgroundHandler.post(periodicClassify);
                phonestable = true;
              }
              totalCnt += 1;
              stringBuilder.append( totalCnt + "  fps  " + xFps +  "  I  " + df.format(max) +" "+stablecnt+'\n');
              max = 0;
            }
          }
        }
        else {
          if (i >0.5) {//当手机处于静止状态时，为保证用户体验，只要某个瞬间I值大于某个界限值立即认定为此时手机已经处于运动，并调整帧率大小
            lastTime = currentTime;
            xFps = (int) Math.round(30 * (Math.pow(Math.E, -1 * i * a)));//保守和激进时可以改变0.08的值
            if (xFps != lastFps) {
//              setFps(xFps);
              if (xFps < 15) {
                xFps = 15;
              }
              previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[xFps - 15]);
              try {
                // Finally, we start displaying the camera preview.
                previewRequest = previewRequestBuilder.build();
                captureSession.setRepeatingRequest(previewRequest, captureCallback, backgroundHandler);
              } catch (CameraAccessException e) {
                Log.e(TAG, "Failed to set up config to capture Camera", e);
              }
              lastFps = xFps;
//              t2.setText("fps"+lastFps);
            }
            stablecnt = 0;
            phonestable = false;
            totalCnt += 1;
            stringBuilder.append( totalCnt + "  fps  " + xFps +  "  I  " + df.format(max) +" "+stablecnt+'\n');
            max = 0;
          }
          if (currentTime - lastTime > 50) {
            lastTime = currentTime;
            totalCnt += 1;
            stablecnt += 1;
            stringBuilder.append( totalCnt + "  fps  " + xFps +  "  I  " + df.format(max) +" "+stablecnt+'\n');
            max = 0;
          }
        }


        if (isHold && totalCnt % 20 == 0) {
          int deltaL = (int) (b * max2 * x_luminance);
          int temp_luminance = x_luminance - deltaL;
          if (temp_luminance > min_brightness) {
            x_luminance = temp_luminance;
//              setBrightness(getActivity(), x_luminance);
//          WindowManager.LayoutParams lp = getActivity().getWindow().getAttributes();
//          lp.screenBrightness = (float) x_luminance * (1f / 255f);
//          getActivity().getWindow().setAttributes(lp);
          } else {
//              setBrightness(getActivity(), min_brightness);
//          WindowManager.LayoutParams lp = getActivity().getWindow().getAttributes();
//          lp.screenBrightness = (float) min_brightness * (1f / 255f);
//          getActivity().getWindow().setAttributes(lp);
            isHold = false;
            x_luminance = min_brightness;
          }

          stringBuilderBright.append( totalCnt + "  bright  " + x_luminance + "  I  " + df.format(max2)+'\n');
          max2 = 0;
        }


      }
      firstIn = false;

    }
  }


  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {

  }

  private static class ImageSaver implements Runnable {

    /**
     * The JPEG image
     */
    private final Image mImage;
    /**
     * The file we save the image into.
     */
    private final File mFile;

    ImageSaver(Image image, File file) {
      mImage = image;
      mFile = file;
    }

    @Override
    public void run() {
      ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
      byte[] bytes = new byte[buffer.remaining()];
      buffer.get(bytes);
      FileOutputStream output = null;
      try {
        output = new FileOutputStream(mFile);
        output.write(bytes);
      } catch (IOException e) {
        e.printStackTrace();
      } finally {
        mImage.close();
        if (null != output) {
          try {
            output.close();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }

  }

  /** Compares two {@code Size}s based on their areas. */
  private static class CompareSizesByArea implements Comparator<Size> {

    @Override
    public int compare(Size lhs, Size rhs) {
      // We cast here to ensure the multiplications won't overflow
      return Long.signum(
              (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
    }
  }

  /** Shows an error message dialog. */
  public static class ErrorDialog extends DialogFragment {

    private static final String ARG_MESSAGE = "message";

    public static ErrorDialog newInstance(String message) {
      ErrorDialog dialog = new ErrorDialog();
      Bundle args = new Bundle();
      args.putString(ARG_MESSAGE, message);
      dialog.setArguments(args);
      return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
      final Activity activity = getActivity();
      return new AlertDialog.Builder(activity)
              .setMessage(getArguments().getString(ARG_MESSAGE))
              .setPositiveButton(
                      android.R.string.ok,
                      new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                          activity.finish();
                        }
                      })
              .create();
    }
  }

  public void setFps(int ps){

    // Auto focus should be continuous for camera preview.
    //手机非静止，帧率相对较低

    if(ps<15){
      ps = 15;
    }else if(ps>30){
      ps = 30;
    }
    switch (ps) {
      case 15:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[0]);
        break;
      case 16:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[1]);
        break;
      case 17:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[2]);
        break;
      case 18:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[3]);
        break;
      case 19:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[4]);
        break;
      case 20:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[5]);
        break;
      case 21:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[6]);
        break;
      case 22:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[7]);
        break;
      case 23:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[8]);
        break;
      case 24:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[9]);
        break;
      case 25:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[10]);
        break;
      case 26:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[11]);
        break;
      case 27:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[12]);
        break;
      case 28:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[13]);
        break;
      case 29:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[14]);
        break;
      case 30:
        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, FPS[15]);
        break;
      default:
        //showToast(adapter.getItem(position).toString());
        break;
    }
    try {

      // Finally, we start displaying the camera preview.
      previewRequest = previewRequestBuilder.build();
      captureSession.setRepeatingRequest(
              previewRequest, captureCallback, backgroundHandler);
    } catch (CameraAccessException e) {
      Log.e(TAG, "Failed to set up config to capture Camera", e);
    }



  }
  public static void addTxtToFileBuffered(File file, String content) {
    //在文本文本中追加内容
    BufferedWriter out = null;
    try {
      //FileOutputStream(file, true),第二个参数为true是追加内容，false是覆盖
      out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true)));
      out.newLine();//换行
      out.write(content);
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      try {
        if(out != null){
          out.close();
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }


  public static void saveFile(String str,String name,int filename) {
    String filePath = null;
    boolean hasSDCard = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
    if (hasSDCard) { // SD卡根目录的hello.text
      filePath = Environment.getExternalStorageDirectory().toString() + File.separator + name +filename+".txt";
//      Log.i("加速度计存储位置", "" + filePath);
    } else  // 系统下载缓存根目录的hello.text
      filePath = Environment.getDownloadCacheDirectory().toString() + File.separator + name+filename+".txt";

    try {
      File file = new File(filePath);
      if (!file.exists()) {
        File dir = new File(file.getParent());
        dir.mkdirs();
        file.createNewFile();
      }
      addTxtToFileBuffered(file, str);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * 获取屏幕的亮度
   */
  public static int getScreenBrightness(Context context) {
    int nowBrightnessValue = 0;
    ContentResolver resolver = context.getContentResolver();
    try {
      nowBrightnessValue = android.provider.Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return nowBrightnessValue;
  }
  /**
   * 设置当前Activity显示时的亮度
   * 屏幕亮度最大数值一般为255，各款手机有所不同
   * screenBrightness 的取值范围在[0,1]之间
   */
  public static void setBrightness(Activity activity, int brightness) {
    WindowManager.LayoutParams lp = activity.getWindow().getAttributes();
    lp.screenBrightness = Float.valueOf(brightness) * (1f / 255f);
    activity.getWindow().setAttributes(lp);
  }




}
