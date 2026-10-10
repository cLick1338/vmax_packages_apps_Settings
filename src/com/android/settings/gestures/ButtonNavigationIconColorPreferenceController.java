//Vmax
package com.android.settings.gestures;

import static android.view.WindowManagerPolicyConstants.NAV_BAR_MODE_3BUTTON;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;

import com.android.internal.util.NavigationBarUtils;
import com.android.settings.core.BasePreferenceController;

public class ButtonNavigationIconColorPreferenceController extends BasePreferenceController
        implements Preference.OnPreferenceChangeListener {

    public ButtonNavigationIconColorPreferenceController(Context context, String key) {
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
        ((ButtonNavigationIconColorPreference) preference).setValue(NavigationBarUtils.getIconColor(
                mContext.getContentResolver(), mContext.getUserId()));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.BUTTON_NAVIGATION_ICON_COLOR, (Integer) newValue);
    }
}
