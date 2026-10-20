//Vmax
package com.android.settings.fuelgauge;

import android.content.Context;
import android.os.SystemProperties;
import android.os.UserManager;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;

public class CpuEfficiencyModePreferenceController extends TogglePreferenceController {
    private static final String TAG = "CpuEfficiencyMode";
    // Vmax: retain the existing saved flag; efficiency mode is its inverse.
    private static final String PROPERTY = "persist.sys.vmax.cpu_high_freq";

    public CpuEfficiencyModePreferenceController(Context context, String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        if (!mContext.getResources().getBoolean(R.bool.config_show_cpu_efficiency_mode)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return mContext.getSystemService(UserManager.class).isAdminUser()
                ? AVAILABLE : DISABLED_FOR_USER;
    }

    @Override
    public boolean isChecked() {
        return !SystemProperties.getBoolean(PROPERTY, false);
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        try {
            SystemProperties.set(PROPERTY, isChecked ? "0" : "1");
            return isChecked() == isChecked;
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to save CPU frequency preference", e);
            return false;
        }
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_battery;
    }
}
