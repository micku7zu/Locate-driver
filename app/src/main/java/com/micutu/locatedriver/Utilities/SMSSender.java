package com.micutu.locatedriver.Utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.preference.PreferenceManager;
import android.telephony.SmsManager;
import android.util.Log;

import com.google.gson.Gson;
import com.micutu.locatedriver.Model.LDPlace;
import com.micutu.locatedriver.R;

import org.json.JSONException;
import org.json.JSONObject;

public class SmsSender implements LocationGetter.OnLocationGetListener {
    private String TAG = SmsSender.class.getSimpleName();

    private Resources r = null;
    private Context context = null;
    private String phoneNumber = null;
    private OnSmsSenderFinishListener onSmsSenderFinishListener = null;


    private LDPlace place = null;
    private boolean keywordReceivedSms = false;
    private boolean gpsSms = false;
    private boolean googleMapsSms = false;
    private boolean networkSms = false;

    private boolean alreadySentFlag = false;

    public SmsSender(Context context, String phoneNumber, OnSmsSenderFinishListener onSmsSenderFinishListener) {
        this.context = context;
        this.r = context.getResources();
        this.phoneNumber = phoneNumber;
        this.onSmsSenderFinishListener = onSmsSenderFinishListener;

        initSending();
    }

    private void initSending() {
        Log.d(TAG, "initSending()");
        readSettings();

        if (keywordReceivedSms) {
            this.sendAcknowledgeMessage(phoneNumber);
        }

        LocationGetter locationGetter = new LocationGetter(context, this); //listener implemented below
    }

    @Override
    public void onLocationGet(Location location) {
        Log.d(TAG, "onLocationGet: " + location.getAccuracy());

        if(alreadySentFlag) { //for protection
            Log.d(TAG, "ALREADY SENT, CEVA SE INTAMPLA!!!");
            return;
        }
        alreadySentFlag = true;


        if (location == null) {
            this.sendSMS(phoneNumber, r.getString(R.string.error_getting_location));
            onSmsSenderFinishListener.onSmsSenderFinish();
            return;
        }

        if (gpsSms) {
            this.sendLocationMessage(phoneNumber, location);
        }

        if (googleMapsSms) {
            this.sendGoogleMapsMessage(phoneNumber, location);
        }

        if (!networkSms) {
            onSmsSenderFinishListener.onSmsSenderFinish();
            return;
        }

        this.sendNetworkMessage(phoneNumber, location, place, new OnNetworkMessageSentListener() {
            @Override
            public void onNetworkMessageSent() {
                onSmsSenderFinishListener.onSmsSenderFinish();
            }
        });
    }

    private void readSettings() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        keywordReceivedSms = settings.getBoolean("settings_detected_sms", false);
        gpsSms = settings.getBoolean("settings_gps_sms", false);
        googleMapsSms = settings.getBoolean("settings_google_sms", false);
        networkSms = settings.getBoolean("settings_network_sms", false);

        String json = settings.getString("place", "");
        Gson gson = new Gson();
        this.place = gson.fromJson(json, LDPlace.class);
    }

    public static void sendSMS(String phoneNumber, String message) {
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
    }

    public String booleanToString(Boolean enabled) {
        return (enabled) ? context.getResources().getString(R.string.enabled) :
                context.getResources().getString(R.string.disabled);
    }

    public void sendAcknowledgeMessage(String phoneNumber) {
        Resources r = context.getResources();
        String text = r.getString(R.string.acknowledgeMessage);
        text += " " + r.getString(R.string.network) + " " + this.booleanToString(Network.isNetworkAvailable(context));
        text += ", " + r.getString(R.string.gps) + " " + LocationGetter.locationToString(context, LocationGetter.getLocationMode(context));
        SmsSender.sendSMS(phoneNumber, text);
    }

    public void sendLocationMessage(String phoneNumber, Location location) {
        Log.d(TAG, "sendLocationMessage()" + location.getAccuracy());
        Resources r = context.getResources();
        String text = r.getString(R.string.accuracy) + " " + location.getAccuracy() + "m\n";
        text += r.getString(R.string.latitude) + " " + location.getLatitude() + "\n";
        text += r.getString(R.string.longitude) + " " + location.getLongitude() + "\n";
        text += r.getString(R.string.speed) + " " + location.getSpeed() + "KM/H";
        SmsSender.sendSMS(phoneNumber, text);
    }

    public void sendGoogleMapsMessage(String phoneNumber, Location location) {
        Log.d(TAG, "sendGoogleMapsMessage() " + location.getAccuracy());
        String text = "https://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
        SmsSender.sendSMS(phoneNumber, text);
    }

    public void sendNetworkMessage(final String phoneNumber, final Location location, final LDPlace place, final OnNetworkMessageSentListener onNetworkMessageSentListener) {
        Log.d(TAG, "sendNetworkMessage() " + location.getAccuracy());
        if (!Network.isNetworkAvailable(context)) {
            SmsSender.sendSMS(phoneNumber, r.getString(R.string.no_network));
            onNetworkMessageSentListener.onNetworkMessageSent();
            return;
        }


        Network.get("https://maps.googleapis.com/maps/api/geocode/json?latlng=" + location.getLatitude() + "," + location.getLongitude(), new Network.OnGetFinishListener() {
            @Override
            public void onGetFinish(String result) {
                try {
                    final String address = new JSONObject(result).getJSONArray("results").getJSONObject(0).getString("formatted_address");
                    final String firstText = r.getString(R.string.address) + " " + address + ". ";

                    if (place == null) {
                        SmsSender.sendSMS(phoneNumber, firstText + r.getString(R.string.no_destination));
                        onNetworkMessageSentListener.onNetworkMessageSent();
                        return;
                    }

                    Network.get("https://maps.googleapis.com/maps/api/directions/json?origin=" + location.getLatitude() + "," + location.getLongitude() + "&destination=" + place.getLatitude() + "," + place.getLongitude(), new Network.OnGetFinishListener() {
                        @Override
                        public void onGetFinish(String result) {
                            try {
                                JSONObject j = new JSONObject(result).getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0);
                                String distance = j.getJSONObject("distance").getString("text");
                                String duration = j.getJSONObject("duration").getString("text");

                                SmsSender.sendSMS(phoneNumber, firstText + r.getString(R.string.remaining_distance_to) + " " + place.getName() + ": " + distance + ". " + r.getString(R.string.aprox_duration) + " " + duration + ".");
                                onNetworkMessageSentListener.onNetworkMessageSent();
                                return;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    public interface OnSmsSenderFinishListener {
        public void onSmsSenderFinish();
    }

    public interface OnNetworkMessageSentListener {
        public void onNetworkMessageSent();
    }
}
