package com.micutu.locatedriver.BroadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;

import com.micutu.locatedriver.Services.SmsSenderService;

import java.util.ArrayList;

public class SmsReceiver extends BroadcastReceiver {
    private String TAG = SmsReceiver.class.getSimpleName();

    public SmsReceiver() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String keyword = PreferenceManager.getDefaultSharedPreferences(context).getString("keyword", "");

        if(keyword.length() == 0) {
            //Log.d(TAG, "No keyword available. Exit");
            return;
        }

        ArrayList<SmsMessage> list = getMessagesWithKeyword(keyword, intent.getExtras());

        if (list.size() == 0) {
            //Log.d(TAG, "No message available. Exit");
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
                SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdus[i]);
                if (sms.getMessageBody().toString().equals(keyword)) {
                    list.add(sms);
                }
            }
        }
        return list;
    }

}