package com.lefebure.ntripclient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

//import android.bluetooth.BluetoothAdapter;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;

public class EditPreferences extends PreferenceActivity implements OnSharedPreferenceChangeListener {
	private CharSequence[] StreamListEntries = null;
	private CharSequence[] StreamListEntryValues = null;
	
	private boolean ShouldSeeBluetoothThings = true;
	private boolean CanSeeBluetoothThings = true;
	private boolean ShouldSeeUDPThings = true;
	private boolean CanSeeUDPThings = true;
	
	private boolean ShouldSeeAutoConfigCommand = true;
	private boolean CanSeeAutoConfigCommand = true;
	
	private boolean ShouldSeeServerPort = true;
	private boolean ShouldSeeNTRIPThings = true;
	private boolean ShouldSeeLatLon = true;
	private boolean CanSeeServerPort = true;
	private boolean CanSeeNTRIPThings = true;
	private boolean CanSeeLatLon = true;
	
	Preference pinternaludpport;
	Preference pbluetooth_mac;
	Preference pbluetoothconnectionmethod;
	Preference pautoswitchbluetooth;
	Preference preceiverautoconfig;
	Preference preceiverautoconfigcommand;
	Preference psavenmeadata;
	Preference psendmocklocation;
	
	Preference pntripcasterip;
	Preference pntripcasterport;
	Preference pntripusername;
	Preference pntrippassword;
	Preference pntripstream;
	Preference pntriplocation;
	Preference pntriplongitude;
	Preference pntriplatitude;

	Preference pinfo1;
	Preference pinfo2;
	Preference pfixchangebeep;
	

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);

		pinternaludpport = findPreference("internaludpport");
		pbluetooth_mac = findPreference("bluetooth_mac");
		pbluetoothconnectionmethod = findPreference("bluetoothconnectionmethod");
		pautoswitchbluetooth = findPreference("autoswitchbluetooth");
		preceiverautoconfig = findPreference("receiverautoconfig");
		preceiverautoconfigcommand = findPreference("receiverautoconfigcommands");
		psavenmeadata = findPreference("savenmeadata");
		psendmocklocation = findPreference("sendmocklocation");
		
		pntripcasterip = findPreference("ntripcasterip");
		pntripcasterport = findPreference("ntripcasterport");
		pntripusername = findPreference("ntripusername");
		pntrippassword = findPreference("ntrippassword");
		pntripstream = findPreference("ntripstream");
		pntriplocation = findPreference("ntriplocation");
		pntriplongitude = findPreference("ntriplongitude");
		pntriplatitude = findPreference("ntriplatitude");
		
		pinfo1 = findPreference("info1");
		pinfo2 = findPreference("info2");
		pfixchangebeep = findPreference("fixchangebeep");
		
		
//		//Check if there is a Bluetooth device. If not, disable option for Receiver settings
//		Preference pr = findPreference("receiversettings");
//		BluetoothAdapter bta = BluetoothAdapter.getDefaultAdapter();
//		if(bta == null) {
//			pr.setEnabled(false);
//			pr.setSummary("No Bluetooth Device Found");
//		} else {
//			pr.setEnabled(true);	
//			pr.setSummary("");
//		}

        //Build list of mountpoints from string
        ListPreference StreamList = (ListPreference) findPreference("ntripstream");
        PopulateStreamList();
        StreamList.setEntryValues(StreamListEntryValues);
        StreamList.setEntries(StreamListEntries);

        // Display the current values
		for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
			initSummary(getPreferenceScreen().getPreference(i));
		}

		Preference p = findPreference("ntripsourcetable"); //This hides the sourcetable preference since the user doesn't need to see it.
		((PreferenceGroup) findPreference("ntripsettings")).removePreference(p);
	}
	private void PopulateStreamList() {
    	List<String> Entries = new ArrayList<String>();
    	List<String> EntryValues = new ArrayList<String>();
    	Entries.add("Refresh Stream List");
    	EntryValues.add("");
    	String sourcetable = getPreferenceScreen().getSharedPreferences().getString("ntripsourcetable","");
    	String[] lines = sourcetable.split("\\r?\\n");
    	for (int i=0; i < lines.length; i++) {
    		String[] fields = lines[i].split(";");
    		if (fields.length > 4) {
    			if (fields[0].toLowerCase(Locale.ENGLISH).equals("str")) {
    				Entries.add(fields[1]);
                	EntryValues.add(fields[1]);
    			}
    		}
    	}
    	StreamListEntries = Entries.toArray(new CharSequence[Entries.size()]);
    	StreamListEntryValues = EntryValues.toArray(new CharSequence[EntryValues.size()]);
      }
	@Override
    protected void onResume() {
        super.onResume();

        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // Do something. A preference value changed
		updatePrefSummary(findPreference(key));
		
		if (findPreference(key) instanceof ListPreference) {
			if (key.equals("receiverconnection")) {
				ListPreference listPref = (ListPreference) findPreference(key);
				if (listPref.getEntry().equals("Internal via UDP (Development Only)")) {
					//Throw warning message
					new AlertDialog.Builder(this)
					.setTitle("Notice:")
					.setMessage("'Internal via UDP' is currently in OEM development. Unless you are using development hardware that specifically supports this functionality, it won't do anything for you.")
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setNegativeButton(android.R.string.yes, null).show();
				}
			} else if (key.equals("bluetoothconnectionmethod")) {
				ListPreference listPref = (ListPreference) findPreference(key);
				if (listPref.getEntry().equals("Insecure (Default)")) {
					if (Integer.valueOf(android.os.Build.VERSION.SDK_INT) < 10) { //This device is older than 2.3.3.
						new AlertDialog.Builder(this)
						.setTitle("Notice:")
						.setMessage("The Bluetooth Connection Method 'Insecure' requires Android 2.3.3 or above. Please select a different connection method.")
						.setIcon(android.R.drawable.ic_dialog_alert)
						.setNegativeButton(android.R.string.yes, null).show();
					}
				}
			}
		}
		
    }
	private void initSummary(Preference p){
		if (p instanceof PreferenceScreen) {
			PreferenceScreen pScr = (PreferenceScreen) p;
			for (int i = 0; i < pScr.getPreferenceCount(); i++) {
				initSummary(pScr.getPreference(i));
			}
		} else {
			updatePrefSummary(p);
		}

     }
	
	private void updatePrefSummary(Preference p){
		updatePrefSummary(p, true);
	}
	private void updatePrefSummary(Preference p, boolean updatevis){
		if (p instanceof ListPreference) {
        	ListPreference listPref = (ListPreference) p;
        	p.setSummary(listPref.getEntry());

        	if (p.getKey().equals("receiverconnection")) {
        		if (listPref.getEntry().equals("External via Bluetooth")) {
        			ShouldSeeBluetoothThings = true;
        			ShouldSeeUDPThings = false;
        		} else if (listPref.getEntry().equals("Internal via UDP (Development Only)")) {
        			ShouldSeeBluetoothThings = false;
        			ShouldSeeUDPThings = true;
        		} else { //None
        			ShouldSeeBluetoothThings = false;
        			ShouldSeeUDPThings = false;
        		}
        		if (updatevis) UpdateVisibilities();
        	}
        	if (p.getKey().equals("receiverautoconfig")) {
        		if (listPref.getEntry().equals("Custom")) {
        			ShouldSeeAutoConfigCommand = true;
        		} else {
        			ShouldSeeAutoConfigCommand = false;
        		}
        		if (updatevis) UpdateVisibilities();
        	}
        	if (p.getKey().equals("networkprotocol")) {
        		if (listPref.getEntry().equals("NTRIP v1.0")) {
        			ShouldSeeServerPort = true;
        			ShouldSeeNTRIPThings = true;
        		} else if (listPref.getEntry().equals("Raw TCP/IP")) {
        			ShouldSeeServerPort = true;
        			ShouldSeeNTRIPThings = false;
        		} else { //None
        			ShouldSeeServerPort = false;
        		}
        		if (updatevis) UpdateVisibilities();
        	}
        	if (p.getKey().equals("ntriplocation")) {
        		if (listPref.getEntry().equals("Automatic")) {
        			ShouldSeeLatLon = false;
        		} else {
        			ShouldSeeLatLon = true;
        		}
        		if (updatevis) UpdateVisibilities();
        	}

        } else if (p instanceof EditTextPreference) {
        	EditTextPreference editTextPref = (EditTextPreference) p; 
        	p.setSummary(editTextPref.getText());

        } else if (p instanceof CheckBoxPreference) {
        	if (p.getKey().equals("savenmeadata")) {
        		CheckBoxPreference checkPref = (CheckBoxPreference) p;
        		checkPref.setSummary(p.getSharedPreferences().getBoolean(p.getKey(), false) ? "/NTRIP/GPS-YYYY-MM-DD.txt" : "");
        	}
        	if (p.getKey().equals("savedgpsdata")) {
        		CheckBoxPreference checkPref = (CheckBoxPreference) p;
        		checkPref.setSummary(p.getSharedPreferences().getBoolean(p.getKey(), false) ? "/NTRIP/NTRIP-YYYY-MM-DD.txt" : "");
        	}
        }
    }
	
	
	private void UpdateVisibilities(){
		if (ShouldSeeBluetoothThings) {
			if (!CanSeeBluetoothThings) ShowBluetoothThings();
			if (ShouldSeeAutoConfigCommand) {
				if (!CanSeeAutoConfigCommand) ShowAutoConfigCommand();
			} else { // !ShouldSeeAutoConfigCommand
				if (CanSeeAutoConfigCommand) HideAutoConfigCommand();
			}
		} else { // !ShouldSeeBluetoothThings
			if (CanSeeBluetoothThings) HideBluetoothThings();
			if (CanSeeAutoConfigCommand) HideAutoConfigCommand();
		}
		if (ShouldSeeUDPThings) {
			if (!CanSeeUDPThings) ShowUDPThings();
		} else { // !ShouldSeeUDPThings
			if (CanSeeUDPThings) HideUDPThings();
		}
		if (ShouldSeeServerPort) {
			if (!CanSeeServerPort) ShowServerPort();
			if (ShouldSeeNTRIPThings) {
				//pntripcasterip.setTitle("Caster IP");
				//pntripcasterport.setTitle("Caster Port");
				if (!CanSeeNTRIPThings) ShowNTRIPThings();
				if (ShouldSeeLatLon) {
					if (!CanSeeLatLon) ShowLatLon();
				} else {
					if (CanSeeLatLon) HideLatLon();
				}
			} else {
				//pntripcasterip.setTitle("Server IP");
				//pntripcasterport.setTitle("Server Port");
				if (CanSeeNTRIPThings) HideNTRIPThings();
				if (CanSeeLatLon) HideLatLon();
			}
		} else {
			if (CanSeeServerPort) HideServerPort();
			if (CanSeeNTRIPThings) HideNTRIPThings();
			if (CanSeeLatLon) HideLatLon();
		}
	}

		
	
	
	private void HideBluetoothThings(){
		((PreferenceGroup) findPreference("receiversettings")).removePreference(pbluetooth_mac);
		((PreferenceGroup) findPreference("receiversettings")).removePreference(pbluetoothconnectionmethod);
		((PreferenceGroup) findPreference("receiversettings")).removePreference(pautoswitchbluetooth);
		((PreferenceGroup) findPreference("receiversettings")).removePreference(preceiverautoconfig);
		((PreferenceGroup) findPreference("receiversettings")).removePreference(psavenmeadata);
		((PreferenceGroup) findPreference("receiversettings")).removePreference(psendmocklocation);
		((PreferenceGroup) findPreference("displaysettings")).removePreference(pinfo1);
		((PreferenceGroup) findPreference("displaysettings")).removePreference(pinfo2);
		((PreferenceGroup) findPreference("displaysettings")).removePreference(pfixchangebeep);
		CanSeeBluetoothThings = false;
	}
	private void ShowBluetoothThings(){
		((PreferenceGroup) findPreference("receiversettings")).addPreference(pbluetooth_mac);
		((PreferenceGroup) findPreference("receiversettings")).addPreference(pbluetoothconnectionmethod);
		((PreferenceGroup) findPreference("receiversettings")).addPreference(pautoswitchbluetooth);
		((PreferenceGroup) findPreference("receiversettings")).addPreference(preceiverautoconfig);
		((PreferenceGroup) findPreference("receiversettings")).addPreference(psavenmeadata);
		((PreferenceGroup) findPreference("receiversettings")).addPreference(psendmocklocation);
		((PreferenceGroup) findPreference("displaysettings")).addPreference(pinfo1);
		((PreferenceGroup) findPreference("displaysettings")).addPreference(pinfo2);
		((PreferenceGroup) findPreference("displaysettings")).addPreference(pfixchangebeep);
		updatePrefSummary(pbluetooth_mac);
		updatePrefSummary(pbluetoothconnectionmethod, false);
		updatePrefSummary(pautoswitchbluetooth);
		updatePrefSummary(preceiverautoconfig, false);
		updatePrefSummary(psavenmeadata);
		updatePrefSummary(psendmocklocation);
		CanSeeBluetoothThings = true;
	}
	private void HideAutoConfigCommand(){
		((PreferenceGroup) findPreference("receiversettings")).removePreference(preceiverautoconfigcommand);
		CanSeeAutoConfigCommand = false;
	}
	private void ShowAutoConfigCommand(){
		((PreferenceGroup) findPreference("receiversettings")).addPreference(preceiverautoconfigcommand);
		updatePrefSummary(preceiverautoconfigcommand);
		CanSeeAutoConfigCommand = true;
	}
	
	
	private void HideUDPThings(){
		((PreferenceGroup) findPreference("receiversettings")).removePreference(pinternaludpport);
		CanSeeUDPThings = false;
	}
	private void ShowUDPThings(){
		((PreferenceGroup) findPreference("receiversettings")).addPreference(pinternaludpport);
		updatePrefSummary(pinternaludpport);
		CanSeeUDPThings = true;
	}
private void HideServerPort(){
		((PreferenceGroup) findPreference("ntripsettings")).removePreference(pntripcasterip);
		((PreferenceGroup) findPreference("ntripsettings")).removePreference(pntripcasterport);
						
		CanSeeServerPort = false;
	}
	private void ShowServerPort(){
		((PreferenceGroup) findPreference("ntripsettings")).addPreference(pntripcasterip);
		((PreferenceGroup) findPreference("ntripsettings")).addPreference(pntripcasterport);
		updatePrefSummary(pntripcasterip);
		updatePrefSummary(pntripcasterport);
		
		CanSeeServerPort = true;
	}
	private void HideNTRIPThings(){
		((PreferenceGroup) findPreference("ntripsettings")).removePreference(pntripusername);
		((PreferenceGroup) findPreference("ntripsettings")).removePreference(pntrippassword);
		((PreferenceGroup) findPreference("ntripsettings")).removePreference(pntripstream);
		((PreferenceGroup) findPreference("ntripsettings")).removePreference(pntriplocation);
		
		CanSeeNTRIPThings = false;
	}
	private void ShowNTRIPThings(){
		((PreferenceGroup) findPreference("ntripsettings")).addPreference(pntripusername);
		((PreferenceGroup) findPreference("ntripsettings")).addPreference(pntrippassword);
		((PreferenceGroup) findPreference("ntripsettings")).addPreference(pntripstream);
		((PreferenceGroup) findPreference("ntripsettings")).addPreference(pntriplocation);
		updatePrefSummary(pntripusername);
		updatePrefSummary(pntrippassword);
		updatePrefSummary(pntripstream, false);
		updatePrefSummary(pntriplocation, false);
		
		CanSeeNTRIPThings = true;
	}
	private void HideLatLon(){
		((PreferenceGroup) findPreference("ntripsettings")).removePreference(pntriplatitude);
		((PreferenceGroup) findPreference("ntripsettings")).removePreference(pntriplongitude);
		
		CanSeeLatLon = false;
	}
	private void ShowLatLon(){
		((PreferenceGroup) findPreference("ntripsettings")).addPreference(pntriplatitude);
		((PreferenceGroup) findPreference("ntripsettings")).addPreference(pntriplongitude);
		updatePrefSummary(pntriplatitude);
		updatePrefSummary(pntriplongitude);
		
		CanSeeLatLon = true;
	}
	
	
	@Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }
}
