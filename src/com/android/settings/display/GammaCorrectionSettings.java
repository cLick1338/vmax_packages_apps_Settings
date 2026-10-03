//Vmax

package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.content.Context;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class GammaCorrectionSettings extends DashboardFragment {
    @Override
    public void onResume() {
        super.onResume();
        try {
            findPreference("gamma_calibration").setSummary(GammaCalibration.load().custom
                    ? R.string.gamma_calibration_custom : R.string.gamma_calibration_default);
        } catch (java.io.IOException e) {
            findPreference("gamma_calibration").setSummary(R.string.gamma_calibration_unavailable);
        }
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.gamma_correction_settings;
    }

    @Override
    protected String getLogTag() {
        return "GammaCorrectionSettings";
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.gamma_correction_settings) {
                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return context.getResources().getBoolean(R.bool.config_showGammaCorrection);
                }
            };
}
