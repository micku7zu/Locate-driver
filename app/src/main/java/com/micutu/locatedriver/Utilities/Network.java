package com.micutu.locatedriver.Utilities;

import android.content.Context;
import android.net.ConnectivityManager;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Network {

    public static boolean isNetworkAvailable(final Context context) {
        final ConnectivityManager connectivityManager = ((ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE));
        return connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected();
    }

    public static void get(final String urlString, final OnGetFinishListener onGetFinishListener) {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    URL url = new URL(urlString);
                    HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                    InputStream in = new BufferedInputStream(urlConnection.getInputStream());

                    BufferedReader r = new BufferedReader(new InputStreamReader(in));
                    StringBuilder total = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) {
                        total.append(line).append('\n');
                    }

                    onGetFinishListener.onGetFinish(total.toString());
                } catch (Exception e) {
                    onGetFinishListener.onGetFinish("{}");
                }
            }
        };

        thread.start();
    }

    public interface OnGetFinishListener {
        public void onGetFinish(String result);
    }
}
