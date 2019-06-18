package com.example.usuario.pasarela.Core.UI.Adapters;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.usuario.pasarela.Core.Recorders.CameraRecorder;
import com.example.usuario.pasarela.Core.Recorders.Recorder;
import com.example.usuario.pasarela.Core.Recorders.VideoRecorder;
import com.example.usuario.pasarela.Core.UI.Activities.MainActivity;
import com.example.usuario.pasarela.R;

import java.util.List;

public class CameraAdapter extends ArrayAdapter<Recorder> {
    public static final String LOG_TAG = "CameraAdapter";
    private MainActivity ctx;

    public CameraAdapter(Context context, List<Recorder> objects, MainActivity mainContext) {
        super(context, 0, objects);
        this.ctx = mainContext;
    }

    private void changeBorderAccordingly(View view, Recorder camera){
        if(!camera.isEnabled()){
            GradientDrawable borde = (GradientDrawable) view.getBackground();
            borde.setStroke(3, Color.parseColor("#880000")); //red
        }else{
            GradientDrawable borde = (GradientDrawable) view.getBackground();
            borde.setStroke(3, Color.parseColor("#008800")); //green
        }
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent)
    {
        final LayoutInflater layoutInflater = LayoutInflater.from(this.getContext());
        final Recorder camera = this.getItem(position);

        if (convertView == null) {
            convertView = layoutInflater.inflate( R.layout.list_camera, null );
        }

        final TextView cameraName = convertView.findViewById(R.id.tvCameraName);
        cameraName.setText(camera.getRecorderName());
        final TextView resourceSource = convertView.findViewById(R.id.tvResourceSource);
        resourceSource.setText(camera.getRecorderResource());

        final ImageButton ibOnOff = convertView.findViewById(R.id.ibOnOff);
        ImageButton ibWatch = convertView.findViewById(R.id.ibWatch);

        final ImageButton ibSaveReplay = convertView.findViewById(R.id.ibSaveReplay);
        final ImageButton ibStartRecording = convertView.findViewById(R.id.ibStartRecording);
        final ImageButton ibStopRecording = convertView.findViewById(R.id.ibStopRecording);
        final ImageButton ibRemove = convertView.findViewById(R.id.ibRemove);

        changeBorderAccordingly(convertView, camera);

        final View finalConvertView = convertView;
        ibOnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(camera.isEnabled()){
                    if(camera.stopRecording()){
                        changeBorderAccordingly(finalConvertView, camera);
                    }
                }else{
                    if(camera.resumeRecording()){
                        changeBorderAccordingly(finalConvertView, camera);
                    }
                }
            }
        });

        ibWatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //TODO: Invoke activity_rtsplive
            }
        });

        ibRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder dlg = new AlertDialog.Builder(getContext());
                dlg.setTitle(ctx.getResources().getString(R.string.removeCamera));
                dlg.setMessage(ctx.getResources().getString(R.string.msgRemove));
                dlg.setNeutralButton(ctx.getResources().getString(R.string.cancel), null);
                dlg.setPositiveButton(ctx.getResources().getString(R.string.remove), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ctx.removeCamera(camera);
                    }
                });
                dlg.create().show();
            }
        });

        ibSaveReplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera.saveVideoReplay();
            }
        });

        ibStartRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            if(camera.startVideoReplaySaving()){
                ibStartRecording.setClickable(false);
                ibStartRecording.setVisibility(View.INVISIBLE);
                ibStopRecording.setClickable(true);
                ibStopRecording.setVisibility(View.VISIBLE);
            }
            }
        });

        ibStopRecording.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            if(camera.stopVideoReplaySaving()){
                ibStopRecording.setClickable(false);
                ibStopRecording.setVisibility(View.INVISIBLE);
                ibStartRecording.setClickable(true);
                ibStartRecording.setVisibility(View.VISIBLE);
            }
            }
        });

        cameraName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                AlertDialog.Builder dlg = new AlertDialog.Builder(getContext());
                dlg.setTitle(ctx.getResources().getString(R.string.changeName));
                final EditText etCameraName = new EditText(ctx);
                etCameraName.setText(camera.getRecorderName());
                dlg.setView(etCameraName);
                dlg.setPositiveButton(ctx.getResources().getString(R.string.edit), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = etCameraName.getText().toString();
                        if(name.length() > 3 && name.trim().length() != 0){
                            camera.setRecorderName(etCameraName.getText().toString());
                        }else{
                            Toast.makeText(ctx, ctx.getResources().getString(R.string.charCamera), Toast.LENGTH_LONG).show();
                        }
                    }
                });
                dlg.setNeutralButton(ctx.getResources().getString(R.string.cancel), null);
                dlg.create().show();
                return true;
            }
        });

        resourceSource.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                AlertDialog.Builder dlg = new AlertDialog.Builder(getContext());
                dlg.setTitle(ctx.getResources().getString(R.string.changeSource));
                final EditText etIP = new EditText(ctx);
                final EditText etPort = new EditText(ctx);
                final EditText etCameraID = new EditText(ctx);
                if(camera instanceof VideoRecorder){
                    final LinearLayout llView = new LinearLayout(ctx);
                    etIP.setText(((VideoRecorder) camera).getIP());
                    etPort.setText(Integer.toString(((VideoRecorder) camera).getPort()));
                    llView.setOrientation(LinearLayout.VERTICAL);
                    llView.addView(etIP);
                    llView.addView(etPort);
                    dlg.setView(llView);
                }else if(camera instanceof CameraRecorder){
                    etCameraID.setText(((CameraRecorder) camera).getCameraID());
                    dlg.setView(etCameraID);
                }
                dlg.setPositiveButton(ctx.getResources().getString(R.string.edit), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if(camera instanceof VideoRecorder){
                            String ip = ((VideoRecorder) camera).getIP();
                            int port = ((VideoRecorder) camera).getPort();
                            if(Patterns.IP_ADDRESS.matcher(etIP.getText().toString()).matches()){
                                ip = etIP.getText().toString();
                            }else{
                                Toast.makeText(ctx, ctx.getResources().getString(R.string.invalidIP), Toast.LENGTH_LONG).show();
                            }

                            try{
                                if(Integer.parseInt(etPort.getText().toString()) >= 0 && Integer.parseInt(etPort.getText().toString()) < 65536){
                                    port = Integer.parseInt(etPort.getText().toString());
                                }
                            }catch (Exception e){
                                Toast.makeText(ctx, ctx.getResources().getString(R.string.invalidPort), Toast.LENGTH_LONG).show();
                            }

                            ctx.addCamera(new VideoRecorder(camera.getRecorderName(), ip, port, ctx), camera.isEnabled());
                            ctx.removeCamera(camera);
                        }else if(camera instanceof CameraRecorder){
                            int cameraID = ((CameraRecorder) camera).getCameraID();
                            try{
                                cameraID = Integer.parseInt(etCameraID.getText().toString());
                            }catch (Exception e){
                                Toast.makeText(ctx, ctx.getResources().getString(R.string.invalidPort), Toast.LENGTH_LONG).show();
                            }
                            try{
                                ctx.addCamera(new CameraRecorder(camera.getRecorderName(), cameraID), camera.isEnabled());
                                ctx.removeCamera(camera);
                            }catch (Exception e){
                                Toast.makeText(ctx, ctx.getResources().getString(R.string.invalidPort), Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                });
                dlg.setNeutralButton(ctx.getResources().getString(R.string.cancel), null);
                dlg.create().show();
                return true;
            }
        });


        return convertView;
    }
}
