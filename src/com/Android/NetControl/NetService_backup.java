package com.Android.NetControl;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.Manifest;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

public class NetService extends Service {

	final String accessDomainDirectory = "/etc/domains/";
	String SOCKET_ADDRESS = "/test/socket/localServer";
	String TAG = "NetService";
	public static boolean responseReady = false;
	public static int responsevalue = 0;
	
	private List getUIDList()
	{
		List inetUIDList = new ArrayList();
		PackageManager pm = this.getPackageManager();
		List<PackageInfo> applist= pm.getInstalledPackages(0);
		Iterator<PackageInfo> it= applist.iterator();
		while (it.hasNext()){
			PackageInfo pk= (PackageInfo)it.next();

			if(PackageManager.PERMISSION_GRANTED==(pm.checkPermission(Manifest.permission.INTERNET, pk.packageName))) //checking if the package is having INTERNET permission
			{
				inetUIDList.add(""+pk.applicationInfo.uid);
			}

		}
		inetUIDList.add(0+"");

		return inetUIDList;
	}

	private void createFiles(List uidlist,String directory)
	{
		try{
			Iterator iter = uidlist.iterator();
			File sdDir = Environment.getExternalStorageDirectory();
			String storagedir = sdDir.getAbsoluteFile()+directory;
			File dir = new File(sdDir.getAbsoluteFile()+directory);
			if(!dir.exists())
			{
				if(!dir.mkdirs())
					Log.d("createFiles","Unable to Create the directory"+dir.getAbsolutePath());
			}
			while(iter.hasNext())
			{
				String curuid = (String) iter.next();
				File f;
				f=new File(storagedir+"/"+curuid);
				if(!f.exists()){
					if(!f.createNewFile())
						Log.d("createFiles","Unable to create file "+curuid);
				}
			}
		}catch(IOException e){
			Log.d("createFiles", e.toString());
		}
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onCreate(){
		//Get the list of apps that have internet permission
		ArrayList uidlist = (ArrayList) getUIDList();
		createFiles(uidlist,accessDomainDirectory);
	}

	@Override
	public void onDestroy(){

	}


	@Override
	public void onStart(Intent intent, int startid)
	{
		//Start server on domainsocket
		startServer();
	}

	//Start server on domain socket
	private void startServer()
	{
		localSocketServer mLocalSocketServer = new localSocketServer();
		mLocalSocketServer.start();
	}

	public class localSocketServer extends Thread {

		int bufferSize = 32;
		byte[] buffer;
		int bytesRead;
		int totalBytesRead;
		int posOffset;
		LocalServerSocket server;
		LocalSocket receiver;
		BufferedReader input;
		PrintWriter output;
		String requeststr="";
		String responsestr="";

		public localSocketServer() {
			Log.d(TAG, " +++ Begin of localSocketServer() +++ ");
			buffer = new byte[bufferSize];
			bytesRead = 0;
			totalBytesRead = 0;
			posOffset = 0;

			try {
				server = new LocalServerSocket(SOCKET_ADDRESS);
			} catch (IOException e) {
				Log.d(TAG, "Exception: "+e.getMessage());
				e.printStackTrace();
			}

			LocalSocketAddress localSocketAddress; 
			localSocketAddress = server.getLocalSocketAddress();
			String str = localSocketAddress.getName();

			Log.d(TAG, "The LocalSocketAddress = " + str);
		}

		public void run() {             

			Log.d(TAG, " +++ Begin of run() +++ ");

			while (true) {

				if (null == server){
					Log.d(TAG, "The localSocketServer is NULL !!!");
					break;
				}

				try {
					Log.d(TAG, "localSocketServer begins to accept()");
					receiver = server.accept();
				} catch (IOException e) {
					Log.d(TAG, "localSocketServer accept() failed !!!");
					e.printStackTrace();
					continue;
				}                   

				try {
					input = new BufferedReader(new InputStreamReader(receiver.getInputStream()));
					output = new PrintWriter(receiver.getOutputStream(),true);
				} catch (IOException e) {
					Log.d(TAG, "Getting Input and Output stream failed: "+e.getMessage());
					e.printStackTrace();
					continue;
				}

				Log.d(TAG, "The client connected to LocalServerSocket");

				try {
					//bytesRead = input.read(buffer, posOffset,(bufferSize - totalBytesRead));
					requeststr = responsestr = "";
					requeststr = input.readLine();
					Log.d(TAG,"String received on socket is "+requeststr);
					int responseval = getResponse(requeststr,receiver,output);
					responsestr = responseval+"\0";
					output.write(responsestr);
					Log.d(TAG,"Response sent successfully. The sent response is "+responsestr);
					output.close();
					input.close();
					receiver.close();
				} catch (IOException e) 
				{
					Log.d(TAG, "Exception when reading socket: "+e.getMessage());
					e.printStackTrace();
					break;
				}
			}
		}

	}

	private int getResponse(String requeststr,LocalSocket receiver,PrintWriter output)
	{
		try
		{
			Log.d(TAG, "Inside getResponse(). The requeststr is "+requeststr);
			String[] values = requeststr.split(" ");
			Log.d(TAG, "ip value split is "+values[0]);
			Log.d(TAG, "port value split is "+values[1]);
			int port = Integer.parseInt(values[1].trim());
			return isDomainAllowed(values[0],port,receiver,output);
		}
		catch(Exception e){
			Log.e(TAG,"Error in getResponse: "); e.printStackTrace();
		}
		return 0;
	}


	//Will be called by native code
	public int isDomainAllowed(String ip,int port,LocalSocket receiver,PrintWriter output)
	{
		//Get uid of the app from its ip and port
		int uid = getUIDFromPort(port);
		if(uid == -1)
		{
			Log.e(TAG,"Unable to find uid for the port "+port);
			return 1;
		}

		Log.d(TAG,"The uid for the port "+port+" is "+uid);
		//Check if ip is present in the uid file already
		boolean allowed = isIPAllowedForUID(ip,uid);

		//If it is already there, then just return 0
		if(allowed) return 0;

		Log.d(TAG,"Going to ask user's permission");
		return askUser(uid,ip,receiver,output);

	}

	//Ask user if the app can access an ip
	private int askUser(int uid, String ip,LocalSocket receiver, PrintWriter output)
	{
		//Trying out Gson
		Intent intent = new Intent(this, DialogActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
	    intent.putExtra("uid",uid);
	    intent.putExtra("ip",ip);
	    startActivity(intent);
	    Log.d(TAG,"Inside askUser: Started the Activity. Waiting for response ready to change");
	    //Wait until user gives a response
	    while(NetService.responseReady == false)
	    {
	    }
	    Log.d(TAG,"askUser: Response is ready. The responsevalue is "+NetService.responsevalue);
	    responseReady = false;
		return NetService.responsevalue;
	}

	//Get all open tcp sockets and their details from /proc/net/tcp6 file
	private int getUIDFromPort(int port) {
		try{
			Log.d(TAG,"Trying to find uid for the local port "+port);
			FileInputStream fstream = new FileInputStream("/proc/net/tcp6");
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			br.readLine();//For the name of the fields
			while ((strLine = br.readLine()) != null)   {
				String localPort = strLine.substring(39, 43); localPort = localPort.trim();
				
				int localport = Integer.parseInt(localPort,16);
				int firstbyte = localport & 0x00ff;
		        int secondbyte = localport & 0xff00;
		        secondbyte = secondbyte >> 8;
		        firstbyte = firstbyte << 8;
		        localport = firstbyte | secondbyte;
		        
				String uid = strLine.substring(124, 129);
				uid = uid.trim();
				if(localport == port)
				{
					br.close();
					return Integer.parseInt(uid);
				}
			}
			br.close();
			return -1;
		}catch(Exception e){
			Log.e("NetService", "Error in getUIDFromPort: "+e.getMessage());
		}
		return -1;
	}

	//See if an IP is allowed to be accessed by a package with uid
	boolean isIPAllowedForUID(String ip,int uid)
	{
		boolean allowed = false;
		try {
			File sdDir = Environment.getExternalStorageDirectory();
			String fileString = sdDir.getAbsolutePath()+accessDomainDirectory+uid;
			Log.d(TAG,"Inside isIPAllowedForUID. The uid directory is "+fileString);
			FileInputStream fstream = null;
			fstream = new FileInputStream(fileString);

			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String currentip = "";
			while((currentip = br.readLine()) != null)
			{
				if(currentip.equals(ip))
				{
					Log.d(TAG,ip+" is found in the uid file "+uid);
					allowed = true;
					break;
				}
			}
			br.close();
		} catch (Exception e) {
			Log.e("NetService", "Error: "+e.getMessage());
		}
		return allowed;
	}

	//Add a particular IP to a UID's list of allowed IP addresses to access. This is done by appending the IP to the uid file
	private void addIPtoUID(String IP, String UID)
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

}

