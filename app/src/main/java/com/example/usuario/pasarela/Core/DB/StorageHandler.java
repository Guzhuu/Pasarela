package com.example.usuario.pasarela.Core.DB;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.example.usuario.pasarela.Core.Uploader.FileUploader;
import com.example.usuario.pasarela.Core.Uploader.Observer;
import com.example.usuario.pasarela.Exceptions.NoPermissionException;

import java.io.File;
import java.util.Arrays;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * This class manages the files that will work with the app:
 *  - Returns writtable files for VideoRecorder class
 *  - Saves files that will have to be uploaded
 *  - Manages files that will be uploaded
 *      So if VideoRecorder asks for a pair of Files to write the camera's recording, and the user
 *      saves the video, VideoRecorder will indicate StorageHandler to save those videos for recording.
 *      Then when other activity wants to know what to upload, StorageHandler will provide file names.
 * */
public class StorageHandler{
    private static final String LOG_TAG = "StorageHandler";
    private static final String DIRECTORY_IN_PROGRESS = ".in-progress";
    private static final String DIRECTORY_DONE = ".done";

    private static Context context;
    // Directory list containing files to upload
    private static List<File> videosToUpload;
    private static File defaultDirectory;
    private static Observer observer = FileUploader.getInstance();

    private String name;
    private boolean switcher;

    public StorageHandler(String name){
        Log.d(LOG_TAG, "Creating storage handler for " + name);
        this.name = name;
    }

    /*  Returns the name for the video to save*/
    private String generateDefaultName(){
        switcher = !switcher;
        return generateDefaultName(name, switcher);
    }

    /*  Returns the defaultName a file would have with the specified cameraName and switcher value */
    private static String generateDefaultName(String cameraName, boolean switcher){
        return cameraName + "-" + switcher + ".mp4";
    }

    // ../DIRECTORY
    private static File getDefaultDirectory() throws NoPermissionException{
        if(!isExternalStorageWritable()){
            throw new NoPermissionException("No permission to access storage");
        }
        if(isExternalStorageReadable() && context != null){
            File[] avaliableStorages = context.getExternalFilesDirs(null);
            if(avaliableStorages.length <= 0){
                Log.e(LOG_TAG, "No storage available");
                return null;
            }
            if(defaultDirectory == null || Arrays.binarySearch(avaliableStorages, defaultDirectory) < 0){
                Log.d(LOG_TAG, "Setting directory to " + avaliableStorages[avaliableStorages.length - 1].getAbsolutePath());
                defaultDirectory = avaliableStorages[avaliableStorages.length - 1];
            }
            Log.d(LOG_TAG, "Default directory: " + defaultDirectory.getAbsolutePath());
            return defaultDirectory;
        }else{
            return null;
        }
    }

    // ../Movies/.RecordedVideos/.in-progress
    private static File getDefaultInProgressDirectory() throws NoPermissionException{
        if(!isExternalStorageWritable()){
            throw new NoPermissionException("No permission to access storage");
        }
        File defaultDirectory;
        if((defaultDirectory = getDefaultDirectory()) != null){
            return new File(defaultDirectory + File.separator + DIRECTORY_IN_PROGRESS);
        }else{
            return null;
        }
    }

    // ../Movies/.RecordedVideos/.in-progress/cameraName
    private static File getInProgressDirectoryFor(String cameraName) throws NoPermissionException{
        if(!isExternalStorageWritable()){
            throw new NoPermissionException("No permission to access storage");
        }
        File retorno;
        boolean done = false;
        if((retorno = getDefaultInProgressDirectory()) != null){
            retorno = new File(retorno + File.separator + cameraName);
            if(!retorno.exists()){
                done = retorno.mkdirs();
                Log.i(LOG_TAG, "Creating in-progress directory for " + cameraName + ": " + retorno.getAbsolutePath() + " -> " + (done ? "successfully" : "failed"));
            }else{
                done = true;
                Log.d(LOG_TAG, "Directory " + retorno.getAbsolutePath() + " already created");
            }
        }
        return done ? retorno : null;
    }

    // ../Movies/.RecordedVideos/.in-progress/this.name
    private File getInProgressDirectory() throws NoPermissionException{
        if(!isExternalStorageWritable()){
            throw new NoPermissionException("No permission to access storage");
        }
        return getInProgressDirectoryFor(this.name);
    }

    // ../Movies/.RecordedVideos/.done
    private static File getDefaultDoneDirectory() throws NoPermissionException{
        if(!isExternalStorageWritable()){
            throw new NoPermissionException("No permission to access storage");
        }
        File defaultDirectory;
        if((defaultDirectory = getDefaultDirectory()) != null){
            return new File(defaultDirectory + File.separator + DIRECTORY_DONE);
        }else{
            return null;
        }
    }

    private static File getDoneDirectoryFor(String cameraName) throws NoPermissionException{
        if(!isExternalStorageWritable()){
            throw new NoPermissionException("No permission to access storage");
        }
        File retorno;
        boolean done = false;
        if((retorno = getDefaultDoneDirectory()) != null){
            Calendar now = Calendar.getInstance();
            // .RecordedVideos/.done/cameraNameDD-MM-YYYY HH_mm_ss
            String year = Integer.toString(now.get(Calendar.YEAR));
            String month = now.get(Calendar.MONTH) + 1 < 10 ? "0" + (now.get(Calendar.MONTH)+1) : Integer.toString(now.get(Calendar.MONTH) + 1);
            String day = now.get(Calendar.DAY_OF_MONTH) < 10 ? "0" + now.get(Calendar.DAY_OF_MONTH) : Integer.toString(now.get(Calendar.DAY_OF_MONTH));
            String hour = now.get(Calendar.HOUR_OF_DAY) < 10 ? "0" + now.get(Calendar.HOUR_OF_DAY) : Integer.toString(now.get(Calendar.HOUR_OF_DAY));
            String minute = now.get(Calendar.MINUTE) < 10 ? "0" + now.get(Calendar.MINUTE) : Integer.toString(now.get(Calendar.MINUTE));
            String second = now.get(Calendar.SECOND) < 10 ? "0" + now.get(Calendar.SECOND) : Integer.toString(now.get(Calendar.SECOND));
            retorno = new File(retorno + File.separator + (cameraName + "--" + year + "-" + month + "-" + day + "_" + hour + "-" + minute + "-" + second));
            if(!retorno.exists()){
                done = retorno.mkdirs();
                Log.i(LOG_TAG, "Creating done directory for " + cameraName + ": " + retorno.getAbsolutePath() + " -> " + (done ? "successfully" : "failed"));
            }else{
                done = true;
                Log.d(LOG_TAG, "Directory " + retorno.getAbsolutePath() + " already created");
            }
        }
        return done ? retorno : null;
    }

    private File getDoneDirectory() throws NoPermissionException{
        if(!isExternalStorageWritable()){
            throw new NoPermissionException("No permission to access storage");
        }
        return getDoneDirectoryFor(this.name);
    }

    /* Returns a File. If the file is going to be saved, call saveFile() before getting a new one */
    public File getFile() throws NoPermissionException {
        try{
            return  getFileWithName(generateDefaultName(), name);
        }catch (NoPermissionException e){
            throw new NoPermissionException(e.getMessage());
        }
    }

    /* Returns a File to write to from the in-progress directory with the specified cameraName */
    private static File getFileWithName(String fileName, String cameraName) throws NoPermissionException{
        File directory;
        if(isExternalStorageWritable() && (directory = getInProgressDirectoryFor(cameraName)) != null) {
            return new File(directory, fileName);
        }else{
            throw new NoPermissionException("Can't access external storage");
        }
    }

    /* Gets a pair of files to be uploaded. Can be any camera. Returns null if there are no videos to be uploaded */
    public static File[] getFilesToUpload() throws NoPermissionException{
        if(!isExternalStorageWritable()){
            throw new NoPermissionException("No permission to access storage");
        }
        if(!videosToUpload.isEmpty()){
            File[] files;
            int index;
            try{
                index = 0;
                File dir = null;
                for(index = 0; index < videosToUpload.size(); index++){
                   dir = videosToUpload.get(index);
                   if(dir.exists() && dir.isDirectory() && dir.canRead()){
                       break;
                   }else{
                       Log.d(LOG_TAG, "Invalid directory " + dir.getAbsolutePath());
                   }
                }
                if(index >= videosToUpload.size() || dir == null){
                    Log.w(LOG_TAG, "No files available to upload " + videosToUpload.toString());
                    return null;
                }
                files = dir.listFiles();
            }catch (Exception e){
                Log.w(LOG_TAG, "Directory " + videosToUpload.get(0).getName() + " failed: " + e.toString());
                return null;
            }
            if(files != null && files.length > 0 && files.length <= 2){
                Log.d(LOG_TAG, "Sending " + files.length + " files");
                return files;
            }else{
                Log.w(LOG_TAG, "More files than expected in done directory or unexistent " + videosToUpload.get(index));
                for(int i = 0; i < files.length; i++){
                    Log.d(LOG_TAG, "Deleting " + files[i].getAbsolutePath() + ":" + files[i].delete());
                }
                boolean deleted = false;
                Log.d(LOG_TAG, "Deleting directory " + ((deleted = videosToUpload.get(0).delete()) ? "success" : "failed"));
                if(deleted){
                    videosToUpload.remove(0);
                }
                return null;
            }
        }else{
            Log.d(LOG_TAG, "No videos to upload");
        }
        return null;
    }

    /* Marks a pair of files as uploaded and to be deleted. */
    public static boolean markFilesAsUploaded(File[] files) throws NoPermissionException {
        if(!isExternalStorageWritable()){
            throw new NoPermissionException("Can't access external storage");
        }
        boolean abort = false;
        String parentDirectory = "";
        Log.d(LOG_TAG, "Trying to remove files " + Arrays.toString(files));
        for(int i = 0; i < files.length; i++){
            if(!files[i].isDirectory() && files[i].exists()){
                if(parentDirectory.equals("")){
                    parentDirectory = files[i].getParent();
                }else if(!parentDirectory.equals(files[i].getParent())){
                    Log.d(LOG_TAG, "Files were different: " + parentDirectory + " vs " + files[i].getParent());
                    abort = true;
                    break;
                }
            }else{
                Log.d(LOG_TAG, "File was a directory or didnt exist " + files[i].getAbsolutePath());
                abort = true;
                break;
            }
        }
        if(!abort) {
            for(int i = 0; i < files.length; i++){
                Log.i(LOG_TAG, "Deleting " + files[i].getName() + ": " + (files[i].delete() ? "success" : "failed"));
            }
        }
        Log.d(LOG_TAG, "Finished removing files from " + parentDirectory + ", should be in videosToUpload (" + (videosToUpload.contains(parentDirectory)) + ")");
        for(File f : videosToUpload){
            if(f.getPath().trim().equals(parentDirectory.trim())){
                Log.d(LOG_TAG, "Removing directory " + parentDirectory);
                new File(parentDirectory).delete();
                videosToUpload.remove(f);
                return true;
            }
        }
        return !abort;
    }

    public void clearProgressFiles() throws NoPermissionException {
        if (!isExternalStorageWritable()) {
            throw new NoPermissionException("Can't access external storage");
        }
        File inProgressDir = getInProgressDirectory();
        File[] files;

        if(inProgressDir != null && (files = inProgressDir.listFiles()) != null){
            for(int i = 0; i < files.length; i++){
                Log.d(LOG_TAG, "Clearing file " + files[i].getAbsolutePath());
                files[i].delete();
            }
        }
    }

    /* Marks current file to be saved and uploaded, which will move them to another directory */
    public boolean saveFile() throws NoPermissionException {
        if(!isExternalStorageWritable()){
            throw new NoPermissionException("Can't access external storage");
        }
        File inProgressDir = getInProgressDirectory();
        File doneNewDirectory = getDoneDirectory();
        if(inProgressDir != null && doneNewDirectory != null){
            File[] files = inProgressDir.listFiles();
            if(files.length > 0 && files.length <= 2){
                boolean done = false;
                for(int i = 0; i < files.length; i++){
                    done = files[i].renameTo(new File(doneNewDirectory, doneNewDirectory.getName() + "-" + i + ".mp4"));
                    Log.d(LOG_TAG, "Saving and renaming " + files[i].getAbsolutePath() + " to " + (doneNewDirectory.getName() + "-" + i + ".mp4") + ": " +
                            (done ? "success" : "failed"));
                    if(done && !videosToUpload.contains(doneNewDirectory)){
                        videosToUpload.add(doneNewDirectory);
                        Log.d(LOG_TAG, "Registered directory " + doneNewDirectory.getAbsolutePath() + " to upload");
                    }
                }
                notifyObserver();
                return true;
            }else{
                Log.d(LOG_TAG, "More files than expected");
                return false;
            }
        }else{
            Log.e(LOG_TAG, "Could not create new directories");
            return false;
        }
    }

    public static void setDirectory(String path){
        if(path != null){
            File dir = new File(path);
            if(dir.exists() && dir.isDirectory() && dir.canWrite()){
                Log.d(LOG_TAG, "Setting default directory: " + path);
                defaultDirectory = dir;
            }else{
                Log.d(LOG_TAG, "Invalid default directory");
                try{
                    getDefaultDirectory();
                }catch (Exception e){
                    Log.e(LOG_TAG, "Can't access storage: " + e.toString());
                }
            }
        }else{
            Log.d(LOG_TAG, "Invalid null directory");
            try{
                getDefaultDirectory();
            }catch (Exception e){
                Log.e(LOG_TAG, "Can't access storage: " + e.toString());
            }
        }
    }

    public static String getDirectory(){
        return defaultDirectory.getAbsolutePath();
    }

    public static void setContext(Context ctx){
        context = ctx;
    }

    public static void setVideosToUpload(List<File> newlist) throws NoPermissionException {
        if(!isExternalStorageWritable()){
            throw new NoPermissionException("Can't access external storage");
        }
        videosToUpload = new LinkedList<>();
        for(File f : newlist){
            if(f.getAbsolutePath().isEmpty()){
                Log.w(LOG_TAG, "Trying to put invalid file");
            }else{
                videosToUpload.add(f);
            }
        }
    }

    public static List<File> getVideosToUpload(){
        return videosToUpload;
    }

    private void notifyObserver(){
        observer.update();
    }

    public void setName(String name){
        this.name = name;
    }

    /* Check external storage writable */
    public static boolean isExternalStorageWritable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /* Check external storage readable */
    public static boolean isExternalStorageReadable() {
        return  Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(Environment.getExternalStorageState());
    }
}
