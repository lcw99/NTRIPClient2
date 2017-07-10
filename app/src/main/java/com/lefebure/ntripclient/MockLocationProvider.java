package com.lefebure.ntripclient;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

public class MockLocationProvider {
	String providerName;
	Context ctx;

	public MockLocationProvider(String name, Context ctx) {
		this.providerName = name;
		this.ctx = ctx;

		LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
		lm.addTestProvider(providerName, false, false, false, false, false, true, true, 0, 5);
		lm.setTestProviderEnabled(providerName, true);
	}

	public void pushLocation(double lat, double lon, float elev, float speed, float bearing, float accuracy) {
		Location mockLocation = new Location(providerName);
		mockLocation.setLatitude(lat);
		mockLocation.setLongitude(lon);
		mockLocation.setAltitude(elev); //in meters
		mockLocation.setSpeed(speed * 0.514444F); //from knots to meters/second
		mockLocation.setBearing(bearing); //in degrees
		mockLocation.setAccuracy(accuracy); //in meters
		mockLocation.setTime(System.currentTimeMillis());
		LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
		lm.setTestProviderLocation(providerName, mockLocation);
	}

	public void shutdown() {
		try {
			LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
			lm.removeTestProvider(providerName);	
		}
		catch (Exception e) {}
	}
}
