package com.example.usuario.pasarela.Core.Recorders;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import com.example.usuario.pasarela.Core.DB.StorageHandler;
import com.example.usuario.pasarela.Core.UI.Activities.MainActivity;
import com.example.usuario.pasarela.Exceptions.NoPermissionException;
import com.example.usuario.pasarela.R;

import java.io.File;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;

import nl.bravobit.ffmpeg.FFcommandExecuteResponseHandler;
import nl.bravobit.ffmpeg.FFmpeg;
import nl.bravobit.ffmpeg.FFtask;

public class VideoRecorder extends AsyncTask<Void, Void, Boolean> implements Recorder {
    private static final String LOG_TAG = "VideoRecorder";
    private static final int ERR_NONE = 0;
    private static final int ERR_NOPERMISSIONS = 1;
    private static final int ERR_BADIP = 2;

    private static MainActivity mainContext;

    private String cameraName;

    private String IP;
    private int port;
    private String RTSP_URL;

    private boolean recieve;
    private boolean isSaving;
    private boolean isRecording;
    private boolean running;
    private int err;

    private FFmpeg ffmpeg;
    private FFtask ffTask;
    private StorageHandler storageHandler;
    private Timer timer;
    private boolean killRunningFFTask;

    public VideoRecorder(String cameraName, String IP, int port, MainActivity mainContext){
        if(this.mainContext == null){
            this.mainContext = mainContext;
        }
        ffmpeg = FFmpeg.getInstance(this.mainContext);
        this.IP = IP;
        this.port = port;
        RTSP_URL = "rtsp://" + IP + ":" + port;
        this.recieve = false;
        this.isRecording = false;
        this.isSaving = false;
        this.running = false;
        this.err = ERR_NONE;
        this.cameraName = cameraName;
        this.storageHandler = new StorageHandler(cameraName);
    }

    @Override
    protected Boolean doInBackground(Void... voids) {
        running = true;
        try{
            //Check the IP is right
            Log.d(LOG_TAG, InetAddress.getByName(getIP()).toString());
            Log.d(LOG_TAG, InetAddress.getByName(getIP()).getHostName());
        }catch (Exception e){
            Log.w(LOG_TAG, "Testing IP/hostname: " + e.toString());
            this.err = ERR_BADIP;
            return false;
        }

        if (ContextCompat.checkSelfPermission(mainContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            this.err = ERR_NOPERMISSIONS;
            return false;
        }

        return true;
    }

    private void startNextFile() throws NoPermissionException{
        try{
            startRecording(storageHandler.getFile());
        }catch (NoPermissionException e){
            if(ffTask != null){
                ffTask.sendQuitSignal();
            }
            Log.e(LOG_TAG, e.toString());
            throw new NoPermissionException(e.getMessage());
        }
    }

    private void startRecording(final File f) {
        try{
            if (recieve && !isRecording) {
                isRecording = true;
                final FFtask prevFFTask = ffTask;
                /**
                 * -y -> Overwrite file
                 * -i -> Source (next argument)
                 * -acodec -> Audio codec (copy aka same)
                 * -vcodec -> Video codec (copy aka same)
                 */
                String[] ffmpegCommand = new String[]{"-y", "-i", RTSP_URL, "-acodec", "copy", "-vcodec", "copy", f.toString()};

                timer = new java.util.Timer();

                final TimerTask reconnectTimerTask;
                killRunningFFTask = true;

                ffTask = ffmpeg.execute(ffmpegCommand, new FFcommandExecuteResponseHandler() {
                    TimerTask newRecordingTimerTask;

                    @Override
                    public void onStart() {
                        Log.i(LOG_TAG, "Recording starting " + cameraName);
                    }

                    @Override
                    public void onProgress(String message) {
                        if (message.contains("Input")) {
                            killRunningFFTask = false;
                            Log.i(LOG_TAG, "Connected to RTSP server " + cameraName);
                        }
                        if (message.contains("Output")) {
                            try{
                                killRunningFFTask = false;
                                Log.i(LOG_TAG, "Started recording from RTSP server " + cameraName);
                                if (prevFFTask != null) {
                                    prevFFTask.sendQuitSignal();
                                }

                                newRecordingTimerTask = new TimerTask() {
                                    @Override
                                    public void run() {
                                        try {
                                            isRecording = false;
                                            startNextFile();
                                        } catch (Exception e) {
                                            Log.e(LOG_TAG, "No permissions inside ");
                                            err = ERR_NOPERMISSIONS;
                                        }
                                        Log.i(LOG_TAG, "VideoLength reached, starting a new recording " + cameraName);
                                    }
                                };

                                getTimer().schedule(newRecordingTimerTask, MainActivity.VIDEOLENGTH);
                            }catch(IllegalStateException e){
                                Log.e(LOG_TAG, "Timer already canceled " + e.toString());
                            }
                        }
                    }

                    @Override
                    public void onFailure(String message) {
                        //Happens if we stop ffmpeg recording (which is not a bug) or other
                        Log.i(LOG_TAG, "Recording " + cameraName + " finished unsuccesfully");
                    }

                    @Override
                    public void onSuccess(String message) {
                        //Happens if RTSP server closes while recording
                        Log.i(LOG_TAG, "Recording " + cameraName + " finished succesfully");
                    }

                    @Override
                    public void onFinish() {
                        Log.i(LOG_TAG, "Recording " + cameraName + " finished");
                    }
                });

                // Manges reconnection try from this method
                reconnectTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        if (killRunningFFTask) {
                            ffTask.killRunningProcess();
                            Log.i(LOG_TAG, "ffTask ran out of time to start");
                            isRecording = false;
                            startRecording(f);
                        }
                    }
                };

                //Schedules the recconection try in RECONNECTTIME ms
                getTimer().schedule(reconnectTimerTask, MainActivity.RECONNECTTIME);
            } else {
                Log.i(LOG_TAG, "Already recording");
            }
        }catch(IllegalStateException e){
            Log.e(LOG_TAG, "Timer already canceled " + e.toString());
        }
    }

    private Timer getTimer(){
        return timer;
    }

    @Override
    public boolean resumeRecording(){
        running = true;
        recieve = true;
        Log.i(LOG_TAG, "Video recording resuming");
        try{
            if(!isRecording){
                storageHandler.clearProgressFiles();
                Toast.makeText(mainContext.getApplicationContext(), mainContext.getResources().getString(R.string.recording), Toast.LENGTH_SHORT).show();
                startNextFile();
            }else{
                Toast.makeText(mainContext.getApplicationContext(), mainContext.getResources().getString(R.string.alreadyRecording), Toast.LENGTH_SHORT).show();
            }
        }catch (NoPermissionException e){
            Log.e(LOG_TAG, "Unable to resume recording" + e.toString());
            return false;
        }
        return true;
    }

    @Override
    public boolean stopRecording(){
        Log.i(LOG_TAG, "Video recording stopping");
        recieve = false;
        if(isRecording){
            isRecording = false;
            try{
                timer.cancel();
            }catch (Exception e){

            }
            try{
                ffTask.sendQuitSignal();
            }catch (Exception e){

            }
            try{
                timer.purge();
            }catch (Exception e){

            }
        }
        Toast.makeText(mainContext.getApplicationContext(), mainContext.getResources().getString(R.string.disableRecording), Toast.LENGTH_SHORT).show();
        return true;
    }

    /* Saves a video replay to be uploaded */
    @Override
    public void saveVideoReplay(){
        if(running && recieve && !isSaving){
            Log.i(LOG_TAG, "Video recording saving");
            recieve = false;
            getTimer().cancel();
            getTimer().purge();
            try{
                Thread.sleep(MainActivity.VIDEODELAYPOSTSTOP);
            }catch (Exception e){

            }
            try{
                Log.d(LOG_TAG, "Saving video");
                Toast.makeText(mainContext.getApplicationContext(), mainContext.getResources().getString(R.string.savingReplay), Toast.LENGTH_SHORT).show();
                ffTask.sendQuitSignal();
                int waitLimit = 50; // Prevents from fftask not running ever
                while(!ffTask.isProcessCompleted() && waitLimit > 0){
                    Log.d(LOG_TAG, "Waiting for task to end " + ffTask.isProcessCompleted());
                    Thread.sleep(100);
                    waitLimit--;
                }
                if(waitLimit <= 0){
                    Log.d(LOG_TAG, "Task ended badly");
                    ffTask.killRunningProcess();
                }else{
                    Log.d(LOG_TAG, "Task ended");
                    storageHandler.saveFile();
                }
                recieve = true;
                isRecording = false;
                Log.d(LOG_TAG, "Restarting recording");
                startNextFile();
            }catch (NoPermissionException e){
                Log.e(LOG_TAG, "No permissions");
                err = ERR_NOPERMISSIONS;
            }catch (InterruptedException e){
                Log.e(LOG_TAG, e.toString());
            }
        }else{
            Log.i(LOG_TAG, "Can't save replay");
            Toast.makeText(mainContext.getApplicationContext(), mainContext.getResources().getString(R.string.noReplay), Toast.LENGTH_SHORT).show();
        }
    }

    /* Stops clip feature, until stopVideoReplaySaving is called */
    @Override
    public boolean startVideoReplaySaving() {
        if(running && isRecording){
            Log.i(LOG_TAG, "Video replay saving starting");
            if (!isSaving) {
                recieve = false;
                isSaving = true;
                try{
                    timer.cancel();
                    timer.purge();
                }catch (Exception e){

                }
                Toast.makeText(mainContext.getApplicationContext(), mainContext.getResources().getString(R.string.recordingStart), Toast.LENGTH_SHORT).show();
                return true;
            } else {
                Log.i(LOG_TAG, "Already recording video");
                return false;
            }
        }else{
            Log.i(LOG_TAG, "Video recording has not started");
            Toast.makeText(mainContext.getApplicationContext(), mainContext.getResources().getString(R.string.recordingStarted), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /* Ends startVideoReplaySaving and marks it for upload */
    @Override
    public boolean stopVideoReplaySaving(){
        if(running){
            if(isSaving){
                Log.i(LOG_TAG, "Video replay saving ending");
                try{
                    Thread.sleep(MainActivity.VIDEODELAYPOSTSTOP);
                }catch (Exception e){

                }
                try{
                    Log.d(LOG_TAG, "Saving video recording");
                    ffTask.sendQuitSignal();
                    while(!ffTask.isProcessCompleted()){
                        Log.d(LOG_TAG, "Waiting for task to end");
                        Thread.sleep(100);
                    }
                    Log.d(LOG_TAG, "Task ended");
                    storageHandler.saveFile();
                    isSaving = false;
                    recieve = true;
                    isRecording = false;
                    Log.d(LOG_TAG, "Restarting recording");
                    Toast.makeText(mainContext.getApplicationContext(), mainContext.getResources().getString(R.string.recordingEnd), Toast.LENGTH_SHORT).show();
                    startNextFile();
                    return true;
                }catch (NoPermissionException e){
                    Log.e(LOG_TAG, "No permissions");
                    err = ERR_NOPERMISSIONS;
                    return false;
                }catch (InterruptedException e){
                    Log.e(LOG_TAG, e.toString());
                    return false;
                }
            }else{
                Log.i(LOG_TAG, "Video replay saving didn't start");
                return false;
            }
        }else{
            Log.i(LOG_TAG, "Video recording has not started");
            Toast.makeText(mainContext.getApplicationContext(), mainContext.getResources().getString(R.string.recordingNoStart), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    public String getRecorderName() {
        return cameraName;
    }

    @Override
    public String getRecorderResource() {
        return RTSP_URL;
    }

    @Override
    public void setRecorderName(String newName) {
        if(newName != null && newName != ""){
            this.cameraName = newName;
            this.storageHandler.setName(newName);
        }
    }

    public String getIP(){
        return this.IP;
    }

    public int getPort(){
        return this.port;
    }

    public String toString(){
        return cameraName + ":" + IP + ":" + port;
    }

    public boolean isRunning(){
        return this.running;
    }

    @Override
    public boolean isEnabled(){
        return this.recieve;
    }
}