/*
 * Copyright (C) 2014 Slimroms
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.slim.device.settings;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.internal.util.slim.AppHelper;
import com.android.internal.util.slim.ButtonsConstants;
import com.android.internal.util.slim.DeviceUtils;
import com.android.internal.util.slim.DeviceUtils.FilteredDeviceFeaturesArray;

import com.slim.device.KernelControl;
import com.slim.device.R;
import com.slim.device.util.ShortcutPickerHelper;
import com.slim.device.util.Utils;

public class ScreenOffGesture extends PreferenceFragment implements
        OnPreferenceChangeListener, OnPreferenceClickListener,
        ShortcutPickerHelper.OnPickListener {

    private static final String SETTINGS_METADATA_NAME = "com.android.settings";

    public static final String GESTURE_SETTINGS = "screen_off_gesture_settings";
    public static final String PREF_TOUCHSCREEN_HAPTIC_FEEDBACK_KEY = "touchscreen_haptic_feedback";
    private static String TOUCHSCREEN_HAPTIC_FEEDBACK_NODE = "/proc/touchpanel/haptic_feedback_enable";

    public static final String PREF_GESTURE_ENABLE = "enable_gestures";
    public static final String PREF_GESTURE_CIRCLE = "gesture_circle";
    public static final String PREF_GESTURE_DOUBLE_SWIPE = "gesture_double_swipe";
    public static final String PREF_GESTURE_ARROW_UP = "gesture_arrow_up";
    public static final String PREF_GESTURE_ARROW_DOWN = "gesture_arrow_down";
    public static final String PREF_GESTURE_ARROW_LEFT = "gesture_arrow_left";
    public static final String PREF_GESTURE_ARROW_RIGHT = "gesture_arrow_right";
    public static final String PREF_GESTURE_DOUBLE_TAP = "gesture_double_tap";

    private static final int DLG_SHOW_ACTION_DIALOG  = 0;
    private static final int DLG_RESET_TO_DEFAULT    = 1;

    private static final int MENU_RESET = Menu.FIRST;

    private Preference mGestureCircle;
    private Preference mGestureDoubleSwipe;
    private Preference mGestureArrowUp;
    private Preference mGestureArrowDown;
    private Preference mGestureArrowLeft;
    private Preference mGestureArrowRight;
    private Preference mGestureDoubleTap;
    private CheckBoxPreference mEnableGestures;
    private CheckBoxPreference mEnableHaptic;

    private boolean mCheckPreferences;
    private SharedPreferences mScreenOffGestureSharedPreferences;
    private SharedPreferences mScreenOffHapticSharedPreferences;

    private ShortcutPickerHelper mPicker;
    private String mPendingSettingsKey;
    private static FilteredDeviceFeaturesArray sFinalActionDialogArray;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPicker = new ShortcutPickerHelper(getActivity(), this);

        mScreenOffGestureSharedPreferences = getActivity().getSharedPreferences(
                GESTURE_SETTINGS, Activity.MODE_PRIVATE);
        mScreenOffHapticSharedPreferences = getActivity().getSharedPreferences(
                PREF_TOUCHSCREEN_HAPTIC_FEEDBACK_KEY, Activity.MODE_PRIVATE);

        // Before we start filter out unsupported options on the
        // ListPreference values and entries
        PackageManager pm = getActivity().getPackageManager();
        Resources settingsResources = null;
        try {
            settingsResources = pm.getResourcesForApplication(SETTINGS_METADATA_NAME);
        } catch (Exception e) {
            return;
        }
        sFinalActionDialogArray = new FilteredDeviceFeaturesArray();
        sFinalActionDialogArray = DeviceUtils.filterUnsupportedDeviceFeatures(getActivity(),
                settingsResources.getStringArray(
                        settingsResources.getIdentifier(SETTINGS_METADATA_NAME
                        + ":array/shortcut_action_screen_off_values", null, null)),
                settingsResources.getStringArray(
                        settingsResources.getIdentifier(SETTINGS_METADATA_NAME
                        + ":array/shortcut_action_screen_off_entries", null, null)));

        // Attach final settings screen.
        reloadSettings();

        setHasOptionsMenu(true);
    }

    private PreferenceScreen reloadSettings() {
        mCheckPreferences = false;
        PreferenceScreen prefs = getPreferenceScreen();
        if (prefs != null) {
            prefs.removeAll();
        }

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.screen_off_gesture);
        prefs = getPreferenceScreen();

        mEnableGestures = (CheckBoxPreference) prefs.findPreference(PREF_GESTURE_ENABLE);
        mEnableHaptic = (CheckBoxPreference) prefs.findPreference(PREF_TOUCHSCREEN_HAPTIC_FEEDBACK_KEY);
        boolean enableHaptics =
            mScreenOffHapticSharedPreferences.getBoolean(PREF_TOUCHSCREEN_HAPTIC_FEEDBACK_KEY, true);
        if (!KernelControl.isHapticSupported()) {
            mEnableHaptic.setEnabled(false);
            mEnableHaptic.setSummary(R.string.kernel_does_not_support);
        } else {
            mEnableHaptic.setChecked(enableHaptics);
            Utils.writeValue(TOUCHSCREEN_HAPTIC_FEEDBACK_NODE, enableHaptics);
            mEnableHaptic.setOnPreferenceChangeListener(this);
        }

        mGestureCircle = (Preference) prefs.findPreference(PREF_GESTURE_CIRCLE);
        mGestureDoubleSwipe = (Preference) prefs.findPreference(PREF_GESTURE_DOUBLE_SWIPE);
        mGestureArrowUp = (Preference) prefs.findPreference(PREF_GESTURE_ARROW_UP);
        mGestureArrowDown = (Preference) prefs.findPreference(PREF_GESTURE_ARROW_DOWN);
        mGestureArrowLeft = (Preference) prefs.findPreference(PREF_GESTURE_ARROW_LEFT);
        mGestureArrowRight = (Preference) prefs.findPreference(PREF_GESTURE_ARROW_RIGHT);
        mGestureDoubleTap = (Preference) prefs.findPreference(PREF_GESTURE_DOUBLE_TAP);

        setupOrUpdatePreference(mGestureCircle, mScreenOffGestureSharedPreferences
                .getString(PREF_GESTURE_CIRCLE, ButtonsConstants.ACTION_CAMERA));
        setupOrUpdatePreference(mGestureDoubleSwipe, mScreenOffGestureSharedPreferences
                .getString(PREF_GESTURE_DOUBLE_SWIPE, ButtonsConstants.ACTION_MEDIA_PLAY_PAUSE));

        if (KernelControl.isArrowUpSupported()) {
            setupOrUpdatePreference(mGestureArrowUp, mScreenOffGestureSharedPreferences
                    .getString(PREF_GESTURE_ARROW_UP, ButtonsConstants.ACTION_TORCH));
        } else {
            prefs.removePreference(mGestureArrowUp);
        }

        setupOrUpdatePreference(mGestureArrowDown, mScreenOffGestureSharedPreferences
                .getString(PREF_GESTURE_ARROW_DOWN, ButtonsConstants.ACTION_VIB_SILENT));
        setupOrUpdatePreference(mGestureArrowLeft, mScreenOffGestureSharedPreferences
                .getString(PREF_GESTURE_ARROW_LEFT, ButtonsConstants.ACTION_MEDIA_PREVIOUS));
        setupOrUpdatePreference(mGestureArrowRight, mScreenOffGestureSharedPreferences
                .getString(PREF_GESTURE_ARROW_RIGHT, ButtonsConstants.ACTION_MEDIA_NEXT));
        setupOrUpdatePreference(mGestureDoubleTap, mScreenOffGestureSharedPreferences
                .getString(PREF_GESTURE_DOUBLE_TAP, ButtonsConstants.ACTION_WAKE_DEVICE));

        boolean enableGestures =
                mScreenOffGestureSharedPreferences.getBoolean(PREF_GESTURE_ENABLE, true);
        mEnableGestures.setChecked(enableGestures);
        mEnableGestures.setOnPreferenceChangeListener(this);

        mCheckPreferences = true;
        return prefs;
    }

    private void setupOrUpdatePreference(Preference preference, String action) {
        if (preference == null || action == null) {
            return;
        }

        if (action.startsWith("**")) {
            preference.setSummary(getDescription(action));
        } else {
            preference.setSummary(AppHelper.getFriendlyNameForUri(
                    getActivity(), getActivity().getPackageManager(), action));
        }
        preference.setOnPreferenceClickListener(this);
    }

    private String getDescription(String action) {
        if (sFinalActionDialogArray == null || action == null) {
            return null;
        }
        int i = 0;
        for (String actionValue : sFinalActionDialogArray.values) {
            if (action.equals(actionValue)) {
                return sFinalActionDialogArray.entries[i];
            }
            i++;
        }
        return null;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String settingsKey = null;
        int dialogTitle = 0;
        if (preference == mGestureCircle) {
            settingsKey = PREF_GESTURE_CIRCLE;
            dialogTitle = R.string.gesture_circle_title;
        } else if (preference == mGestureDoubleSwipe) {
            settingsKey = PREF_GESTURE_DOUBLE_SWIPE;
            dialogTitle = R.string.gesture_double_swipe_title;
        } else if (preference == mGestureArrowUp) {
            settingsKey = PREF_GESTURE_ARROW_UP;
            dialogTitle = R.string.gesture_arrow_up_title;
        } else if (preference == mGestureArrowDown) {
            settingsKey = PREF_GESTURE_ARROW_DOWN;
            dialogTitle = R.string.gesture_arrow_down_title;
        } else if (preference == mGestureArrowLeft) {
            settingsKey = PREF_GESTURE_ARROW_LEFT;
            dialogTitle = R.string.gesture_arrow_left_title;
        } else if (preference == mGestureArrowRight) {
            settingsKey = PREF_GESTURE_ARROW_RIGHT;
            dialogTitle = R.string.gesture_arrow_right_title;
        } else if (preference == mGestureDoubleTap) {
            settingsKey = PREF_GESTURE_DOUBLE_TAP;
            dialogTitle = R.string.gesture_double_tap_title;
        }
        if (settingsKey != null) {
            showDialogInner(DLG_SHOW_ACTION_DIALOG, settingsKey, dialogTitle);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!mCheckPreferences) {
            return false;
        }
        if (preference == mEnableGestures) {
            mScreenOffGestureSharedPreferences.edit()
                    .putBoolean(PREF_GESTURE_ENABLE, (Boolean) newValue).commit();
            KernelControl.enableGestures((Boolean) newValue);
            return true;
        }
        if (preference == mEnableHaptic) {
            mScreenOffHapticSharedPreferences.edit()
                    .putBoolean(PREF_TOUCHSCREEN_HAPTIC_FEEDBACK_KEY, (Boolean) newValue).commit();
            KernelControl.enableHaptics((Boolean) newValue);
            return true;
        }
        return false;
    }

    // Reset all entries to default.
    private void resetToDefault() {
        SharedPreferences.Editor editor = mScreenOffGestureSharedPreferences.edit();
        mScreenOffGestureSharedPreferences.edit()
                .putBoolean(PREF_GESTURE_ENABLE, true).commit();
        mScreenOffHapticSharedPreferences.edit()
                .putBoolean(PREF_TOUCHSCREEN_HAPTIC_FEEDBACK_KEY, true).commit();
        editor.putString(PREF_GESTURE_CIRCLE,
                ButtonsConstants.ACTION_CAMERA).commit();
        editor.putString(PREF_GESTURE_DOUBLE_SWIPE,
                ButtonsConstants.ACTION_MEDIA_PLAY_PAUSE).commit();
        editor.putString(PREF_GESTURE_ARROW_UP,
                ButtonsConstants.ACTION_TORCH).commit();
        editor.putString(PREF_GESTURE_ARROW_DOWN,
                ButtonsConstants.ACTION_VIB_SILENT).commit();
        editor.putString(PREF_GESTURE_ARROW_LEFT,
                ButtonsConstants.ACTION_MEDIA_PREVIOUS).commit();
        editor.putString(PREF_GESTURE_ARROW_RIGHT,
                ButtonsConstants.ACTION_MEDIA_NEXT).commit();
        editor.putString(PREF_GESTURE_DOUBLE_TAP,
                ButtonsConstants.ACTION_WAKE_DEVICE).commit();
        editor.commit();
        KernelControl.enableGestures(true);
        KernelControl.enableHaptics(true);
        reloadSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void shortcutPicked(String action,
                String description, Bitmap bmp, boolean isApplication) {
        if (mPendingSettingsKey == null || action == null) {
            return;
        }
        mScreenOffGestureSharedPreferences.edit().putString(mPendingSettingsKey, action).commit();
        reloadSettings();
        mPendingSettingsKey = null;
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == ShortcutPickerHelper.REQUEST_PICK_SHORTCUT
                    || requestCode == ShortcutPickerHelper.REQUEST_PICK_APPLICATION
                    || requestCode == ShortcutPickerHelper.REQUEST_CREATE_SHORTCUT) {
                mPicker.onActivityResult(requestCode, resultCode, data);

            }
        } else {
            mPendingSettingsKey = null;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                    showDialogInner(DLG_RESET_TO_DEFAULT, null, 0);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, R.string.reset)
                .setIcon(R.drawable.ic_settings_reset)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    private void showDialogInner(int id, String settingsKey, int dialogTitle) {
        DialogFragment newFragment =
                MyAlertDialogFragment.newInstance(id, settingsKey, dialogTitle);
        newFragment.setTargetFragment(this, 0);
        newFragment.show(getFragmentManager(), "dialog " + id);
    }

    public static class MyAlertDialogFragment extends DialogFragment {

        public static MyAlertDialogFragment newInstance(
                int id, String settingsKey, int dialogTitle) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("id", id);
            args.putString("settingsKey", settingsKey);
            args.putInt("dialogTitle", dialogTitle);
            frag.setArguments(args);
            return frag;
        }

        ScreenOffGesture getOwner() {
            return (ScreenOffGesture) getTargetFragment();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int id = getArguments().getInt("id");
            final String settingsKey = getArguments().getString("settingsKey");
            int dialogTitle = getArguments().getInt("dialogTitle");
            switch (id) {
                case DLG_SHOW_ACTION_DIALOG:
                    if (sFinalActionDialogArray == null) {
                        return null;
                    }
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(dialogTitle)
                    .setNegativeButton(R.string.cancel, null)
                    .setItems(getOwner().sFinalActionDialogArray.entries,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int item) {
                            if (getOwner().sFinalActionDialogArray.values[item]
                                    .equals(ButtonsConstants.ACTION_APP)) {
                                if (getOwner().mPicker != null) {
                                    getOwner().mPendingSettingsKey = settingsKey;
                                    getOwner().mPicker.pickShortcut(getOwner().getId());
                                }
                            } else {
                                getOwner().mScreenOffGestureSharedPreferences.edit()
                                        .putString(settingsKey,
                                        getOwner().sFinalActionDialogArray.values[item]).commit();
                                getOwner().reloadSettings();
                            }
                        }
                    })
                    .create();
                case DLG_RESET_TO_DEFAULT:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.reset)
                    .setMessage(R.string.reset_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            getOwner().resetToDefault();
                        }
                    })
                    .create();
            }
            throw new IllegalArgumentException("unknown id " + id);
        }

        @Override
        public void onCancel(DialogInterface dialog) {
        }
    }

    public static void restore(Context context) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (KernelControl.isHapticSupported()) {
            Utils.writeValue(TOUCHSCREEN_HAPTIC_FEEDBACK_NODE,
                    sharedPrefs.getBoolean(PREF_TOUCHSCREEN_HAPTIC_FEEDBACK_KEY, true) ? "1" : "0");
        }
    }
}
