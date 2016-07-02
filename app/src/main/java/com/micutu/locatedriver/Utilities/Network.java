package com.micutu.locatedriver.Utilities;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Network {

    public static void get(final String urlString, final NetworkGetCallback callback) {
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

                    callback.received(total.toString());
                } catch (Exception e) {
                    callback.received("{}");
                }
            }
        };

        thread.start();
    }

    public interface NetworkGetCallback {
        public void received(String result);
    }
}
