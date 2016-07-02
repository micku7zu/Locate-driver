package com.micutu.locatedriver;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.gson.Gson;
import com.micutu.locatedriver.BroadcastReceivers.SmsReceiver;
import com.micutu.locatedriver.Fragments.LDPlaceAutocompleteFragment;
import com.micutu.locatedriver.Model.LDPlace;

import java.util.ArrayList;


public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_RECEIVE_SMS = 0;
    private static final int PERMISSION_SEND_SMS = 1;
    private static final int PERMISSION_ACCESS_NETWORK_STATE = 2;
    private static final int PERMISSION_FINE_LOCATION = 3;
    private static final int PERMISSION_CORSE_LOCATION = 4;

    private Boolean running = null;
    private String keyword = null;
    private LDPlace place = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPermissions(); //check marshmallow permissions
        setContentView(R.layout.activity_main);
        restoreSavedData(); //restore keyword, destination, running
        setupToolbar();
        initApp();
        updateUI();
        toggleBroadcastReceiver(); //set broadcast receiver for sms
        scrollTop();
    }

    private void scrollTop() {
        final ScrollView scrollView = (ScrollView) this.findViewById(R.id.scrollview);

        scrollView.post(new Runnable() {
            public void run() {
                scrollView.scrollTo(0, 0);
            }
        });
    }

    private void checkPermissions() {
        ArrayList<String> permissions = new ArrayList();
        permissions.add(PERMISSION_RECEIVE_SMS, Manifest.permission.RECEIVE_SMS);
        permissions.add(PERMISSION_SEND_SMS, Manifest.permission.SEND_SMS);
        permissions.add(PERMISSION_ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_NETWORK_STATE);
        permissions.add(PERMISSION_FINE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(PERMISSION_CORSE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION);

        for (int i = 0; i < permissions.size(); i++) {
            if (ContextCompat.checkSelfPermission(this, permissions.get(i)) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{permissions.get(i)}, i);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.error_permission), Toast.LENGTH_SHORT).show();
        }
    }

    private void clearFocus() {
        View current = getCurrentFocus();
        if (current != null) {
            current.clearFocus();
        }

        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(findViewById(android.R.id.content).getWindowToken(), 0);
    }

    private void updateUI() {
        ((Button) this.findViewById(R.id.running_button)).setText((running) ? getResources().getString(R.string.stop) : getResources().getString(R.string.start));
        ((Button) this.findViewById(R.id.running_button)).setBackgroundTintList(ColorStateList.valueOf(getResources().getColor((running) ? R.color.colorAccent : R.color.colorPrimary)));

        EditText keywordInput = (EditText) this.findViewById(R.id.keyword);
        keywordInput.setEnabled(!running);

        LDPlaceAutocompleteFragment destination = (LDPlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.destination_autocomplete);

        destination.setEnabled(!running);

    }

    private void toggleRunning() {
        String currentKeyword = ((TextView) this.findViewById(R.id.keyword)).getText() + "";
        if (currentKeyword.length() == 0 && this.running == false) {
            //can't start application with no keyword
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.error_no_keyword), Toast.LENGTH_SHORT).show();
            return;
        }

        this.running = !this.running;
        saveData();
        updateUI();
        toggleBroadcastReceiver();
    }

    private void toggleBroadcastReceiver() {
        ComponentName receiver = new ComponentName(getApplicationContext(), SmsReceiver.class);
        PackageManager pm = getApplicationContext().getPackageManager();

        pm.setComponentEnabledSetting(receiver,
                (running) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private void initApp() {
        initDestinationPlace();
        initRunningButton();
        ((TextView) this.findViewById(R.id.keyword)).setText(this.keyword);
    }

    private void initRunningButton() {
        Button runningButton = (Button) this.findViewById(R.id.running_button);

        runningButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.toggleRunning();
                MainActivity.this.clearFocus();
            }
        });
    }

    private void initDestinationPlace() {
        final LDPlaceAutocompleteFragment destination = (LDPlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.destination_autocomplete);

        destination.setHint(getResources().getString(R.string.destination));
        destination.setText((this.place == null) ? "" : this.place.getName());

        destination.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                MainActivity.this.setPlace(place);
            }

            @Override
            public void onError(Status status) {
                MainActivity.this.setPlace(null);
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.destination_error), Toast.LENGTH_SHORT).show();
            }
        });

        destination.setOnPlaceClearListener(new LDPlaceAutocompleteFragment.PlaceClearListener() {
            @Override
            public void cleared() {
                MainActivity.this.setPlace(null);
            }
        });
    }


    private void saveData() {
        this.keyword = ((TextView) this.findViewById(R.id.keyword)).getText() + "";

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("running", this.running);
        editor.putString("keyword", this.keyword);

        Gson gson = new Gson();
        editor.putString("place", gson.toJson(place, LDPlace.class));

        editor.commit();
    }

    private void restoreSavedData() {
        PreferenceManager.setDefaultValues(this, R.xml.settings_preferences, false);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        this.running = settings.getBoolean("running", false);
        this.keyword = settings.getString("keyword", "");

        String json = settings.getString("place", "");
        Gson gson = new Gson();
        this.place = gson.fromJson(json, LDPlace.class);
    }

    private void setPlace(Place place) {
        if (place == null) {
            this.place = null;
        } else {
            this.place = new LDPlace(place.getName() + "", place.getId(), place.getLatLng().latitude, place.getLatLng().longitude);
        }
    }

    protected void setupToolbar() {
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }
}
