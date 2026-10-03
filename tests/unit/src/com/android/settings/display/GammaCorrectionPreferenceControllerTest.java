//Vmax

package com.android.settings.display;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import android.content.Context;
import android.content.res.Resources;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;

@RunWith(AndroidJUnit4.class)
public class GammaCorrectionPreferenceControllerTest {
    private Context mContext;
    private Resources mResources;
    private File mDirectory;
    private File mDisabledFile;
    private GammaCorrectionPreferenceController mController;

    @Before
    @UiThreadTest
    public void setUp() throws Exception {
        mContext = spy(ApplicationProvider.getApplicationContext());
        mResources = spy(mContext.getResources());
        doReturn(mResources).when(mContext).getResources();
        doReturn(true).when(mResources).getBoolean(R.bool.config_showGammaCorrection);
        mDirectory = Files.createTempDirectory(mContext.getCacheDir().toPath(), "gamma-").toFile();
        mDisabledFile = new File(mDirectory, "disabled");
        mController = new GammaCorrectionPreferenceController(mContext, "gamma", mDisabledFile);
    }

    @After
    public void tearDown() throws Exception {
        for (File file : mDirectory.listFiles()) {
            Files.delete(file.toPath());
        }
        Files.delete(mDirectory.toPath());
    }

    @Test
    @UiThreadTest
    public void toggling_preservesCalibrationAndPersistsAcrossControllerInstances() throws Exception {
        File calibration = new File(mDirectory, "calibration.txt");
        byte[] contents = "10 3\n90 -2\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(calibration.toPath(), contents);

        assertThat(mController.isChecked()).isTrue();
        assertThat(mController.setChecked(false)).isTrue();
        GammaCorrectionPreferenceController second = new GammaCorrectionPreferenceController(
                mContext, "gamma_detail", mDisabledFile);
        assertThat(second.isChecked()).isFalse();
        assertThat(second.setChecked(true)).isTrue();
        assertThat(mController.isChecked()).isTrue();
        assertThat(Files.readAllBytes(calibration.toPath())).isEqualTo(contents);
    }

    @Test
    @UiThreadTest
    public void existingCliFlag_isDisabledAndCanBeReenabled() throws Exception {
        Files.createFile(mDisabledFile.toPath());
        assertThat(mController.isChecked()).isFalse();
        assertThat(mController.setChecked(false)).isTrue();
        assertThat(mController.setChecked(true)).isTrue();
        assertThat(mDisabledFile.exists()).isFalse();
        assertThat(mController.setChecked(true)).isTrue();
    }

    @Test
    @UiThreadTest
    public void unsupportedDevice_doesNotWriteFlag() {
        doReturn(false).when(mResources).getBoolean(R.bool.config_showGammaCorrection);
        assertThat(mController.getAvailabilityStatus())
                .isEqualTo(BasePreferenceController.UNSUPPORTED_ON_DEVICE);
        assertThat(mController.setChecked(false)).isFalse();
        assertThat(mDisabledFile.exists()).isFalse();
    }
}
