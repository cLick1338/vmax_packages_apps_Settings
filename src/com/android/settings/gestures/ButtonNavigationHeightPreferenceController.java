//Vmax
package com.android.settings.gestures;

import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.internal.util.NavigationBarUtils;
import com.android.settings.R;
import com.android.settings.core.BasePreferenceController;
import com.android.settings.widget.SeekBarPreference;

public class ButtonNavigationHeightPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {

    public ButtonNavigationHeightPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getInteger(
                com.android.internal.R.integer.config_navBarInteractionMode) == NAV_BAR_MODE_3BUTTON
                ? AVAILABLE : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    public void updateState(Preference preference) {
        final int height = NavigationBarUtils.getHeightScale(
                mContext.getContentResolver(), mContext.getUserId());
        ((SeekBarPreference) preference).setProgress(height);
        updateValue((SeekBarPreference) preference, height);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final int height = (Integer) newValue;
        if (height < NavigationBarUtils.HEIGHT_MIN || height > NavigationBarUtils.HEIGHT_MAX
                || !Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.BUTTON_NAVIGATION_HEIGHT, height)) {
            return false;
        }
        updateValue((SeekBarPreference) preference, height);
        return true;
    }

    private void updateValue(SeekBarPreference preference, int height) {
        final String value = mContext.getString(R.string.button_navigation_height_value, height);
        preference.setSeekBarStateDescription(value);
    }
}
