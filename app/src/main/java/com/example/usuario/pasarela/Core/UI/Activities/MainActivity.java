package com.example.usuario.pasarela.Core.UI.Activities;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.usuario.pasarela.Core.DB.StorageHandler;
import com.example.usuario.pasarela.Core.Recorders.CameraRecorder;
import com.example.usuario.pasarela.Core.Recorders.Recorder;
import com.example.usuario.pasarela.Core.UI.Adapters.CameraAdapter;
import com.example.usuario.pasarela.Core.Uploader.FileUploader;
import com.example.usuario.pasarela.Exceptions.NoPermissionException;
import com.example.usuario.pasarela.R;
import com.example.usuario.pasarela.Core.Recorders.VideoRecorder;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import nl.bravobit.ffmpeg.FFmpeg;

public class MainActivity extends AppCompatActivity {
    public static final String LOG_TAG = "MainActivity";
    public static final int WRITE_STORAGE_PERMISSION = 1;
    public static final int CAMERA_PERMISSION = 2;
    public static final int INTERNET_PERMISSION = 3;
    public static final int INTERNET_STATE_PERMISSION = 4;

    private static final String SHARED_PREFS_CAMERAS = "cameras";
    private static final String SHARED_PREFS_VIDEOS_TO_UPLOAD = "videostoupload";

    private static final String SHARED_PREFS_STOP_WHEN_PAUSE = "stop";
    private static final String SHARED_PREFS_CONTINUE_WHEN_RESUME = "continue";
    private static final String SHARED_PREFS_UPLOAD_WITH_DATA = "data";

    private static final String SHARED_PREFS_RECONNECT_TIME = "reconnect";
    private static final String SHARED_PREFS_VIDEO_DELAY_POST_STOP = "delay";
    private static final String SHARED_PREFS_VIDEO_LENGTH = "length";

    private static final String SHARED_PREFS_PUBLIC_IP = "ippublic";
    private static final String SHARED_PREFS_PUBLIC_PORT = "portpublic";
    private static final String SHARED_PREFS_LOCAL_IP = "iplocal";
    private static final String SHARED_PREFS_LOCAL_PORT = "portlocal";

    private static final String SHARED_PREFS_DEFAULT_DIRECTORY = "directory";

    /** SETTINGS **/
    public static boolean stopWhenOnPause;
    public static boolean continueWhenOnResume;
    public static boolean uploadWithData = false;

    public static int RECONNECTTIME = 6000; //Time to reconnect if rtsp server fails
    public static int VIDEODELAYPOSTSTOP = 1000; //Time post-recorded when saving video (delays via rtsp)
    public static int VIDEOLENGTH = 12000; // Might be aroud +8s if SD card is not great (needs testing)

    //IPs and Ports go in FileUploader
    /** SETTINGS **/

    private List<Recorder> cameras;
    private CameraAdapter camerasAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(getResources().getString(R.string.actionbarText));
        if (FFmpeg.getInstance(this).isSupported()) {
            checkPermissions();
            Log.i(LOG_TAG, "FFmpeg is supported");
            setTextView(null, false);
            try{
                FileUploader.setMainContext(getApplicationContext());
                StorageHandler.setContext(getApplicationContext());
                StorageHandler.setVideosToUpload(recoverVideosToUpload());
                recoverCameras();
                createCameraList();
            }catch (NoPermissionException e){
                this.askForExternalStorageWrittingPermissions();
                setTextView(getResources().getString(R.string.noPermissionDefault), true);
            }
        } else {
            Log.i(LOG_TAG, "FFmpeg is not supported");
            setTextView(getResources().getString(R.string.noSupportedFFmpeg), true);
        }
    }

    /* Restarts the timer if coming from onPause() or starts the timer after onCreate() */
    @Override
    protected void onStart() {
        super.onStart();
        recoverConfig();
        for(int i = 0; i < getExternalFilesDirs(null).length; i++){
            Log.d(LOG_TAG, "Place: " + getExternalFilesDirs(null)[i].getAbsolutePath() + ": " + (new StatFs(getExternalFilesDirs(null)[i].getAbsolutePath()).getBlockCountLong() * new StatFs(getExternalFilesDirs(null)[i].getAbsolutePath()).getBlockSizeLong()));
        }
        try{
            if(continueWhenOnResume && stopWhenOnPause){
                resumeCamerasRecording();
            }
            camerasAdapter.notifyDataSetChanged();
        }catch (Exception e){
            Log.e(LOG_TAG, "Error while renaming: " + e.toString());
        }
    }

    /* When pausing the app, the video stops saving */
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "Will stop: " + stopWhenOnPause);
        saveCameras();
        saveConfig();
        saveVideosToUpload(StorageHandler.getVideosToUpload());
        if(stopWhenOnPause){
            for(Recorder e : this.cameras){
                e.stopRecording();
            }
        }
    }

    private void recoverConfig(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        // Behaviour
        stopWhenOnPause = prefs.getBoolean(SHARED_PREFS_STOP_WHEN_PAUSE, true);
        continueWhenOnResume = prefs.getBoolean(SHARED_PREFS_CONTINUE_WHEN_RESUME, true);
        uploadWithData = prefs.getBoolean(SHARED_PREFS_UPLOAD_WITH_DATA, false);

        // Integer related config
        RECONNECTTIME = prefs.getInt(SHARED_PREFS_RECONNECT_TIME, 6000);
        VIDEODELAYPOSTSTOP = prefs.getInt(SHARED_PREFS_VIDEO_DELAY_POST_STOP, 1000);
        VIDEOLENGTH = prefs.getInt(SHARED_PREFS_VIDEO_LENGTH, 12000);

        //IP-Port related
        FileUploader.setPublicIP(prefs.getString(SHARED_PREFS_PUBLIC_IP, null));
        FileUploader.setPublicPort(prefs.getInt(SHARED_PREFS_PUBLIC_PORT, 40406));
        FileUploader.setLocalIP(prefs.getString(SHARED_PREFS_LOCAL_IP, null)); // Generally, Static IPs start at 200
        FileUploader.setLocalPort(prefs.getInt(SHARED_PREFS_LOCAL_PORT, 40406));

        //Storage handler storage directory
        StorageHandler.setDirectory(prefs.getString(SHARED_PREFS_DEFAULT_DIRECTORY, null));

        Log.d(LOG_TAG, "Config: " + stopWhenOnPause + ", " + continueWhenOnResume + ", " + uploadWithData);
    }

    private void saveConfig(){
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();

        // Behaviour
        editor.putBoolean(SHARED_PREFS_STOP_WHEN_PAUSE, stopWhenOnPause);
        editor.putBoolean(SHARED_PREFS_CONTINUE_WHEN_RESUME, continueWhenOnResume);
        editor.putBoolean(SHARED_PREFS_UPLOAD_WITH_DATA, uploadWithData);

        // Integer related config
        editor.putInt(SHARED_PREFS_RECONNECT_TIME, RECONNECTTIME);
        editor.putInt(SHARED_PREFS_VIDEO_DELAY_POST_STOP, VIDEODELAYPOSTSTOP);
        editor.putInt(SHARED_PREFS_VIDEO_LENGTH, VIDEOLENGTH);

        //IP-Port related
        editor.putString(SHARED_PREFS_PUBLIC_IP, FileUploader.getPublicIP());
        editor.putInt(SHARED_PREFS_PUBLIC_PORT, FileUploader.getPublicPort());
        editor.putString(SHARED_PREFS_LOCAL_IP, FileUploader.getLocalIP());
        editor.putInt(SHARED_PREFS_LOCAL_PORT, FileUploader.getLocalPort());

        //Storage handler directory
        editor.putString(SHARED_PREFS_DEFAULT_DIRECTORY, StorageHandler.getDirectory());

        editor.commit();
        Log.d(LOG_TAG, "Configuration recovered");
    }

    private void recoverCameras(){
        this.cameras = new LinkedList<>();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Set<String> cameras;
        final MainActivity mainActivity = this;
        if((cameras = prefs.getStringSet(SHARED_PREFS_CAMERAS, null)) != null){
            Log.d(LOG_TAG, cameras.toString());
            for(String camera : cameras){
                Log.d(LOG_TAG, "Camera found: " + camera);
                String[] data = camera.split(":");
                if(data.length == 2 && data[0].trim().length() > 0){
                    //CameraRecorder => CameraName:cameraID
                    try{
                        this.cameras.add(new CameraRecorder(data[0], Integer.parseInt(data[1])));
                    }catch (NumberFormatException e){
                        Log.e(LOG_TAG, "CameraRecorder not saved properly: " + camera);
                    }
                }else if(data.length == 3 && data[0].trim().length() > 0){
                    //VideoRecorder => CameraName:IP:port
                    try{
                        this.cameras.add(new VideoRecorder(data[0], data[1], Integer.parseInt(data[2]), mainActivity));
                    }catch (NumberFormatException e){
                        Log.e(LOG_TAG, "VideoRecorder not saved properly: " + camera);
                    }
                }
            }
            Log.i(LOG_TAG, "Cameras recovered");
        }else{
            Log.w(LOG_TAG, "Could not find saved cameras");
        }
    }

    private void saveCameras(){
        Set<String> toSave = new HashSet<>();
        for(Recorder e : cameras){
            Log.d(LOG_TAG, "Saving " + e.toString());
            if(e instanceof VideoRecorder){
                // Format  => CameraName:IP:port
                // Example => CameraFront:192.168.43.24:5554
                toSave.add(e.getRecorderName() + ":" + ((VideoRecorder) e).getIP() + ":" + ((VideoRecorder) e).getPort());
            }else if(e instanceof CameraRecorder){
                // Format  => CameraName:cameraID
                // Example => CameraFront:cameraID
                toSave.add(e.getRecorderName() + ":" + ((CameraRecorder) e).getCameraID());
            }
        }
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
        editor.putStringSet(SHARED_PREFS_CAMERAS, toSave);
        editor.commit();
        Log.i(LOG_TAG, "Cameras saved " + toSave.toString());
    }

    private List<File> recoverVideosToUpload(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        List<File> retorno = new LinkedList<>();
        String files = prefs.getString(SHARED_PREFS_VIDEOS_TO_UPLOAD, "");
        String[] fileNames = files.split("\n");

        Log.d(LOG_TAG, "Saved file list: \n" + files);
        for(int i = 0; i < fileNames.length; i++){
            if(!fileNames[i].isEmpty() && fileNames[i] != "/"){
                retorno.add(new File(fileNames[i].trim()));
            }else{
                Log.d(LOG_TAG, "Unrecoverable file: " + fileNames[i] + "; " + fileNames[i].isEmpty());
            }
        }

        return retorno;
    }

    private void saveVideosToUpload(List<File> list){
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
        StringBuilder save = new StringBuilder();

        Log.d(LOG_TAG, "Saving file list: \n");
        for(File f : list){
            Log.d(LOG_TAG, "\t- " + f.getAbsolutePath());
            save.append(f.getAbsolutePath().trim() + "\n");
        }

        editor.putString(SHARED_PREFS_VIDEOS_TO_UPLOAD, save.toString().trim());
        editor.commit();
    }

    private void createCameraList(){
        final ListView lvCameras = findViewById(R.id.lvCameras);
        //TODO: Recover from internal memory
        this.camerasAdapter = new CameraAdapter(this, this.cameras, this);

        lvCameras.setAdapter(camerasAdapter);

        this.registerForContextMenu(lvCameras);
    }

    private void resumeCamerasRecording(){
        for(Recorder e : cameras){
            e.resumeRecording();
        }
    }

    public void createCamera(){
        boolean cameraLocal = true;
        for(Recorder e : cameras){
            if(e instanceof CameraRecorder){
                cameraLocal = false;
                break;
            }
        }

        //AlertDialog to create local or rtsp camera
        createCameraRTSP();
    }

    public boolean removeCamera(Recorder camera){
        boolean retorno = this.cameras.remove(camera);
        if(retorno){
            camera.stopRecording();
            camera = null;
            this.camerasAdapter.notifyDataSetChanged();
        }
        return retorno;
    }

    public void addCamera(Recorder camera, boolean initiate){
        this.cameras.add(camera);

        if(initiate){
            camera.resumeRecording();
        }

        camerasAdapter.notifyDataSetChanged();
    }

    // Creates a camera from the local cameras of the mobile phone (CameraRecorder)
    public void createLocalCamera(){

    }

    // Connects to a RTSP camera
    public void createCameraRTSP(){
        AlertDialog.Builder dlg = new AlertDialog.Builder(this);
        dlg.setTitle(getResources().getString(R.string.addCamera));
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        final EditText etCameraName = new EditText(this);
        etCameraName.setHint(getResources().getString(R.string.nameCamera));
        layout.addView(etCameraName);

        final EditText etIP = new EditText(this);
        etIP.setText(getResources().getString(R.string.default_ip));
        layout.addView(etIP);

        final EditText etPort = new EditText(this);
        etPort.setHint(getResources().getString(R.string.port));
        layout.addView(etPort);

        dlg.setView(layout);
        final MainActivity mainActivity = this;

        dlg.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if( !etCameraName.getText().toString().equals("") && !etIP.getText().toString().equals("") &&
                    Patterns.IP_ADDRESS.matcher(etIP.getText().toString()).matches() && !etPort.getText().toString().equals("")){
                    Log.d(LOG_TAG, "Creating camera");
                    try{
                        int port = Integer.parseInt(etPort.getText().toString());
                        cameras.add(new VideoRecorder(etCameraName.getText().toString(), etIP.getText().toString(), port, mainActivity));
                        camerasAdapter.notifyDataSetChanged();
                        Log.d(LOG_TAG, "Camera added");
                    }catch (NumberFormatException e){
                        Toast.makeText(mainActivity.getApplicationContext(), R.string.wrongPort, Toast.LENGTH_LONG).show();
                        Log.e(LOG_TAG, "Bad port: " + e.toString());
                    }
                }else{
                    Log.i(LOG_TAG, "Bad data: " + etCameraName.getText().toString() + ":" + etIP.getText().toString() + ":" + etPort.getText().toString());
                }
            }
        });

        dlg.setNegativeButton(R.string.cancel, null);
        dlg.create().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.itemAddCamera:
                this.createCamera();
                return true;
            case R.id.itemConfig:
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void setTextView(String msg, boolean show){
        final TextView tvInfo = findViewById(R.id.tvInformative);
        if(show){
            tvInfo.setVisibility(View.VISIBLE);
            tvInfo.setText(msg);
        }else{
            tvInfo.setText("");
            tvInfo.setVisibility(View.INVISIBLE);
        }
    }

    public void checkPermissions(){
        this.askForExternalStorageWrittingPermissions();
        this.askForInternetPermission();
        this.askForInternetStatePermission();
    }

    public void askForExternalStorageWrittingPermissions(){
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                AlertDialog.Builder dlg = new AlertDialog.Builder(this);
                dlg.setTitle(R.string.storageRequest);
                dlg.setMessage(R.string.storageRequestMsg);
                final Activity main = this;
                dlg.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(main, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_STORAGE_PERMISSION);
                    }
                });
                dlg.create().show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_STORAGE_PERMISSION);
            }
        }
    }

    public void askForInternetPermission(){
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.INTERNET)) {
                Toast.makeText(this, getResources().getString(R.string.storageRequest), Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, INTERNET_PERMISSION);
            }
        }
    }

    public void askForInternetStatePermission(){
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_NETWORK_STATE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_NETWORK_STATE)) {
                Toast.makeText(this, getResources().getString(R.string.storageRequest), Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_NETWORK_STATE}, INTERNET_STATE_PERMISSION);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,  String permissions[], int[] grantResults) {
        switch (requestCode) {
            case WRITE_STORAGE_PERMISSION:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    this.onStart();
                }else{
                    askForExternalStorageWrittingPermissions();
                }
                break;

            case CAMERA_PERMISSION:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission granted
                }else{

                }
                break;

            case INTERNET_PERMISSION:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission granted
                }else{
                    askForInternetPermission();
                }
                break;

            case INTERNET_STATE_PERMISSION:
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //Permission granted
                }else{
                    askForInternetStatePermission();
                }
                break;
        }
    }
}
