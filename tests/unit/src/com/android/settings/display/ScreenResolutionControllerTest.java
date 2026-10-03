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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settings.core.BasePreferenceController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ScreenResolutionControllerTest {
    private Context mContext;
    private Display mDisplay;

    @Before
    public void setUp() {
        mContext = spy(ApplicationProvider.getApplicationContext());
        DisplayManager manager = mock(DisplayManager.class);
        mDisplay = mock(Display.class);
        doReturn(manager).when(mContext).getSystemService(DisplayManager.class);
        when(manager.getDisplay(Display.DEFAULT_DISPLAY)).thenReturn(mDisplay);
    }

    @Test
    public void threeResolutions_available() {
        when(mDisplay.getSupportedModes()).thenReturn(new Display.Mode[] {
                new Display.Mode(1, 1440, 2880, 60f),
                new Display.Mode(2, 720, 1440, 60f),
                new Display.Mode(3, 1080, 2160, 60f)});

        ScreenResolutionController controller = new ScreenResolutionController(mContext, "test");

        assertThat(controller.getAvailabilityStatus()).isEqualTo(BasePreferenceController.AVAILABLE);
        assertThat(controller.getAllSupportedResolutions()).hasSize(3);
    }

    @Test
    public void sameResolutionAtDifferentRefreshRates_unsupported() {
        when(mDisplay.getSupportedModes()).thenReturn(new Display.Mode[] {
                new Display.Mode(1, 1080, 2160, 60f),
                new Display.Mode(2, 1080, 2160, 90f)});

        assertThat(new ScreenResolutionController(mContext, "test").getAvailabilityStatus())
                .isEqualTo(BasePreferenceController.UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void summary_readsActiveModeIncludingIntermediateResolution() {
        when(mDisplay.getSupportedModes()).thenReturn(new Display.Mode[] {
                new Display.Mode(1, 720, 1440, 60f),
                new Display.Mode(2, 1080, 2160, 60f),
                new Display.Mode(3, 1440, 2880, 60f)});
        ScreenResolutionController controller = new ScreenResolutionController(mContext, "test");

        when(mDisplay.getMode()).thenReturn(new Display.Mode(2, 1080, 2160, 60f));
        assertThat(controller.getSummary().toString()).isEqualTo("1080x2160");
        when(mDisplay.getMode()).thenReturn(new Display.Mode(1, 720, 1440, 60f));
        assertThat(controller.getSummary().toString()).isEqualTo("720x1440");
        when(mDisplay.getMode()).thenReturn(new Display.Mode(3, 1440, 2880, 60f));
        assertThat(controller.getSummary().toString()).isEqualTo("1440x2880");
    }
}
