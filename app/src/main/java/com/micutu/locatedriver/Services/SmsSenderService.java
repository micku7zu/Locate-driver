package com.micutu.locatedriver.Services;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.SmsManager;

import com.google.gson.Gson;
import com.micutu.locatedriver.Model.LDPlace;
import com.micutu.locatedriver.R;
import com.micutu.locatedriver.Utilities.Network;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;
import java.util.ArrayList;

import io.nlopez.smartlocation.OnLocationUpdatedListener;
import io.nlopez.smartlocation.SmartLocation;
import io.nlopez.smartlocation.location.config.LocationParams;
import io.nlopez.smartlocation.location.providers.LocationGooglePlayServicesWithFallbackProvider;

public class SmsSenderService extends IntentService implements OnLocationUpdatedListener {
    private final static String TAG = SmsSenderService.class.getSimpleName();

    private final static int LOCATION_REQUEST_MAX_WAIT_TIME = 60;

    private Resources r = null;
    private Context context = null;
    private String phoneNumber = null;

    private LDPlace place = null;
    private boolean keywordReceivedSms = false;
    private boolean gpsSms = false;
    private boolean googleMapsSms = false;
    private boolean networkSms = false;
    private int speedType = 0;

    private boolean alreadySentFlag = false;

    private Location bestLocation = null;
    private long startTime = 0;

    public SmsSenderService() {
        super("SmsSenderService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        //Log.d(TAG, "onHandleIntent");
        this.phoneNumber = intent.getExtras().getString("phoneNumber");

        if (this.phoneNumber.length() == 0) {
            //Log.d(TAG, "Phonenumber empty, return.");
            return;
        }

        this.context = this;
        this.r = context.getResources();
        initSending();
    }


    private void initSending() {
        //Log.d(TAG, "initSending()");
        readSettings();

        if (keywordReceivedSms) {
            this.sendAcknowledgeMessage(phoneNumber);
        }

        //set bestLocation to null and start time
        startTime = System.currentTimeMillis() / 1000;
        bestLocation = null;

        SmartLocation.with(context).location(new LocationGooglePlayServicesWithFallbackProvider(context))
                .config(LocationParams.NAVIGATION)
                .start(this);

    }

    public static boolean isLocationFused(Location location) {
        return !location.hasAltitude() || !location.hasSpeed() || location.getAltitude() == 0;
    }

    @Override
    public void onLocationUpdated(Location location) {
        //Log.d(TAG, "LOCATION UPDATE");

        long currentTime = System.currentTimeMillis() / 1000;

        //Log.d(TAG, "Start time: " + startTime + ", Current time: " + currentTime);
        //Log.d(TAG, "Difference: " + (currentTime - startTime));

        if (currentTime - startTime < this.LOCATION_REQUEST_MAX_WAIT_TIME) {
            //Log.d(TAG, "NOT EXPIRED YET. CHECK");

            if (bestLocation == null) {
                bestLocation = location;
            }

            //still null? check again
            if (bestLocation == null) {
                //Log.d(TAG, "BEST LOCATION STILL NULL, CHECK MORE");
                return;
            }

            //Log.d(TAG, bestLocation.toString());
            //Log.d(TAG, location.toString());

            //Log.d(TAG, "HAS ALTITUDE:" + location.hasAltitude());
            //Log.d(TAG, "HAS SPEED: " + location.hasSpeed());
            //Log.d(TAG, "LOCATION PROVIDER: " + location.getProvider());


            if (!bestLocation.getProvider().equals(LocationManager.GPS_PROVIDER) || bestLocation.getProvider().equals(location.getProvider())) {
                //Log.d(TAG, "NOT GPS OR BOTH GPS!");
                if (location.getAccuracy() < bestLocation.getAccuracy()) {
                    //Log.d(TAG, "Update best location.");
                    bestLocation = location;
                }
            }


            if (this.isLocationFused(bestLocation)) {
                //Log.d(TAG, "Location still fused.");
                return;
            }

            if (bestLocation.getAccuracy() > 100) {
                //Log.d(TAG, "Accuracy more than 100, check again.");
                return;
            }
        }


        //stop the location
        //Log.d(TAG, "STOP LOCATION BECAUSE TIME ELAPSED OR ACCURACY IS GOOD");
        SmartLocation.with(context).location().stop();

        if (bestLocation == null) {
            this.sendSMS(phoneNumber, r.getString(R.string.error_getting_location));
            return;
        }

        if (gpsSms) {
            this.sendLocationMessage(phoneNumber, bestLocation);
        }

        if (googleMapsSms) {
            this.sendGoogleMapsMessage(phoneNumber, bestLocation);
        }

        if (!networkSms) {
            return;
        }

        this.sendNetworkMessage(phoneNumber, bestLocation, place, new OnNetworkMessageSentListener() {
            @Override
            public void onNetworkMessageSent() {
                //Log.d(TAG, "on Network Message Sent");
            }
        });
    }

    private void readSettings() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);

        keywordReceivedSms = settings.getBoolean("settings_detected_sms", false);
        gpsSms = settings.getBoolean("settings_gps_sms", false);
        googleMapsSms = settings.getBoolean("settings_google_sms", false);
        networkSms = settings.getBoolean("settings_network_sms", false);
        speedType = Integer.parseInt(settings.getString("settings_kmh_or_mph", "0"));

        String json = settings.getString("place", "");
        Gson gson = new Gson();
        this.place = gson.fromJson(json, LDPlace.class);
    }

    public static void sendSMS(String phoneNumber, String message) {
        //Log.d(TAG, "Send SMS: " + phoneNumber + ", " + message);
        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> parts = smsManager.divideMessage(message);
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
    }

    public String booleanToString(Boolean enabled) {
        return (enabled) ? context.getResources().getString(R.string.enabled) :
                context.getResources().getString(R.string.disabled);
    }

    public void sendAcknowledgeMessage(String phoneNumber) {
        Resources r = context.getResources();
        String text = r.getString(R.string.acknowledgeMessage);
        text += " " + r.getString(R.string.network) + " " + this.booleanToString(Network.isNetworkAvailable(context));
        text += ", " + r.getString(R.string.gps) + " " + this.locationToString(context, this.getLocationMode(context));
        SmsSenderService.sendSMS(phoneNumber, text);
    }

    public double convertMPStoKMH(double speed) {
        return speed * 3.6;
    }

    public double convertMPStoMPH(double speed) {
        return speed * 2.23694;
    }


    public void sendLocationMessage(String phoneNumber, Location location) {
        //Log.d(TAG, "sendLocationMessage()" + location.getAccuracy());
        Resources r = context.getResources();
        Boolean fused = isLocationFused(location);

        DecimalFormat latAndLongFormat = new DecimalFormat("#.######");

        String text = r.getString(fused ? R.string.approximate : R.string.accurate) + " location:\n";


        text += r.getString(R.string.accuracy) + " " + Math.round(location.getAccuracy()) + "m\n";
        text += r.getString(R.string.latitude) + " " + latAndLongFormat.format(location.getLatitude()) + "\n";
        text += r.getString(R.string.longitude) + " " + latAndLongFormat.format(location.getLongitude()) + "";

        if (location.hasSpeed()) {
            if (speedType == 0) {
                text += "\n" + r.getString(R.string.speed) + " " + ((int) convertMPStoKMH(location.getSpeed())) + "KM/H";
            } else {
                text += "\n" + r.getString(R.string.speed) + " " + ((int) convertMPStoMPH(location.getSpeed())) + "MPH";
            }
        }

        if (location.hasAltitude() && location.getAltitude() != 0) {
            text += "\n" + r.getString(R.string.altitude) + " " + ((int) location.getAltitude()) + "m";
        }

        SmsSenderService.sendSMS(phoneNumber, text);
    }

    public void sendGoogleMapsMessage(String phoneNumber, Location location) {
        //Log.d(TAG, "sendGoogleMapsMessage() " + location.getAccuracy());
        String text = "https://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
        SmsSenderService.sendSMS(phoneNumber, text);
    }


    /* NEEDS REFACTOR */
    public void sendNetworkMessage(final String phoneNumber, final Location location, final LDPlace place, final OnNetworkMessageSentListener onNetworkMessageSentListener) {
        //Log.d(TAG, "sendNetworkMessage() " + location.getAccuracy());

        if (!Network.isNetworkAvailable(context)) {
            SmsSenderService.sendSMS(phoneNumber, r.getString(R.string.no_network));
            onNetworkMessageSentListener.onNetworkMessageSent();
            return;
        }


        //Log.d(TAG, "STARTED NETWORK REQUEST");
        Network.get("https://maps.googleapis.com/maps/api/geocode/json?latlng=" + location.getLatitude() + "," + location.getLongitude(), new Network.OnGetFinishListener() {
            @Override
            public void onGetFinish(String result) {
                //Log.d(TAG, "RESULT ARRIVED");
                try {
                    final String address = new JSONObject(result).getJSONArray("results").getJSONObject(0).getString("formatted_address");
                    final String firstText = r.getString(R.string.address) + " " + address + ". ";

                    if (place == null) {
                        SmsSenderService.sendSMS(phoneNumber, firstText + r.getString(R.string.no_destination));
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

                                SmsSenderService.sendSMS(phoneNumber, firstText + r.getString(R.string.remaining_distance_to) + " " + place.getName() + ": " + distance + ". " + r.getString(R.string.aprox_duration) + " " + duration + ".");
                                onNetworkMessageSentListener.onNetworkMessageSent();
                                return;
                            } catch (Exception e) {
                                //Log.d(TAG, "EXCEPTION E: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (JSONException e) {
                    e.printStackTrace();
                    //Log.d(TAG, "JSON EXCEPTION");
                }
            }
        });
    }

    public interface OnNetworkMessageSentListener {
        public void onNetworkMessageSent();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        //Log.d(TAG, "onCreate()");
    }

    @Override
    public void onDestroy() {
        //Log.d(TAG, "onDestroy()");
        super.onDestroy();
    }

    public static int getLocationMode(Context context) {
        try {
            return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
        } catch (Settings.SettingNotFoundException e) {
            return -1;
        }
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
}

