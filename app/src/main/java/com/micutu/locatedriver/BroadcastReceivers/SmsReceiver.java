package com.micutu.locatedriver.BroadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.widget.Toast;

import com.micutu.locatedriver.R;
import com.micutu.locatedriver.Services.SmsSenderService;
import com.micutu.locatedriver.Utilities.Permissions;

import java.util.ArrayList;

public class SmsReceiver extends BroadcastReceiver {
    private final static String TAG = SmsReceiver.class.getSimpleName();

    public SmsReceiver() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String keyword = PreferenceManager.getDefaultSharedPreferences(context).getString("keyword", "");

        if (keyword.length() == 0) {
            //Log.d(TAG, "No keyword available. Exit");
            return;
        }

        ArrayList<SmsMessage> list = null;
        try {
            list = getMessagesWithKeyword(keyword, intent.getExtras());
        } catch (Exception e) {
            return;
        }

        if (list.size() == 0) {
            //Log.d(TAG, "No message available. Exit");
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

        Intent newIntent = new Intent(context, SmsSenderService.class);
        newIntent.putExtra("phoneNumber", list.get(0).getOriginatingAddress());
        context.startService(newIntent);
    }

    private ArrayList<SmsMessage> getMessagesWithKeyword(String keyword, Bundle bundle) {
        ArrayList<SmsMessage> list = new ArrayList<SmsMessage>();
        if (bundle != null) {
            Object[] pdus = (Object[]) bundle.get("pdus");
            for (int i = 0; i < pdus.length; i++) {
                SmsMessage sms = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    String format = bundle.getString("format");
                    sms = SmsMessage.createFromPdu((byte[]) pdus[i], format);
                } else {
                    sms = SmsMessage.createFromPdu((byte[]) pdus[i]);
                }

                if (sms.getMessageBody().toString().equals(keyword)) {
                    list.add(sms);
                }
            }
        }
        return list;
    }

}