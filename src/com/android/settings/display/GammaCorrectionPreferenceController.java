//Vmax

package com.android.settings.display;

import android.content.Context;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.TogglePreferenceController;
import com.android.settings.widget.SettingsMainSwitchPreference;
import com.android.settingslib.PrimarySwitchPreference;
import com.android.settingslib.core.lifecycle.LifecycleObserver;
import com.android.settingslib.core.lifecycle.events.OnStart;
import com.android.settingslib.core.lifecycle.events.OnStop;

import java.io.File;
import java.io.FileDescriptor;

/** Controls vmaxdisplay's persistent disabled flag without changing the calibration. */
public class GammaCorrectionPreferenceController extends TogglePreferenceController
        implements LifecycleObserver, OnStart, OnStop {
    private static final String TAG = "GammaCorrection";
    private static final String DISABLED_PATH = "/data/vendor/vmaxdisplay/disabled";

    private final File mDisabledFile;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private FileObserver mObserver;
    private Preference mPreference;

    public GammaCorrectionPreferenceController(Context context, String key) {
        this(context, key, new File(DISABLED_PATH));
    }

    @VisibleForTesting
    GammaCorrectionPreferenceController(Context context, String key, File disabledFile) {
        super(context, key);
        mDisabledFile = disabledFile;
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(R.bool.config_showGammaCorrection)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean isChecked() {
        try {
            Os.stat(mDisabledFile.getPath());
            return false;
        } catch (ErrnoException e) {
            if (e.errno == OsConstants.ENOENT) {
                return true;
            }
            Log.e(TAG, "Could not read gamma correction state", e);
            return false;
        }
    }

    @Override
    public boolean setChecked(boolean checked) {
        if (!isAvailable()) {
            return false;
        }
        try {
            if (checked) {
                Os.unlink(mDisabledFile.getPath());
            } else {
                FileDescriptor fd = Os.open(mDisabledFile.getPath(),
                        OsConstants.O_WRONLY | OsConstants.O_CREAT | OsConstants.O_EXCL, 0644);
                Os.close(fd);
            }
            return true;
        } catch (ErrnoException e) {
            if ((checked && e.errno == OsConstants.ENOENT)
                    || (!checked && e.errno == OsConstants.EEXIST)) {
                return true;
            }
            Log.e(TAG, "Could not change gamma correction state", e);
            Toast.makeText(mContext, R.string.gamma_correction_change_failed,
                    Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        if (mPreference instanceof SettingsMainSwitchPreference) {
            SettingsMainSwitchPreference pref = (SettingsMainSwitchPreference) mPreference;
            pref.addOnSwitchChangeListener((button, checked) -> {
                if (checked != isChecked()) {
                    setChecked(checked);
                    pref.setChecked(isChecked());
                }
            });
        }
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (preference instanceof PrimarySwitchPreference) {
            refreshSummary(preference);
        }
    }

    @Override
    public CharSequence getSummary() {
        return mContext.getText(isChecked() ? R.string.switch_on_text : R.string.switch_off_text);
    }

    @Override
    public int getSliceHighlightMenuRes() {
        return R.string.menu_key_display;
    }

    @Override
    public void onStart() {
        if (!isAvailable()) {
            return;
        }
        mObserver = new FileObserver(mDisabledFile.getParentFile(),
                FileObserver.CREATE | FileObserver.DELETE | FileObserver.MOVED_FROM
                        | FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE | FileObserver.ATTRIB) {
            @Override
            public void onEvent(int event, String path) {
                if (mDisabledFile.getName().equals(path)) {
                    mHandler.post(() -> {
                        if (mPreference != null) {
                            updateState(mPreference);
                        }
                    });
                }
            }
        };
        mObserver.startWatching();
        if (mPreference != null) {
            updateState(mPreference);
        }
    }

    @Override
    public void onStop() {
        if (mObserver != null) {
            mObserver.stopWatching();
            mObserver = null;
        }
        mHandler.removeCallbacksAndMessages(null);
    }
}
