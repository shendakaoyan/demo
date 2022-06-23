/* Copyright 2018 The TensorFlow Authors. All Rights Reserved.

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
import java.io.IOException;

/** This classifier works with the float MobileNet model. */
public class ImageClassifierFloatMobileNet extends ImageClassifier {

  /** The mobile net requires additional normalization of the used input. */
  private static final float IMAGE_MEAN = 127.5f;

  private static final float IMAGE_STD = 127.5f;

  /**
   * An array to hold inference results, to be feed into Tensorflow Lite as outputs. This isn't part
   * of the super class, because we need a primitive array here.
   */
  private float[][] labelProbArray = null;

  /**
   * Initializes an {@code ImageClassifierFloatMobileNet}.
   *
   * @param activity
   */
  ImageClassifierFloatMobileNet(Activity activity) throws IOException {
    super(activity);
    labelProbArray = new float[1][getNumLabels()];
  }

  @Override
  protected String getModelPath() {
    // you can download this file from
    // see build.gradle for where to obtain this file. It should be auto
    // downloaded into assets.
//    return "new_mobile_model.tflite";
//    return "imagenet_mobilenet_v2_100_224_classification_5.tflite";
//    return "imagenet_mobilenet_v2_075_224_classification_5.tflite";
//    return "imagenet_mobilenet_v2_050_224_classification_5.tflite";
//    return "imagenet_mobilenet_v2_035_224_classification_5.tflite";
//      return "imagenet_mobilenet_v2_100_192_classification_5.tflite";
//    return "imagenet_mobilenet_v2_075_192_classification_5.tflite";
//    return "imagenet_mobilenet_v2_050_192_classification_5.tflite";
//    return "imagenet_mobilenet_v2_035_192_classification_5.tflite";
//    return "imagenet_mobilenet_v2_100_160_classification_5.tflite";
    return "imagenet_mobilenet_v2_075_160_classification_5.tflite";
//    return "imagenet_mobilenet_v2_050_160_classification_5.tflite";
//    return "imagenet_mobilenet_v2_035_160_classification_5.tflite";
//      return "imagenet_mobilenet_v2_100_128_classification_5.tflite";
//    return "imagenet_mobilenet_v2_075_128_classification_5.tflite";
//    return "imagenet_mobilenet_v2_050_128_classification_5.tflite";
//    return "imagenet_mobilenet_v2_035_128_classification_5.tflite";
//      return "imagenet_label.tflite";
//    return "imagenet_mobilenet_v2_075_224_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_050_224_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_035_224_10_classification_5.tflite";
//      return "imagenet_mobilenet_v2_100_192_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_075_192_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_050_192_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_035_192_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_100_160_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_075_160_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_050_160_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_035_160_10_classification_5.tflite";
//      return "imagenet_mobilenet_v2_100_128_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_075_128_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_050_128_10_classification_5.tflite";
//    return "imagenet_mobilenet_v2_035_128_10_classification_5.tflite";
  }

  @Override
  protected String getLabelPath() {
    return "class_labels.txt";
  }

  @Override
  protected int getImageSizeX() {
//    return 224;
//    return 192;
    return 160;
//    return 128;
  }

  @Override
  protected int getImageSizeY() {
//    return 224;
//    return 192;
    return 160;
//    return 128;
  }

  @Override
  protected int getNumBytesPerChannel() {
    return 4; // Float.SIZE / Byte.SIZE;
  }

  @Override
  protected void addPixelValue(int pixelValue) {
    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
  }

  @Override
  protected float getProbability(int labelIndex) {
    return labelProbArray[0][labelIndex];
  }

  @Override
  protected void setProbability(int labelIndex, Number value) {
    labelProbArray[0][labelIndex] = value.floatValue();
  }

  @Override
  protected float getNormalizedProbability(int labelIndex) {
    return labelProbArray[0][labelIndex];
  }

  @Override
  protected void runInference() {
    tflite.run(imgData, labelProbArray);
  }
}
