package com.dishii.soh;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * Native Android activity for remapping N64 buttons to physical gamepad buttons.
 *
 * Shows the 14 digital N64 buttons. Tapping "Set" for any button puts the
 * activity into listening mode; the next gamepad KeyEvent is recorded as the
 * new mapping. Mappings are written directly to the CVar store via
 * NativeSettingsBridge and take effect when the game next reads its config.
 */
public class ControllerMappingActivity extends AppCompatActivity {

    // N64 button bitmasks — match BTN_* in libultraship/libultra/controller.h
    private static final int[] N64_BITMASKS = {
        0x8000, // A
        0x4000, // B
        0x2000, // Z
        0x1000, // Start
        0x0800, // D-Up
        0x0400, // D-Down
        0x0200, // D-Left
        0x0100, // D-Right
        0x0020, // L
        0x0010, // R
        0x0008, // C-Up
        0x0004, // C-Down
        0x0002, // C-Left
        0x0001, // C-Right
    };

    private static final String[] N64_BUTTON_NAMES = {
        "A", "B", "Z", "Start",
        "D-Up", "D-Down", "D-Left", "D-Right",
        "L", "R",
        "C-Up", "C-Down", "C-Left", "C-Right"
    };

    // SDL_GameControllerButton display names (index = SDL button value)
    private static final String[] SDL_BUTTON_NAMES = {
        "A", "B", "X", "Y",
        "Back / Select", "Guide", "Start",
        "L Stick Click", "R Stick Click",
        "L Shoulder (L1)", "R Shoulder (R1)",
        "DPad Up", "DPad Down", "DPad Left", "DPad Right"
    };

    // Android KeyCode → SDL_GameControllerButton index
    private static int keycodeToSdlButton(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A:      return 0;
            case KeyEvent.KEYCODE_BUTTON_B:      return 1;
            case KeyEvent.KEYCODE_BUTTON_X:      return 2;
            case KeyEvent.KEYCODE_BUTTON_Y:      return 3;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return 4;
            case KeyEvent.KEYCODE_BUTTON_MODE:   return 5;
            case KeyEvent.KEYCODE_BUTTON_START:  return 6;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return 7;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return 8;
            case KeyEvent.KEYCODE_BUTTON_L1:     return 9;
            case KeyEvent.KEYCODE_BUTTON_R1:     return 10;
            case KeyEvent.KEYCODE_DPAD_UP:       return 11;
            case KeyEvent.KEYCODE_DPAD_DOWN:     return 12;
            case KeyEvent.KEYCODE_DPAD_LEFT:     return 13;
            case KeyEvent.KEYCODE_DPAD_RIGHT:    return 14;
            default:                             return -1;
        }
    }

    private static String sdlButtonName(int sdlBtn) {
        if (sdlBtn >= 0 && sdlBtn < SDL_BUTTON_NAMES.length) return SDL_BUTTON_NAMES[sdlBtn];
        if (sdlBtn >= 0) return "Button " + sdlBtn;
        return "Not mapped";
    }

    private NativeSettingsBridge mBridge;
    private int mPort = 0;                  // Player 1 = port 0
    private int mListeningBitmask = -1;
    private AlertDialog mListenDialog;
    private final TextView[] mMappingLabels = new TextView[N64_BITMASKS.length];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBridge = new NativeSettingsBridge();

        // Build the layout programmatically — avoids needing extra XML layout files.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        Toolbar toolbar = new Toolbar(this);
        toolbar.setTitle("Controller Mapping");
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        root.addView(toolbar);

        // Hint banner
        TextView hint = new TextView(this);
        hint.setText("Tap Set, then press a button on your gamepad.\nChanges take effect on next game launch.");
        hint.setPadding(dp(16), dp(12), dp(16), dp(12));
        hint.setTextSize(13);
        root.addView(hint);

        // Scrollable button list
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(8), 0, dp(8), dp(16));

        for (int i = 0; i < N64_BITMASKS.length; i++) {
            content.addView(buildRow(i));
            View divider = new View(this);
            divider.setBackgroundColor(0x22888888);
            content.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
        }

        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private LinearLayout buildRow(int idx) {
        int bitmask = N64_BITMASKS[idx];

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(6), dp(4), dp(6));

        // N64 action name
        TextView nameView = new TextView(this);
        nameView.setText(N64_BUTTON_NAMES[idx]);
        nameView.setTextSize(15);
        nameView.setPadding(0, 0, dp(8), 0);
        row.addView(nameView, new LinearLayout.LayoutParams(dp(80), LinearLayout.LayoutParams.WRAP_CONTENT));

        // Current SDL mapping
        TextView mappingView = new TextView(this);
        int current = mBridge.getButtonMapping(mPort, bitmask);
        mappingView.setText(sdlButtonName(current));
        mappingView.setTextSize(13);
        mMappingLabels[idx] = mappingView;
        row.addView(mappingView, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        // Set button
        Button setBtn = new Button(this);
        setBtn.setText("Set");
        setBtn.setTextSize(12);
        setBtn.setPadding(dp(8), 0, dp(8), 0);
        final int capturedIdx = idx;
        setBtn.setOnClickListener(v -> startListening(capturedIdx));
        row.addView(setBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Clear button
        Button clearBtn = new Button(this);
        clearBtn.setText("Clear");
        clearBtn.setTextSize(12);
        clearBtn.setPadding(dp(8), 0, dp(8), 0);
        clearBtn.setOnClickListener(v -> {
            mBridge.clearButtonMapping(mPort, bitmask);
            mappingView.setText("Not mapped");
        });
        row.addView(clearBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        return row;
    }

    private void startListening(int idx) {
        mListeningBitmask = N64_BITMASKS[idx];

        mListenDialog = new AlertDialog.Builder(this)
                .setTitle("Press a button")
                .setMessage("Press any button on your gamepad to map it to: " + N64_BUTTON_NAMES[idx])
                .setNegativeButton("Cancel", (d, w) -> mListeningBitmask = -1)
                .create();

        // Intercept gamepad keys while the dialog is visible.
        mListenDialog.setOnKeyListener((dialog, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                mListeningBitmask = -1;
                dialog.dismiss();
                return true;
            }
            int sdlBtn = keycodeToSdlButton(keyCode);
            if (sdlBtn == -1) return false;
            applyMapping(idx, sdlBtn);
            dialog.dismiss();
            return true;
        });

        mListenDialog.show();
    }

    private void applyMapping(int idx, int sdlBtn) {
        mBridge.setButtonMapping(mPort, N64_BITMASKS[idx], sdlBtn);
        mMappingLabels[idx].setText(sdlButtonName(sdlBtn));
        mListeningBitmask = -1;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
