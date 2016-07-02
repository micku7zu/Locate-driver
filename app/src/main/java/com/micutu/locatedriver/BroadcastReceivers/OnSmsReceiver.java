package com.micutu.locatedriver.BroadcastReceivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;
import android.util.Log;

import com.micutu.locatedriver.Utilities.SmsSender;

import java.util.ArrayList;

public class OnSmsReceiver extends BroadcastReceiver implements SmsSender.OnSmsSenderFinishListener {
    private String TAG = OnSmsReceiver.class.getSimpleName();

    private PendingResult broadcastResult = null;

    public OnSmsReceiver() {

    }

    @Override
    public void onReceive(Context context, Intent intent) {
        broadcastResult = goAsync();

        String keyword = PreferenceManager.getDefaultSharedPreferences(context).getString("keyword", "");

        if(keyword.length() == 0) {
            Log.d(TAG, "No keyword available.");
            broadcastResult.finish();
            return;
        }

        ArrayList<SmsMessage> list = getMessagesWithKeyword(keyword, intent.getExtras());

        if (list.size() == 0) {
            Log.d(TAG, "No message available.");
            broadcastResult.finish();
            return;
        }

        SmsSender smsSender = new SmsSender(context, list.get(0).getOriginatingAddress(), this);
    }

    @Override
    public void onSmsSenderFinish() {
        Log.d(TAG, "onSmsSenderFinish()");
        broadcastResult.finish();
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