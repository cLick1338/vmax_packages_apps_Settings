//Vmax
package com.android.settings.gestures;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settings.widget.SeekBarPreference;

public class ButtonNavigationHeightPreference extends SeekBarPreference {
    private TextView mValue;

    public ButtonNavigationHeightPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.preference_button_navigation_height);
        setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateValue(progress);
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
        mValue = (TextView) holder.findViewById(R.id.button_navigation_height_value);
        updateValue(getProgress());
    }

    private void updateValue(int progress) {
        String value = getContext().getString(R.string.button_navigation_height_value, progress);
        if (mValue != null) {
            mValue.setText(value);
        }
        setSeekBarStateDescription(value);
    }
}
