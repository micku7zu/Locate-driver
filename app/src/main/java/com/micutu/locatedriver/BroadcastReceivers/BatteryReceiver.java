package com.micutu.locatedriver.BroadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import com.micutu.locatedriver.R;
import com.micutu.locatedriver.Services.SmsSenderService;
import com.micutu.locatedriver.Utilities.Constants;
import com.micutu.locatedriver.Utilities.Permissions;

public class BatteryReceiver extends BroadcastReceiver {
    private final static String TAG = BatteryReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent == null) {
            return;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        String targetPhoneNumber = settings.getString(Constants.PHONE_NUMBER_KEY, null);
        boolean shouldSendChargingStoppedMessages = settings.getBoolean(context.getString(R.string.settings_send_charging_stopped_warnings), false);
        boolean shouldSendLowBatteryMessages = settings.getBoolean(context.getString(R.string.settings_send_low_battery_warnings), false);

        if(!shouldSendLowBatteryMessages && !shouldSendChargingStoppedMessages) {
            return;
        }
        if(targetPhoneNumber == null) {
            Log.w(TAG, "Cannot send battery warning, no phone number provided");
            return;
        }
        if (!Permissions.haveSendSMSAndLocationPermission(context)) {
            try {
                Permissions.setPermissionNotification(context);
            } catch (Exception e) {
                Toast.makeText(context, R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        String smsType = null;
        if(Intent.ACTION_POWER_DISCONNECTED.equals(intent.getAction()) && shouldSendChargingStoppedMessages) {
            smsType = SmsSenderService.SMS_TYPE_SEND_CHARGING_STOPPED_WARNING;
        } else if(Intent.ACTION_BATTERY_LOW.equals(intent.getAction()) && shouldSendLowBatteryMessages) {
            smsType = SmsSenderService.SMS_TYPE_SEND_LOW_BATTERY_WARNING;
        }
        if(smsType != null) {
            Intent newIntent = new Intent(context, SmsSenderService.class);
            newIntent.putExtra(SmsSenderService.PHONE_NUMBER_KEY, targetPhoneNumber);
            newIntent.putExtra(SmsSenderService.SMS_TYPE_KEY, smsType);
            context.startService(newIntent);
        }
    }
}