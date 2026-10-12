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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.RemoteException;
import android.view.Display;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ScreenResolutionFragmentTest {
    private ScreenResolutionFragment mFragment;
    private Display mDisplay;

    @Before
    @UiThreadTest
    public void setUp() throws RemoteException {
        Context context = spy(ApplicationProvider.getApplicationContext());
        DisplayManager manager = mock(DisplayManager.class);
        mDisplay = mock(Display.class);
        doReturn(manager).when(context).getSystemService(DisplayManager.class);
        when(manager.getDisplay(Display.DEFAULT_DISPLAY)).thenReturn(mDisplay);
        when(mDisplay.getSupportedModes()).thenReturn(new Display.Mode[] {
                new Display.Mode(1, 1440, 2880, 60f),
                new Display.Mode(2, 720, 1440, 60f),
                new Display.Mode(3, 1080, 2160, 60f),
                new Display.Mode(4, 1080, 2160, 90f)});
        when(mDisplay.getMode()).thenReturn(new Display.Mode(1, 1440, 2880, 60f));
        mFragment = spy(new ScreenResolutionFragment());
        doReturn(context).when(mFragment).getContext();
        mFragment.onAttach(context);
        doReturn(true).when(mFragment).saveResolution(any());
        doNothing().when(mFragment).setDisplayDensity(anyInt());
    }

    @Test
    @UiThreadTest
    public void candidates_threeDistinctResolutionsInAscendingOrder() {
        List<ScreenResolutionFragment.ResolutionCandidateInfo> candidates = mFragment.getCandidates();

        assertThat(candidates).hasSize(3);
        assertThat(candidates.get(0).getKey()).isEqualTo("720x1440");
        assertThat(candidates.get(1).getKey()).isEqualTo("1080x2160");
        assertThat(candidates.get(2).getKey()).isEqualTo("1440x2880");
    }

    @Test
    @UiThreadTest
    public void selection_requestsPhysicalResolutionWithoutForcingRefreshRate()
            throws RemoteException {
        assertThat(mFragment.setDefaultKey("1080x2160")).isTrue();
        verify(mFragment).saveResolution("1080x2160");
        verify(mFragment).setDisplayDensity(1080);
        ArgumentCaptor<Display.Mode> mode = ArgumentCaptor.forClass(Display.Mode.class);
        verify(mDisplay).setUserPreferredDisplayMode(mode.capture());
        assertThat(mode.getValue().getPhysicalWidth()).isEqualTo(1080);
        assertThat(mode.getValue().getPhysicalHeight()).isEqualTo(2160);
        assertThat(mode.getValue().getRefreshRate()).isEqualTo(0f);
        // The request alone must not change what the UI reports as active.
        assertThat(mFragment.getDefaultKey()).isEqualTo("1440x2880");
        when(mDisplay.getMode()).thenReturn(new Display.Mode(3, 1080, 2160, 60f));
        assertThat(mFragment.getDefaultKey()).isEqualTo("1080x2160");
    }

    @Test
    @UiThreadTest
    public void invalidResolution_doesNotRequestModeChange() {
        assertThat(mFragment.setDefaultKey("720x2880")).isFalse();
        verify(mDisplay, never()).setUserPreferredDisplayMode(any());
        verify(mFragment, never()).saveResolution(any());
    }

    @Test
    @UiThreadTest
    public void reselectActiveResolution_savesForNextBoot() {
        assertThat(mFragment.setDefaultKey("1440x2880")).isTrue();
        verify(mFragment).saveResolution("1440x2880");
        verify(mDisplay, never()).setUserPreferredDisplayMode(any());
    }

    @Test
    public void density_scalesFromBaseWithoutDriftAcrossSwitches() {
        // 450 dpi at QHD: 337.5 at FHD rounds up, yet returning to QHD must give 450 again.
        final long base = ScreenResolutionFragment.baseFromDensity(450, 1440, 1440);
        int density = 0;
        for (int i = 0; i < 10; i++) {
            density = ScreenResolutionFragment.densityForWidth(base, 1080, 1440);
            assertThat(density).isEqualTo(338);
            density = ScreenResolutionFragment.densityForWidth(base, 720, 1440);
            assertThat(density).isEqualTo(225);
            density = ScreenResolutionFragment.densityForWidth(base, 1440, 1440);
            assertThat(density).isEqualTo(450);
        }
        // A user density picked at FHD becomes a base that reproduces itself there.
        final long fromFhd = ScreenResolutionFragment.baseFromDensity(338, 1080, 1440);
        assertThat(ScreenResolutionFragment.densityForWidth(fromFhd, 1080, 1440)).isEqualTo(338);
    }
}
