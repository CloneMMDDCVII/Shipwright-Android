package com.dishii.soh;

import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Dynamically builds a PreferenceScreen from the JSON schema that the C++ game
 * engine writes to settings_schema.json on first launch.
 *
 * All preferences are non-persistent: values are read from and written to the
 * CVar system via NativeSettingsBridge rather than SharedPreferences.
 */
public class SettingsFragment extends PreferenceFragmentCompat {

    static final String ARG_MENU_JSON = "menu_json";

    private NativeSettingsBridge mBridge;

    static SettingsFragment newInstance(JSONObject menuJson) {
        SettingsFragment f = new SettingsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_MENU_JSON, menuJson.toString());
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        mBridge = new NativeSettingsBridge();

        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(requireContext());
        setPreferenceScreen(screen);

        Bundle args = getArguments();
        if (args == null) return;

        try {
            JSONObject menuJson = new JSONObject(args.getString(ARG_MENU_JSON, "{}"));
            JSONArray sections = menuJson.optJSONArray("sections");
            if (sections == null) return;

            for (int s = 0; s < sections.length(); s++) {
                JSONObject section = sections.getJSONObject(s);
                String sectionName = section.optString("name", "");
                JSONArray widgets = section.optJSONArray("widgets");
                if (widgets == null || widgets.length() == 0) continue;

                PreferenceCategory category = new PreferenceCategory(requireContext());
                category.setTitle(sectionName);
                category.setIconSpaceReserved(false);
                screen.addPreference(category);

                for (int i = 0; i < widgets.length(); i++) {
                    JSONObject w = widgets.getJSONObject(i);
                    Preference pref = buildPreference(w);
                    if (pref != null) {
                        category.addPreference(pref);
                    }
                }
            }
        } catch (Exception e) {
            Preference err = new Preference(requireContext());
            err.setTitle("Error loading settings");
            err.setSummary(e.getMessage());
            screen.addPreference(err);
        }
    }

    private Preference buildPreference(JSONObject w) {
        try {
            String type = w.optString("type", "");
            String label = w.optString("label", "");
            String cvar = w.optString("cvar", "");
            String tooltip = w.optString("tooltip", "");

            switch (type) {
                case "separator":
                    // Separators become visual headers; handled as category labels above.
                    // Return a disabled placeholder so the text shows inline.
                    Preference sep = new Preference(requireContext());
                    sep.setTitle(label);
                    sep.setEnabled(false);
                    sep.setIconSpaceReserved(false);
                    sep.setPersistent(false);
                    return sep;

                case "checkbox":
                    return buildCheckbox(label, cvar, tooltip, w);

                case "slider_int":
                    return buildIntSlider(label, cvar, tooltip, w);

                case "slider_float":
                    return buildFloatSlider(label, cvar, tooltip, w);

                case "combobox":
                    return buildCombobox(label, cvar, tooltip, w);

                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    // -----------------------------------------------------------------------

    private SwitchPreferenceCompat buildCheckbox(String label, String cvar,
                                                  String tooltip, JSONObject w) {
        SwitchPreferenceCompat pref = new SwitchPreferenceCompat(requireContext());
        pref.setTitle(label);
        if (!tooltip.isEmpty()) pref.setSummary(tooltip);
        pref.setIconSpaceReserved(false);
        pref.setPersistent(false);
        pref.setChecked(mBridge.getCVarInt(cvar) != 0);
        pref.setOnPreferenceChangeListener((p, newVal) -> {
            mBridge.setCVarInt(cvar, Boolean.TRUE.equals(newVal) ? 1 : 0);
            return true;
        });
        return pref;
    }

    private SeekBarPreference buildIntSlider(String label, String cvar,
                                              String tooltip, JSONObject w) throws Exception {
        int min  = w.optInt("min", 0);
        int max  = w.optInt("max", 100);
        int step = w.optInt("step", 1);
        int def  = w.optInt("default", min);

        SeekBarPreference pref = new SeekBarPreference(requireContext());
        pref.setTitle(label);
        pref.setIconSpaceReserved(false);
        pref.setPersistent(false);
        pref.setMin(min);
        pref.setMax(max);
        pref.setSeekBarIncrement(step);
        pref.setShowSeekBarValue(true);
        pref.setUpdatesContinuously(false);

        int current = mBridge.getCVarInt(cvar);
        pref.setValue(Math.max(min, Math.min(max, current)));

        if (!tooltip.isEmpty()) pref.setSummary(tooltip);

        pref.setOnPreferenceChangeListener((p, newVal) -> {
            mBridge.setCVarInt(cvar, (Integer) newVal);
            return true;
        });
        return pref;
    }

    private SeekBarPreference buildFloatSlider(String label, String cvar,
                                                String tooltip, JSONObject w) throws Exception {
        float minF  = (float) w.optDouble("min", 0.0);
        float maxF  = (float) w.optDouble("max", 1.0);
        float stepF = (float) w.optDouble("step", 0.01);
        boolean pct = w.optBoolean("percentage", false);

        // Map float range onto an integer range for SeekBarPreference.
        // scale = nearest power of 10 such that step * scale is a whole number.
        int scale = computeScale(stepF);
        int intMin = Math.round(minF * scale);
        int intMax = Math.round(maxF * scale);
        int intStep = Math.max(1, Math.round(stepF * scale));

        SeekBarPreference pref = new SeekBarPreference(requireContext());
        pref.setTitle(label);
        pref.setIconSpaceReserved(false);
        pref.setPersistent(false);
        pref.setMin(intMin);
        pref.setMax(intMax);
        pref.setSeekBarIncrement(intStep);
        pref.setShowSeekBarValue(false);
        pref.setUpdatesContinuously(false);

        float current = mBridge.getCVarFloat(cvar);
        int intCurrent = Math.round(current * scale);
        pref.setValue(Math.max(intMin, Math.min(intMax, intCurrent)));

        final int finalScale = scale;
        pref.setSummaryProvider((Preference.SummaryProvider<SeekBarPreference>) p -> {
            float v = p.getValue() / (float) finalScale;
            return pct ? String.format("%.0f%%", v * 100f) : String.format("%.2f", v);
        });
        if (!tooltip.isEmpty() && pref.getSummary() == null) pref.setSummary(tooltip);

        pref.setOnPreferenceChangeListener((p, newVal) -> {
            mBridge.setCVarFloat(cvar, (Integer) newVal / (float) finalScale);
            return true;
        });
        return pref;
    }

    private ListPreference buildCombobox(String label, String cvar,
                                          String tooltip, JSONObject w) throws Exception {
        JSONArray opts = w.optJSONArray("options");
        if (opts == null || opts.length() == 0) return null;

        int count = opts.length();
        CharSequence[] entries = new CharSequence[count];
        CharSequence[] values  = new CharSequence[count];
        for (int i = 0; i < count; i++) {
            JSONObject opt = opts.getJSONObject(i);
            entries[i] = opt.optString("label", "?");
            values[i]  = String.valueOf(opt.optInt("value", i));
        }

        ListPreference pref = new ListPreference(requireContext());
        pref.setTitle(label);
        if (!tooltip.isEmpty()) pref.setSummary(tooltip);
        pref.setIconSpaceReserved(false);
        pref.setPersistent(false);
        pref.setEntries(entries);
        pref.setEntryValues(values);
        pref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());

        String currentVal = String.valueOf(mBridge.getCVarInt(cvar));
        pref.setValue(currentVal);

        pref.setOnPreferenceChangeListener((p, newVal) -> {
            mBridge.setCVarInt(cvar, Integer.parseInt((String) newVal));
            return true;
        });
        return pref;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Returns a scale factor (power of 10) such that step * scale is an integer. */
    private static int computeScale(float step) {
        int scale = 1;
        for (int i = 0; i < 4; i++) {
            if (Math.round(step * scale) == step * scale) break;
            scale *= 10;
        }
        return scale;
    }
}
