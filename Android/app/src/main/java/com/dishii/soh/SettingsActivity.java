package com.dishii.soh;

import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Native Android settings activity driven by the JSON schema that the game
 * engine writes on first launch (settings_schema.json in the SOH data dir).
 *
 * Navigation: a Spinner in the toolbar lets the user pick a top-level menu
 * (Settings / Enhancements / Dev Tools …).  Each selection swaps in a
 * SettingsFragment that renders that menu's sections as PreferenceCategories.
 */
public class SettingsActivity extends AppCompatActivity {

    private static final String SCHEMA_PATH =
            Environment.getExternalStorageDirectory() + "/SOH/settings_schema.json";

    private JSONArray mMenus;
    private List<String> mMenuNames = new ArrayList<>();
    private NativeSettingsBridge mBridge;
    private int mCurrentMenuIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Settings");
        }

        mBridge = new NativeSettingsBridge();

        if (!mBridge.isEngineInitialized()) {
            showError("Launch the game once to generate the settings schema.");
            return;
        }

        File schemaFile = new File(SCHEMA_PATH);
        if (!schemaFile.exists()) {
            showError("Settings schema not found.\nPlease launch the game once first.");
            return;
        }

        try {
            byte[] bytes = Files.readAllBytes(schemaFile.toPath());
            JSONObject schema = new JSONObject(new String(bytes));
            mMenus = schema.optJSONArray("menus");
        } catch (Exception e) {
            showError("Could not read settings schema:\n" + e.getMessage());
            return;
        }

        if (mMenus == null || mMenus.length() == 0) {
            showError("Settings schema is empty.");
            return;
        }

        for (int i = 0; i < mMenus.length(); i++) {
            mMenuNames.add(mMenus.optJSONObject(i).optString("name", "Menu " + i));
        }
        mMenuNames.add("Controller Mapping");

        setupSpinner();

        if (savedInstanceState == null) {
            loadMenu(0);
        }
    }

    private void setupSpinner() {
        Spinner spinner = findViewById(R.id.settings_spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, mMenuNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                int controllerMappingPos = mMenuNames.size() - 1;
                if (pos == controllerMappingPos) {
                    // Reset spinner to previous menu without triggering another event.
                    spinner.post(() -> spinner.setSelection(mCurrentMenuIndex));
                    startActivity(new Intent(SettingsActivity.this, ControllerMappingActivity.class));
                } else {
                    mCurrentMenuIndex = pos;
                    loadMenu(pos);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadMenu(int index) {
        try {
            JSONObject menuJson = mMenus.getJSONObject(index);
            SettingsFragment fragment = SettingsFragment.newInstance(menuJson);
            FragmentManager fm = getSupportFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            ft.replace(R.id.settings_fragment_container, fragment);
            ft.commit();
        } catch (Exception e) {
            showError("Error loading menu: " + e.getMessage());
        }
    }

    private void showError(String message) {
        TextView tv = findViewById(R.id.settings_error);
        if (tv != null) {
            tv.setVisibility(View.VISIBLE);
            tv.setText(message);
        }
        View spinner = findViewById(R.id.settings_spinner);
        View container = findViewById(R.id.settings_fragment_container);
        if (spinner != null)   spinner.setVisibility(View.GONE);
        if (container != null) container.setVisibility(View.GONE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
