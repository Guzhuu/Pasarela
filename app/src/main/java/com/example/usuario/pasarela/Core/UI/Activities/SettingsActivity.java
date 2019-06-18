package com.example.usuario.pasarela.Core.UI.Activities;

import android.app.Activity;
import android.os.Bundle;

import com.example.usuario.pasarela.Core.UI.Fragments.SettingsFragment;

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new SettingsFragment())
                .commit();
    }
}
