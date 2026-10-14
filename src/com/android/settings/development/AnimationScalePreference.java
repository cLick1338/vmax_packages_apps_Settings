//Vmax
package com.android.settings.development;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.widget.SeekBarPreference;

import java.util.Locale;

public class AnimationScalePreference extends SeekBarPreference {
    private TextView mValueView;
    private float mScale = 1f;

    public AnimationScalePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_animation_scale);
        setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateValue(progress / 20f);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                updateValue(mScale);
            }
        });
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        final SeekBar seekBar = (SeekBar) holder.findViewById(
                com.android.internal.R.id.seekbar);
        seekBar.setKeyProgressIncrement(1);
        mValueView = (TextView) holder.findViewById(R.id.animation_scale_value);
        updateValue(mScale);
    }

    public void setScale(float scale) {
        mScale = scale;
        setProgress(Math.round(scale * 20));
        updateValue(scale);
    }

    private void updateValue(float scale) {
        final String value = String.format(Locale.ROOT, "%.2fx", scale);
        if (mValueView != null) {
            mValueView.setText(value);
        }
        setSeekBarStateDescription(value);
    }
}
