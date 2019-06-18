package com.example.usuario.pasarela.Core.Uploader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.util.Log;

public class NetworkListener extends BroadcastReceiver {
    private static FileUploader uploader = FileUploader.getInstance();
    private static final String LOG_TAG = "NetworkListener";

    public NetworkListener(){
        Log.d(LOG_TAG, "Network listener created");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        // TODO Auto-generated method stub
        String action = intent.getAction();
        Log.d(LOG_TAG, "New connection recieved: " + action);
        if(action != null && action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)){
            uploader.update();
        }
    }
}
