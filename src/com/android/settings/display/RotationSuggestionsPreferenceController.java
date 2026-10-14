//Vmax
package com.android.settings.display;

import android.content.Context;
import android.provider.Settings;

import com.android.internal.view.RotationPolicy;
import com.android.settings.core.TogglePreferenceController;

public class RotationSuggestionsPreferenceController extends TogglePreferenceController {

    public RotationSuggestionsPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return RotationPolicy.isRotationLockToggleVisible(mContext)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean isChecked() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.SHOW_ROTATION_SUGGESTIONS,
                Settings.Secure.SHOW_ROTATION_SUGGESTIONS_DEFAULT)
                == Settings.Secure.SHOW_ROTATION_SUGGESTIONS_ENABLED;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.SHOW_ROTATION_SUGGESTIONS,
                isChecked ? Settings.Secure.SHOW_ROTATION_SUGGESTIONS_ENABLED
                        : Settings.Secure.SHOW_ROTATION_SUGGESTIONS_DISABLED);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return com.android.settings.R.string.menu_key_display;
    }
}
