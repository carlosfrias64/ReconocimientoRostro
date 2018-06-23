package com.example.maduch.myapplication;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.BaseAdapter;
import com.example.maduch.myapplication.Classifier.Recognition;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private static final int INPUT_SIZE = 250;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "Placeholder";
    private static final String OUTPUT_NAME = "final_result"; //output
    private static final String TAG = "MainActivity";


    private static final String MODEL_FILE = "file:///android_asset/retrained_graph.pb";
    private static final String LABEL_FILE =
            "file:///android_asset/retrained_labels.txt";

    private static final int CAMERA_REQUEST = 1888;
    private ImageView imageView;
    private static final int MY_CAMERA_PERMISSION_CODE = 100;
    private Classifier classifier;

    private SoundPool sp;
    private int soundID, person;
    boolean plays = false, loaded = false, playing = false;
    float actVolume, maxVolume, volume;
    AudioManager audioManager;

    static final int REQUEST_IMAGE_CAPTURE = 1;
    private Bitmap mImageBitmap;
    private String mCurrentPhotoPath;

    //SoundPool sp = new SoundPool(5, AudioManager.STREAM_MUSIC, 0);
    //Context context = getApplicationContext();
    //int soundId = sp.load(this, R.raw.siren, 1); // in 2nd param u have to pass your desire ringtone


    //MediaPlayer mPlayer = MediaPlayer.create(context, R.raw.siren); // in 2nd param u have to pass your desire ringtone
    Button photoButton;
    TextView result;
    int i = 0;


    String[] names = {"Charly","Eduardo","Fer"};


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        // AudioManager audio settings for adjusting the volume
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        actVolume = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        maxVolume = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        volume = actVolume / maxVolume;

        //Hardware buttons setting to adjust the media sound
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // the counter will help us recognize the stream id of the sound played  now
        //counter = 0;

        // Load the sounds
        sp = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        sp.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                loaded = true;
            }
        });
        soundID = sp.load(this, R.raw.siren, 1);
        playing = false;


        this.imageView = (ImageView) this.findViewById(R.id.image_pic);
        photoButton = (Button) this.findViewById(R.id.button_search);

        photoButton.setVisibility(View.INVISIBLE);
        Spinner spin = (Spinner) findViewById(R.id.valid_names);
        spin.setOnItemSelectedListener(this);
        //Creating the ArrayAdapter instance having the bank name list

        ArrayAdapter aa = new ArrayAdapter(this,android.R.layout.simple_spinner_item,names);
        aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        //Setting the ArrayAdapter data on the Spinner
        spin.setAdapter(aa);

        result = (TextView) findViewById(R.id.text_result);

        photoButton.setOnClickListener(new View.OnClickListener() {


            @Override
            public void onClick(View v) {
                if (checkSelfPermission(Manifest.permission.CAMERA)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA},
                            MY_CAMERA_PERMISSION_CODE);
                } else {
                    Intent cameraIntent = new
                            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    if (cameraIntent.resolveActivity(getPackageManager()) != null) {
                        // Create the File where the photo should go
                        File photoFile = null;
                        try {
                            photoFile = createImageFile();
                        } catch (IOException ex) {
                            // Error occurred while creating the File
                            //Log.i(TAG, "IOException");
                            Log.i("S", ex.toString());
                        }
                        // Continue only if the File was successfully created
                        if (photoFile != null) {
                            Uri uri = FileProvider.getUriForFile(getApplicationContext(), getPackageName()+".fileprovider", photoFile);
                            cameraIntent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
                            startActivityForResult(cameraIntent, CAMERA_REQUEST);
                        }
                    }
                }
            }
        });
    }

    public  boolean isStoragePermissionGranted() {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
     }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
                Intent cameraIntent = new
                        Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);

                startActivityForResult(cameraIntent, CAMERA_REQUEST);
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }

        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        int person_id = 0;

        if (requestCode == CAMERA_REQUEST && resultCode == RESULT_OK) {
            try {
                mImageBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), Uri.parse(mCurrentPhotoPath));
                mImageBitmap = Bitmap.createScaledBitmap(mImageBitmap, INPUT_SIZE, INPUT_SIZE, true);
                imageView.setImageBitmap(mImageBitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }

            classifier =
                TensorFlowImageClassifier.create(
                        getAssets(),
                        MODEL_FILE,
                        LABEL_FILE,
                        INPUT_SIZE,
                        IMAGE_MEAN,
                        IMAGE_STD,
                        INPUT_NAME,
                        OUTPUT_NAME);



            imageView.setImageBitmap(mImageBitmap);
            final List<Classifier.Recognition> results = classifier.recognizeImage(mImageBitmap);
            //result.setText(results.toString());
            String personName = "olis";
            for (final Recognition recog : results) {

                /*switch(recog.getId()){
                    case 3: person_id = 0; break;
                    case 4: person_id = 1; break;
                    case 6: person_id = 2; break;
                    default: break;
                }*/
                personName = recog.getTitle();
                result.setText(personName + ": " + recog.getConfidence());

                break;
            }

            boolean pass = false;
            if(         (personName.equals("carlos") && person == 0)
                    ||  (personName.equals("corbal") && person == 1)
                    ||  (personName.equals("fer") && person == 2)       )
            {
                pass = true;
            }

            if(!pass)
            {
                if(!playing)
                {
                    sp.play(soundID, volume, volume, 1, -1, 1f);
                    playing = true;
                }
            }
            else
            {
                if(playing)
                {
                    playing = false;
                    sp.pause(soundID);
                    soundID = sp.load(this, R.raw.siren, 1);
                }
            }

        }
    }

    //Performing action onItemSelected and onNothing selected
    @Override
    public void onItemSelected(AdapterView<?> arg0, View arg1, int position,long id) {
        //Toast.makeText(getApplicationContext(), names[position], Toast.LENGTH_LONG).show();

        //mPlayer.prepare();
        //mPlayer.start();
        person = position;

        //switch(position){
        //    case 0: sp.stop(soundID);break;//result.setText("Charly"); break;
        //    case 1: sp.play(soundID, volume, volume, 1, -1, 1f);plays=true;break;//result.setText("Eduardo"); break;
        //    case 2: sp.stop(soundID);break;//result.setText("Fer"); break;
        //    default: break;//sp.stop(soundId);break;
        //}

        photoButton.setVisibility(View.VISIBLE);
    }

    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
        // TODO Auto-generated method stub

    }

    private File createImageFile() throws IOException {
        if(isStoragePermissionGranted()) {
            // Create an image file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES);
            File image = File.createTempFile(
                    imageFileName,  // prefix
                    ".jpg",         // suffix
                    storageDir      // directory
            );

            // Save a file: path for use with ACTION_VIEW intents
            mCurrentPhotoPath = "file:" + image.getAbsolutePath();
            return image;
        }
        else {
            throw new IOException("Error obtaining permission");
        }
    }
}