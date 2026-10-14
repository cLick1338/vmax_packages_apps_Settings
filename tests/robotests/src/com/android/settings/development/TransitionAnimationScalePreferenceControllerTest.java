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

import static com.android.settings.development.TransitionAnimationScalePreferenceController.TRANSITION_ANIMATION_SCALE_SELECTOR;
import static com.android.settings.development.TransitionAnimationScalePreferenceController.DEFAULT_VALUE;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.RemoteException;
import android.view.IWindowManager;

import androidx.preference.PreferenceScreen;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
public class TransitionAnimationScalePreferenceControllerTest {

    @Mock
    private AnimationScalePreference mPreference;
    @Mock
    private PreferenceScreen mScreen;
    @Mock
    private IWindowManager mWindowManager;

    private Context mContext;
    private TransitionAnimationScalePreferenceController mController;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mController = new TransitionAnimationScalePreferenceController(mContext);
        ReflectionHelpers.setField(mController, "mWindowManager", mWindowManager);
        when(mScreen.findPreference(mController.getPreferenceKey())).thenReturn(mPreference);
        mController.displayPreference(mScreen);
    }

    @Test
    public void onPreferenceChange_fractionalStep_shouldSetExactScale() throws RemoteException {
        assertThat(mController.onPreferenceChange(mPreference, 17)).isTrue();

        verify(mWindowManager).setAnimationScale(TRANSITION_ANIMATION_SCALE_SELECTOR, 0.85f);
    }

    @Test
    public void onPreferenceChange_endpoints_shouldSetZeroAndTwo() throws RemoteException {
        assertThat(mController.onPreferenceChange(mPreference, 0)).isTrue();
        assertThat(mController.onPreferenceChange(mPreference, 40)).isTrue();

        verify(mWindowManager).setAnimationScale(TRANSITION_ANIMATION_SCALE_SELECTOR, 0f);
        verify(mWindowManager).setAnimationScale(TRANSITION_ANIMATION_SCALE_SELECTOR, 2f);
    }

    @Test
    public void onPreferenceChange_invalidProgress_shouldReject() throws RemoteException {
        assertThat(mController.onPreferenceChange(mPreference, -1)).isFalse();
        assertThat(mController.onPreferenceChange(mPreference, 41)).isFalse();
        assertThat(mController.onPreferenceChange(mPreference, null)).isFalse();

        verifyNoInteractions(mWindowManager);
    }

    @Test
    public void onPreferenceChange_remoteFailure_shouldReject() throws RemoteException {
        org.mockito.Mockito.doThrow(new RemoteException()).when(mWindowManager)
                .setAnimationScale(TRANSITION_ANIMATION_SCALE_SELECTOR, 0.85f);

        assertThat(mController.onPreferenceChange(mPreference, 17)).isFalse();
    }

    @Test
    public void updateState_fractionalScale_shouldShowExactValue() throws RemoteException {
        when(mWindowManager.getAnimationScale(TRANSITION_ANIMATION_SCALE_SELECTOR)).thenReturn(0.85f);

        mController.updateState(mPreference);

        verify(mPreference).setScale(0.85f);
    }

    @Test
    public void onDeveloperOptionsSwitchDisabled_shouldResetAndDisable() throws RemoteException {
        mController.onDeveloperOptionsSwitchDisabled();

        verify(mWindowManager).setAnimationScale(TRANSITION_ANIMATION_SCALE_SELECTOR, DEFAULT_VALUE);
        verify(mPreference).setEnabled(false);
    }
}
