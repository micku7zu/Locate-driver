package com.micutu.locatedriver;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.TintableBackgroundView;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
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
import com.micutu.locatedriver.Services.SmsSenderService;
import com.micutu.locatedriver.Utilities.Constants;
import com.micutu.locatedriver.Utilities.Permissions;


public class MainActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Boolean running = null;
    private String keyword = null;
    private String phoneNumber = null;
    private LDPlace place = null;
    private int IGNORE_BATTERY_OPTIMIZATIONS_REQUEST_CODE = 3;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Permissions.checkAndRequestPermissions(this); //check marshmallow permissions
        setContentView(R.layout.activity_main);
        restoreSavedData(); //restore keyword, destination, running
        setupToolbar();
        initApp();
        updateUI();
        toggleBroadcastReceiver(); //set broadcast receiver for sms
        scrollTop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PreferenceManager.getDefaultSharedPreferences(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        PreferenceManager.getDefaultSharedPreferences(this).unregisterOnSharedPreferenceChangeListener(this);
    }

    @RequiresApi(23)
    private void requestIgnoreBatteryOptimizationsPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission
                .REQUEST_IGNORE_BATTERY_OPTIMIZATIONS}, IGNORE_BATTERY_OPTIMIZATIONS_REQUEST_CODE);
    }

    @RequiresApi(23)
    private void enableAppInUltraPowerSavingMode() {
        Intent intent = new Intent();
        String packageName = getPackageName();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm.isIgnoringBatteryOptimizations(packageName))
            intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        else {
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + packageName));
        }
        startActivity(intent);
    }

    private void scrollTop() {
        final ScrollView scrollView = (ScrollView) this.findViewById(R.id.scrollview);

        scrollView.post(new Runnable() {
            public void run() {
                scrollView.scrollTo(0, 0);
            }
        });
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
        ((TintableBackgroundView) (Button) this.findViewById(R.id.running_button)).setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor((running) ? R.color.colorAccent : R.color.colorPrimary)));
        ((TintableBackgroundView) (Button) this.findViewById(R.id.send_button)).setSupportBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.colorPrimary)));
    }

    private void toggleRunning() {
        String currentKeyword = ((TextView) this.findViewById(R.id.keyword)).getText() + "";
        if (currentKeyword.length() == 0 && this.running == false) {
            //can't start application with no keyword
            Toast.makeText(getApplicationContext(), getResources().getString(R.string.error_no_keyword), Toast.LENGTH_SHORT).show();
            return;
        }

        if (this.running == false && !Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
            Permissions.requestSendSMSAndLocationPermission(MainActivity.this);
            Toast.makeText(getApplicationContext(), R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
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
                (running) ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager
                        .COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    private void initApp() {
        initDestinationPlace();
        initRunningButton();
        initSendLocationButton();
        initKeywordInput();
        initPhoneNumberInput();
    }

    private void initKeywordInput() {
        final TextView keywordInput = (TextView) this.findViewById(R.id.keyword);
        keywordInput.setText(this.keyword);

        keywordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (keywordInput.getText().length() == 0) {
                    MainActivity.this.stop();
                }
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void initPhoneNumberInput() {
        final TextView phoneNumberInput = (TextView) this.findViewById(R.id.phone_number_input);
        phoneNumberInput.setText(this.phoneNumber);
        phoneNumberInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                phoneNumber = s.toString();
                SharedPreferences.Editor editor = getSettingsEditor();
                editor.putString(Constants.PHONE_NUMBER_KEY, phoneNumber);
                editor.apply();
            }
        });
    }

    private void stop() {
        if (this.running) {
            this.toggleRunning();
        }
    }

    private void initSendLocationButton() {
        Button sendLocationButton = (Button) this.findViewById(R.id.send_button);

        sendLocationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /* start the contact picker */

                if (!Permissions.haveReadContactsPermission(MainActivity.this)) {
                    Permissions.requestContactsPermission(MainActivity.this);
                    Toast.makeText(getApplicationContext(), R.string.read_contacts_permission, Toast.LENGTH_SHORT).show();
                    return;
                }

                if (!Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
                    Permissions.requestSendSMSAndLocationPermission(MainActivity.this);
                    Toast.makeText(getApplicationContext(), R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE);
                startActivityForResult(intent, 1);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == IGNORE_BATTERY_OPTIMIZATIONS_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                // Request that app can ignore battery optimizations
                if (Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.equals(permissions[i])
                        && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    if (Build.VERSION.SDK_INT >= 23) {
                        enableAppInUltraPowerSavingMode();
                    }
                }
            }
        }
    }

    /* read the contact from the contact picker */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (data != null) {
            Uri uri = data.getData();

            if (uri != null) {
                Cursor c = null;
                try {
                    c = getContentResolver().query(uri, new String[]{
                                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                                    ContactsContract.CommonDataKinds.Phone.TYPE},
                            null, null, null);

                    if (c != null && c.moveToFirst()) {
                        String number = c.getString(0);
                        int type = c.getInt(1);
                        launchService(number);
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
            }
        }
    }

    private void launchService(final String number) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setPositiveButton(R.string.send, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {

                if (!Permissions.haveSendSMSAndLocationPermission(MainActivity.this)) {
                    Permissions.requestSendSMSAndLocationPermission(MainActivity.this);
                    Toast.makeText(getApplicationContext(), R.string.send_sms_and_location_permission, Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent newIntent = new Intent(MainActivity.this, SmsSenderService.class);
                newIntent.putExtra(SmsSenderService.PHONE_NUMBER_KEY, number);
                MainActivity.this.startService(newIntent);
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setMessage(this.getResources().getString(R.string.are_you_sure) + " " + number +
                "?");
        AlertDialog dialog = builder.create();
        dialog.show();
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

    private SharedPreferences.Editor getSettingsEditor() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        return settings.edit();
    }

    private void saveData() {
        this.keyword = ((TextView) this.findViewById(R.id.keyword)).getText() + "";
        this.phoneNumber = ((TextView) this.findViewById(R.id.phone_number_input)).getText() + "";

        SharedPreferences.Editor editor = getSettingsEditor();
        editor.putBoolean("running", this.running);
        editor.putString("keyword", this.keyword);
        editor.putString(Constants.PHONE_NUMBER_KEY, this.phoneNumber);

        Gson gson = new Gson();
        editor.putString("place", gson.toJson(place, LDPlace.class));

        editor.commit();
    }

    private void restoreSavedData() {
        PreferenceManager.setDefaultValues(this, R.xml.settings_preferences, false);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        this.running = settings.getBoolean("running", false);
        this.keyword = settings.getString("keyword", "");
        this.phoneNumber = settings.getString(Constants.PHONE_NUMBER_KEY, "");

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

        this.saveData();
    }

    protected void setupToolbar() {
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(getString(R.string.settings_enable_in_ups_mode).equals(key)) {
            // Ups mode: Ultra power saving mode
            boolean shouldAppBeEnabledInUpsMode = sharedPreferences.getBoolean(key, false);
            if(shouldAppBeEnabledInUpsMode && Build.VERSION.SDK_INT >= 23) {
                requestIgnoreBatteryOptimizationsPermission();
            }
        }
    }
}
