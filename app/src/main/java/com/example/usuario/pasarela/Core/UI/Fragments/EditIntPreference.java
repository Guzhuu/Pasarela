package com.example.usuario.pasarela.Core.UI.Fragments;

import android.content.Context;
import android.preference.EditTextPreference;
import android.util.AttributeSet;

public class EditIntPreference extends EditTextPreference {
    public EditIntPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public EditIntPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditIntPreference(Context context) {
        super(context);
    }


    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setInt(restoreValue ? getPersistedInt(0) : (Integer) defaultValue);
    }

    @Override
    protected String getPersistedString(String defaultReturnValue) {
        return String.valueOf(getPersistedInt(0));
    }

    @Override
    protected boolean persistString(String value) {
        return persistInt(Integer.valueOf(value));
    }

    public Integer getInt(){
        return Integer.valueOf(getText().trim());
    }

    public void setInt(int value){
        this.setText(String.valueOf(value));
    }
}
