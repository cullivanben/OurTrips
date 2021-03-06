package com.alsaeedcullivan.ourtrips;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.alsaeedcullivan.ourtrips.cloud.AccessBucket;
import com.alsaeedcullivan.ourtrips.cloud.AccessDB;
import com.alsaeedcullivan.ourtrips.fragments.CustomDialogFragment;
import com.alsaeedcullivan.ourtrips.glide.GlideApp;
import com.alsaeedcullivan.ourtrips.models.Pic;
import com.alsaeedcullivan.ourtrips.utils.Const;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.cloud.landmark.FirebaseVisionCloudLandmark;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionLatLng;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ViewPictureActivity extends AppCompatActivity {

    // keys for the hash map that contains info about the recognized location that was most likely
    private static final String LOC_NAME_KEY = "loc_name";
    private static final String LOC_CONFIDENCE_KEY = "conf";
    private static final String LOC_LOC_KEY = "loc_loc";

    private Pic mPhoto;
    private String mTripId;
    private ImageView mImage;
    private Button mRecognize;
    private TextView mLoading;
    private ProgressBar mSpinner;
    private int mPosition = -1;
    private ArrayList<Pic> mPics;
    private FirebaseVisionImage mVisionImage;
    private List<FirebaseVisionCloudLandmark> mLandmarks;
    private Map<String, Object> mDetectedLocation = new HashMap<>();
    private String mLocationName;
    private Float mLocationConfidence;
    private List<FirebaseVisionLatLng> mLocationLocations;
    private LatLng mLatLng;
    private StorageReference mRef;
    private File mFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_picture);

        // set the back button
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        if (intent != null && intent.getStringExtra(Const.TRIP_ID_TAG) != null &&
                intent.getParcelableExtra(Const.PIC_TAG) != null &&
                intent.getIntExtra(Const.POSITION_TAG, -1) != -1
                && intent.getParcelableArrayListExtra(Const.GALLERY_TAG) != null) {
            mPhoto = intent.getParcelableExtra(Const.PIC_TAG);
            mTripId = intent.getStringExtra(Const.TRIP_ID_TAG);
            mPosition = intent.getIntExtra(Const.POSITION_TAG, -1);
            mPics = intent.getParcelableArrayListExtra(Const.GALLERY_TAG);
        } else finish();

        // get a reference to the widgets and set initial visibility
        mImage = findViewById(R.id.view_image_picture);
        mLoading = findViewById(R.id.view_loading);
        mSpinner = findViewById(R.id.view_spinner);
        mRecognize = findViewById(R.id.recognize_button);
        mRecognize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recognizeLocation();
            }
        });

        hideSpinner();

        // load the picture
        mRef = FirebaseStorage.getInstance().getReference(mPhoto.getPicPath());
        GlideApp.with(this).load(mRef).into(mImage);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.view_picture_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(ViewPictureActivity.this, GalleryActivity.class);
                intent.putExtra(Const.GALLERY_TAG, mPics);
                intent.putExtra(Const.TRIP_ID_TAG, mTripId);
                startActivity(intent);
                finish();
                return true;
            case R.id.delete_photo:
                deletePhoto();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * recognizeLocation()
     * begins the task that has the firebase ML kit recognize the location in the image that is
     * currently being displayed in the image view
     */
    private void recognizeLocation() {
        if (mImage.getDrawable() == null) return;
        Bitmap bitmap = ((BitmapDrawable)mImage.getDrawable()).getBitmap();
        if (bitmap == null) return;

        // create a FirebaseVisionImage object from the bitmap
        mVisionImage = FirebaseVisionImage.fromBitmap(bitmap);

        String rec = "Recognizing location...";
        mLoading.setText(rec);
        showSpinner();

        // detect the landmark
        new DetectLandmarkTask().execute();
    }

    /**
     * addToMap()
     * adds this landmark to the map
     */
    public void addToMap() {
        if (mLocationLocations == null || mLocationLocations.size() == 0 || mLocationName == null ||
                mTripId == null) return;

        // get the location of the landmark
        FirebaseVisionLatLng location = mLocationLocations.get(0);
        if (location == null) return;
        mLatLng = new LatLng(location.getLatitude(), location.getLongitude());

        // add this location to the map
        new AddToMapTask().execute();

        Log.d(Const.TAG, "addToMap: " + mLatLng);
        Log.d(Const.TAG, "addToMap: map");
    }

    /**
     * deletePhoto()
     * deletes this photo from storage and the db
     */
    private void deletePhoto() {
        if (mPhoto == null || mTripId == null || mPhoto.getDocId() == null || mPosition == -1 ||
                mPics == null || mPhoto.getPicPath() == null) return;
        Log.d(Const.TAG, "deletePhoto: " + mPhoto.getDocId());
        Log.d(Const.TAG, "deletePhoto: " + mPhoto.getPicPath());
        String del = "Deleting...";
        mLoading.setText(del);
        // show the progress bar
        showSpinner();
        // remove this photo from the list
        mPics.remove(mPosition);
        // delete this picture
        new DeletePicTask().execute();
    }

    /**
     * showSpinner()
     * shows the progress bar
     */
    private void showSpinner() {
        mImage.setVisibility(View.GONE);
        mRecognize.setVisibility(View.GONE);
        mSpinner.setVisibility(View.VISIBLE);
        mLoading.setVisibility(View.VISIBLE);
    }

    /**
     * hideSpinner()
     * hides the progress bar
     */
    private void hideSpinner() {
        mSpinner.setVisibility(View.GONE);
        mLoading.setVisibility(View.GONE);
        mImage.setVisibility(View.VISIBLE);
        mRecognize.setVisibility(View.VISIBLE);
    }

    // GETTERS

    public String getLocationName() {
        return mLocationName;
    }

    public Float getLocationConfidence() {
        return mLocationConfidence;
    }


    // ASYNC TASKS

    /**
     * DetectLandMarkTask
     * sends a request to the firebase vision api to detect any landmarks in the current image
     */
    private class DetectLandmarkTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mVisionImage == null) return null;

            // get a landmark detector
            FirebaseVision.getInstance()
                    .getVisionCloudLandmarkDetector()
                    .detectInImage(mVisionImage)
                    .addOnCompleteListener(new OnCompleteListener<List<FirebaseVisionCloudLandmark>>() {
                        @Override
                        public void onComplete(@NonNull Task<List<FirebaseVisionCloudLandmark>> task) {
                            if (task.isSuccessful() && task.getResult() != null) {
                                mLandmarks = task.getResult();
                                // process the landmarks that were returned
                                new ProcessLandmarkTask().execute();
                            } else {
                                hideSpinner();
                                Log.d(Const.TAG, "onComplete: recognition failure");
                            }
                        }
                    });

            return null;
        }
    }

    /**
     * ProcessLandMarkTasks
     * handles the results of the request to the firebase vision api and responds accordingly
     */
    private class ProcessLandmarkTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (mDetectedLocation != null) mDetectedLocation.clear();
            else mDetectedLocation = new HashMap<>();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (mLandmarks == null || mDetectedLocation == null) return null;

            // the top confidence landmark
            float topConfidence = (float)-1.0;
            FirebaseVisionCloudLandmark mostConfident = null;

            // loop over the landmarks and find the one that is the most likely
            for (FirebaseVisionCloudLandmark mark : mLandmarks) {
                if (mark.getConfidence() > topConfidence) {
                    topConfidence = mark.getConfidence();
                    mostConfident = mark;
                }
            }

            if (mostConfident == null) return null;

            // add the data of the most likely landmark to a map

            Float confidence = mostConfident.getConfidence();
            String name = mostConfident.getLandmark();
            List<FirebaseVisionLatLng> locations = mostConfident.getLocations();

            mDetectedLocation.put(LOC_NAME_KEY, name);
            mDetectedLocation.put(LOC_CONFIDENCE_KEY, confidence);
            mDetectedLocation.put(LOC_LOC_KEY, locations);

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            // hide the progress bar
            hideSpinner();

            // if the location could not be recognized, inform the user
            if (mDetectedLocation == null || !mDetectedLocation.containsKey(LOC_NAME_KEY) || !mDetectedLocation
                    .containsKey(LOC_CONFIDENCE_KEY) || !mDetectedLocation.containsKey(LOC_LOC_KEY)) {
                Toast t = Toast.makeText(ViewPictureActivity.this, "This location " +
                        "could not be recognized", Toast.LENGTH_SHORT);
                t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                t.show();
                return;
            }

            // extract and save the data
            mLocationName = (String) mDetectedLocation.get(LOC_NAME_KEY);
            mLocationConfidence = (Float) mDetectedLocation.get(LOC_CONFIDENCE_KEY);
            // this cast will not produce an exception
            mLocationLocations = (List<FirebaseVisionLatLng>) mDetectedLocation.get(LOC_LOC_KEY);

            // show the dialog asking the user if they want to add this location to the map
            CustomDialogFragment.newInstance(CustomDialogFragment.RECOGNIZE_LOC_ID)
                    .show(getSupportFragmentManager(), CustomDialogFragment.TAG);
        }
    }

    /**
     * AddToMapTask
     * adds the location that was recognized to the map
     */
    private class AddToMapTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mLocationName == null || mTripId == null || mLatLng == null) return null;

            // add the location to the trip in the db
            AccessDB.addTripLocation(mTripId, mLatLng, mLocationName, new Date().getTime())
                    .addOnCompleteListener(new OnCompleteListener<DocumentReference>() {
                @Override
                public void onComplete(@NonNull Task<DocumentReference> task) {
                    if (task.isSuccessful()) {
                        Toast t = Toast.makeText(ViewPictureActivity.this, mLocationName
                                + " was added to the map!", Toast.LENGTH_SHORT);
                        t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                        t.show();
                    } else {
                        Toast t = Toast.makeText(ViewPictureActivity.this, "This " +
                                "location could not be added to the map", Toast.LENGTH_SHORT);
                        t.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 0);
                        t.show();
                    }
                }
            });

            return null;
        }
    }

    /**
     * DeletePicTask
     * deletes a picture from the storage bucket and deletes its path from the db
     */
    private class DeletePicTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            if (mTripId == null || mPhoto == null || mPics == null) return null;

            // remove the photo from the db and storage
            Task<Void> dbTask = AccessDB.deleteTripPhoto(mTripId, mPhoto.getDocId());
            Task<Void> storeTask = AccessBucket.deleteFromStorage(mPhoto.getPicPath());
            // when the photo is deleted, finish the activity
            Tasks.whenAll(dbTask, storeTask).addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    Intent intent = new Intent(ViewPictureActivity.this, GalleryActivity.class);
                    intent.putExtra(Const.GALLERY_TAG, mPics);
                    intent.putExtra(Const.TRIP_ID_TAG, mTripId);
                    startActivity(intent);
                    Log.d(Const.TAG, "onComplete: finish activity deleted pic");
                    finish();
                }
            });

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            Log.d(Const.TAG, "onPostExecute: delete pic task done");
        }
    }
}
