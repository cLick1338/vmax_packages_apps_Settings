/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.development;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.IWindowManager;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;

public class WindowAnimationScalePreferenceController extends
        DeveloperOptionsPreferenceController implements Preference.OnPreferenceChangeListener,
        PreferenceControllerMixin {

    private static final String WINDOW_ANIMATION_SCALE_KEY = "window_animation_scale";

    @VisibleForTesting
    static final int WINDOW_ANIMATION_SCALE_SELECTOR = 0;
    @VisibleForTesting
    static final float DEFAULT_VALUE = 1;

    private static final int STEPS_PER_SCALE = 20;
    private static final int MAX_PROGRESS = 40;

    private final IWindowManager mWindowManager;

    public WindowAnimationScalePreferenceController(Context context) {
        super(context);

        mWindowManager = IWindowManager.Stub.asInterface(
                ServiceManager.getService(Context.WINDOW_SERVICE));
    }

    @Override
    public String getPreferenceKey() {
        return WINDOW_ANIMATION_SCALE_KEY;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (!(newValue instanceof Integer)) {
            return false;
        }
        final int progress = (Integer) newValue;
        if (progress < 0 || progress > MAX_PROGRESS) {
            return false;
        }
        return writeAnimationScaleOption(progress / (float) STEPS_PER_SCALE);
    }

    @Override
    public void updateState(Preference preference) {
        updateAnimationScaleValue();
    }

    @Override
    protected void onDeveloperOptionsSwitchDisabled() {
        super.onDeveloperOptionsSwitchDisabled();
        writeAnimationScaleOption(DEFAULT_VALUE);
    }

    private boolean writeAnimationScaleOption(float scale) {
        try {
            mWindowManager.setAnimationScale(WINDOW_ANIMATION_SCALE_SELECTOR, scale);
            updateAnimationScaleValue();
            return true;
        } catch (RemoteException e) {
            return false;
        }
    }

    private void updateAnimationScaleValue() {
        try {
            final float scale = mWindowManager.getAnimationScale(WINDOW_ANIMATION_SCALE_SELECTOR);
            ((AnimationScalePreference) mPreference).setScale(scale);
        } catch (RemoteException e) {
            // intentional no-op
        }
    }
}
