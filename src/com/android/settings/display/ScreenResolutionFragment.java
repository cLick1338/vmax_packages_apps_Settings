/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.IWindowManager;
import android.view.WindowManagerGlobal;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.RadioButtonPickerFragment;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.CandidateInfo;
import com.android.settingslib.widget.FooterPreference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Selects a physical panel resolution without forcing a refresh rate or logical display size. */
@SearchIndexable
public class ScreenResolutionFragment extends RadioButtonPickerFragment
        implements DisplayManager.DisplayListener {
    private static final String TAG = "ScreenResolution";
    private static final String SCREEN_RESOLUTION_KEY = "screen_resolution";
    // Shared with vmaxdisplay's boot restore and CLI. Use Global so all users share one panel mode.
    private static final String SAVED_RESOLUTION = "user_selected_resolution";
    // "<base milli-dpi at the base width> <last applied dpi> <last applied width>", shared with
    // vmaxdisplay. Scaling from the base avoids rounding drift across switches.
    private static final String SAVED_DENSITY = "user_selected_density";

    private DisplayManager mDisplayManager;
    private Display mDefaultDisplay;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mDefaultDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
    }

    @Override
    public void onResume() {
        super.onResume();
        mDisplayManager.registerDisplayListener(this, new Handler(Looper.getMainLooper()));
        updateCandidates();
    }

    @Override
    public void onPause() {
        mDisplayManager.unregisterDisplayListener(this);
        super.onPause();
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.screen_resolution_settings;
    }

    @Override
    protected void addStaticPreferences(PreferenceScreen screen) {
        final FooterPreference explanation = new FooterPreference(screen.getContext());
        explanation.setTitle(R.string.screen_resolution_explanation);
        explanation.setSelectable(false);
        // Static preferences precede the radio buttons, directly below the screen title.
        explanation.setOrder(-1);
        screen.addPreference(explanation);
    }

    @Override
    protected List<ResolutionCandidateInfo> getCandidates() {
        // Several refresh rates can advertise the same resolution. Show it only once.
        final List<Point> resolutions = new ArrayList<>(
                new ScreenResolutionController(getContext(), SCREEN_RESOLUTION_KEY)
                        .getAllSupportedResolutions());
        resolutions.sort(Comparator.comparingLong(p -> (long) p.x * p.y));
        final List<ResolutionCandidateInfo> candidates = new ArrayList<>();
        for (Point resolution : resolutions) {
            candidates.add(new ResolutionCandidateInfo(resolution.x, resolution.y));
        }
        return candidates;
    }

    @VisibleForTesting
    public Display.Mode getDisplayMode() {
        return mDefaultDisplay.getMode();
    }

    @Override
    protected String getDefaultKey() {
        final Display.Mode mode = getDisplayMode();
        return resolutionKey(mode.getPhysicalWidth(), mode.getPhysicalHeight());
    }

    @Override
    protected boolean setDefaultKey(String key) {
        for (ResolutionCandidateInfo candidate : getCandidates()) {
            if (!candidate.getKey().equals(key)) {
                continue;
            }
            try {
                if (!key.equals(getDefaultKey())) {
                    // Like vmaxdisplay's cmd display invocation, 0 leaves refresh rate unspecified.
                    mDefaultDisplay.setUserPreferredDisplayMode(
                            new Display.Mode(candidate.mWidth, candidate.mHeight, 0f));
                }
                setDisplayDensity(candidate.mWidth);
                // Persist even when reselecting the active mode: it may have been set externally.
                // On joan, vmaxdisplay reapplies this after boot; framework restoration alone
                // does not reliably restore the panel's resolution.
                if (!saveResolution(key)) {
                    Toast.makeText(getContext(), R.string.screen_resolution_save_failed,
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            } catch (RemoteException | RuntimeException e) {
                Log.e(TAG, "Could not change display resolution", e);
                Toast.makeText(getContext(), R.string.screen_resolution_change_failed,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return false;
    }

    @VisibleForTesting
    void setDisplayDensity(int width) throws RemoteException {
        final int baseWidth = getContext().getResources().getInteger(
                R.integer.config_screenResolutionBaseWidth);
        final int baseDensity = getContext().getResources().getInteger(
                R.integer.config_screenResolutionBaseDensity);
        if (baseWidth <= 0 || baseDensity <= 0) {
            return;
        }
        final IWindowManager wm = WindowManagerGlobal.getWindowManagerService();
        final int current = wm.getBaseDisplayDensity(Display.DEFAULT_DISPLAY);
        final long[] state = loadDensityState();
        final long base;
        if (state != null && (current <= 0 || current == state[1])) {
            base = state[0];
        } else if (state != null) {
            // wm density no longer matches what was applied: the user changed it at that width.
            base = baseFromDensity(current, (int) state[2], baseWidth);
        } else if (current > 0 && getDisplayMode().getPhysicalWidth() > 0) {
            base = baseFromDensity(current, getDisplayMode().getPhysicalWidth(), baseWidth);
        } else {
            base = baseDensity * 1000L;
        }
        final int density = densityForWidth(base, width, baseWidth);
        wm.setForcedDisplayDensityForUser(
                Display.DEFAULT_DISPLAY, density, UserHandle.myUserId());
        Settings.Global.putString(getContext().getContentResolver(), SAVED_DENSITY,
                base + " " + density + " " + width);
    }

    @VisibleForTesting
    static int densityForWidth(long baseMilli, int width, int baseWidth) {
        final long unit = baseWidth * 1000L;
        return (int) ((baseMilli * width + unit / 2) / unit);
    }

    @VisibleForTesting
    static long baseFromDensity(int density, int width, int baseWidth) {
        return ((long) density * baseWidth * 1000L + width / 2) / width;
    }

    private long[] loadDensityState() {
        final String value = Settings.Global.getString(getContext().getContentResolver(),
                SAVED_DENSITY);
        if (value == null) {
            return null;
        }
        final String[] parts = value.split(" ");
        try {
            if (parts.length == 3) {
                final long[] state = {Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                        Long.parseLong(parts[2])};
                if (state[0] > 0 && state[1] > 0 && state[2] > 0) {
                    return state;
                }
            }
        } catch (NumberFormatException e) {
            // Fall through: treated as no saved state.
        }
        return null;
    }

    @VisibleForTesting
    boolean saveResolution(String key) {
        return Settings.Global.putString(getContext().getContentResolver(), SAVED_RESOLUTION, key);
    }

    @Override
    protected void onSelectionPerformed(boolean success) {
        // A request is asynchronous. Keep the radio selection tied to the active panel mode.
        updateCheckedState(getDefaultKey());
    }

    @Override
    public void onDisplayChanged(int displayId) {
        if (displayId == Display.DEFAULT_DISPLAY) {
            updateCandidates();
        }
    }

    @Override
    public void onDisplayAdded(int displayId) {}

    @Override
    public void onDisplayRemoved(int displayId) {}

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SCREEN_RESOLUTION;
    }

    private static String resolutionKey(int width, int height) {
        return width + "x" + height;
    }

    static class ResolutionCandidateInfo extends CandidateInfo {
        private final int mWidth;
        private final int mHeight;

        ResolutionCandidateInfo(int width, int height) {
            super(true);
            mWidth = width;
            mHeight = height;
        }

        @Override
        public CharSequence loadLabel() {
            return getKey();
        }

        @Override
        public Drawable loadIcon() {
            return null;
        }

        @Override
        public String getKey() {
            return resolutionKey(mWidth, mHeight);
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.screen_resolution_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return new ScreenResolutionController(context, SCREEN_RESOLUTION_KEY)
                            .checkSupportedResolutions();
                }
            };
}
