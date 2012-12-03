package com.Android.NetControl;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;

import org.xbill.DNS.DClass;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Message;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.ReverseMap;
import org.xbill.DNS.Section;
import org.xbill.DNS.Type;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;

public class DialogActivity extends Activity {

	String TAG = "DialogActivity";
	public static String packagename = "";
	public static String hostname = "";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		//setContentView(R.layout.activity_dialog);
		try{
			Log.d(TAG,"Inside DialogActivity");
			//Get the value of IP and UID passed when invoking this Activity
			final int uid = this.getIntent().getIntExtra("uid", 0);
			final String ip = this.getIntent().getStringExtra("ip");
			Log.d(TAG,"ip received from intent is "+ip+" and uid received from intent is "+uid+". Showing dialog");
			
			DialogActivity.packagename = this.getPackageNameFromUID(uid+"");
			
//			InetAddress ia = InetAddress.getByName(ip);
//			if(ia.getCanonicalHostName() != null)
//				DialogActivity.hostname = ia.getCanonicalHostName();
			
			DialogActivity.hostname = reverseDns(ip);
			if(DialogActivity.hostname == null)
				DialogActivity.hostname = ip;

			//Get input from the user from an Allow/Deny dialog
			DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) {
					try{
						switch (which){
						case DialogInterface.BUTTON_POSITIVE:
							//Allow button clicked
							Log.i(TAG, "User selected Allow");
							DialogActivity.addIPtoUID(ip, uid+"");
							NetService.responsevalue = 0;
							NetService.responseReady = true;
							finish();
							break;

						case DialogInterface.BUTTON_NEGATIVE:
							//Deny button clicked
							Log.i(TAG, "User selected Deny");
							DialogActivity.addIPtoUIDDeny(ip, uid+"");
							NetService.responsevalue = 1;
							NetService.responseReady = true;
							finish();
							break;
						}
					}catch(Exception e){
						Log.e(TAG,"Error in DialogActivity");
						e.printStackTrace();
					}
				}
			};
			
			DialogInterface.OnCancelListener onCancelListener = new DialogInterface.OnCancelListener() {
				
				public void onCancel(DialogInterface dialog) {
					// User pressed the back button. Assume it means Allow
					Log.i(TAG, "User selected Deny");
					DialogActivity.addIPtoUIDDeny(ip, uid+"");
					NetService.responsevalue = 0;
					NetService.responseReady = true;
					finish();
				}
			};

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setMessage("The app "+DialogActivity.packagename+" wants to access "+DialogActivity.hostname
					+" ("
					+ip+")"+". Do you want to allow?").setPositiveButton("Allow", dialogClickListener)
			.setNegativeButton("Deny", dialogClickListener).setOnCancelListener(onCancelListener).show();


		}catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private String reverseDns(String hostIp) throws IOException {
        Record opt = null;
        Resolver res = new ExtendedResolver();

        Name name = ReverseMap.fromAddress(hostIp);
        int type = Type.PTR;
        int dclass = DClass.IN;
        Record rec = Record.newRecord(name, type, dclass);
        Message query = Message.newQuery(rec);
        Message response = res.send(query);

        Record[] answers = response.getSectionArray(Section.ANSWER);
        if (answers.length == 0)
           return hostIp;
        else
           return answers[0].rdataToString();
  }
	
	
	//Print the package names associated with a UID
	private String getPackageNameFromUID(String uid)
	{
		String displaymsg = "";
		try{
			PackageManager pm = this.getPackageManager();
			String[] packages; 
			if( (packages = pm.getPackagesForUid(Integer.parseInt(uid))) != null)
				for(int i = 0 ; i < packages.length ; i++)
				{
					displaymsg += packages[i]+" ";
				}
			Log.d(TAG, "The app name is "+displaymsg);
		}
		catch(Exception e)
		{
			Log.e(TAG,"Error in printing package name: "+e.getMessage());
		}
//		DialogActivity.packagename = displaymsg;
		return displaymsg;
	}
	

	//Add a particular IP to a UID's list of allowed IP addresses to access. This is done by appending the IP to the uid file
	public static void addIPtoUID(String IP, String UID)
	{
		String directory = "/etc/domains/"+UID;
		File sdDir = Environment.getExternalStorageDirectory();
		String storagedir = sdDir.getAbsoluteFile()+directory;
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(storagedir, true)));
			out.println(IP);
			out.close();
		} catch (IOException e) {
			Log.e("InitialActivity",e.getMessage());
		}
	}
	
	//Add a particular IP to a UID's list of denied IP addresses to access. This is done by appending the IP to the uid file
	public static void addIPtoUIDDeny(String IP, String UID)
	{
		String directory = "/etc/domains/"+UID+"_deny";
		File sdDir = Environment.getExternalStorageDirectory();
		String storagedir = sdDir.getAbsoluteFile()+directory;
		try {
			PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(storagedir, true)));
			out.println(IP);
			out.close();
		} catch (IOException e) {
			Log.e("InitialActivity",e.getMessage());
		}
	}
	

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_dialog, menu);
		return true;
	}
}
