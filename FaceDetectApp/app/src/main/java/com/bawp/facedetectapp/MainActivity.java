package com.bawp.facedetectapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceContour;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;
import com.otaliastudios.cameraview.CameraView;
import com.otaliastudios.cameraview.Facing;
import com.otaliastudios.cameraview.Frame;
import com.otaliastudios.cameraview.FrameProcessor;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MainActivity extends AppCompatActivity implements FrameProcessor {

    private Facing cameraFacing = Facing.FRONT;
    private ImageView imageView;
    private CameraView faceDetectionCameraView;
    private RecyclerView bottomSheetRecyclerView;
    private BottomSheetBehavior bottomSheetBehavior;
    private ArrayList<FaceDetectionModel> faceDetectionModels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        faceDetectionModels = new ArrayList<>();
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));

        imageView = findViewById(R.id.face_detection_image_view);
        faceDetectionCameraView = findViewById(R.id.face_detection_camera_view);
        Button toggleButton = findViewById(R.id.face_detection_camera_toggle_button);
        FrameLayout bottomSheetButton = findViewById(R.id.bottom_sheet_button);
        bottomSheetRecyclerView = findViewById(R.id.bottom_sheet_recycler_view);


        faceDetectionCameraView.setFacing(cameraFacing);
        faceDetectionCameraView.setLifecycleOwner(MainActivity.this);
        faceDetectionCameraView.addFrameProcessor(this);


        toggleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraFacing = (cameraFacing == Facing.FRONT) ? Facing.BACK : Facing.FRONT;
               // cameraFacing = (cameraFacing == Facing.FRONT) ? Facing.BACK : Facing.FRONT;
                faceDetectionCameraView.setFacing(cameraFacing);

            }
        });

        bottomSheetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CropImage.activity().start(MainActivity.this);
            }
        });

        bottomSheetRecyclerView.setLayoutManager(new LinearLayoutManager(MainActivity.this));
         bottomSheetRecyclerView.setAdapter(new FaceDetectionAdapter(faceDetectionModels, MainActivity.this));

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);

            if (resultCode == RESULT_OK) {
                assert result != null;
                Uri imageUri = result.getUri();
                try {
                    analyzeImage(MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void analyzeImage(final Bitmap bitmap) {
        if (bitmap == null) {
            Toast.makeText(MainActivity.this, "There was an error", Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        imageView.setImageBitmap(null);
        faceDetectionModels.clear();
        Objects.requireNonNull(bottomSheetRecyclerView.getAdapter()).notifyDataSetChanged();
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        //show Progress
        showProgress();
        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap);
        FirebaseVisionFaceDetectorOptions options =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
                        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
                        .build();

        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance()
                .getVisionFaceDetector(options);
        faceDetector.detectInImage(firebaseVisionImage)
                .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
                    @Override
                    public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                        Bitmap mutableImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);

                        detectFaces(firebaseVisionFaces, mutableImage);

//                        imageView.setImageBitmap(mutableImage);
//
                      hideProgress();
                        Objects.requireNonNull(bottomSheetRecyclerView.getAdapter()).notifyDataSetChanged();
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                       Toast.makeText(MainActivity.this, "There was some error",
                               Toast.LENGTH_SHORT).show();
                       hideProgress();
                    }
                });
    }

    private void detectFaces(List<FirebaseVisionFace> firebaseVisionFaces, Bitmap bitmap) {
        if (firebaseVisionFaces == null || bitmap == null) {
            Toast.makeText(this, "There was an error", Toast.LENGTH_SHORT).show();
            return;
        }
        Canvas canvas = new Canvas(bitmap);
        Paint facePaint = new Paint();
        facePaint.setColor(Color.GREEN);
        facePaint.setStyle(Paint.Style.STROKE);
        facePaint.setStrokeWidth(5f);

        Paint faceTextPaint = new Paint();
        faceTextPaint.setColor(Color.BLUE);
        faceTextPaint.setTextSize(30f);
        faceTextPaint.setTypeface(Typeface.SANS_SERIF);

        Paint landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.RED);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(8f);


        for (int i = 0; i < firebaseVisionFaces.size(); i++) {
            canvas.drawRect(firebaseVisionFaces.get(i).getBoundingBox(), facePaint);
            canvas.drawText("Face " + i,
                             (firebaseVisionFaces.get(i).getBoundingBox().centerX()
                            - (firebaseVisionFaces.get(i).getBoundingBox().width() >> 1) + 8f), // added >> to avoid errors when dividing with "/"
                           (firebaseVisionFaces.get(i).getBoundingBox().centerY()
                            + firebaseVisionFaces.get(i).getBoundingBox().height() >> 1) - 8F,
                            facePaint);

            FirebaseVisionFace face = firebaseVisionFaces.get(i);
            if (face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE) != null) {
                FirebaseVisionFaceLandmark leftEye = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE);
                assert  leftEye != null;
                canvas.drawCircle(
                        leftEye.getPosition().getX(),
                        leftEye.getPosition().getY(),
                        8f,
                        landmarkPaint
                );
             }
            if (face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE) != null) {
                FirebaseVisionFaceLandmark rightEye = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE);
                assert  rightEye != null;
                canvas.drawCircle(
                        rightEye.getPosition().getX(),
                        rightEye.getPosition().getY(),
                        8f,
                        landmarkPaint
                );
            }

            if (face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE) != null) {
                FirebaseVisionFaceLandmark nose = face.getLandmark(FirebaseVisionFaceLandmark.NOSE_BASE);
                assert  nose != null;
                canvas.drawCircle(
                        nose.getPosition().getX(),
                        nose.getPosition().getY(),
                        8f,
                        landmarkPaint
                );
            }
            if (face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR) != null) {
                FirebaseVisionFaceLandmark leftEar = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR);
                canvas.drawCircle(Objects.requireNonNull(leftEar).getPosition().getX(),
                        leftEar.getPosition().getY(),
                        8f,
                        landmarkPaint
                );
            }

            if (face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EAR) != null) {
                FirebaseVisionFaceLandmark rightEar = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EAR);
                canvas.drawCircle(Objects.requireNonNull(rightEar).getPosition().getX(),
                        rightEar.getPosition().getY(),
                        8f,
                        landmarkPaint
                );
            }
            if (face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT) != null
                    && face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM) != null
                    && face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT) != null) {
                FirebaseVisionFaceLandmark leftMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_LEFT);
                FirebaseVisionFaceLandmark bottomMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_BOTTOM);
                FirebaseVisionFaceLandmark rightMouth = face.getLandmark(FirebaseVisionFaceLandmark.MOUTH_RIGHT);
                canvas.drawLine(leftMouth.getPosition().getX(),
                        leftMouth.getPosition().getY(),
                        bottomMouth.getPosition().getX(),
                        bottomMouth.getPosition().getY(),
                        landmarkPaint);
                canvas.drawLine(bottomMouth.getPosition().getX(),
                        bottomMouth.getPosition().getY(),
                        rightMouth.getPosition().getX(),
                        rightMouth.getPosition().getY(), landmarkPaint);
            }

            faceDetectionModels.add(new FaceDetectionModel(i, "Smiling Probability " + face.getSmilingProbability()));
            faceDetectionModels.add(new FaceDetectionModel(i, "Left Eye Open Probability " + face.getLeftEyeOpenProbability()));
            faceDetectionModels.add(new FaceDetectionModel(i, "Right Eye Open Probability " + face.getRightEyeOpenProbability()));

        }
        //end of loop
    }

    private void showProgress() {
        findViewById(R.id.bottom_sheet_button_image).setVisibility(View.GONE);
        findViewById(R.id.bottom_sheet_button_progress).setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        findViewById(R.id.bottom_sheet_button_image).setVisibility(View.VISIBLE);
        findViewById(R.id.bottom_sheet_button_progress).setVisibility(View.GONE);
    }

    @Override
    public void process(@NonNull Frame frame) {
//        final int width = frame.getSize().getWidth();
//        final int height = frame.getSize().getHeight();
//
//
//        FirebaseVisionImageMetadata metadata = new FirebaseVisionImageMetadata
//                .Builder()
//                .setWidth(width)
//                .setHeight(height)
//                .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
//                .setRotation((cameraFacing == Facing.FRONT)
//                        ? FirebaseVisionImageMetadata.ROTATION_270 :
//                        FirebaseVisionImageMetadata.ROTATION_90)
//                .build();
//
//        FirebaseVisionImage firebaseVisionImage = FirebaseVisionImage
//                .fromByteArray(frame.getData(), metadata);
//        FirebaseVisionFaceDetectorOptions options = new FirebaseVisionFaceDetectorOptions.Builder()
//                .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
//                .build();
//
//        FirebaseVisionFaceDetector faceDetector = FirebaseVision.getInstance().getVisionFaceDetector(options);
//        faceDetector.detectInImage(firebaseVisionImage)
//                .addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
//                    @Override
//                    public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
//                        //imageView.setImageBitmap(null);
//
//                        Bitmap bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
//                        Canvas canvas = new Canvas(bitmap);
//                        Paint dotPaint = new Paint();
//                        dotPaint.setColor(Color.RED);
//                        dotPaint.setStyle(Paint.Style.FILL);
//                        dotPaint.setStrokeWidth(3f);
//
//                        Paint linePaint = new Paint();
//                        linePaint.setColor(Color.GREEN);
//                        linePaint.setStyle(Paint.Style.STROKE);
//                        linePaint.setStrokeWidth(2f);
//
//                        for (FirebaseVisionFace face : firebaseVisionFaces) {
//                            List<FirebaseVisionPoint> faceContours = face.getContour(
//                                    FirebaseVisionFaceContour.FACE
//                            ).getPoints();
//                            for (int i = 0; i < faceContours.size(); i++) {
//                                FirebaseVisionPoint faceContour = null;
//                                if (i != (faceContours.size() - 1)) {
//                                    faceContour = faceContours.get(i);
//                                    canvas.drawLine(faceContour.getX(),
//                                            faceContour.getY(),
//                                            faceContours.get(i + 1).getX(),
//                                            faceContours.get(i + 1).getY(),
//                                            linePaint
//
//                                    );
//
//                                } else {
//                                    canvas.drawLine(faceContour.getX(),
//                                            faceContour.getY(),
//                                            faceContours.get(0).getX(),
//                                            faceContours.get(0).getY(),
//                                            linePaint);
//                                }
//                                canvas.drawCircle(faceContour.getX(),
//                                        faceContour.getY(),
//                                        4f,
//                                        dotPaint        );
//                            }
//
//                            List<FirebaseVisionPoint> leftEyebrowTopCountours = face.getContour(
//                                    FirebaseVisionFaceContour.LEFT_EYEBROW_TOP).getPoints();
//                              for (int i = 0; i < leftEyebrowTopCountours.size(); i++) {
//                                  FirebaseVisionPoint contour = leftEyebrowTopCountours.get(i);
//                                  if (i != (leftEyebrowTopCountours.size() - 1))
//                                      canvas.drawLine(contour.getX(), contour.getY(), leftEyebrowTopCountours.get(i + 1).getX(),leftEyebrowTopCountours.get(i + 1).getY(), linePaint);
//                                  canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
//
//                              }
//
//                            List<FirebaseVisionPoint> rightEyebrowTopCountours = face.getContour(
//                                    FirebaseVisionFaceContour. RIGHT_EYEBROW_TOP).getPoints();
//                            for (int i = 0; i < rightEyebrowTopCountours.size(); i++) {
//                                FirebaseVisionPoint contour = rightEyebrowTopCountours.get(i);
//                                if (i != (rightEyebrowTopCountours.size() - 1))
//                                    canvas.drawLine(contour.getX(), contour.getY(), rightEyebrowTopCountours.get(i + 1).getX(),rightEyebrowTopCountours.get(i + 1).getY(), linePaint);
//                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
//
//                            }
//
//                            List<FirebaseVisionPoint> rightEyebrowBottomCountours = face.getContour(
//                                    FirebaseVisionFaceContour. RIGHT_EYEBROW_BOTTOM).getPoints();
//                            for (int i = 0; i < rightEyebrowBottomCountours.size(); i++) {
//                                FirebaseVisionPoint contour = rightEyebrowBottomCountours.get(i);
//                                if (i != (rightEyebrowBottomCountours.size() - 1))
//                                    canvas.drawLine(contour.getX(), contour.getY(), rightEyebrowBottomCountours.get(i + 1).getX(),rightEyebrowBottomCountours.get(i + 1).getY(), linePaint);
//                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
//
//                            }
//
//                            List<FirebaseVisionPoint> leftEyeContours = face.getContour(
//                                    FirebaseVisionFaceContour.LEFT_EYE).getPoints();
//                            for (int i = 0; i < leftEyeContours.size(); i++) {
//                                FirebaseVisionPoint contour = leftEyeContours.get(i);
//                                if (i != (leftEyeContours.size() - 1)){
//                                    canvas.drawLine(contour.getX(), contour.getY(), leftEyeContours.get(i + 1).getX(),leftEyeContours.get(i + 1).getY(), linePaint);
//
//                               }else {
//                                    canvas.drawLine(contour.getX(), contour.getY(), leftEyeContours.get(0).getX(),
//                                            leftEyeContours.get(0).getY(), linePaint);
//                                }
//                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
//
//
//                            }
//
//                            List<FirebaseVisionPoint> rightEyeContours = face.getContour(
//                                    FirebaseVisionFaceContour.RIGHT_EYE).getPoints();
//                            for (int i = 0; i < rightEyeContours.size(); i++) {
//                                FirebaseVisionPoint contour = rightEyeContours.get(i);
//                                if (i != (rightEyeContours.size() - 1)){
//                                    canvas.drawLine(contour.getX(), contour.getY(), rightEyeContours.get(i + 1).getX(),rightEyeContours.get(i + 1).getY(), linePaint);
//
//                                }else {
//                                    canvas.drawLine(contour.getX(), contour.getY(), rightEyeContours.get(0).getX(),
//                                            rightEyeContours.get(0).getY(), linePaint);
//                                }
//                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
//
//
//                            }
//
//                            List<FirebaseVisionPoint> upperLipTopContour = face.getContour(
//                                    FirebaseVisionFaceContour.UPPER_LIP_TOP).getPoints();
//                            for (int i = 0; i < upperLipTopContour.size(); i++) {
//                                FirebaseVisionPoint contour = upperLipTopContour.get(i);
//                                if (i != (upperLipTopContour.size() - 1)){
//                                    canvas.drawLine(contour.getX(), contour.getY(),
//                                            upperLipTopContour.get(i + 1).getX(),
//                                            upperLipTopContour.get(i + 1).getY(), linePaint);
//                                }
//                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
//
//                            }
//
//                            List<FirebaseVisionPoint> upperLipBottomContour = face.getContour(
//                                    FirebaseVisionFaceContour.UPPER_LIP_BOTTOM).getPoints();
//                            for (int i = 0; i < upperLipBottomContour.size(); i++) {
//                                FirebaseVisionPoint contour = upperLipBottomContour.get(i);
//                                if (i != (upperLipBottomContour.size() - 1)){
//                                    canvas.drawLine(contour.getX(), contour.getY(), upperLipBottomContour.get(i + 1).getX(),upperLipBottomContour.get(i + 1).getY(), linePaint);
//                                }
//                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
//
//                            }
//                            List<FirebaseVisionPoint> lowerLipTopContour = face.getContour(
//                                    FirebaseVisionFaceContour.LOWER_LIP_TOP).getPoints();
//                            for (int i = 0; i < lowerLipTopContour.size(); i++) {
//                                FirebaseVisionPoint contour = lowerLipTopContour.get(i);
//                                if (i != (lowerLipTopContour.size() - 1)){
//                                    canvas.drawLine(contour.getX(), contour.getY(), lowerLipTopContour.get(i + 1).getX(),lowerLipTopContour.get(i + 1).getY(), linePaint);
//                                }
//                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
//
//                            }
//                            List<FirebaseVisionPoint> lowerLipBottomContour = face.getContour(
//                                    FirebaseVisionFaceContour.LOWER_LIP_BOTTOM).getPoints();
//                            for (int i = 0; i < lowerLipBottomContour.size(); i++) {
//                                FirebaseVisionPoint contour = lowerLipBottomContour.get(i);
//                                if (i != (lowerLipBottomContour.size() - 1)){
//                                    canvas.drawLine(contour.getX(), contour.getY(), lowerLipBottomContour.get(i + 1).getX(),lowerLipBottomContour.get(i + 1).getY(), linePaint);
//                                }
//                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
//
//                            }
//
//                            List<FirebaseVisionPoint> noseBridgeContours = face.getContour(
//                                    FirebaseVisionFaceContour.NOSE_BRIDGE).getPoints();
//                            for (int i = 0; i < noseBridgeContours.size(); i++) {
//                                FirebaseVisionPoint contour = noseBridgeContours.get(i);
//                                if (i != (noseBridgeContours.size() - 1)) {
//                                    canvas.drawLine(contour.getX(), contour.getY(), noseBridgeContours.get(i + 1).getX(),noseBridgeContours.get(i + 1).getY(), linePaint);
//                                }
//                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
//
//                            }
//
//                            List<FirebaseVisionPoint> noseBottomContours = face.getContour(
//                                    FirebaseVisionFaceContour.NOSE_BOTTOM).getPoints();
//                            for (int i = 0; i < noseBottomContours.size(); i++) {
//                                FirebaseVisionPoint contour = noseBottomContours.get(i);
//                                if (i != (noseBottomContours.size() - 1)) {
//                                    canvas.drawLine(contour.getX(), contour.getY(), noseBottomContours.get(i + 1).getX(),noseBottomContours.get(i + 1).getY(), linePaint);
//                                }
//                                canvas.drawCircle(contour.getX(), contour.getY(), 4f, dotPaint);
//
//                            }
//                            if (cameraFacing == Facing.FRONT) {
//                                //Flip image!
//                                Matrix matrix = new Matrix();
//                                matrix.preScale(-1f, 1f);
//                                Bitmap flippedBitmap = Bitmap.createBitmap(bitmap, 0, 0,
//                                        bitmap.getWidth(), bitmap.getHeight(),
//                                        matrix, true);
//                                imageView.setImageBitmap(flippedBitmap);
//                            }else
//                                imageView.setImageBitmap(bitmap);
//                        }//end forloop
//
//
//                    }
//                }).addOnFailureListener(new OnFailureListener() {
//            @Override
//            public void onFailure(@NonNull Exception e) {
//                imageView.setImageBitmap(null);
//
//            }
//        });

    }


//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        // Inflate the menu; this adds items to the action bar if it is present.
//        getMenuInflater().inflate(R.menu.menu_main, menu);
//        return true;
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        // Handle action bar item clicks here. The action bar will
//        // automatically handle clicks on the Home/Up button, so long
//        // as you specify a parent activity in AndroidManifest.xml.
//        int id = item.getItemId();
//
//        //noinspection SimplifiableIfStatement
//        if (id == R.id.action_settings) {
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }
}
