package com.micutu.locatedriver.Services;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.location.Location;
import android.location.LocationManager;
import android.os.BatteryManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.util.Log;

import com.google.gson.Gson;
import com.micutu.locatedriver.Model.LDPlace;
import com.micutu.locatedriver.R;
import com.micutu.locatedriver.Utilities.Constants;
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
    public static final String PHONE_NUMBER_KEY = "phoneNumber";
    public static final String SMS_TYPE_KEY = "sms_type";
    public static final String SMS_TYPE_SEND_LOCATION = "send_location";
    public static final String SMS_TYPE_SEND_CHARGING_STOPPED_WARNING = "send_charging_stopped_warning";
    public static final String SMS_TYPE_SEND_LOW_BATTERY_WARNING = "send_low_battery_warning";
    private final static String TAG = SmsSenderService.class.getSimpleName();
    private final static int LOCATION_REQUEST_MAX_WAIT_TIME = 60;
    protected static final int ONE_HOUR_MS = 3600000;

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

    public static boolean isLocationFused(Location location) {
        return !location.hasAltitude() || !location.hasSpeed() || location.getAltitude() == 0;
    }

    public static int getLocationMode(Context context) {
        try {
            return Settings.Secure.getInt(context.getContentResolver(), Settings.Secure
                    .LOCATION_MODE);
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

    @Override
    protected void onHandleIntent(Intent intent) {
        if (!isIntentValid(intent)) {
            return;
        }
        //Log.d(TAG, "onHandleIntent");
        this.phoneNumber = intent.getExtras().getString(PHONE_NUMBER_KEY);

        if (this.phoneNumber.length() == 0) {
            //Log.d(TAG, "Phonenumber empty, return.");
            return;
        }

        this.context = this;
        this.r = context.getResources();
        switch(intent.getExtras().getString(SMS_TYPE_KEY)) {
            case SMS_TYPE_SEND_LOCATION:
                initSendingLocation();
                break;
            case SMS_TYPE_SEND_LOW_BATTERY_WARNING:
                sendLowBatteryWarning();
                break;
            case SMS_TYPE_SEND_CHARGING_STOPPED_WARNING:
                if(!wasLastChargingStoppedWarningSentRecently()) {
                    sendChargingStoppedWarning();
                } else {
                    Log.w(TAG, "Not sending charging stopped warning as it was sent recently and may cause unwanted charges");
                }
                break;
        }
    }

    private boolean wasLastChargingStoppedWarningSentRecently() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        long lastSentAt = settings.getLong(Constants.LAST_CHARGING_STOPPED_WARNING_SENT_AT_KEY, 0);
        return System.currentTimeMillis() - lastSentAt < ONE_HOUR_MS * 6;
    }

    private boolean isSupportedSmsType(String type) {
        return SMS_TYPE_SEND_LOCATION.equals(type) || SMS_TYPE_SEND_LOW_BATTERY_WARNING.equals
                (type) || SMS_TYPE_SEND_CHARGING_STOPPED_WARNING.equals(type);
    }

    private boolean isIntentValid(Intent intent) {
        if(intent == null) {
            Log.w(TAG, "Intent is null");
            return false;
        }
        if(intent.getExtras() == null) {
            Log.w(TAG, "Intent has no extras");
            return false;
        }
        if(intent.getExtras().getString(PHONE_NUMBER_KEY) == null) {
            Log.w(TAG, "Intent contains no phone number");
            return false;
        }
        if(!isSupportedSmsType(intent.getExtras().getString
                (SMS_TYPE_KEY))) {
            Log.w(TAG, "SMS type is missing or not supported: \"" + intent.getExtras().getString
                    (SMS_TYPE_KEY) + "\"");
            return false;
        }
        return true;
    }

    private void initSendingLocation() {
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

    private void sendLowBatteryWarning() {
        long maybeBatteryPercentage = getBatteryPercentageIfApi21();
        String message;
        if(maybeBatteryPercentage != -1) {
            message = String.format(getString(R.string.low_battery_x_remaining), maybeBatteryPercentage);
        } else {
            message = getString(R.string.low_battery);
        }
        this.sendSMS(phoneNumber, message);
    }

    private long getBatteryPercentageIfApi21() {
        if(Build.VERSION.SDK_INT >= 21) {
            BatteryManager bm = (BatteryManager) getSystemService(BATTERY_SERVICE);
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else {
            return -1;
        }
    }

    private void sendChargingStoppedWarning() {
        long maybeBatteryPercentage = getBatteryPercentageIfApi21();
        String message;
        if(maybeBatteryPercentage != -1) {
            message = String.format(getString(R.string.charging_stopped_x_remaining), maybeBatteryPercentage);
        } else {
            message = getString(R.string.charging_stopped);
        }
        this.sendSMS(phoneNumber, message);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putLong(Constants.LAST_CHARGING_STOPPED_WARNING_SENT_AT_KEY, System.currentTimeMillis());
        editor.apply();
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

    public String booleanToString(Boolean enabled) {
        return (enabled) ? context.getResources().getString(R.string.enabled) :
                context.getResources().getString(R.string.disabled);
    }

    public void sendAcknowledgeMessage(String phoneNumber) {
        Resources r = context.getResources();
        String text = r.getString(R.string.acknowledgeMessage);
        text += " " + r.getString(R.string.network) + " " + this.booleanToString(Network.isNetworkAvailable(context));
        text += ", " + r.getString(R.string.gps) + " " + this.locationToString(context, this.getLocationMode(context));
        SmsSenderService.this.sendSMS(phoneNumber, text);
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

        SmsSenderService.this.sendSMS(phoneNumber, text);
    }

    public void sendGoogleMapsMessage(String phoneNumber, Location location) {
        //Log.d(TAG, "sendGoogleMapsMessage() " + location.getAccuracy());
        String text = "https://maps.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
        SmsSenderService.this.sendSMS(phoneNumber, text);
    }


    /* NEEDS REFACTOR */
    public void sendNetworkMessage(final String phoneNumber, final Location location, final LDPlace place, final OnNetworkMessageSentListener onNetworkMessageSentListener) {
        //Log.d(TAG, "sendNetworkMessage() " + location.getAccuracy());

        if (!Network.isNetworkAvailable(context)) {
            SmsSenderService.this.sendSMS(phoneNumber, r.getString(R.string.no_network));
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
                        SmsSenderService.this.sendSMS(phoneNumber, firstText + r.getString(R.string.no_destination));
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

                                SmsSenderService.this.sendSMS(phoneNumber, firstText + r.getString(R.string.remaining_distance_to) + " " + place.getName() + ": " + distance + ". " + r.getString(R.string.aprox_duration) + " " + duration + ".");
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

    public void sendSMS(String phoneNumber, String message) {
        //Log.d(TAG, "Send SMS: " + phoneNumber + ", " + message);
        //on samsung intents can't be null. the messages are not sent if intents are null
        ArrayList<PendingIntent> samsungFix = new ArrayList<>();
        samsungFix.add(PendingIntent.getBroadcast(context, 0, new Intent("SMS_RECEIVED"), 0));

        SmsManager smsManager = SmsManager.getDefault();
        ArrayList<String> parts = smsManager.divideMessage(message);
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, samsungFix, samsungFix);
    }

    public interface OnNetworkMessageSentListener {
        public void onNetworkMessageSent();
    }
}

