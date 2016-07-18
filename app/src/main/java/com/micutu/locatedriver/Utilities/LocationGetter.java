package com.micutu.locatedriver.Utilities;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.micutu.locatedriver.R;

public class LocationGetter implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {
    private final static String TAG = LocationGetter.class.getSimpleName();

    public final static int LOCATION_REQUEST_INTERVAL = 1000 * 10;
    public final static int LOCATION_REQUEST_FASTEST_INTERVAL = 1000;
    public final static int LOCATION_REQUEST_MAX_WAIT_TIME = 2 * 60 * 1000;

    private Context context = null;
    private GoogleApiClient mGoogleApiClient;
    private OnLocationGetListener onLocationGetListener = null;
    private Location currentLocation = null;

    private Handler handler = null;

    public LocationGetter(Context context, OnLocationGetListener listener) {
        this.context = context;
        this.onLocationGetListener = listener;

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mGoogleApiClient.connect();
    }

    private void sendLocationToListener(Location location) {
        //Log.d(TAG, "sendLocationToListener");

        if(mGoogleApiClient.isConnected()) {
            //Log.d(TAG, "DISCONNECT LOCATION UPDATES");
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }

        if(handler != null) {
            //Log.d(TAG, "REMOVE TIMEOUTS.");
            handler.removeCallbacksAndMessages(null);
        }

        onLocationGetListener.onLocationGet(location);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        //Log.d(TAG, "onConnected()");

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //Log.d(TAG, "Permission check error.");
            sendLocationToListener(null);
            return;
        }


        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(this.LOCATION_REQUEST_INTERVAL);
        locationRequest.setFastestInterval(this.LOCATION_REQUEST_FASTEST_INTERVAL);
        locationRequest.setMaxWaitTime(this.LOCATION_REQUEST_MAX_WAIT_TIME);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);

        handler = new Handler();
        handler.postDelayed(
                new Runnable() {
                    public void run() {
                        //Log.d(TAG, "MAX WAIT TIME EXPIRED");
                        sendLocationToListener(currentLocation);
                    }
                },
                this.LOCATION_REQUEST_MAX_WAIT_TIME);
    }

    @Override
    public void onLocationChanged(Location location) {
        //Log.d(TAG, "onLocationChanged() " + location.getAccuracy() + " - " + location.getElapsedRealtimeNanos() + " - " + location.getTime() + " - " + location);


        //the most accurate location
        if (currentLocation == null || location.getAccuracy() < currentLocation.getAccuracy()) {
            currentLocation = location;
        }

        //Log.d(TAG, "Diferrence: " + location.getAccuracy() + " - " + currentLocation.getAccuracy());

        //send location if is already lower than 100
        if (currentLocation.getAccuracy() < 100) {
            sendLocationToListener(currentLocation);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        //Log.d(TAG, "CONNECTION SUSPENDED");
        sendLocationToListener(currentLocation);
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        //Log.d(TAG, "CONNECTION FAILED");
        sendLocationToListener(null);
    }

    public int getLocationMode() {
        return LocationGetter.getLocationMode(context);
    }

    public static int getLocationMode(Context context) {
        try {
            return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
        } catch (Settings.SettingNotFoundException e) {
            return -1;
        }
    }

    public String locationToString(int mode) {
        return LocationGetter.locationToString(context, mode);
    }

    public static String locationToString(Context context, int mode) {
        switch (mode) {
            case Settings.Secure.LOCATION_MODE_OFF:
                return context.getResources().getString(R.string.location_mode_off);
            case Settings.Secure.LOCATION_MODE_BATTERY_SAVING:
                return context.getResources().getString(R.string.location_battery_saving);
            case Settings.Secure.LOCATION_MODE_SENSORS_ONLY:
                return context.getResources().getString(R.string.locateion_sensors_only);
            case Settings.Secure.LOCATION_MODE_HIGH_ACCURACY:
                return context.getResources().getString(R.string.location_high_accuracy);
            default:
                return "Error";
        }
    }

    public interface OnLocationGetListener {
        public void onLocationGet(Location location);
    }
}
