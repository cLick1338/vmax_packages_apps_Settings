//Vmax
package com.android.settings.display;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.RadioButtonPickerFragment;
import com.android.settings.widget.SeekBarPreference;
import com.android.settingslib.search.SearchIndexable;
import com.android.settingslib.widget.CandidateInfo;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable
public class ScreenOffAnimationSettingsFragment extends RadioButtonPickerFragment {
    private static final int ANIMATION_FADE = 0;
    private static final int ANIMATION_CRT = 1;
    private static final int ANIMATION_OFF = 2;
    private static final int SPEED_DEFAULT_TENTHS = 10;
    private static final int SPEED_MIN_TENTHS = 1;
    private static final int SPEED_MAX_TENTHS = 20;
    private static final int SPEED_SLIDER_MAX = SPEED_MAX_TENTHS - SPEED_MIN_TENTHS;
    private static final String KEY_SPEED_SLIDER = "screen_off_animation_speed_slider";

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.screen_off_animation_settings;
    }

    @Override
    protected void addStaticPreferences(PreferenceScreen screen) {
        final ScreenOffSpeedPreference speedPreference =
                new ScreenOffSpeedPreference(screen.getContext());
        speedPreference.setKey(KEY_SPEED_SLIDER);
        speedPreference.setTitle(R.string.screen_off_animation_speed_title);
        speedPreference.setOrder(-1);
        speedPreference.setPersistent(false);
        speedPreference.setMin(0);
        speedPreference.setMax(SPEED_SLIDER_MAX);

        final int speed = getSpeedSetting();
        speedPreference.setProgress(speed - SPEED_MIN_TENTHS);
        speedPreference.setSeekBarContentDescription(
                getString(R.string.screen_off_animation_speed_title));
        speedPreference.setSeekBarStateDescription(getSpeedValue(speed));
        speedPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (!(newValue instanceof Integer)) {
                return false;
            }
            final int newSpeed = clampSpeed((int) newValue + SPEED_MIN_TENTHS);
            if (!Settings.System.putIntForUser(getContext().getContentResolver(),
                    Settings.System.SCREEN_OFF_ANIMATION_SPEED, newSpeed, mUserId)) {
                return false;
            }
            return true;
        });
        screen.addPreference(speedPreference);
    }

    @Override
    protected List<? extends CandidateInfo> getCandidates() {
        final List<CandidateInfo> candidates = new ArrayList<>();
        candidates.add(new AnimationCandidateInfo(ANIMATION_OFF,
                getString(R.string.screen_off_animation_off)));
        candidates.add(new AnimationCandidateInfo(ANIMATION_FADE,
                getString(R.string.screen_off_animation_fade)));
        candidates.add(new AnimationCandidateInfo(ANIMATION_CRT,
                getString(R.string.screen_off_animation_crt)));
        return candidates;
    }

    @Override
    protected String getDefaultKey() {
        final int value = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.SCREEN_OFF_ANIMATION, ANIMATION_FADE, mUserId);
        return Integer.toString(isValidAnimation(value) ? value : ANIMATION_FADE);
    }

    @Override
    protected boolean setDefaultKey(String key) {
        final int value;
        try {
            value = Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return false;
        }
        return isValidAnimation(value) && Settings.System.putIntForUser(
                getContext().getContentResolver(), Settings.System.SCREEN_OFF_ANIMATION,
                value, mUserId);
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.DISPLAY;
    }

    private static boolean isValidAnimation(int value) {
        return value == ANIMATION_FADE || value == ANIMATION_CRT || value == ANIMATION_OFF;
    }

    private int getSpeedSetting() {
        final int speed = Settings.System.getIntForUser(getContext().getContentResolver(),
                Settings.System.SCREEN_OFF_ANIMATION_SPEED, SPEED_DEFAULT_TENTHS, mUserId);
        return speed < SPEED_MIN_TENTHS || speed > SPEED_MAX_TENTHS
                ? SPEED_DEFAULT_TENTHS : speed;
    }

    private static int clampSpeed(int speed) {
        return Math.max(SPEED_MIN_TENTHS, Math.min(SPEED_MAX_TENTHS, speed));
    }

    private String getSpeedValue(int speed) {
        return getString(R.string.screen_off_animation_speed_value,
                speed / 10, speed % 10);
    }

    private final class ScreenOffSpeedPreference extends SeekBarPreference {
        private TextView mSpeedValue;

        ScreenOffSpeedPreference(Context context) {
            super(context);
            setLayoutResource(R.layout.preference_screen_off_animation_speed);
            setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateSpeedValue(progress);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            mSpeedValue = (TextView) holder.findViewById(
                    R.id.screen_off_animation_speed_value);
            updateSpeedValue(getProgress());
        }

        private void updateSpeedValue(int progress) {
            final String value = getSpeedValue(progress + SPEED_MIN_TENTHS);
            if (mSpeedValue != null) {
                mSpeedValue.setText(value);
            }
            setSeekBarStateDescription(value);
        }
    }

    private static class AnimationCandidateInfo extends CandidateInfo {
        private final int mValue;
        private final String mLabel;

        AnimationCandidateInfo(int value, String label) {
            super(true);
            mValue = value;
            mLabel = label;
        }

        @Override
        public CharSequence loadLabel() {
            return mLabel;
        }

        @Override
        public Drawable loadIcon() {
            return null;
        }

        @Override
        public String getKey() {
            return Integer.toString(mValue);
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.screen_off_animation_settings);
}
