package com.micutu.locatedriver.BroadcastReceivers;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;
import com.micutu.locatedriver.Model.LDPlace;
import com.micutu.locatedriver.R;
import com.micutu.locatedriver.Utilities.SMSSender;

import java.util.ArrayList;

public class SmsReceiver extends BroadcastReceiver implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {
    private String TAG = SmsReceiver.class.getSimpleName();

    private Context context = null;
    private PendingResult broadcastResult = null;

    private GoogleApiClient mGoogleApiClient;

    private SharedPreferences settings;
    private LDPlace place = null;
    private String keyword = null;
    private boolean keywordReceivedSms = false;
    private boolean gpsSms = false;
    private boolean googleMapsSms = false;
    private boolean networkSms = false;
    private Location location = null;
    private boolean locationSent = false;
    private String phoneNumber = null;


    public SmsReceiver() {
    }

    @Override
    public void onReceive(final Context context, Intent intent) {
        broadcastResult = goAsync();
        this.context = context; //save the context for further use

        getSettings(context);
        if (keyword.length() == 0) { //end if no keyword is set
            finishBroadcast("NO KEYWORD");
            return;
        }

        ArrayList<SmsMessage> list = getMessagesWithKeyword(keyword, intent.getExtras());

        if (list.size() == 0) {
            finishBroadcast("NO MESSAGE!");
            return;
        }


        SmsMessage sms = list.get(0);
        phoneNumber = sms.getOriginatingAddress();

        Toast.makeText(context, "Message received: " + sms.getOriginatingAddress() + " - " + sms.getMessageBody().toString(), Toast.LENGTH_SHORT).show();

        if (keywordReceivedSms) {
            SMSSender.sendAcknowledgeMessage(context, sms.getOriginatingAddress());
        }

        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();


        mGoogleApiClient.connect();
    }

    private ArrayList<SmsMessage> getMessagesWithKeyword(String keyword, Bundle bundle) {
        ArrayList<SmsMessage> list = new ArrayList<SmsMessage>();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            for (int i = 0; i < pdus.length; i++) {
                SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdus[i]);
                if (sms.getMessageBody().toString().equals(keyword)) {
                    list.add(sms);
                }
            }
        }
        return list;
    }

    public void getSettings(Context context) {
        settings = PreferenceManager.getDefaultSharedPreferences(context);

        keyword = settings.getString("keyword", "");
        keywordReceivedSms = settings.getBoolean("settings_detected_sms", false);
        gpsSms = settings.getBoolean("settings_gps_sms", false);
        googleMapsSms = settings.getBoolean("settings_google_sms", false);
        networkSms = settings.getBoolean("settings_network_sms", false);

        String json = settings.getString("place", "");
        Gson gson = new Gson();
        this.place = gson.fromJson(json, LDPlace.class);
    }

    private void finishBroadcast(String str) {
        Log.d(TAG, "Finish broadcast: " + str);

        if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }

        broadcastResult.finish();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "Connected!");

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            finishBroadcast("ON CONNECTED");
            return;
        }

        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setInterval(1000 * 10);
        locationRequest.setFastestInterval(1000);
        locationRequest.setMaxWaitTime(2 * 60 * 1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, locationRequest, this);

        new Handler().postDelayed(
                new Runnable() {
                    public void run() {
                        Log.d("DELAYED", "MAX WAIT TIME EXPIRED");
                        sendLocation();
                    }
                },
                2 * 60 * 1000);
    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "CONNECTION SUSPENDED");
        finishBroadcast("ON CONNECTION SUSPENDED");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "Conection FAILED");
        finishBroadcast("CONNECTION FAILED");
    }

    @Override
    public void onLocationChanged(Location l) {
        Log.d(TAG, "Location changed: " + l.getAccuracy());

        if (location == null || location.getAccuracy() < l.getAccuracy()) {
            location = l;
        }

        if (l.getAccuracy() < 100) {
            sendLocation();
        }
    }

    private void sendLocation() {
        Log.d("TEST", "SEND LOCATION()");
        if (locationSent) {
            return;
        }
        locationSent = true;

        if (location == null) {
            SMSSender.sendSMS(phoneNumber, context.getResources().getString(R.string.error_getting_location));
            finishBroadcast("ERROR, sendLocation with null");
        }

        Log.d(TAG, "Final: " + location.getAccuracy() + " - " + location.getLatitude() + " - " + location.getLongitude());

        if (gpsSms) {
            SMSSender.sendLocationMessage(context, phoneNumber, location);
        }

        if (googleMapsSms) {
            SMSSender.sendGoogleMapsMessage(context, phoneNumber, location);
        }

        if (networkSms) {
            SMSSender.sendNetworkMessage(context, phoneNumber, location, place, new SMSSender.NetworkMessageSentCallback() {
                @Override
                public void sent() {
                    finishBroadcast("SEND LOCATION WITH NETWORK.");
                }
            });
        } else {
            finishBroadcast("SEND LOCATION NO NETWORK");
        }
    }
}