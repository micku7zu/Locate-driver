package com.micutu.locatedriver.Utilities;

import android.content.Context;
import android.content.res.Resources;
import android.location.Location;
import android.net.ConnectivityManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;

import com.micutu.locatedriver.Model.LDPlace;
import com.micutu.locatedriver.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;

public class SMSSender {

    public static int getLocationMode(Context context) {
        try {
            return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
        } catch (Settings.SettingNotFoundException e) {
            return -1;
        }
    }

    public static boolean isNetworkAvailable(final Context context) {
        final ConnectivityManager connectivityManager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        return connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected();
    }

    public static void sendSMS(String phoneNumber, String message) {
        SmsManager smsManager = SmsManager.getDefault();
        smsManager.sendTextMessage(phoneNumber, null, message, null, null);
    }

    public static String booleanToString(Context context, Boolean enabled) {
        return (enabled) ? context.getResources().getString(R.string.enabled) :
                context.getResources().getString(R.string.disabled);
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

    public static void sendAcknowledgeMessage(Context context, String phoneNumber) {
        Resources r = context.getResources();
        String text = r.getString(R.string.acknowledgeMessage);
        text += " " + r.getString(R.string.network) + " " + SMSSender.booleanToString(context, SMSSender.isNetworkAvailable(context));
        text += ", " + r.getString(R.string.gps) + " " + SMSSender.locationToString(context, SMSSender.getLocationMode(context));
        SMSSender.sendSMS(phoneNumber, text);
    }

    public static void sendLocationMessage(Context context, String phoneNumber, Location location) {
        Resources r = context.getResources();
        String text = r.getString(R.string.accuracy) + " " + location.getAccuracy() + "m\n";
        text += r.getString(R.string.latitude) + " " + location.getLatitude() + "\n";
        text += r.getString(R.string.longitude) + " " + location.getLongitude() + "\n";
        text += r.getString(R.string.speed) + " " + location.getSpeed() + "KM/H";
        SMSSender.sendSMS(phoneNumber, text);
    }

    public static void sendGoogleMapsMessage(Context context, String phoneNumber, Location location) {
        String text = "https://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
        SMSSender.sendSMS(phoneNumber, text);
    }

    public static void sendNetworkMessage(final Context context, final String phoneNumber, final Location location, final LDPlace place, final NetworkMessageSentCallback callback) {
        final Resources r = context.getResources();

        if (!isNetworkAvailable(context)) {
            SMSSender.sendSMS(phoneNumber, r.getString(R.string.no_network));
            callback.sent();
            return;
        }


        Network.get("https://maps.googleapis.com/maps/api/geocode/json?latlng=" + location.getLatitude() + "," + location.getLongitude(), new Network.NetworkGetCallback() {
            @Override
            public void received(String result) {
                try {
                    final String address = new JSONObject(result).getJSONArray("results").getJSONObject(0).getString("formatted_address");
                    final String firstText = r.getString(R.string.address) + " " + address + ". ";

                    if (place == null) {
                        SMSSender.sendSMS(phoneNumber, firstText + r.getString(R.string.no_destination));
                        callback.sent();
                        return;
                    }

                    Network.get("https://maps.googleapis.com/maps/api/directions/json?origin=" + location.getLatitude() + "," + location.getLongitude() + "&destination=" + place.getLatitude() + "," + place.getLongitude(), new Network.NetworkGetCallback() {
                        @Override
                        public void received(String result) {
                            try {
                                JSONObject j = new JSONObject(result).getJSONArray("routes").getJSONObject(0).getJSONArray("legs").getJSONObject(0);
                                String distance = j.getJSONObject("distance").getString("text");
                                String duration = j.getJSONObject("duration").getString("text");

                                SMSSender.sendSMS(phoneNumber, firstText + r.getString(R.string.remaining_distance_to) + " " + place.getName() + ": " + distance + ". " + r.getString(R.string.aprox_duration) + " " + duration + ".");
                                callback.sent();
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


    public interface NetworkMessageSentCallback {
        public void sent();
    }
}
