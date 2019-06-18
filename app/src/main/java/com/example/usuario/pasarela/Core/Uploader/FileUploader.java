package com.example.usuario.pasarela.Core.Uploader;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import com.example.usuario.pasarela.Core.DB.StorageHandler;
import com.example.usuario.pasarela.Core.UI.Activities.MainActivity;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import packet.Packet;

// Singleton, only one thread of this class is possible at the same time
public class FileUploader implements Runnable, Observer {
    private static final String LOG_TAG = "FileUploader";
    private static int timeout = 10000;
    public static final int DEFAULT_PACKET_SIZE = 8192;

    private static FileUploader singleton;
    private static boolean running;
    private static Context context;

    private static String publicIP;
    private static int publicPort;

    private static String localIP;
    private static int localPort;

    private final Object notifier = new Object();
    private ConnectivityManager cmanager;
    private boolean send;

    private FileUploader(){
        running = false;
        cmanager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
    }

    public static FileUploader getInstance(){
        if(singleton == null && context != null){
            singleton = new FileUploader();
        }
        if(!running){
            Log.d(LOG_TAG, "FileUploader started");
            new Thread(singleton).start();
        }
        return singleton;
    }

    public static void setMainContext(Context ctx){
        if(context == null){
            context = ctx;
        }
    }


    @Override
    public void run() {
        try {
            if (!running) {
                try {
                    running = true;
                    while (true) {
                        synchronized (notifier) {
                            try {
                                notifier.wait();
                            } catch (InterruptedException e) {
                                Log.i(LOG_TAG, "Notifier interrupted: " + e.toString());
                            }
                        }
                        File[] newFiles;
                        try {
                            while ((newFiles = StorageHandler.getFilesToUpload()) != null) {
                                if (newFiles.length > 0 && newFiles[0] != null) {
                                    send = true;
                                    Log.i(LOG_TAG, "File nº1: " + newFiles[0].getName());
                                }
                                if (newFiles.length > 1 && newFiles[1] != null) {
                                    send = true;
                                    Log.i(LOG_TAG, "File nº2: " + newFiles[1].getName());
                                }
                                uploadFiles(newFiles);
                                Thread.sleep(timeout);
                            }
                            Log.i(LOG_TAG, "No more files to upload");
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "Error getting files: " + e.toString());
                        }
                    }
                }finally {
                    running = false;
                }
            }else{
                Log.w(LOG_TAG, "FileUploader already working");
            }
        }catch (Exception e){
            Log.e(LOG_TAG, "General error: " + e.toString());
        }
    }

    @Override
    public void update() {
        Log.d(LOG_TAG, "Trying to upload files");
        send = true;
        synchronized (notifier){
            notifier.notify();
        }
    }

    private boolean isNetworkMetered(){
        return cmanager.isActiveNetworkMetered();
    }

    private void uploadFiles(File[] newFiles){
        try{
            Log.d(LOG_TAG, "Trying to reach " + getLocalIP() + " and " +  getPublicIP());
            boolean local = false, publica = false;
            // Would be great to check if local ip is in range
            if(getLocalInetAddress() != null){
                Log.d(LOG_TAG, "Connecting to local IP");
                local = startFileUploading(newFiles, getLocalIP(), getLocalPort());
            }
            if(!local && getPublicInetAddress() != null && !getPublicInetAddress().equals(getLocalInetAddress()) && (!isNetworkMetered() || MainActivity.uploadWithData)){
                Log.d(LOG_TAG, "Connecting to public IP");
                publica = startFileUploading(newFiles, getPublicIP(), getPublicPort());
            }
            if(!local && !publica){
                Log.i(LOG_TAG, "Can't upload files");
            }
        }catch (Exception e){
            Log.e(LOG_TAG, e.toString());
        }
    }

    private boolean startFileUploading(File[] files, String IP, int port){
        Socket socket = null;
        try{
            socket = new Socket(IP, port);
            socket.setSoTimeout(timeout);
            Log.d(LOG_TAG, "Post timeout");
            boolean file0 = false, file1 = false;
            //Upload first file
            if(files.length < 1) {
                Log.i(LOG_TAG, "First file is null");
                file0 = true;
            }else if(files[0] == null){
                Log.i(LOG_TAG, "First file is null");
                file0 = true;
            }else if(!files[0].isDirectory() && files[0].exists()){
                Log.d(LOG_TAG, "Uploading file " + files[0].getName() + " with " + files[0].length() + " bytes");
                file0 = uploadFile(files[0], socket);
            }
            //Upload second file
            if(files.length < 2) {
                Log.i(LOG_TAG, "Second file is null");
                file1 = true;
            }else if(files[1] == null){
                Log.i(LOG_TAG, "Second file is null");
                file1 = true;
            }else if(!files[1].isDirectory() && files[1].exists()){
                Log.d(LOG_TAG, "Uploading file " + files[1].getName() + " with " + files[1].length() + " bytes");
                file1 = uploadFile(files[1], socket);
            }
            if(file0 && file1){
                try{
                    if(StorageHandler.markFilesAsUploaded(files)){
                        return true;
                    }else{
                        Log.w(LOG_TAG, "Files uploaded but not deleted");
                        return true;
                    }
                }catch (Exception e){
                    Log.e(LOG_TAG, "Fail when setting files as uploaded: " + e.toString());
                    try{
                        Log.w(LOG_TAG, "Trying to delete files manually");
                        files[0].delete();
                        files[1].delete();
                    }catch (Exception e2){
                        Log.e(LOG_TAG, "Big error, files might have already been deleted: " + e2.toString());
                    }
                }
            }
            socket.close();
            return false;
        }catch (IOException e){
            Log.e(LOG_TAG, "Socket related in startFileUploading: " + e.toString());
        }catch (Exception e){
            Log.e(LOG_TAG, "Exception for " + IP + ":" + port);
        }finally {
            if(socket != null){
                try{
                    socket.close();
                }catch (IOException e){
                    Log.e(LOG_TAG, "Error closing socket: " + e.toString());
                }
            }
        }
        return false;
    }

    /** Tres pasos:
    **  - Mensaje de petición de config de conexión con el flag a true
     *  - Pillar configuración recibida
     *  - Enviar bytes hasta fin de archivo con config recibida
     */
    private boolean uploadFile(File file, Socket socket){
        try{
            RandomAccessFile fileRandom = new RandomAccessFile(file, "r");
            boolean configGood = false;
            ObjectOutputStream oos;
            ObjectInputStream ois;
            Packet recieve;
            Object aux;
            int configPieceNum = 0;
            int configPieceTotal = 0;
            int packetSize = FileUploader.DEFAULT_PACKET_SIZE;
            while(!configGood) {
                // Sends request configuration for file.getName()
                oos = new ObjectOutputStream(socket.getOutputStream());
                oos.writeObject(new Packet(file.getName()));
                //Recieves configuration for file.getName()
                try{
                    ois = new ObjectInputStream(socket.getInputStream());
                    aux = ois.readObject();
                }catch(SocketTimeoutException e){
                    Log.e(LOG_TAG, "Socket timed out while receiving configuration: " + e.toString());
                    return false;
                }
                // If the packet is a Packet and config flag is one
                if(aux instanceof Packet){
                    recieve = (Packet) aux;
                    Log.d(LOG_TAG, "Recieved " + recieve.toString());
                    if(recieve.flagConnectionConfig && recieve.fileName.equals(file.getName())){
                        configPieceNum = recieve.numPiece;
                        packetSize = recieve.data.length;
                        configPieceTotal = (int) Math.ceil(file.length() / packetSize);
                        if(configPieceNum < configPieceTotal){
                            configGood = true;
                            if(packetSize <= 0){
                                Log.w(LOG_TAG, "Negative packet size: " + packetSize);
                                packetSize = FileUploader.DEFAULT_PACKET_SIZE;
                            }
                            Log.w(LOG_TAG,
                                    "\n\t-Num size: " + (configPieceNum * packetSize) +
                                    "\n\t-Total size: " + ((configPieceTotal - 1) * packetSize + file.length() - ((configPieceTotal - 1) * packetSize)) +
                                    "\n\t-File size: " + file.length());
                        }else{
                            Log.w(LOG_TAG, "File has already been sent");
                            return true;
                        }
                    }else{
                        Log.w(LOG_TAG, "Trying to configure different file: " + recieve.fileName + " vs " + file.getName());
                        return false;
                    }
                }else{
                    Log.w(LOG_TAG, "Recieved object is not a Packet");
                    return false;
                }
            }

            //Seek to indicated by configuration
            fileRandom.seek(configPieceNum*packetSize);
            byte[] bytes = new byte[packetSize];
            int n;
            while((n = fileRandom.read(bytes, 0, bytes.length)) >= 0 && send){
                Log.d(LOG_TAG, "Reading and sending " + n + " bytes, " + (configPieceNum) + "/" + configPieceTotal + " pieces");
                oos = new ObjectOutputStream(socket.getOutputStream());
                // Dangerous 1 offset configPieceNum vs configPieceTotal
                oos.writeObject(new Packet(file.getName(), bytes, configPieceNum, configPieceTotal));
                try{
                    ois = new ObjectInputStream(socket.getInputStream());
                    aux = ois.readObject();
                    if(aux instanceof Packet){
                        recieve = (Packet) aux;
                        Log.d(LOG_TAG, "Recieved " + recieve.flagOK);
                        if(!recieve.flagOK){
                            // If we recieve a not ok packet, file should have not been sent, this happens
                            // if the file has already been sent and piece is 0, or other problem occured
                            Log.e(LOG_TAG, "Big error sending file");
                            // fileRandom.close();
                            if(configPieceNum == 0){
                                // If file has already been sent, true is returned as if it has already
                                // been uploaded
                                return true;
                            }
                            return false;
                        }
                        configPieceNum++;
                    }
                }catch(SocketTimeoutException e){
                    Log.e(LOG_TAG, "Socked timed out while receiving data");
                    send = false;
                    break;
                }
            }
            Log.i(LOG_TAG, "Sending finished, " + configPieceNum + "/" + configPieceTotal);
            if(!send){
                Log.e(LOG_TAG, "Sending stopped at " + configPieceNum + "/" + configPieceTotal);
                return configPieceNum >= configPieceTotal;
            }
            return true;
        }catch (IOException e){
            Log.e(LOG_TAG, "Socket related: " + e.toString());
        }catch (ClassNotFoundException e){
            Log.e(LOG_TAG, "No class found: " + e.toString());
        }catch (Exception e){
            Log.e(LOG_TAG, "Unhandled: " + e.toString());
        }
        return false;
    }


    public static void setPublicIP(String IP){
        if(localIP == null || localIP.equals("")){
            localIP = IP;
        }
        publicIP = IP;
    }

    private InetAddress getPublicInetAddress(){
        try{
            return InetAddress.getByName(publicIP);
        }catch (Exception e){
            Log.e(LOG_TAG, "Public IP: " + e.toString());
            return null;
        }
    }

    public static void setLocalIP(String IP){
        if(publicIP == null || publicIP.equals("")){
            publicIP = IP;
        }
        localIP = IP;
    }

    private InetAddress getLocalInetAddress(){
        try{
            return InetAddress.getByName(localIP);
        }catch (Exception e){
            Log.e(LOG_TAG, "Private IP: " + e.toString());
            return null;
        }
    }

    public static void setPublicPort(int port){
        if(localPort == 0){
            localPort = port;
        }
        publicPort = port;
    }

    public static void setLocalPort(int port){
        if(publicPort == 0){
            publicPort = port;
        }
        localPort = port;
    }

    public static boolean isRunning() {
        return running;
    }

    public static String getPublicIP() {
        return publicIP;
    }

    public static int getPublicPort() {
        return publicPort;
    }

    public static String getLocalIP() {
        return localIP;
    }

    public static int getLocalPort() {
        return localPort;
    }
}
