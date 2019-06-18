package com.example.usuario.pasarela.Core.Recorders;

public interface Recorder {
    /* Resumes the recording */
    boolean resumeRecording();

    /* Stops the recording */
    boolean stopRecording();

    /* Saves a video replay to be uploaded */
    void saveVideoReplay();

    /* Stops clip feature, until stopVideoReplaySaving is called */
    boolean startVideoReplaySaving();

    /* Ends startVideoReplaySaving and marks it for upload */
    boolean stopVideoReplaySaving();

    String getRecorderName();
    String getRecorderResource();

    void setRecorderName(String newName);

    boolean isEnabled();
}
