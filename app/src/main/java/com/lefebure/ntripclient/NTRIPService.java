package com.lefebure.ntripclient;

//import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Base64;
import android.widget.Toast;
import java.lang.reflect.Method;
//import com.lefebure.ntripclient.InsecureBluetooth;

public class NTRIPService extends Service {
	//private static final String NTAG = "NTRIPThread";
	//private static final String BTAG = "BTThread";

	private NotificationManager mNoticationManager;
	private Notification mNotification;
	MockLocationProvider mock;
	private boolean SendMockLocation = false; 
	Thread nThread;
	Socket nsocket; // Network Socket
	InputStream nis = null; // Network Input Stream
	OutputStream nos = null; // Network Output Stream



	private BluetoothAdapter mBluetoothAdapter = null;
	private BTConnectThread mBTConnectThread;
    private BTConnectedThread mBTConnectedThread;
    private int mBTState;
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device
    private Boolean BTShouldAutoSwitch = false;
	private Boolean BTwasDisabled = false;

	private static boolean isRunning = false;
	private Timer timer = new Timer();
	private String logmsgs = "";
	private String lognmea = "";
	private int DisplayMsgType = 0;

	ArrayList<Messenger> mClients = new ArrayList<Messenger>(); // Keeps track of all current registered clients.
	int mValue = 0; // Holds last value set by a client.
	static final int MSG_THREAD_SUICIDE = 0;
	static final int MSG_REGISTER_CLIENT = 1;
	static final int MSG_UNREGISTER_CLIENT = 2;
	static final int MSG_UPDATE_STATUS = 3;
	static final int MSG_UPDATE_BYTESIN = 4;
	static final int MSG_SHOW_PROGRESSBAR = 5;
	static final int MSG_UPDATE_LOG_APPEND = 6;
	static final int MSG_UPDATE_LOG_FULL = 7;
	static final int MSG_TOGGLE_LOG_TYPE = 8;
	static final int MSG_ADD_NOTE_TO_NMEA = 9;
	static final int MSG_RELOAD_PREFERENCES = 10;
	static final int MSG_TIMER_TICK = 100;
	static final int MSG_NETWORK_GOT_DATA = 101;
	static final int MSG_NETWORK_TIMEOUT = 198;
	static final int MSG_NETWORK_FINISHED = 199;
	static final int MSG_BT_LOG_MESSAGE = 200;
	static final int MSG_BT_GOT_DATA = 201;
	static final int MSG_BT_FINISHED = 299;

	
	//private Boolean NTRIPShouldBeConnected = false;
	private String NetworkProtocol = "none";
	private String SERVERIP = "";
	private int SERVERPORT = 10000;
	private String USERNAME = "";
	private String PASSWORD = "";
	private String MOUNTPOINT = "";
	private Boolean NetworkIsConnected = false;
	private int NetworkReceivedByteCount = 0;
	private int NetworkReConnectInTicks = 2;
	private int NetworkConnectionAttempts = 0;
	private Boolean NTRIPStreamRequiresGGA = false;
	private int NTRIPTicksSinceGGASent = 0;
	private int NetworkDataMode = 0;
	private String NTRIPResponse = "";
	private String NMEA = "";
	
	private Boolean ReceiverBluetooth = false;
	private Boolean ReceiverUDP = false;
	private int LocalhostUDPPort = 20000;
	
	
	private String MACAddress = "00:00:00:00:00:00";
	//private Boolean UseHTCConnectionWorkaround = false;
	private int BTConnectionMethod = 3;
	private String AutoConfigReceiver = "none";
	private String AutoConfigCommand = "unlogall thisport";
	private Boolean SaveNMEADataToFile = false;
	private Boolean FixChangeAudioAlert = false;
	private byte[] DataNMEAToSave = new byte[4096];
	private int DataNMEAToSaveIndex = 0;
	private Boolean SaveDGPSDataToFile = false;
	private byte[] DataDGPSToSave = new byte[4096];
	private int DataDGPSToSaveIndex = 0;
	private Boolean UseManualLocation = false;
	private Double ManualLat = 41.0;
	private Double ManualLon = -91.0;
	
	private boolean completeline;
	private String MostRecentGGA = ""; //"$GPGGA,154223,4000,N,08312,W,4,10,1,200,M,1,M,8,0*7F"; // $GPGGA,213711.00,4158.8440010,N,09147.4414792,W,9,04,4.9,266.536,M,-32.00,M,03,0138*6C";

	private double Latitude = 0;
	private double Longitude = 0;
	private int FixType = 10;
	private float FixAccuracy = 100;
	private int SatsTracked = 0;
	private String HDOP = "";
	private String CorrectionAge = "?";
	private float Elevation = 0;
	private float Speed = 0;
	private float Heading = 0;
	private int TicksSinceLastStatusSent = 0;
	private int outInfo1 = 1;
	private int outInfo2 = 4;

	final Messenger inMessenger = new Messenger(new IncomingHandler(this));
	@Override
	public IBinder onBind(Intent intent) {
		return inMessenger.getBinder();
	}
	public static class IncomingHandler extends Handler { // Handler of incoming messages from clients.
		private final WeakReference<NTRIPService> mTarget; 
	    IncomingHandler(NTRIPService target) {
	    	mTarget = new WeakReference<NTRIPService>(target);
	    }
		
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			NTRIPService target = mTarget.get();
	        if (target != null) {
	            switch (msg.what) {
	 			case MSG_REGISTER_CLIENT:
	 				target.mClients.add(msg.replyTo);
	 				break;
	 			case MSG_UNREGISTER_CLIENT:
	 				target.mClients.remove(msg.replyTo);
	 				break;
	 			case MSG_UPDATE_STATUS:
	 				target.sendStatusMessageToUI(); // Client requested a status update
	 				break;
	 			case MSG_RELOAD_PREFERENCES:
	 				target.LoadPreferences(true); // Client requested that the service reload the shared preferences
	 				break;
	 			case MSG_UPDATE_LOG_FULL:
	 				target.sendAllLogMessagesToUI(); // Client requested all of the log messages.
	 				if (!isRunning) {
	 					target.InformActivityOfThreadSuicide();
	 				}
	 				if (target.NetworkReceivedByteCount > 0) {
	 					target.SendByteCountProgressBarVisibility(1);
	 					target.SendByteCountToActivity();
	 				}
	 				break;
	 			case MSG_TOGGLE_LOG_TYPE:
	 				if (target.DisplayMsgType == 0) {
	 					target.SetDisplayMsgType(1);
	 				} else {
	 					target.SetDisplayMsgType(0);
	 				}
	 				break;
	 			case MSG_ADD_NOTE_TO_NMEA:
	 				//Bundle b1 = msg.getData();
	 				//SaveNMEALineToFile("#" + b1.getString("note"));
	 				break;
	 			default:
	 			}
	         }
		}
	}

	
	private void InformActivityOfThreadSuicide() {
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				mClients.get(i).send(Message.obtain(null, MSG_THREAD_SUICIDE, 0, 0));
				//Log.i("MyService", "Service informed Activity of Suicide. +");
			} catch (RemoteException e) {
				// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
				mClients.remove(i);
				//Log.i("MyService", "Service informed Activity of Suicide. -");
			}
		}
	}
	private void LogMessage(String m) {
		// Check if log is too long, shorten if necessary.
		if (logmsgs.length() > 1000) {
			int tempi = logmsgs.length();
			tempi = logmsgs.indexOf("\n", tempi - 500);
			logmsgs = logmsgs.substring(tempi + 1);
		}

		// Append new message to the log.
		logmsgs += "\n" + TheTimeIs() + m;

		if (DisplayMsgType == 0) {
			// Build bundle
			Bundle b = new Bundle();
			b.putString("logappend", TheTimeIs() + m);
			for (int i = mClients.size() - 1; i >= 0; i--) {
				try {
					Message msg = Message.obtain(null, MSG_UPDATE_LOG_APPEND);
					msg.setData(b);
					mClients.get(i).send(msg);
				} catch (RemoteException e) {
					// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
					mClients.remove(i);
				}
			}
		}
	}
	private void LogNMEA(String m) {
		// Check if log is too long, shorten if necessary.
		if (lognmea.length() > 1000) {
			int tempi = lognmea.length();
			tempi = lognmea.indexOf("\n", tempi - 500);
			lognmea = lognmea.substring(tempi + 1);
		}
		lognmea += "\n" + m; //Append new message to the log.

		if (DisplayMsgType == 1) {
			// Build bundle
			Bundle b = new Bundle();
			b.putString("logappend", m);
			for (int i = mClients.size() - 1; i >= 0; i--) {
				try {
					Message msg = Message.obtain(null, MSG_UPDATE_LOG_APPEND);
					msg.setData(b);
					mClients.get(i).send(msg);
				} catch (RemoteException e) {
					// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
					mClients.remove(i);
				}
			}
		}
	}
	private void sendAllLogMessagesToUI() {
		Bundle b = new Bundle();
		if (DisplayMsgType == 1) {
			b.putString("logfull", lognmea);
		} else {
			b.putString("logfull", logmsgs);
		}
		
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				Message msg = Message.obtain(null, MSG_UPDATE_LOG_FULL);
				msg.setData(b);
				mClients.get(i).send(msg);
			} catch (RemoteException e) {
				// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
				mClients.remove(i);
			}
		}
	}
	private void SetDisplayMsgType(int MsgType) {
		if (lognmea.length() == 0 && MsgType == 1) { //Can't change to NMEA, no data there
			MsgType = 0;
		}
		if (DisplayMsgType != MsgType) { //Type changed. Need to re-send everything
			DisplayMsgType = MsgType;
			sendAllLogMessagesToUI();
		}
	}
	private void sendStatusMessageToUI() {
		// Build bundle
		String textFix;
		switch (FixType) {
		case 0:
			textFix = "Invalid";
			break;
		case 1:
			textFix = "GPS";
			break;
		case 2:
			textFix = "DGPS";
			break;
		case 3:
			textFix = "PPS";
			break;
		case 4:
			textFix = "RTK";
			break;
		case 5:
			textFix = "FloatRTK";
			break;
		case 6:
			textFix = "Estimated";
			break;
		case 7:
			textFix = "Manual";
			break;
		case 8:
			textFix = "Simulation";
			break;
		case 9:
			textFix = "WAAS";
			break;
		case 10:
			textFix = "No Data";
			break;
		default:
			textFix = "Unknown";
		}
		if (FixType < 10) {
			textFix += ":" + SatsTracked;
		}

		Bundle b = new Bundle();
		if (ReceiverBluetooth) {
			b.putString("fix", textFix);
			b.putString("info1", GetOutputInfo(outInfo1));
			b.putString("info2", GetOutputInfo(outInfo2));
		} else {
			if (ReceiverUDP) {
				b.putString("fix", "Internal GPS");
			} else {
				b.putString("fix", "NTRIP Test Mode");
			}
			b.putString("info1", "");
			b.putString("info2", "");
		}
		
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				Message msg = Message.obtain(null, MSG_UPDATE_STATUS);
				msg.setData(b);
				mClients.get(i).send(msg);
			} catch (RemoteException e) {
				// The client is dead. Remove it from the list; we are going
				// through the list from back to front so this is safe to do
				// inside the loop.
				mClients.remove(i);
			}
		}
		TicksSinceLastStatusSent = 0; // Reset to zero
	}
	private String GetOutputInfo(int infotype) {
		switch (infotype) {
		case 1: // Correction Age
			return "Age: " + CorrectionAge + "s";
		case 2: // Elevation Feet
			return new DecimalFormat("0.0").format(Elevation * 3.2808399) + "'";
		case 3: // Elevation Meters
			return new DecimalFormat("0.000").format(Elevation) + "m";
		case 4: // Speed MPH
			return new DecimalFormat("0.0").format(Speed * 1.15077945) + " MPH";
		case 5: // Speed Km/h
			return new DecimalFormat("0.0").format(Speed * 1.852) + " km/h";
		case 6: // Heading
			return new DecimalFormat("0.0").format(Heading) + "'";
		case 7: // H-DOP
			return "HDOP:" + HDOP;
		case 8: // V-DOP
			return "VDOP";
		case 9: // P-DOP
			return "PDOP";
		case 10: //Latitude
			return new DecimalFormat("0.000000").format(Latitude);
		case 11: //Longitude
			return new DecimalFormat("0.000000").format(Longitude);
		default: // Nothing
			return "";
		}
	}

	@Override
	public void onCreate() {
		super.onCreate();
		//Log.i("MyService", "Service Started.");
		logmsgs = TheTimeIs() + "Service Started";

		isRunning = true;
		LoadPreferences(false);
		
		mNotification = new Notification(R.drawable.notify_list,getText(R.string.service_started), System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), 0);
		mNotification.setLatestEventInfo(this, getText(R.string.service_label), getText(R.string.service_started), contentIntent);
		mNoticationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNoticationManager.cancelAll(); //Kill all old notifications
		mNotification.flags = Notification.FLAG_ONGOING_EVENT;
		mNoticationManager.notify(R.string.app_name, mNotification);

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		
		timer.scheduleAtFixedRate(new TimerTask(){ public void run()
		{onTimerTick_TimerThread();}}, 0, 1000L);
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		//Log.i("MyService", "Received start id " + startId + ": " + intent);
		return START_STICKY; // run until explicitly stopped.
	}
	private void LoadPreferences(Boolean NotifyOfChanges) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		Boolean NetworkSettingsChanged = false;
		try {
			outInfo1 = Integer.parseInt(preferences.getString("info1", "1"));
			outInfo2 = Integer.parseInt(preferences.getString("info2", "4"));
			FixChangeAudioAlert = preferences.getBoolean("fixchangebeep", false);
			
			String newReceiverType = preferences.getString("receiverconnection", "none");
			if (newReceiverType.equals("bluetooth")) {
				ReceiverBluetooth = true;
				ReceiverUDP = false;
			} else if (newReceiverType.equals("udp")) {
				ReceiverBluetooth = false;
				ReceiverUDP = true;
				BTstop(); //try to stop BT in case it is running.
			} else {
				ReceiverBluetooth = false;
				ReceiverUDP = false;
				BTstop(); //try to stop BT in case it is running.
				LogMessage("Network Testing Mode. Will not connect to receiver.");
			}

			boolean prefSendMockLocation = preferences.getBoolean("sendmocklocation", false);
			if (prefSendMockLocation != SendMockLocation) { //Changed from before
				if (prefSendMockLocation) {
					if (Settings.Secure.getString(getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION).contentEquals("1")) {
						mock = new MockLocationProvider(LocationManager.GPS_PROVIDER, this);
						SendMockLocation = true;
						LogMessage("GPS Mock Location Enabled");
					} else { //Not enabled in system settings. Nag user.
						Toast.makeText(this, "GPS Mock Locations are not enabled. You can change this under Settings -> Applications -> Development -> Allow mock locations.", Toast.LENGTH_LONG).show();
					}
				} else {
					SendMockLocation = false;
					mock.shutdown();
					LogMessage("GPS Mock Location Disabled");
				}
			}

			String tLocalhostUDPPort = preferences.getString("internaludpport", Integer.toString(LocalhostUDPPort));
			if (isInteger(tLocalhostUDPPort)) {
				int iLocalhostUDPPort = Integer.parseInt(tLocalhostUDPPort);
				if (iLocalhostUDPPort > 0 && iLocalhostUDPPort < 65536) {
					if (LocalhostUDPPort != iLocalhostUDPPort) {
						LocalhostUDPPort = iLocalhostUDPPort;
						NetworkSettingsChanged = true;
					}
				} else {
					LogMessage("Error: UDP Port number is invalid");
					ReceiverUDP = false;
				}
			}
			
			BTShouldAutoSwitch = preferences.getBoolean("autoswitchbluetooth", false);
			AutoConfigReceiver = preferences.getString("receiverautoconfig", "none");
			AutoConfigCommand = preferences.getString("receiverautoconfigcommands", "none");
			//UseHTCConnectionWorkaround = preferences.getBoolean("htcconnectworkaround", false);
			BTConnectionMethod = Integer.parseInt(preferences.getString("bluetoothconnectionmethod", "3"));
			
			
			SaveNMEADataToFile = preferences.getBoolean("savenmeadata", false);
			SaveDGPSDataToFile = preferences.getBoolean("savedgpsdata", false);
			
			
			String tNetworkProtocol = preferences.getString("networkprotocol", "none");
			if (!tNetworkProtocol.equals(NetworkProtocol)) {
				NetworkProtocol = tNetworkProtocol;
				NetworkSettingsChanged = true;
			}
			
			String newMACAddress = preferences.getString("bluetooth_mac", "00:00:00:00:00:00");
			if (newMACAddress != MACAddress) {
				if (!MACAddress.equals("00:00:00:00:00:00")) {
					LogMessage("BT: Target Device Changed. You will need to Disconnet/Reconnect.");
				}
				MACAddress = newMACAddress;
				BTstop();
			}

			String tSERVERIP = preferences.getString("ntripcasterip", "165.206.203.10");
			if (tSERVERIP.length() > 2) {
				if (!tSERVERIP.equals(SERVERIP)) {
					SERVERIP = tSERVERIP;
					NetworkSettingsChanged = true;
				}
			} else {
				LogMessage("Error: NTRIP Server IP is too short");
				NetworkProtocol = "none";
			}

			String tSERVERPORT = preferences.getString("ntripcasterport", Integer.toString(SERVERPORT));
			if (isInteger(tSERVERPORT)) {
				int iSERVERPORT = Integer.parseInt(tSERVERPORT);
				if (iSERVERPORT > 0 && iSERVERPORT < 65536) {
					if (SERVERPORT != iSERVERPORT) {
						SERVERPORT = iSERVERPORT;
						NetworkSettingsChanged = true;
					}
				} else {
					LogMessage("Error: NTRIP Port number is invalid");
					NetworkProtocol = "none";
				}
			}

			String tUSERNAME = preferences.getString("ntripusername", "");
			if (!tUSERNAME.equals(USERNAME)) {
				USERNAME = tUSERNAME;
				NetworkSettingsChanged = true;
			}

			String tPASSWORD = preferences.getString("ntrippassword", "");
			if (!tPASSWORD.equals(PASSWORD)) {
				PASSWORD = tPASSWORD;
				NetworkSettingsChanged = true;
			}

			String tMOUNTPOINT = preferences.getString("ntripstream", "");
			if (!tMOUNTPOINT.equals(MOUNTPOINT)) {
				MOUNTPOINT = tMOUNTPOINT;
				NetworkSettingsChanged = true;
			}
			
			String tUseManualLocation = preferences.getString("ntriplocation", "auto");
			Boolean uUseManualLocation = false;
			if (tUseManualLocation.equals("manual")) {uUseManualLocation = true;}

			if (uUseManualLocation != UseManualLocation) {
				UseManualLocation = uUseManualLocation;
				NetworkSettingsChanged = true;
			}
			
			if (UseManualLocation) {
				String tLat = preferences.getString("ntriplatitude", "");
				String tLon = preferences.getString("ntriplongitude", "");
				Double dLat = 1000.0;
				Double dLon = 1000.0;
				try {
					dLat = Double.parseDouble(tLat);  
			    } catch(NumberFormatException nfe) {}  
			    try {
					dLon = Double.parseDouble(tLon);  
			    } catch(NumberFormatException nfe) {}  
			    if (Math.abs(ManualLat - dLat) > 0.001) { //dLat != ManualLat) {
			    	ManualLat = dLat;
			    	NetworkSettingsChanged = true;
			    }
			    if (Math.abs(ManualLon - dLon) > 0.001) { //dLon != ManualLon) {
			    	ManualLon = dLon;
			    	NetworkSettingsChanged = true;
			    }
			}
			
		} catch (NumberFormatException nfe) {}

		if (!NetworkProtocol.equals("none")) { //Should be connected
			if (NetworkSettingsChanged) {
				if (NetworkIsConnected) { // Need to disconnect and reconnect
					TerminateNTRIPThread(true);
					LogMessage("Network: Disconnected");
				} else { // Just need to connect
					NetworkReConnectInTicks = 2;
				}
			}
		} else { //Should not be connected
			if (NetworkIsConnected) {
				TerminateNTRIPThread(false);
				LogMessage("Network: Disconnected");
			}
			LogMessage("Network: Disabled");
		}
		
		String sourcetable = preferences.getString("ntripsourcetable", "");
		// Log.i("sourcetable", sourcetable);
		String[] lines = sourcetable.split("\\r?\\n");
		for (int i = 0; i < lines.length; i++) {
			String[] fields = lines[i].split(";");
			if (fields.length > 4) {
				if (fields[0].toLowerCase(Locale.ENGLISH).equals("str")) {
					if (fields[1].equals(MOUNTPOINT)) { // Found the right stream
						if (fields[11].equals("1")) { // Stream requires GGA
							NTRIPStreamRequiresGGA = true;
							//Log.i("LoadPreferences", "This stream requires GGA data");
						} else {
							NTRIPStreamRequiresGGA = false;
							//Log.i("LoadPreferences", "This stream does NOT require GGA data");
						}
					}
				}
			}
		}
		if (NotifyOfChanges) {
			if (NetworkSettingsChanged) {
				LogMessage("Network settings changed.");
			}
		}
	}
	public boolean isInteger(String input) {
		try {
			Integer.parseInt(input);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
	public static boolean isRunning() {
		return isRunning;
	}
	private String TheTimeIs() {
		Calendar calendar = Calendar.getInstance();
		int hours = calendar.get(Calendar.HOUR);
		int minutes = calendar.get(Calendar.MINUTE);
		int seconds = calendar.get(Calendar.SECOND);
		return Make2Digits(hours) + ":" + Make2Digits(minutes) + ":"
				+ Make2Digits(seconds) + " ";
	}
	private String Make2Digits(int i) {
		if (i < 10) {
			return "0" + i;
		} else {
			return Integer.toString(i);
		}
	}
	private void onTimerTick_TimerThread() {
		// This is running on a separate thread. Cannot do UI stuff from here.
		// Send a message to the handler to do that stuff on the main thread.
		// DataHandler.sendMessage(DataHandler.obtainMessage(MSG_TIMER_TICK));
		try {
			dataMessenger.send(Message.obtain(null, MSG_TIMER_TICK));
		} catch (RemoteException e2) {
		}
	}
	private void onTimerTick() { // Back on the main thread.
		TicksSinceLastStatusSent++;
		if (TicksSinceLastStatusSent > 4) {
			if (FixType != 10) { // Only log this once
				LogMessage("Fix type is now Unknown");
				FixType = 10;// No data coming in for 5 seconds
				mNotification.iconLevel = 10;
				mNoticationManager.notify(R.string.app_name, mNotification);
			}
			SatsTracked = 0;
			HDOP = "?";
			CorrectionAge = "?";
			Elevation = 0;
			sendStatusMessageToUI();
		}

		if (getBTState() == STATE_NONE && ReceiverBluetooth) { //We're not connected, but should be connected to a Bluetooth receiver, try to start.
			if (mBluetoothAdapter == null) { //No adapter. Fail
				//Log.e("Bluetooth", "getDefaultAdapter returned null");
				Toast.makeText(this, "This device does not support Bluetooth.", Toast.LENGTH_SHORT).show();
				LogMessage("Bluetooth is NOT supported.");
				isRunning = false;
				InformActivityOfThreadSuicide();
				if (timer != null) {timer.cancel();}
				this.stopSelf();
			} else {
				if (!mBluetoothAdapter.isEnabled()) { //Bluetooth disabled
					if (BTShouldAutoSwitch) { //At least auto-switch is enabled
						if (!BTwasDisabled) { //We need to turn on Bluetooth
							//Log.i("Bluetooth", "Turning on");
							mBluetoothAdapter.enable();
							BTwasDisabled = true;
							LogMessage("Waiting for Bluetooth...");
						} else { //We need to wait until bluetooth comes online
							//Log.i("Bluetooth", "Waiting for for Bluetooth to turn on");
						}
					} else { //and auto-switch is disabled. Fail
						//Log.e("Bluetooth", "Bluetooth is Disabled and we can't autoswitch it on");
						Toast.makeText(this, "Bluetooth is Disabled", Toast.LENGTH_SHORT).show();
						LogMessage("Bluetooth is Disabled");
						isRunning = false;
						InformActivityOfThreadSuicide();
						if (timer != null) {timer.cancel();}
						this.stopSelf();
					}
				} else {
					BTstart();
				}
			}
		}
		
		// Log.i("handleMessage", "NTRIPIsConnected: " + NTRIPIsConnected);
		if (!NetworkProtocol.equals("none") && !NetworkIsConnected && (!ReceiverBluetooth || (ReceiverBluetooth && getBTState() == STATE_CONNECTED))) { // Network Thread is currently not running
			if (NetworkReConnectInTicks > 0) { // We are counting down to time to start the Network thread
				// Do we need to wait for a GGA sentences?
				
				if (NTRIPStreamRequiresGGA && !NetworkProtocol.equals("rawtcpip")) {
					if (UseManualLocation) { //We don't need to wait for a GGA, but should check the lat/lon
						if (NetworkReConnectInTicks == 1) { //This is where we check the lat/lon
							boolean BadLatLon = false;
							//Log.i("BadLatLon", "Lat='" + ManualLat + "', Lon='" + ManualLon + "'");
							if (ManualLat >= 90) {BadLatLon = true;}
							if (ManualLat <= -90) {BadLatLon = true;}
							if (ManualLon > 180) {BadLatLon = true;}
							if (ManualLon <= -180) {BadLatLon = true;}
							if (BadLatLon) {
								LogMessage("NTRIP: Manual Location Latitude or Longitude is out of range.");
								//NTRIPShouldBeConnected = false;
								NetworkProtocol = "none";
								NetworkReConnectInTicks++; // To counter the -- below, hold here
							}

						}
					} else {
						if (MostRecentGGA.length() < 5) { //We don't have a GGA yet.
							if (NetworkReConnectInTicks == 2) {
								if (!ReceiverBluetooth) { //No Bluetooth receiver means no NMEA, but stream requires a GGA, and we're set on automatic.
									LogMessage("Error: This stream requires a GGA sentence. Please enter a manual Lat/Lon.");
								} else {
									LogMessage("NTRIP: Waiting for GGA from Receiver");	
								}
							}
							if (NetworkReConnectInTicks == 1) {
								NetworkReConnectInTicks++; // To counter the -- below, hold here
							}
						}
					}
				}

				NetworkReConnectInTicks--;
				if (NetworkReConnectInTicks == 0) { // It is time to start the NTRIP thread
					NetworkConnectionAttempts++;
					if (NetworkConnectionAttempts == 1) {
						LogMessage("Network: Connecting...");
					} else {
						LogMessage("Network: Connecting... Attempt " + NetworkConnectionAttempts);
					}
					NTRIPResponse = "";
					NetworkReceivedByteCount = 0;
					NetworkDataMode = 0;	
					if (NetworkProtocol.equals("rawtcpip")) {
						NetworkDataMode = 99;
					}
					nThread = new Thread(new NetworkClient(NetworkProtocol, SERVERIP, SERVERPORT, MOUNTPOINT, USERNAME, PASSWORD));
					nThread.start();
					NetworkIsConnected = true;
				}
			}
		}
		if (NetworkIsConnected && NTRIPStreamRequiresGGA) {
			NTRIPTicksSinceGGASent++;
			if (NTRIPTicksSinceGGASent >= 10) { // 10 seconds have passed, time for a re-send
				SendGGAToCaster();
			}
		}
	}


	// Network Data Stuff
	public class NetworkClient implements Runnable {
		String nProtocol = "";
		String nServer = "";
		int nPort = 10000;
		String nMountpoint = "";
		String nUsername = "";
		String nPassword = "";

		public NetworkClient(String pProtocol, String pServer, int pPort, String pMountpoint, String pUsername, String pPassword) {
			nProtocol = pProtocol;
			nServer = pServer;
			nPort = pPort;
			nMountpoint = pMountpoint;
			nUsername = pUsername;
			nPassword = pPassword;
		}

		public void run() {
			try {
				//Log.i(NTAG, "Creating socket");
				SocketAddress sockaddr = new InetSocketAddress(nServer, nPort);
				nsocket = new Socket();
				nsocket.connect(sockaddr, 10 * 1000); // 10 second connection timeout
				if (nsocket.isConnected()) {
					nsocket.setSoTimeout(20 * 1000); // 20 second timeout once data is flowing
					nis = nsocket.getInputStream();
					nos = nsocket.getOutputStream();
					//Log.i(NTAG, "Socket created, streams assigned");

					if (nProtocol.equals("ntripv1")) {
						// Build request message
						//Log.i(NTAG, "This is a NTRIP connection");
						String requestmsg = "GET /" + nMountpoint + " HTTP/1.0\r\n";
						requestmsg += "User-Agent: NTRIP LefebureAndroidNTRIPClient/20121216\r\n";
						requestmsg += "Accept: */*\r\n";
						requestmsg += "Connection: close\r\n";
						if (nUsername.length() > 0) {
							requestmsg += "Authorization: Basic " + ToBase64(nUsername + ":" + nPassword);
						}
						requestmsg += "\r\n";
						nos.write(requestmsg.getBytes());
						//Log.i("Request", requestmsg);
					} else { 
						//Log.i(NTAG, "This is a raw TCP/IP connection");
					}

					//Log.i(NTAG, "Waiting for inital data...");
					byte[] buffer = new byte[4096];
					int read = nis.read(buffer, 0, 4096); // This is blocking
					while (read != -1) {
						byte[] tempdata = new byte[read];
						System.arraycopy(buffer, 0, tempdata, 0, read);
						// Log.i(NTAG, "Got data: " + new String(tempdata));
						//DataHandler.sendMessage(DataHandler.obtainMessage(MSG_NETWORK_GOT_DATA, tempdata));
						try {
							dataMessenger.send(Message.obtain(null, MSG_NETWORK_GOT_DATA, tempdata));
						} catch (RemoteException e2) {
						}
						read = nis.read(buffer, 0, 4096); // This is blocking
					}
				}
			} catch (SocketTimeoutException ex) {
				//DataHandler.sendMessage(DataHandler.obtainMessage(MSG_NETWORK_TIMEOUT));
				try {
					dataMessenger.send(Message.obtain(null, MSG_NETWORK_TIMEOUT));
				} catch (RemoteException e2) {
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try {
					nis.close();
					nos.close();
					nsocket.close();
					//CloseUDPSocket();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (Exception e) {
					e.printStackTrace();
				}
				//Log.i(NTAG, "Finished");
				//DataHandler.sendMessage(DataHandler.obtainMessage(MSG_NETWORK_FINISHED));
				try {
					dataMessenger.send(Message.obtain(null, MSG_NETWORK_FINISHED));
				} catch (RemoteException e2) {
				}
			}
		}

		private String ToBase64(String in) {
			return Base64.encodeToString(in.getBytes(), 4);
		}
	}
	public void SendDataToNetwork(String cmd) { // You run this from the main thread.
		try {
			if (nsocket != null) {
				if (nsocket.isConnected()) {
					if (!nsocket.isClosed()) {
						//Log.i("SendDataToNetwork", "SendDataToNetwork: Writing message to socket");
						nos.write(cmd.getBytes());
					//} else {
					//Log.i("SendDataToNetwork", "SendDataToNetwork: Cannot send message. Socket is closed");
					}
				//} else {
				//Log.i("SendDataToNetwork", "SendDataToNetwork: Cannot send message. Socket is not connected");
				}
			}
		} catch (Exception e) {
			//Log.i("SendDataToNetwork", "SendDataToNetwork: Message send failed. Caught an exception");
		}
	}
	private void ParseNetworkDataStream(byte[] buffer) {
		// Log.i("handleMessage", "bytes from network:" + buffer.length + ", " + new String(buffer));
		if (NetworkDataMode == 0) { // We're just starting up.
			NTRIPResponse += new String(buffer); // Add what we got to the string, in case the response spans more than one packet.
			if (NTRIPResponse.startsWith("ICY 200 OK")) {// Data stream confirmed.
				if (NTRIPStreamRequiresGGA) {
					SendGGAToCaster();
				}
				NetworkDataMode = 99; // Put in to data mode
				LogMessage("NTRIP: Connected to caster");
			} else if (NTRIPResponse.indexOf("401 Unauthorized") > 1) {
				//Log.i("handleMessage", "Invalid Username or Password.");
				LogMessage("NTRIP: Bad username or password.");
				TerminateNTRIPThread(false);
			} else if (NTRIPResponse.startsWith("SOURCETABLE 200 OK")) {
				LogMessage("NTRIP: Downloading stream list");
				//NTRIPShouldBeConnected = false; // So it doesn't reconnect over and over
				NetworkProtocol = "none"; // So it doesn't reconnect over and over
				NetworkDataMode = 1; // Put into source table mode
				NTRIPResponse = NTRIPResponse.substring(20); // Drop the beginning of the data
				CheckIfDownloadedSourceTableIsComplete();
			} else if (NTRIPResponse.length() > 1024) { // We've received 1KB of data but no start command. WTF?
				LogMessage("NTRIP: Unrecognized server response:");
				LogMessage(NTRIPResponse);
				TerminateNTRIPThread(true);
			}
		} else if (NetworkDataMode == 1) { // Save SourceTable
			NTRIPResponse += new String(buffer); // Add what we got to the string, in case the response spans more than one packet.
			CheckIfDownloadedSourceTableIsComplete();
		} else { // Data streaming mode. Forward data to bluetooth socket
			if (NetworkReceivedByteCount == 0) {
				if (NetworkProtocol.equals("rawtcpip")) {
					LogMessage("TCP/IP: Connected to server " + SERVERIP + ":" + SERVERPORT);
				}
				SendByteCountProgressBarVisibility(1);
			}
			
			NetworkReceivedByteCount += buffer.length;
			SendByteCountToActivity();
			if (ReceiverBluetooth) {SendDataToBluetooth(buffer);}
			if (ReceiverUDP) {SendDataToUDP(buffer);}
			if (SaveDGPSDataToFile) {SaveDataDGPSToFileAppend(buffer);}
		}
	}
	private void SendByteCountToActivity() {
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				mClients.get(i).send(Message.obtain(null, MSG_UPDATE_BYTESIN, NetworkReceivedByteCount, 0));
			} catch (RemoteException e) {
				// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
				mClients.remove(i);
			}
		}
	}
	private void SendByteCountProgressBarVisibility(int val) {
		for (int i = mClients.size() - 1; i >= 0; i--) {
			try {
				mClients.get(i).send(Message.obtain(null, MSG_SHOW_PROGRESSBAR, val, 0));
			} catch (RemoteException e) {
				// The client is dead. Remove it from the list; we are going through the list from back to front so this is safe to do inside the loop.
				mClients.remove(i);
			}
		}
	}
	private void CheckIfDownloadedSourceTableIsComplete() {
		if (NTRIPResponse.indexOf("\r\nENDSOURCETABLE") > 0) {
			//Log.i("Sourcetable", "Found the end");
			LogMessage("NTRIP: Downloaded stream list");
			LogMessage("Please select a stream");
			LogMessage("NTRIP: Disabled");
			SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			SharedPreferences.Editor editor = preferences.edit();
			editor.putString("ntripsourcetable", NTRIPResponse);
			editor.commit();
		}
	}
	private void SendGGAToCaster() {
		if (UseManualLocation) {
			SendDataToNetwork(GenerateGGAFromLatLon() + "\r\n");
		} else {
			SendDataToNetwork(MostRecentGGA + "\r\n");
		}
		NTRIPTicksSinceGGASent = 0;
	}
	private String GenerateGGAFromLatLon() {
		String gga = "GPGGA,000001,";
		
		double posnum = Math.abs(ManualLat);
		double latmins = posnum % 1;
		int ggahours = (int)(posnum - latmins);
		latmins = latmins * 60;
		double latfracmins = latmins % 1;
		int ggamins = (int)(latmins - latfracmins);
		int ggafracmins = (int)(latfracmins * 10000);
		ggahours = ggahours * 100 + ggamins;
		if (ggahours < 1000) {
			gga += "0";
			if (ggahours < 100) {
				gga += "0";
			}
		}
		gga += ggahours + ".";
		if (ggafracmins < 1000) {
			gga += "0";
			if (ggafracmins < 100) {
				gga += "0";
				if (ggafracmins < 10) {
					gga += "0";
				}
			}
		}
		gga += ggafracmins;
		if (ManualLat > 0) {
			gga += ",N,";
		} else {
			gga += ",S,";			
		}
		
		posnum = Math.abs(ManualLon);
		latmins = posnum % 1;
		ggahours = (int)(posnum - latmins);
		latmins = latmins * 60;
		latfracmins = latmins % 1;
		ggamins = (int)(latmins - latfracmins);
		ggafracmins = (int)(latfracmins * 10000);
		ggahours = ggahours * 100 + ggamins;
		if (ggahours < 10000) {
			gga += "0";
			if (ggahours < 1000) {
				gga += "0";
				if (ggahours < 100) {
					gga += "0";
				}
			}
		}
		gga += ggahours + ".";
		if (ggafracmins < 1000) {
			gga += "0";
			if (ggafracmins < 100) {
				gga += "0";
				if (ggafracmins < 10) {
					gga += "0";
				}
			}
		}
		gga += ggafracmins;
		if (ManualLon > 0) {
			gga += ",E,";
		} else {
			gga += ",W,";			
		}
		
		gga += "1,8,1,0,M,-32,M,3,0";

		String checksum = CalculateChecksum(gga);

		//Log.i("Manual GGA", "$" + gga + "*" + checksum);
		return "$" + gga + "*" + checksum;
	}
	
	
	// Bluetooth Receiver Data Stuff
	private synchronized void setBTState(int state) {
        //Log.i(BTAG, "setBTState() " + mBTState + " -> " + state);
        mBTState = state;
    }
	public synchronized int getBTState() {
	        return mBTState;
	    }
	public synchronized void BTstart() {
		SetDisplayMsgType(0);
		//Log.i(BTAG, "BTstart");
        // Cancel any thread attempting to make a connection
        if (mBTConnectThread != null) {mBTConnectThread.cancel(); mBTConnectThread = null;}
        // Cancel any thread currently running a connection
        if (mBTConnectedThread != null) {mBTConnectedThread.cancel(); mBTConnectedThread = null;}
        
        if (!BluetoothAdapter.checkBluetoothAddress(MACAddress)) {
        	LogMessage("Invalid Bluetooth MAC Address: \"" + MACAddress + "\"");
        	InformActivityOfThreadSuicide();
        } else if (MACAddress.equals("00:00:00:00:00:00")) {
        	LogMessage("Error: No Bluetooth device has been selected.");
        	isRunning = false;
			InformActivityOfThreadSuicide();
			if (timer != null) {timer.cancel();}
			this.stopSelf();
        } else {
        	setBTState(STATE_LISTEN);
            BluetoothDevice btdevice = mBluetoothAdapter.getRemoteDevice(MACAddress);
            BTconnect(btdevice);	
        }
    }
	public synchronized void BTconnect(BluetoothDevice device) {
        //Log.i(BTAG, "Connecting to device: " + device.getName());
        LogMessage("Device: " + device.getName());
		// Cancel any thread attempting to make a connection
        if (mBTState == STATE_CONNECTING) {
            if (mBTConnectThread != null) {mBTConnectThread.cancel(); mBTConnectThread = null;}
        }
        // Cancel any thread currently running a connection
        if (mBTConnectedThread != null) {mBTConnectedThread.cancel(); mBTConnectedThread = null;}

        // Start the thread to connect with the given device
        mBTConnectThread = new BTConnectThread(device, BTConnectionMethod, AutoConfigReceiver);
        mBTConnectThread.start();
        setBTState(STATE_CONNECTING);
    }

	private class BTConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;
        private final String autoconfig;
        
        @TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1) //This is to suppress errors for createInsecureRfcommSocketToServiceRecord
		public BTConnectThread(BluetoothDevice device, int useBTConnectionMethod, String autoconfigmodel) {
        	autoconfig = autoconfigmodel;
        	mmDevice = device;
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket for a connection with the given BluetoothDevice
            try {
				UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
				//Check API level
				if (Integer.valueOf(android.os.Build.VERSION.SDK_INT) < 10) { //This device is older than 2.3.3.
					if (useBTConnectionMethod == 3) { //User has selected Insecure.
						useBTConnectionMethod = 2;
						dataMessenger.send(Message.obtain(null, MSG_BT_LOG_MESSAGE, "Warning: Insecure Bluetooth is not available on this device. Will use Insecure with Reflection instead."));
					}
				}
				
				switch (useBTConnectionMethod) {
				case 0: //secure with reflection
					Method m = device.getClass().getMethod("createRfcommSocket", new Class[] {int.class});
					tmp = (BluetoothSocket) m.invoke(device, Integer.valueOf(1));
					break;
				case 1: //secure
					tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
					break;
				case 2: //insecure with reflection
					tmp = InsecureBluetooth.createRfcommSocketToServiceRecord(device, MY_UUID, true);
					break;
				default: //case 3: //insecure
					tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
				}

            } catch (Exception e) {
				//Log.e(BTAG, "Error at createRfcommSocketToServiceRecord: " + e);
				e.printStackTrace();
				//DataHandler.sendMessage(DataHandler.obtainMessage(MSG_BT_LOG_MESSAGE, "Exception creating socket: " + e));
				try {
					dataMessenger.send(Message.obtain(null, MSG_BT_LOG_MESSAGE, "Exception creating socket: " + e));
				} catch (RemoteException e2) {
				}
			}
            
            mmSocket = tmp;
        }

        public void run() {
            //Log.i(BTAG, "BEGIN BTConnectThread");
        	//DataHandler.sendMessage(DataHandler.obtainMessage(MSG_BT_LOG_MESSAGE, "Trying to Connect..."));
        	try {
				dataMessenger.send(Message.obtain(null, MSG_BT_LOG_MESSAGE, "Trying to Connect..."));
			} catch (RemoteException e2) {
			}
        	
            // Always cancel discovery because it will slow down a connection
            mBluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a successful connection or an exception
                mmSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    mmSocket.close();
                } catch (IOException e2) {
                    //Log.e(BTAG, "unable to close() socket during connection failure", e2);
                }
                //Log.e(BTAG, "unable to connect() socket. Error: ", e);
                //DataHandler.sendMessage(DataHandler.obtainMessage(MSG_BT_LOG_MESSAGE, "Failed to Connect: " + e));
                //DataHandler.sendMessage(DataHandler.obtainMessage(MSG_BT_FINISHED));
                try {
    				dataMessenger.send(Message.obtain(null, MSG_BT_LOG_MESSAGE, "Failed to Connect: " + e));
    				dataMessenger.send(Message.obtain(null, MSG_BT_FINISHED));
    			} catch (RemoteException e2) {
    			}

                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (NTRIPService.this) {
            	mBTConnectThread = null;
            }

            // Start the connected thread
            BTconnected(mmSocket, mmDevice, autoconfig);
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                //Log.e(BTAG, "close() of connect socket failed", e);
            }
        }
    }
	public synchronized void BTconnected(BluetoothSocket socket, BluetoothDevice device, String autoconfigmodel) {
        //Log.i(BTAG, "Connected");
        // Cancel the thread that completed the connection
        if (mBTConnectThread != null) {mBTConnectThread.cancel(); mBTConnectThread = null;}
        // Cancel any thread currently running a connection
        if (mBTConnectedThread != null) {mBTConnectedThread.cancel(); mBTConnectedThread = null;}

        // Start the thread to manage the connection and perform transmissions
        mBTConnectedThread = new BTConnectedThread(socket, autoconfigmodel);
        mBTConnectedThread.start();

        setBTState(STATE_CONNECTED);
    }
	private class BTConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final String autoconfig;
        
        public BTConnectedThread(BluetoothSocket socket, String autoconfigmodel) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            autoconfig = autoconfigmodel;
            
            // Get the BluetoothSocket input and output streams
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                //Log.e(BTAG, "temp sockets not created", e);
            	//DataHandler.sendMessage(DataHandler.obtainMessage(MSG_BT_LOG_MESSAGE, "Could not create Streams"));
            	//DataHandler.sendMessage(DataHandler.obtainMessage(MSG_BT_FINISHED));
            	try {
    				dataMessenger.send(Message.obtain(null, MSG_BT_LOG_MESSAGE, "Could not create Streams"));
    				dataMessenger.send(Message.obtain(null, MSG_BT_FINISHED));
    			} catch (RemoteException e2) {
    			}
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            //Log.i(BTAG, "BEGIN BTConnectedThread");
        	//DataHandler.sendMessage(DataHandler.obtainMessage(MSG_BT_LOG_MESSAGE, "Bluetooth Device Connected"));
        	try {
				dataMessenger.send(Message.obtain(null, MSG_BT_LOG_MESSAGE, "Bluetooth Device Connected"));
			} catch (RemoteException e2) {
			}
        	
            try {
            	if (autoconfig.equals("novateloemv")) {
            		String requestmsg = "\r\nunlogall thisport\r\nlog gpggalong ontime 0.1\r\nlog gprmc ontime 0.1\r\n";
            		mmOutStream.write(requestmsg.getBytes());
            	} else if (autoconfig.equals("novateloemvrtcmv3")) {
            		String requestmsg = "\r\nunlogall thisport\r\nlog gpggalong ontime 0.1\r\nlog gprmc ontime 0.1\r\ninterfacemode rtcmv3 novatel\r\n";
            		mmOutStream.write(requestmsg.getBytes());
            	} else if (autoconfig.equals("novateloemvrtcm")) {
             		String requestmsg = "\r\nunlogall thisport\r\nlog gpggalong ontime 0.1\r\nlog gprmc ontime 0.1\r\ninterfacemode rtcm novatel\r\n";
            		mmOutStream.write(requestmsg.getBytes());
            	} else if (autoconfig.equals("novateloemvcmr")) {
             		String requestmsg = "\r\nunlogall thisport\r\nlog gpggalong ontime 0.1\r\nlog gprmc ontime 0.1\r\ninterfacemode cmr novatel\r\n";
            		mmOutStream.write(requestmsg.getBytes());
            	} else if (autoconfig.equals("novateloemvcellular")) {
            		String requestmsg = "\r\nunlogall thisport\r\nlog gpggalong ontime 0.1\r\nlog gprmc ontime 0.1\r\nlog cellstatusa onchanged\r\nlog ntripstatusa onchanged\r\n";
            		mmOutStream.write(requestmsg.getBytes());
            	} else if (autoconfig.equals("custom")) {
            		String requestmsg = "\r\n" + AutoConfigCommand + "\r\n";
            		mmOutStream.write(requestmsg.getBytes());
                } //else, no receiver model specified
            } catch (IOException e) {
            	//Log.i(BTAG, "Error auto-configuring receiver: " + e);
            }
            
            byte[] buffer = new byte[1024];
            int bytesread;

            // Keep listening to the InputStream while connected
            while (true) {
                try {
                	bytesread = mmInStream.read(buffer); //This is a blocking call
                    byte[] tempdata = new byte[bytesread];
                    System.arraycopy(buffer, 0, tempdata, 0, bytesread);
                    //Log.d(BTAG, "Got Data: " + new String(tempdata));
                    //DataHandler.sendMessage(DataHandler.obtainMessage(MSG_BT_GOT_DATA, tempdata));
                    try {
        				dataMessenger.send(Message.obtain(null, MSG_BT_GOT_DATA, tempdata));
        			} catch (RemoteException e2) {
        			}
                    //mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    //Log.e(BTAG, "ConnectionLost. Error: " + e);
                	//DataHandler.sendMessage(DataHandler.obtainMessage(MSG_BT_FINISHED));
                	try {
        				dataMessenger.send(Message.obtain(null, MSG_BT_FINISHED));
        			} catch (RemoteException e2) {
        			}
                    break;
                }
            }
        }

        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                //Log.e(BTAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                //Log.e(BTAG, "close() of connected socket failed", e);
            }
        }
    }
	public synchronized void BTstop() {
        //Log.i(BTAG, "BTstop");
        if (mBTConnectThread != null) {mBTConnectThread.cancel(); mBTConnectThread = null;}
        if (mBTConnectedThread != null) {mBTConnectedThread.cancel(); mBTConnectedThread = null;}
        setBTState(STATE_NONE);
    }
	public void SendDataToBluetooth(byte[] buffer) { // You run this from the main thread.
		// Create temporary object
		BTConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mBTState != STATE_CONNECTED) return;
            r = mBTConnectedThread;
            //Log.d(BTAG, "Sent Data");
        }
        // Perform the write unsynchronized
        r.write(buffer);
	}
	private void ParseBTDataStream(byte[] buffer) {
		//Log.i("handleMessage", "bytes from bt:" + buffer.length + ", " + new String(buffer));
		ParseNMEAStream(new String(buffer));
		if (SaveNMEADataToFile) {SaveDataNMEAToFileAppend(buffer);}
		//ParseNMEAStream("$GPGGA,000019.00,4158.8435628,N,09147.4425035,W,4,16,0.7,257.280,M,-32.00,M,01,0602*64\r\n");
	}
	private void ParseNMEAStream(String newdata) {
		// LogMessage(nmea); //For testing only
		NMEA += newdata;
		String[] lines = NMEA.split("\\r?\\n");
		if (lines.length > 0) {
			for (int i = 0; i < lines.length; i++) {
				completeline = false; // Reset this
				if (lines[i].length() > 8) { // There is some data here
					if (lines[i].lastIndexOf("*") + 3 == lines[i].length()) { // Line ends with a * two chars from the end
						completeline = true;
						String checksum = lines[i].substring(lines[i].lastIndexOf("*") + 1);
						String linedata = lines[i].substring(0, lines[i].lastIndexOf("*"));
						if (linedata.lastIndexOf("$") > -1) { // There is a $
							linedata = linedata.substring(linedata.lastIndexOf("$") + 1);
							if (linedata.length() > 5) { // There are still at least 6 characters
								// Log.i("ParseNMEA", linedata + "===" + checksum);
								if (CalculateChecksum(linedata).equals(checksum)) {
									LogNMEA(linedata);
									if (linedata.substring(0, 5).equals("GPGGA") || linedata.substring(0, 5).equals("GNGGA") || linedata.substring(0, 5).equals("GLGGA")) {
										if (ParseGPGGA(linedata)) {
											//Is a valid line, set as most recent GGA
											MostRecentGGA = lines[i].substring(lines[i].lastIndexOf("$"));
										}
									} else if (linedata.substring(0, 5).equals("GPRMC")) {
										ParseGPRMC(linedata);
									}
								}
							}
						}
					}
				}
			}
			NMEA = ""; // Clear out
			if (!completeline) { // Last line wasn't complete, put last incomplete line back
				if (lines[lines.length - 1].length() < 1000) { // Only if less than 1000 characters long.
					NMEA = lines[lines.length - 1];
				}
			}
		}
	}
	private String CalculateChecksum(String line) {
		int chk = 0;
		for (int i = 0; i < line.length(); i++) {
			chk ^= line.charAt(i);
		}
		String chk_s = Integer.toHexString(chk).toUpperCase(Locale.ENGLISH); // convert the integer to a HexString in upper case
		while (chk_s.length() < 2) { // checksum must be 2 characters. if it falls short, add a zero before the checksum
			chk_s = "0" + chk_s;
		}
		return chk_s;
	}
	private Boolean ParseGPGGA(String line) {
		Boolean isvalidline = false;
		//Log.i("ParseGGA", line);
		String[] ary = line.split(",");
		int inFixType = 0;
		int inSatsTracked = 0;
		float inElevation = 0;

		if (ary.length > 9) { // We have at least 9 fields
			double lat = 0;
			double lon = 0;

			if (ary[2].length() < 3) return false; //GPGGA,150211.00,,,,,0,14,0.8,,,,,59,0602 = This line was causing crashes before adding length check. 2012-05-08
			if (ary[3].length() == 0) return false;
			if (ary[4].length() < 4) return false;
			if (ary[5].length() == 0) return false;
			if (ary[6].length() == 0) return false;
			if (ary[7].length() == 0) return false;
			if (ary[9].length() == 0) return false;
			
			String strlat = ary[2];
			String strlatns = ary[3];
			String strlon = ary[4];
			String strlonew = ary[5];
			
			try {
				int latdeg = Integer.parseInt(strlat.substring(0, 2));
				double latmin = Double.parseDouble(strlat.substring(2));
				int londeg = Integer.parseInt(strlon.substring(0, 3));
				double lonmin = Double.parseDouble(strlon.substring(3));
				lat = latdeg + (latmin / 60);
				lon = londeg + (lonmin / 60);
				if (strlatns.contains("S")) lat *= -1;
				if (strlonew.contains("W")) lon *= -1;
			} catch (NumberFormatException nfe) {}
			Latitude = lat;
			Longitude = lon;
			try {
				inFixType = Integer.parseInt(ary[6]);
				inSatsTracked = Integer.parseInt(ary[7]);
				inElevation = Float.valueOf(ary[9].trim()).floatValue();
			} catch (NumberFormatException nfe) {}
			HDOP = ary[8];
			if (ary.length > 13) { // We have at least 13 fields
				CorrectionAge = ary[13];
			} else {
				CorrectionAge = "?";
			}
			if (inFixType != FixType) {
				String textFix;
				switch (inFixType) {
				case 0:
					textFix = "Invalid";
					FixAccuracy = 100;
					UpdateNotification(10);
					break;
				case 1:
					textFix = "GPS";
					FixAccuracy = 10;
					UpdateNotification(1);
					break;
				case 2:
					textFix = "DGPS";
					FixAccuracy = 1;
					UpdateNotification(2);
					break;
				case 3:
					textFix = "PPS";
					FixAccuracy = 0.1F;
					UpdateNotification(3);
					break;
				case 4:
					textFix = "RTK";
					FixAccuracy = 0.02F;
					UpdateNotification(4);
					break;
				case 5:
					textFix = "FloatRTK";
					FixAccuracy = 0.5F;
					UpdateNotification(5);
					break;
				case 6:
					textFix = "Estimated";
					FixAccuracy = 2;
					UpdateNotification(6);
					break;
				case 7:
					textFix = "Manual";
					FixAccuracy = 0.01F;
					UpdateNotification(7);
					break;
				case 8:
					textFix = "Simulation";
					FixAccuracy = 0.01F;
					UpdateNotification(8);
					break;
				case 9:
					textFix = "WAAS";
					FixAccuracy = 1;
					UpdateNotification(9);
					break;
				default:
					textFix = "Unknown";
					FixAccuracy = 100;
					UpdateNotification(10);
				}
				LogMessage("Fix type is now " + textFix);
			}
			if (inSatsTracked != SatsTracked) {
				LogMessage("Using " + inSatsTracked + " satellites");
			}
			FixType = inFixType;
			SatsTracked = inSatsTracked;
			Elevation = inElevation;

			sendStatusMessageToUI();
			
			if (inFixType > 0) {
				isvalidline = true;
				if (SendMockLocation) mock.pushLocation(lat, lon, Elevation, Speed, Heading, FixAccuracy);
			}
		} else {
			FixType = 0;
			SatsTracked = 0;
			Elevation = 0;
			sendStatusMessageToUI();
		}
		return isvalidline;
	}
	private void ParseGPRMC(String line) {
		// Log.i("ParseRMC", line);
		String[] ary = line.split(",");
		float inSpeed = 0;
		float inHeading = 0;

		if (ary.length > 8) { // We have at least 9 fields
			if (ary[7].length() == 0) return;
			if (ary[8].length() == 0) return;
			try {
				inSpeed = Float.valueOf(ary[7].trim()).floatValue();
				inHeading = Float.valueOf(ary[8].trim()).floatValue();
			} catch (NumberFormatException nfe) {
			}
		}

		Speed = inSpeed;
		Heading = inHeading;
	}


	// UDP (Internal GNSS Receiver) Data Stuff
	public void SendDataToUDP(byte[] buffer) { // You run this from the main thread.
        ByteBuffer buf = ByteBuffer.wrap(buffer);
        new UDPClientSendData().execute(buf);
	}
	class UDPClientSendData extends AsyncTask<ByteBuffer, Void, Void> {
		@Override
		protected Void doInBackground(ByteBuffer... params) {
			try {
				DatagramSocket LocalhostUDPClientSocket = new DatagramSocket();
				InetAddress LocalhostIPAddress = InetAddress.getLocalHost();
				DatagramPacket sendPacket = new DatagramPacket(params[0].array(), params[0].array().length, LocalhostIPAddress, LocalhostUDPPort);
				LocalhostUDPClientSocket.send(sendPacket);
				//Log.d("UDP", "UDP port " + LocalhostUDPPort + ": send " + params[0].array().length);
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (SocketException e) {
				e.printStackTrace();
			} catch (IOException e) {
				LogMessage("UDP port error " + e.getMessage());
				//Log.d("UDP", "UDP port error " + e.getMessage());
				e.printStackTrace();
			}
			return null;
		}
	}
	
	
	
	// Save Data to File Stuff	
	private void SaveDataNMEAToFileAppend(byte[] buffer) {
		int copystart = 0;
		int inlen = buffer.length;
		//Log.d("RAWData", "Received " + inlen + " bytes. BinaryDataToSaveIndex is currently " + BinaryDataToSaveIndex);
		while (true) {
			if (DataNMEAToSaveIndex + (inlen - copystart) < 4096) { //Easy, won't fill buffer
				//Log.d("RAWData", "Appending " + (inlen - copystart) + " bytes. BinaryDataToSaveIndex=" + BinaryDataToSaveIndex);
				System.arraycopy(buffer, copystart, DataNMEAToSave, DataNMEAToSaveIndex, inlen - copystart);
				DataNMEAToSaveIndex += inlen - copystart;
				//Log.d("RAWData", "Append complete");
				break;

			} else { //Buffer will get full, need to write data to file
				int copylength = 4096 - DataNMEAToSaveIndex;
				//Log.d("RAWData", "Writing out " + BinaryDataToSaveIndex + "+" + copylength + " bytes.");
				System.arraycopy(buffer, copystart, DataNMEAToSave, DataNMEAToSaveIndex, copylength);
				DataNMEAToSaveIndex += copylength;
				copystart += copylength;
				SaveDataNMEAChunk();
				//Log.d("RAWData", "Write complete");
			}
		}
	}
	private void SaveDataNMEAChunk() {
		if (DataNMEAToSaveIndex > 0) {
			try {
				String state = Environment.getExternalStorageState();
				if (Environment.MEDIA_MOUNTED.equals(state)) { // We can read and write the media
					File sdcard = Environment.getExternalStorageDirectory();
					File dir = new File(sdcard.getAbsolutePath() + "/NTRIP/");
					dir.mkdirs();

					Calendar calendar = Calendar.getInstance();
					int year = calendar.get(Calendar.YEAR);
					int month = calendar.get(Calendar.MONTH) + 1;
					int day = calendar.get(Calendar.DAY_OF_MONTH);
					String filename = "GPS-" + year + "-" + Make2Digits(month) + "-" + Make2Digits(day) + ".txt";
					File file = new File(dir, filename);

					FileOutputStream f = new FileOutputStream(file, true);
					f.write(DataNMEAToSave, 0, DataNMEAToSaveIndex);
					f.flush();
					f.close();

					//Log.d("SaveRawDataChunk", "Saved data to file: " + filename);
				}
			} catch (Exception e) {
				//Log.d("SaveRawDataChunk", e.getMessage());
			}
			DataNMEAToSaveIndex = 0;
		}	
	}
	private void SaveDataDGPSToFileAppend(byte[] buffer) {
		int copystart = 0;
		int inlen = buffer.length;
		//Log.d("RAWData", "Received " + inlen + " bytes. BinaryDataToSaveIndex is currently " + BinaryDataToSaveIndex);
		while (true) {
			if (DataDGPSToSaveIndex + (inlen - copystart) < 4096) { //Easy, won't fill buffer
				//Log.d("RAWData", "Appending " + (inlen - copystart) + " bytes. BinaryDataToSaveIndex=" + BinaryDataToSaveIndex);
				System.arraycopy(buffer, copystart, DataDGPSToSave, DataDGPSToSaveIndex, inlen - copystart);
				DataDGPSToSaveIndex += inlen - copystart;
				//Log.d("RAWData", "Append complete");
				break;

			} else { //Buffer will get full, need to write data to file
				int copylength = 4096 - DataDGPSToSaveIndex;
				//Log.d("RAWData", "Writing out " + BinaryDataToSaveIndex + "+" + copylength + " bytes.");
				System.arraycopy(buffer, copystart, DataDGPSToSave, DataDGPSToSaveIndex, copylength);
				DataDGPSToSaveIndex += copylength;
				copystart += copylength;
				SaveDataDGPSChunk();
				//Log.d("RAWData", "Write complete");
			}
		}
	}
	private void SaveDataDGPSChunk() {
		if (DataDGPSToSaveIndex > 0) {
			try {
				String state = Environment.getExternalStorageState();
				if (Environment.MEDIA_MOUNTED.equals(state)) { // We can read and write the media
					File sdcard = Environment.getExternalStorageDirectory();
					File dir = new File(sdcard.getAbsolutePath() + "/NTRIP/");
					dir.mkdirs();

					Calendar calendar = Calendar.getInstance();
					int year = calendar.get(Calendar.YEAR);
					int month = calendar.get(Calendar.MONTH) + 1;
					int day = calendar.get(Calendar.DAY_OF_MONTH);
					String filename = "NTRIP-" + year + "-" + Make2Digits(month) + "-" + Make2Digits(day) + ".txt";
					File file = new File(dir, filename);

					FileOutputStream f = new FileOutputStream(file, true);
					f.write(DataDGPSToSave, 0, DataDGPSToSaveIndex);
					f.flush();
					f.close();

					//Log.d("SaveRawDataChunk", "Saved data to file: " + filename);
				}
			} catch (Exception e) {
				//Log.d("SaveRawDataChunk", e.getMessage());
			}
			DataDGPSToSaveIndex = 0;
		}	
	}
	
	final Messenger dataMessenger = new Messenger(new DataHandler(this));
	public static class DataHandler extends Handler { // Handler for data coming from the network and Bluetooth sockets
		private final WeakReference<NTRIPService> mService; 
		DataHandler(NTRIPService service) {
	        mService = new WeakReference<NTRIPService>(service);
	    }
		
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			NTRIPService service = mService.get();
	        if (service != null) {
	        	switch (msg.what) {
				case MSG_TIMER_TICK:
					service.onTimerTick();
					break;
				case MSG_NETWORK_GOT_DATA:
					byte[] buffer1 = (byte[]) msg.obj;
					service.ParseNetworkDataStream(buffer1);
					break;
				case MSG_NETWORK_TIMEOUT:
					//Log.i("handleMessage", "MSG_NETWORK_TIMEOUT");
					service.NetworkReceivedByteCount = 0;
					service.SendByteCountToActivity();
					if (!service.NetworkProtocol.equals("none")) { //Should be connected
						service.LogMessage("Network connection timed out.");
					}
					break;
				case MSG_NETWORK_FINISHED:
					//Log.i("handleMessage", "MSG_NETWORK_FINISHED");
					service.NetworkIsConnected = false;
					service.NetworkReConnectInTicks = 2;
					service.NetworkReceivedByteCount = 0;
					service.SendByteCountToActivity();
					service.LogMessage("Network connection dropped.");
					break;


				case MSG_BT_GOT_DATA:
					// Log.i("handleMessage", "MSG_BT_GOT_DATA");
					byte[] buffer2 = (byte[]) msg.obj;
					service.ParseBTDataStream(buffer2);
					break;
				case MSG_BT_LOG_MESSAGE:
					service.LogMessage((String) msg.obj);
					break;
				case MSG_BT_FINISHED:
					//Log.i("handleMessage", "MSG_BT_FINISHED");
					service.BTstop();
					break;
				//default:
				}
	        }
		}
	};
		
	
	private void UpdateNotification(int value) {
		mNotification.iconLevel = value;
		if (FixChangeAudioAlert) {
			mNotification.defaults |= Notification.DEFAULT_SOUND;	
		} else {
			mNotification.defaults = 0; //No sound
		}
		mNoticationManager.notify(R.string.app_name, mNotification);
	}
	private void TerminateNTRIPThread(boolean restart) {
		if (nThread != null) { // If the thread is currently running, close the socket and interrupt it.
			try {
				nis.close();
				nos.close();
				nsocket.close();
				//CloseUDPSocket();
			} catch (Exception e) {
			}
			Thread moribund = nThread;
			nThread = null;
			moribund.interrupt();
		}

		NetworkReceivedByteCount = 0;
		SendByteCountProgressBarVisibility(0);
		SendByteCountToActivity();
		//NTRIPShouldBeConnected = restart;
		if (restart) {
			NetworkReConnectInTicks = 2;
		} else {
			NetworkProtocol = "none"; //Don't automatically restart
		}
	}
	@Override
	public void onDestroy() {
		//SaveNMEAChunk(); //Write data to file
		SaveDataNMEAChunk(); //Write data to file
		SaveDataDGPSChunk(); //Write data to file
		
		// Kill threads
		if (timer != null) {timer.cancel();}
		TerminateNTRIPThread(false);
		BTstop();

		if (SendMockLocation) {
			SendMockLocation = false;
			mock.shutdown();
		}
		
		if (BTShouldAutoSwitch && BTwasDisabled) { // Turn BT off
			//Log.i(BTAG, "ON DESTROY: Turning BT back off");
			mBluetoothAdapter.disable();
			Toast.makeText(this, "Disabling Bluetooth...", Toast.LENGTH_SHORT).show();
		}
		//mNoticationManager.cancel(R.string.app_name);
		mNoticationManager.cancelAll(); //Kill all old notifications
		
		stopForeground(true);
		//Log.i("MyService", "Service Stopped.");
		isRunning = false;
		super.onDestroy();
	}
}