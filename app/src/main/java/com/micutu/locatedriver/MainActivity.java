package com.micutu.locatedriver;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.micutu.locatedriver.Views.LDPlaceAutocompleteFragment;

public class MainActivity extends AppCompatActivity {
    public static final String PREFS_NAME = "LocateDriver";
    private Boolean running = null;
    private String keyword = null;
    private String placeName = null;
    private String placeId = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        restoreSavedData();
        setupToolbar();
        initApp();
        updateUI();
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
        destination.setText(this.placeName);

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

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("running", this.running);
        editor.putString("keyword", this.keyword);
        editor.putString("placeName", this.placeName);
        editor.putString("placeId", this.placeId);

        editor.commit();
    }

    private void restoreSavedData() {
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);

        this.running = settings.getBoolean("running", false);
        this.keyword = settings.getString("keyword", "");
        this.placeName = settings.getString("placeName", "");
        this.placeId = settings.getString("placeId", "");

        if ((this.placeName.length() > 0) != (this.placeId.length() > 0)) {
            this.placeName = "";
            this.placeId = "";
        }

        Log.d("INFO", "Restored settings: ");
        Log.d("INFO", "Running:" + this.running + ", Keyword: " + this.keyword + ", Place: " + this.placeName + " - " + this.placeId);
    }

    private void setPlace(Place place) {
        if (place == null) {
            this.placeName = "";
            this.placeId = "";
        } else {
            this.setPlace(place.getName() + "", place.getId());
        }
    }

    private void setPlace(String placeName, String placeId) {
        this.placeName = placeName;
        this.placeId = placeId;
    }

    protected void setupToolbar() {
        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }
}
