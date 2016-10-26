package com.blueeagle.facedetector;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.FaceRectangle;
import com.squareup.leakcanary.LeakCanary;
import com.squareup.leakcanary.RefWatcher;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private ImageButton imbGallery, imbCamera;
    private ImageView imvPhoto;
    private ProgressDialog progressDialog;

    static String TAG = "MainActivity";
    static final int RC_HANDLE_CAMERA_PERM = 1;
    static final int RC_HANDLE_STORAGE_PERM = 2;
    static final int RC_HANDLE_ALL_PERM = 3;

    static boolean RS_HANDLE_CAMERA_PERM = false;
    static boolean RS_HANDLE_STORAGE_PERM = false;
    static boolean RS_HANDLE_INTERNET_PERM = false;

    static final int REQUEST_IMAGE_CAPTURE = 1;
    static final int REQUEST_PICK_IMAGE = 2;

    private String mCurrentPhotoPath;
    private Bitmap imageBitmap = null;

    // Face client for detector
    private FaceServiceClient faceServiceClient;

    // Detect leak memory
    private RefWatcher refWatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Leak-canary is in analyzing
        if (LeakCanary.isInAnalyzerProcess(this)) {
            Log.d(TAG, "Leak-canary is in analyzing...");
            return;
        }

        // Install Leak-canary
        refWatcher = LeakCanary.install(getApplication());

        // Init view
        initView();

        // Check permission
        checkPermissions();

        // Face client for detector
        String APIKey = getResources().getString(R.string.microsoft_oxford_api_subscription_key);
        faceServiceClient = new FaceServiceRestClient(APIKey);

        imbGallery.setOnClickListener(this);
        imbCamera.setOnClickListener(this);

        // Config progress dialog
        progressDialog = new ProgressDialog(this);
    }

    // Init view
    public void initView() {
        imbCamera = (ImageButton) findViewById(R.id.imbCamera);
        imbGallery = (ImageButton) findViewById(R.id.imbGallery);
        imvPhoto = (ImageView) findViewById(R.id.imvPhoto);
    }

    // Check all permission
    public boolean checkPermissions() {
        String[] requestPermission = new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE};

        List<String> permissionNeedRequest = new ArrayList<>();
        int rs;

        for (String permission : requestPermission) {
            rs = ActivityCompat.checkSelfPermission(this, permission);
            if (rs != PackageManager.PERMISSION_GRANTED)
                permissionNeedRequest.add(permission);
        }

        if (!permissionNeedRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionNeedRequest.toArray(new String[permissionNeedRequest.size()]),
                    RC_HANDLE_ALL_PERM);

            return false;
        }

        return true;
    }

    // Request camera permission
    public void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission...");

        final String[] cameraPermission = new String[]{Manifest.permission.CAMERA};
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, cameraPermission, RC_HANDLE_CAMERA_PERM);
        }
    }

    // Request storage permission
    public void requestStoragePermission() {
        Log.w(TAG, "Storage permission is not granted. Requesting permission...");

        final String[] storagePermission = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(this, storagePermission, RC_HANDLE_STORAGE_PERM);
        }
    }

    // Request storage permission
    public void requestInternetPermission() {
        Log.w(TAG, "Internet permission is not granted. Requesting permission...");

        final String[] internetPermission = new String[]{Manifest.permission.INTERNET};
        if (!ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET)) {
            ActivityCompat.requestPermissions(this, internetPermission, RC_HANDLE_ALL_PERM);
        }
    }

    // Handle result of permission request
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        switch (requestCode) {
            case RC_HANDLE_CAMERA_PERM:
                if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Camera permission is granted.");
                    RS_HANDLE_CAMERA_PERM = true;
                }
                break;

            case RC_HANDLE_STORAGE_PERM:
                if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Storage permission is granted.");
                    RS_HANDLE_STORAGE_PERM = true;
                }
                break;

            case RC_HANDLE_ALL_PERM:
                if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Internet permission is granted.");
                    RS_HANDLE_INTERNET_PERM = true;
                }
                break;

            default:
                break;
        }
    }

    @Override
    public void onClick(View v) {
        int viewId = v.getId();

        switch (viewId) {
            case R.id.imbCamera:
                takePhoto();
                break;

            case R.id.imbGallery:
                pickImageFromGallery();
                break;
        }
    }

    /**
     * Detect gender and age of all people in photo
     *
     * @param imageBitmap: Bitmap image
     */
    public void detectFace(Bitmap imageBitmap) {
        // Create input stream from bitmap
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(outputStream.toByteArray());

        // Detect face from input stream
        new DetectAsyntask().execute(inputStream);
    }

    public void pickImageFromGallery() {
        Intent pickPhotoIntent = new Intent(Intent.ACTION_GET_CONTENT);
        pickPhotoIntent.setType("image/*");
        startActivityForResult(pickPhotoIntent, REQUEST_PICK_IMAGE);
    }

    // Open camera then take a photo
    public void takePhoto() {

        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;

            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Log.e(TAG, "Can't create photo file. " + ex.getMessage());
            }

            if (photoFile != null) {
                Uri photoUri = FileProvider.getUriForFile(
                        this,
                        "com.example.android.fileprovider",
                        photoFile);

                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_IMAGE_CAPTURE:
                if (resultCode == RESULT_OK) {
                    imageBitmap = decodeBitmap();
                    if (imageBitmap != null) {
                        imvPhoto.setImageBitmap(imageBitmap);
                        detectFace(imageBitmap);
                    }
                }
                break;

            case REQUEST_PICK_IMAGE:
                if (resultCode != RESULT_OK)
                    break;

                Uri uri = data.getData();

                try {
                    imageBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                    imvPhoto.setImageBitmap(imageBitmap);
                    detectFace(imageBitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                break;
        }

    }

    public File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storgeDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        File image = File.createTempFile(
                imageFileName,
                ".jpg",
                storgeDir);

        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }

    // Decode bitmap to fit to image view
    public Bitmap decodeBitmap() {
        int targetW = imvPhoto.getWidth();
        int targetH = imvPhoto.getHeight();

        BitmapFactory.Options bmpOption = new BitmapFactory.Options();
        bmpOption.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mCurrentPhotoPath, bmpOption);

        int photoW = bmpOption.outWidth;
        int photoH = bmpOption.outHeight;

        // Caculate scale ratio
        int scaleFactor = Math.min(photoW / targetW, photoH / targetH);

        // Decode the image file into a Bitmap sized to fill the view
        bmpOption.inJustDecodeBounds = false;
        bmpOption.inSampleSize = scaleFactor;
        bmpOption.inPurgeable = true;

        return BitmapFactory.decodeFile(mCurrentPhotoPath, bmpOption);
    }

    public Bitmap drawInfoToBitmap(Bitmap originBitmap, Face[] faces) {
        Bitmap bitmap = originBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();

        if (faces != null) {
            for (Face face : faces) {
                FaceRectangle faceRectangle = face.faceRectangle;

                // Config paint to draw a rect
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(Color.parseColor("#9C27B0"));
                paint.setStrokeWidth(3.0f);

                // Draw a rect
                canvas.drawRect(
                        faceRectangle.left,
                        faceRectangle.top,
                        faceRectangle.left + faceRectangle.width,
                        faceRectangle.top + faceRectangle.height,
                        paint);

                // Draw another rect
                paint.setStyle(Paint.Style.FILL_AND_STROKE);
                canvas.drawRect(
                        faceRectangle.left,
                        faceRectangle.top - 15,
                        faceRectangle.left + faceRectangle.width,
                        faceRectangle.top + 20,
                        paint);

                // Config paint to draw text
                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(16.0f);

                String info = face.faceAttributes.gender + ", " + (int) face.faceAttributes.age;
                // Draw text
                canvas.drawText(info, faceRectangle.left + 10, faceRectangle.top + 5, paint);
            }
        }

        return bitmap;
    }

    class DetectAsyntask extends AsyncTask<InputStream, String, Face[]> {

        @Override
        protected Face[] doInBackground(InputStream... params) {
            try {
                Log.d(TAG, "Detecting...");
                FaceServiceClient.FaceAttributeType types[] = new FaceServiceClient.FaceAttributeType[2];
                types[0] = FaceServiceClient.FaceAttributeType.Age;
                types[1] = FaceServiceClient.FaceAttributeType.Gender;

                Face[] result = faceServiceClient.detect(
                        params[0],
                        true,         // returnFaceId
                        false,        // returnFaceLandmarks
                        types          // returnFaceAttributes: a string like "age, gender"
                );

                if (result == null) {
                    Toast.makeText(getApplicationContext(),
                            "Detection finished. Nothing detected", Toast.LENGTH_LONG).show();
                    return null;
                }

                Log.d(TAG, String.format("Detection Finished. %d face(s) detected", result.length));
                return result;

            } catch (Exception e) {
                Toast.makeText(getApplicationContext(),
                        "Detect failed. Please try again!", Toast.LENGTH_LONG).show();
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            progressDialog.setMessage("Please wait! Detecting...");
            progressDialog.show();
        }

        @Override
        protected void onProgressUpdate(String... progress) {
            //TODO: update progress
        }

        @Override
        protected void onPostExecute(Face[] result) {
            // Hide progress dialog
            progressDialog.dismiss();

            // Nothing detected or detect failed
            if (result == null)
                return;

            Bitmap bitmap = drawInfoToBitmap(imageBitmap, result);
            imvPhoto.setImageBitmap(bitmap);
        }
    }

    @Override
    protected void onDestroy() {
        // Watch leak memory
        refWatcher.watch(this);

        super.onDestroy();
    }
}
