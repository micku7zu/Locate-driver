package com.micutu.locatedriver.Fragments;

import android.content.res.Resources;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.FrameLayout;
import android.widget.ListView;

import com.micutu.locatedriver.R;

public class SettingsCardFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.settings_preferences);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        int width = Resources.getSystem().getDisplayMetrics().widthPixels;

        /* calculate preferences height using measure and set it to parent */
        if (getView() != null) {
            ListView listView = (ListView) getView().findViewById(android.R.id.list);
            Adapter adapter = listView.getAdapter();
            if (adapter != null) {
                int height = 0;
                for (int i = 0; i < adapter.getCount(); i++) {
                    View item = adapter.getView(i, null, listView);
                    item.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), 0);
                    height += item.getMeasuredHeight();
                }
                FrameLayout frame = (FrameLayout) getActivity().findViewById(R.id.settings_preferences_framelayout);
                ViewGroup.LayoutParams param = frame.getLayoutParams();
                param.height = height + (listView.getDividerHeight() * (adapter.getCount()));
                frame.setLayoutParams(param);
            }
        }



    }
}