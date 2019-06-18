package com.example.usuario.pasarela.Core.Recorders;

public class CameraRecorder implements Recorder {
    private static final String LOG_TAG = "CameraRecorder";

    private String cameraName;
    private int cameraID;

    public CameraRecorder(String cameraName, int cameraID){
        this.cameraName = cameraName;
        this.cameraID = cameraID;
    }

    @Override
    public boolean resumeRecording() {
        return false;
    }

    @Override
    public boolean stopRecording() {
        return false;
    }

    @Override
    public void saveVideoReplay() {
    }

    @Override
    public boolean startVideoReplaySaving() {
        return false;
    }

    @Override
    public boolean stopVideoReplaySaving() {
        return false;
    }

    @Override
    public String getRecorderName() {
        return cameraName;
    }

    public int getCameraID(){
        return this.cameraID;
    }

    @Override
    public String getRecorderResource() {
        return "Camera";
    }

    @Override
    public void setRecorderName(String newName) {
        this.cameraName = newName;
    }

    public String toString(){
        return cameraName + ":" + cameraID;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
