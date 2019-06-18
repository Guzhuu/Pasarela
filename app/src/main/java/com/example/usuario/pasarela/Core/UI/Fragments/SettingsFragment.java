package com.example.usuario.pasarela.Core.UI.Fragments;

import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.StatFs;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.util.Patterns;

import com.example.usuario.pasarela.Core.DB.StorageHandler;
import com.example.usuario.pasarela.R;

import java.io.File;
import java.text.DecimalFormat;
import java.util.Arrays;

public class SettingsFragment extends PreferenceFragment implements Preference.OnPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String LOG_TAG = "SettingsFragment";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        configDefaultValues();
    }

    private void configDefaultValues(){
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());

        try{
            ListPreference directory = (ListPreference) findPreference(getResources().getString(R.string.key_directory));
            directory.setEntries(getAvaliableStoragesData());
            directory.setEntryValues(getAvaliableStoragesValues());
            directory.setOnPreferenceChangeListener(this);

            EditTextPreference publicip =  (EditTextPreference) findPreference(getResources().getString(R.string.key_publicip));
            publicip.setText(prefs.getString(getResources().getString(R.string.key_publicip), ""));
            publicip.setOnPreferenceChangeListener(this);
            prefs.registerOnSharedPreferenceChangeListener(this);
            EditIntPreference publicport =  (EditIntPreference) findPreference(getResources().getString(R.string.key_publicport));
            publicport.setInt(prefs.getInt(getResources().getString(R.string.key_publicport), 40406));
            publicport.setOnPreferenceChangeListener(this);
            publicport.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);

            EditTextPreference localip =  (EditTextPreference) findPreference(getResources().getString(R.string.key_localip));
            localip.setText(prefs.getString(getResources().getString(R.string.key_localip), "192.168.1.200"));
            localip.setOnPreferenceChangeListener(this);
            EditIntPreference localport =  (EditIntPreference) findPreference(getResources().getString(R.string.key_localport));
            localport.setInt(prefs.getInt(getResources().getString(R.string.key_localport), 40406));
            localport.setOnPreferenceChangeListener(this);
            localport.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        }catch (Exception e){
            Log.e(LOG_TAG, "Configuring settings: " + e.toString());
        }
    }

    private String[] getAvaliableStoragesValues() {
        File[] storages = getActivity().getApplicationContext().getExternalFilesDirs(null);
        String[] retorno = new String[storages.length];

        for(int i = 0; i < storages.length; i++){
            retorno[i] = storages[i].getAbsolutePath();
        }

        return retorno;
    }

    private String[] getAvaliableStoragesData() {
        File[] storages = getActivity().getApplicationContext().getExternalFilesDirs(null);
        String[] retorno = new String[storages.length];

        for(int i = 0; i < storages.length; i++){
            StatFs stats = new StatFs(storages[i].getAbsolutePath());
            retorno[i] = "Storage #" + i + " espacio libre: " + longToBytes(stats.getBlockSizeLong() * stats.getAvailableBlocksLong()) + "/" + longToBytes(stats.getBlockSizeLong() * stats.getBlockCountLong());
        }

        return retorno;
    }

    public static String longToBytes(long bytes){
        String[] suffix = {"B", "KB", "MB", "GB", "TB"};
        int counter = 0;
        float value = bytes;

        while(value > 1024){
            value = value/1024;
            counter++;
        }

        return new DecimalFormat("#.##"). format(value) + " " + (counter > suffix.length ? suffix[suffix.length - 1] : suffix[counter]);
    }

    public boolean isValidIPorHost(String str){
        Log.d(LOG_TAG, Patterns.IP_ADDRESS.matcher(str).matches() + " . " + Patterns.DOMAIN_NAME.matcher(str).matches());
        return (Patterns.IP_ADDRESS.matcher(str).matches() || Patterns.DOMAIN_NAME.matcher(str).matches());

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        // Returns whether the value should be saved, or manual save if we need an int or other
        Resources resources = getResources();
        String key = preference.getKey();
        //Ports
        if(key.equals(resources.getString(R.string.key_publicport)) || key.equals(resources.getString(R.string.key_localport))){
            try{
                int port = Integer.parseInt(newValue.toString());
                if(port > 0 && port < 65536){
                    Log.i(LOG_TAG, "Port is valid: " + newValue.toString());
                    return true;
                }else{
                    Log.i(LOG_TAG, "Port is not valid: " + newValue.toString());
                    return false;
                }
            }catch (Exception e){
                Log.i(LOG_TAG, "Value for port is not valid: " + e.toString());
                return false;
            }
        }else if(key.equals(resources.getString(R.string.key_publicip)) || key.equals(resources.getString(R.string.key_localip))){
            try{
                String IP = (String) newValue;
                if(isValidIPorHost(IP)){
                    Log.i(LOG_TAG, "IP is valid: " + newValue.toString());
                    return true;
                }else{
                    Log.i(LOG_TAG, "IP is not valid: " + newValue.toString());
                    return false;
                }
            }catch (Exception e){
                Log.i(LOG_TAG, "Value for IP is not valid: " + e.toString());
                return false;
            }
        }else if(key.equals(resources.getString(R.string.key_directory))){
            try{
                String dir = ((String) newValue).trim();
                File[] dirs = getActivity().getApplicationContext().getExternalFilesDirs(null);
                for(int i = 0; i < dirs.length; i++){
                    if(dirs[i].getAbsolutePath().equals(dir)){
                        Log.i(LOG_TAG, "Directory is valid " + dir);
                        StorageHandler.setDirectory(dir);
                        return true;
                    }
                }
                Log.i(LOG_TAG, "Directory is not valid " + dir + " must be in " + Arrays.toString(dirs));
                return false;
            }catch (Exception e){
                Log.i(LOG_TAG, "Value for directory is not valid: " + e.toString());
                return false;
            }
        }
        return false;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference pref = findPreference(key);
        if(pref instanceof EditIntPreference){
            Log.d(LOG_TAG, "Changing value of EditIntPreference");
            ((EditIntPreference) pref).setInt(sharedPreferences.getInt(key, 40406));
        }else if(pref instanceof EditTextPreference){
            Log.d(LOG_TAG, "Changing value of EditTextPreferences");
            ((EditTextPreference) pref).setText(sharedPreferences.getString(key, "40406"));
        }else if(pref instanceof CheckBoxPreference){
            Log.d(LOG_TAG, "Changing value of CheckBoxPreference");
            ((CheckBoxPreference) pref).setChecked(sharedPreferences.getBoolean(key, false));
        }
    }
}
