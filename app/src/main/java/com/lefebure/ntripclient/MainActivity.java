package com.lefebure.ntripclient;

import java.lang.ref.WeakReference;
import java.text.DecimalFormat;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
//import android.util.Log;

public class MainActivity extends Activity {
	Button btnService;
	TextView textFix, textInfo1, textInfo2, textLog, textBytes;
	ScrollView svLog;
	ProgressBar ProgressBar;
	private Boolean SaveNMEAToFile = false;
	private Boolean KeepScreenOn = false;
	DecimalFormat df = new DecimalFormat();
	ImageView mLogoImage;
	
	boolean mIsBound;
	final Messenger inMessenger = new Messenger(new IncomingHandler(this));
	private Messenger outMessenger = null;
	static class IncomingHandler extends Handler {
		private final WeakReference<MainActivity> mTarget; 
		IncomingHandler(MainActivity target) {
			mTarget = new WeakReference<MainActivity>(target);
	    }
		
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			MainActivity target = mTarget.get();
			if (target != null) {
				switch (msg.what) {
				case NTRIPService.MSG_UPDATE_STATUS:
					Bundle b1 = msg.getData();
					target.textFix.setText(b1.getString("fix"));
					target.textInfo1.setText(b1.getString("info1"));
					target.textInfo2.setText(b1.getString("info2"));
					break;
				case NTRIPService.MSG_UPDATE_BYTESIN:
					int bytesin = msg.arg1;
					//Log.i("Activity", "Total bytes: " + bytesin);
					if (bytesin > 0) {
						target.ProgressBar.setProgress(bytesin % 4096);
						target.textBytes.setText(target.df.format((int)bytesin) + " Bytes");
					}
					break;
				case NTRIPService.MSG_SHOW_PROGRESSBAR:
					int vis = msg.arg1;
					if (vis == 1) {
						target.ProgressBar.setProgress(0);
						target.ProgressBar.setVisibility(View.VISIBLE);
					} else{
						target.ProgressBar.setVisibility(View.INVISIBLE);
						target.textBytes.setText("");
					}
					break;
				case NTRIPService.MSG_UPDATE_LOG_APPEND:
					Bundle b2 = msg.getData();
					target.LogMessage(b2.getString("logappend"));
					break;
				case NTRIPService.MSG_UPDATE_LOG_FULL:
					Bundle b3 = msg.getData();
					target.textLog_setText(b3.getString("logfull"));
					break;
				case NTRIPService.MSG_THREAD_SUICIDE:
					//Log.i("Activity", "Service informed Activity of Suicide.");
					target.informOfServiceThreadSuicide();
					break;
				default:
				}
			}
		}
	}
	void textLog_setText(String s) {
		textLog.setText(s);
		svLog.post(new Runnable() { 
			public void run() { 
				svLog.fullScroll(ScrollView.FOCUS_DOWN); 
			} 
		});
	}
	void informOfServiceThreadSuicide() {
		doUnbindService();
		stopService(new Intent(MainActivity.this, NTRIPService.class));
		LogMessage("Service Stopped");
	}
	

	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			outMessenger = new Messenger(service);
			try {
				//Register client with service
				Message msg = Message.obtain(null, NTRIPService.MSG_REGISTER_CLIENT);
				msg.replyTo = inMessenger;
				outMessenger.send(msg);

				//Request a status update.
				msg = Message.obtain(null, NTRIPService.MSG_UPDATE_STATUS, 0, 0);
				outMessenger.send(msg);
				
				//Request full log from service.
				msg = Message.obtain(null, NTRIPService.MSG_UPDATE_LOG_FULL, 0, 0);
				outMessenger.send(msg);
				
			} catch (RemoteException e) {
				// In this case the service has crashed before we could even do anything with it
			}
		}
		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been unexpectedly disconnected - process crashed.
			outMessenger = null;
		}
	};


	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTitle(R.string.app_name_long);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		// Possible work around for market launches. See http://code.google.com/p/android/issues/detail?id=2373
		// for more details. Essentially, the market launches the main activity on top of other activities.
		// we never want this to happen. Instead, we check if we are the root and if not, we finish.
		// http://stackoverflow.com/questions/4341600/how-to-prevent-multiple-instances-of-an-activity-when-it-is-launched-with-differ
		if (!isTaskRoot()) {
		    final Intent intent = getIntent();
		    final String intentAction = intent.getAction(); 
		    if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intentAction != null && intentAction.equals(Intent.ACTION_MAIN)) {
		        //Log.w(LOG_TAG, "Main Activity is not the root.  Finishing Main Activity instead of launching.");
		        finish();
		        return;       
		    }
		}
				
		btnService = (Button)findViewById(R.id.btnService);
		textFix = (TextView)findViewById(R.id.textFix);textFix.setText("Disconnected");
		textInfo1 = (TextView)findViewById(R.id.textInfo1);textInfo1.setText("");
		textInfo2 = (TextView)findViewById(R.id.textInfo2);textInfo2.setText("");
		textLog = (TextView)findViewById(R.id.textLog);
		svLog = (ScrollView)findViewById(R.id.svLog);
		textLog.setText(SetDefaultStatusText());
		ProgressBar=(ProgressBar)findViewById(R.id.progressbar);
		ProgressBar.setVisibility(View.INVISIBLE);
		textBytes = (TextView)findViewById(R.id.textBytes);
		mLogoImage = (ImageView)findViewById(R.id.logo_image);
		
		btnService.setOnClickListener(ListenerBtnService);
		textInfo1.setOnClickListener(ListenerToggleDisplayMsgType);
		textInfo2.setOnClickListener(ListenerToggleDisplayMsgType);
		
		restoreMe(savedInstanceState);
		CheckIfServiceIsRunning();
	}
	private String SetDefaultStatusText() {
		String t = "Comments? Lance@Lefebure.com"; 
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			return "Version: " + packageInfo.versionName + "\n" + t;
		} catch (PackageManager.NameNotFoundException e) {
			return t;	
		}
	}
	@Override
	public void onResume() {
		super.onResume();
		
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		SaveNMEAToFile = preferences.getBoolean("savenmeadata", false);
		KeepScreenOn = preferences.getBoolean("keepscreenon", false);
		
		if (mIsBound) { // Request a status update.
			if (outMessenger != null) {
				try {
					//Request service reload preferences, in case those changed
					Message msg = Message.obtain(null, NTRIPService.MSG_RELOAD_PREFERENCES, 0, 0);
					msg.replyTo = inMessenger;
					outMessenger.send(msg);
				} catch (RemoteException e) {}
			}
		}
		
		if (KeepScreenOn) {
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
	}
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString("textfix", textFix.getText().toString());
		outState.putString("textinfo1", textInfo1.getText().toString());
		outState.putString("textinfo2", textInfo2.getText().toString());
		outState.putString("connectbuttontext", btnService.getText().toString());
		outState.putInt("progressbarvalue", ProgressBar.getProgress());
		outState.putString("textlog", textLog.getText().toString());
		outState.putString("textbytes", textBytes.getText().toString());
	}
	private void restoreMe(Bundle state) {
		if (state!=null) {
			textFix.setText(state.getString("textfix"));
			textInfo1.setText(state.getString("textinfo1"));
			textInfo2.setText(state.getString("textinfo2"));
			btnService.setText(state.getString("connectbuttontext"));
			ProgressBar.setProgress(state.getInt("progressbarvalue"));
			textLog.setText(state.getString("textlog"));
			textBytes.setText(state.getString("textbytes"));
			svLog.post(new Runnable() {
			    public void run() {
			    	svLog.fullScroll(ScrollView.FOCUS_DOWN);
			    }
			});
		}
	}

	private void CheckIfServiceIsRunning() {
		//If the service is running when the activity starts, we want to automatically bind to it.
		if (NTRIPService.isRunning()) {
			doBindService();
			mLogoImage.setVisibility(View.GONE);
		} else {
			btnService.setText("Connect");
			if (textLog.length() > 60) { //More text here than the default start-up amount
				mLogoImage.setVisibility(View.GONE);
			}
			NotificationManager mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
			mNM.cancelAll();
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		//startActivity(new Intent(this, EditPreferences.class));
		super.onCreateOptionsMenu(menu);
		menu.add(0, 0, 0, "Settings").setIcon(R.drawable.settings).setAlphabeticShortcut('s');
//		if (SaveNMEAToFile && mIsBound) { //If service is running and LogNEMAToFile is selected, show option to add notes to file.
//			menu.add(0, 9, 0, "Record Note Here").setIcon(R.drawable.settings).setAlphabeticShortcut('l');
//		}
		return true;
	}
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		//startActivity(new Intent(this, EditPreferences.class));
		super.onCreateOptionsMenu(menu);
		
		menu.removeItem(1);
		if (SaveNMEAToFile && mIsBound) { //If service is running and LogNEMAToFile is selected, show option to add notes to file.
		//	menu.add(0, 1, 0, "Record Note Here").setIcon(R.drawable.flag).setAlphabeticShortcut('l');
		}
		return true;
	}
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch(item.getItemId()) {
		case 0: //Settings
			startActivity(new Intent(this, EditPreferences.class));
			return true;
		case 9: //Record Note Here
			AskUserAboutNote();
			return true;
		}
		return super.onMenuItemSelected(featureId, item);
	}
	private void AskUserAboutNote() {
		final AlertDialog.Builder alert = new AlertDialog.Builder(this);
		final EditText input = new EditText(this);
		alert.setView(input);
		alert.setPositiveButton("Save", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				Bundle b = new Bundle();
				b.putString("note", input.getText().toString().trim());
				if (outMessenger != null) {
					try {
						Message msg = Message.obtain(null, NTRIPService.MSG_ADD_NOTE_TO_NMEA);
						msg.replyTo = inMessenger;
						msg.setData(b);
						outMessenger.send(msg);
					} catch (RemoteException e) {}
				}
				Toast.makeText(getApplicationContext(), "Note Sent to Service", Toast.LENGTH_SHORT).show();
			}
		});
		alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				dialog.cancel();
			}
		});
		alert.show();
	}
	
	private OnClickListener ListenerBtnService = new OnClickListener() {
		public void onClick(View v){
			mLogoImage.setVisibility(View.GONE);

			if(btnService.getText() == "Connect"){
				LogMessage("Starting Service");
				startService(new Intent(MainActivity.this, NTRIPService.class));
				doBindService();
//				if (KeepScreenOn) {
//					getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//				}
			} else {
				doUnbindService();
				stopService(new Intent(MainActivity.this, NTRIPService.class));
				LogMessage("Service Stopped");
//				if (KeepScreenOn) {
//					getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//				}
			}
		}
	};
	private OnClickListener ListenerToggleDisplayMsgType = new OnClickListener() {
		public void onClick(View v){
			if(btnService.getText() != "Connect"){
				if (outMessenger != null) {
					try {
						//Request change of display message type
						Message msg = Message.obtain(null, NTRIPService.MSG_TOGGLE_LOG_TYPE, 0, 0);
						msg.replyTo = inMessenger;
						outMessenger.send(msg);
					} catch (RemoteException e) {}
				}
			}
		}
	};

	private void LogMessage(String m) {
		//Check if log is too long, shorten if necessary.
		if (textLog.getText().toString().length() > 4000) {
			String templog = textLog.getText().toString();
			int tempi = templog.length();
			tempi = templog.indexOf("\n", tempi-1000);
			textLog.setText(templog.substring(tempi+1));
		}
		
		textLog.append("\n" + m);
		svLog.post(new Runnable() { 
		    public void run() { 
		    	svLog.fullScroll(ScrollView.FOCUS_DOWN); 
		    } 
		}); 
	}

	void doBindService() {
		// Establish a connection with the service.  We use an explicit
		// class name because there is no reason to be able to let other
		// applications replace our component.
		bindService(new Intent(this, NTRIPService.class), mConnection, Context.BIND_AUTO_CREATE);
		textFix.setText("Connecting...");
		btnService.setText("Disconnect");
		mIsBound = true;
		if (outMessenger != null) {
			try {
				//Request status update
				Message msg = Message.obtain(null, NTRIPService.MSG_UPDATE_STATUS, 0, 0);
				msg.replyTo = inMessenger;
				outMessenger.send(msg);

				//Request full log from service.
				msg = Message.obtain(null, NTRIPService.MSG_UPDATE_LOG_FULL, 0, 0);
				outMessenger.send(msg);
			} catch (RemoteException e) {}
		}
	}
	void doUnbindService() {
		if (mIsBound) {
			// If we have received the service, and hence registered with it, then now is the time to unregister.
			if (outMessenger != null) {
				try {
					Message msg = Message.obtain(null, NTRIPService.MSG_UNREGISTER_CLIENT);
					msg.replyTo = inMessenger;
					outMessenger.send(msg);
				} catch (RemoteException e) {
					// There is nothing special we need to do if the service has crashed.
				}
			}
			// Detach our existing connection.
			unbindService(mConnection);
			mIsBound = false;
		}
		textFix.setText("Disconnected");
		textInfo1.setText("");
		textInfo2.setText("");
		ProgressBar.setVisibility(View.INVISIBLE);
		textBytes.setText("");
		btnService.setText("Connect");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (KeepScreenOn) {
			getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
		
		try {
			doUnbindService();
		} catch (Throwable t) {
			//Log.e("MainActivity", "Failed to unbind from the service", t);
		}
	}
}