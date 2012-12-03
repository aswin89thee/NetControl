package com.Android.NetControl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

public class InitialActivity extends Activity {
    /** Called when the activity is first created. */
    @SuppressWarnings("deprecation")
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        Process command;
//        try {
//        	command = Runtime.getRuntime().exec("su");
//        } catch (Exception e) {
//        	Log.d("InitialActivity:onCreate",e.getMessage());
//        }
        
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle("Starting");
        alertDialog.setMessage("Starting the service.");
        alertDialog.setButton("OK", new DialogInterface.OnClickListener() {
           public void onClick(DialogInterface dialog, int which) {
        	   // here you can add functions
        	   Log.d("InitialActiviy", "About to start service ");
         	   //Start the service
         	   startMyService();
         	   Log.d("InitialActiviy", "Service started successfully!");
         	   finish();
           }
        });
        alertDialog.show();
        
    }
    
    	//Start the main background service. Call the native code only from inside the service.
    private void startMyService()
    {
    	TextView stateText = (TextView) findViewById(R.id.serviceState);
    	//Start the service
        Intent myIntent = new Intent(getApplicationContext(), NetService.class);
        myIntent.putExtra("extraData", "somedata");
        startService(myIntent);
        stateText.setText("Service Started successfully! You may exit the app now.");
    }
    
}
