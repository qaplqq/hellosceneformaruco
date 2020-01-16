/*
 * Copyright 2018 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.hellosceneform;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.media.Image;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.Toast;
import com.google.ar.core.Anchor;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.ImageFormat;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.aruco.Aruco;
import org.opencv.aruco.Dictionary;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * This is an example activity that uses the Sceneform UX package to make common AR tasks easier.
 */
public class HelloSceneformActivity extends AppCompatActivity {
  private static final String TAG = HelloSceneformActivity.class.getSimpleName();
  private static final double MIN_OPENGL_VERSION = 3.0;


    BaseLoaderCallback baseLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            super.onManagerConnected(status);
            switch (status) {
                case BaseLoaderCallback.SUCCESS: {
                    javaCameraView.enableView();
                    break;
                }
                default: {
                    super.onManagerConnected(status);
                    break;
                }
            }
        }
    };

    static {
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "opencv loaded");
        } else {
            Log.i(TAG, "opencv not loaded");
        }
    }




    private ArFragment arFragment;
    private ModelRenderable andyRenderable;
    private Node node=new Node();
    private CameraBridgeViewBase javaCameraView;
    Mat cameraMatrix =  new Mat(3,3,CvType.CV_32FC1);
    MatOfDouble distorsionMatrix= new MatOfDouble();
    private boolean enableAutoFocus;

    @Override
  @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
  // CompletableFuture requires api level 24
  // FutureReturnValueIgnored is not valid
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!checkIsSupportedDeviceOrFinish(this)) {
      return;
    }

    setContentView(R.layout.activity_ux);
    arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);


// disable plane detect
      arFragment.getPlaneDiscoveryController().hide();
      arFragment.getPlaneDiscoveryController().setInstructionView(null);
      arFragment.getArSceneView().getPlaneRenderer().setEnabled(false);

        cameraMatrix.put(0,0, 469.7718, 0, 259.0107,
                0, 469.5324, 251.2255,
                0, 0, 1.0000);

        double[] distArray =  { -0.1007,
                0.2118,
                0,
                0,
                -0.6476};
        distorsionMatrix.fromArray(distArray);


    // When you build a Renderable, Sceneform loads its resources in the background while returning
    // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
    ModelRenderable.builder()
        .setSource(this, R.raw.andy)
        .build()
        .thenAccept(renderable -> {
            andyRenderable = renderable;

            Scene sce = arFragment.getArSceneView().getScene();

            if(andyRenderable!= null){

                node.setParent(sce);

                node.setLocalPosition(new Vector3(0f, -2f, -7f));
                node.setLocalScale(new Vector3(3f, 3f, 3f));

                node.setRenderable(andyRenderable);

                sce.addChild(node);

            }
        })
        .exceptionally(
            throwable -> {
              Toast toast =
                  Toast.makeText(this, "Unable to load andy renderable", Toast.LENGTH_LONG);
              toast.setGravity(Gravity.CENTER, 0, 0);
              toast.show();
              return null;
            });

    arFragment.setOnTapArPlaneListener(
        (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
          if (andyRenderable == null) {
            return;
          }

          // Create the Anchor.
          Anchor anchor = hitResult.createAnchor();
          AnchorNode anchorNode = new AnchorNode(anchor);
          anchorNode.setParent(arFragment.getArSceneView().getScene());

          // Create the transformable andy and add it to the anchor.
          TransformableNode andy = new TransformableNode(arFragment.getTransformationSystem());
          andy.setParent(anchorNode);
          andy.setRenderable(andyRenderable);
          andy.select();
        });




        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {
            arFragment.onUpdate(frameTime);
            onUpdate();
        });




    }

    private void onUpdate() {

        Frame frame = arFragment.getArSceneView().getArFrame();
        if (frame == null) {
            return;
        }

        try (Image image = frame.acquireCameraImage()) {
            if (image.getFormat() != ImageFormat.YUV_420_888) {
                throw new IllegalArgumentException(
                        "Expected image in YUV_420_888 format, got format " + image.getFormat());
            }

            Mat mat1 = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC1);

            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            mat1.put(0, 0, bytes);

            Dictionary dictionary =  Aruco.getPredefinedDictionary(Aruco.DICT_4X4_50);
            Mat ids = new Mat();

            List<Mat> corners = new ArrayList<>();

            Aruco.detectMarkers(mat1, dictionary, corners, ids);

            if (corners.size()>0) {

                Mat rvecs = new Mat();
                Mat tvecs = new Mat();

                Aruco.estimatePoseSingleMarkers(corners, 100f, cameraMatrix, distorsionMatrix, rvecs, tvecs);

                double[] mvalr1 = rvecs.row(0).get(0, 0);
                double[] mvalt1 = tvecs.row(0).get(0, 0);

                String strl1 = String.format("%.1f",mvalt1[0]) + "," + String.format("%.1f",mvalt1[1]) ;

                float x1 = (float) mvalt1[0];
                float y1 = (float) mvalt1[1];
                float z1 = (float) mvalt1[2];

                Log.d(TAG, "Aruco: "+strl1 +":"+x1+","+y1+","+z1 );

                // How to convert  to x1, y1, z1 in  arcore's coordination?

                node.setLocalPosition(new Vector3(x1/10, y1/10, -7f));



//                node.setLocalPosition(new Vector3(x1/10, y1/10, -z1/100));

                // get corners's left most position
               // Mat a = corners.get(0);
              //  float[] vf1 = new float[2];
               // a.get(0,0,vf1);

            }





        } catch (NotYetAvailableException e) {
            e.printStackTrace();
        }









    }

    /**
   * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
   * on this device.
   *
   * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
   *
   * <p>Finishes the activity if Sceneform can not run
   */
  public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
    if (Build.VERSION.SDK_INT < VERSION_CODES.N) {
      Log.e(TAG, "Sceneform requires Android N or later");
      Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
      activity.finish();
      return false;
    }
    String openGlVersionString =
        ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
            .getDeviceConfigurationInfo()
            .getGlEsVersion();
    if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
      Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
      Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
          .show();
      activity.finish();
      return false;
    }
    return true;
  }
}
