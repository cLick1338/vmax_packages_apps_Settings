//Vmax
package com.android.settings.gestures;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.PreferenceViewHolder;

import com.android.settings.R;
import com.android.settingslib.CustomDialogPreferenceCompat;

public class ButtonNavigationIconColorPreference extends CustomDialogPreferenceCompat {
    private int mValue;
    private SeekBar[] mChannels;
    private TextView[] mChannelValues;
    private View mPreview;
    private final int[] mChannelNames = {
            R.string.button_navigation_color_red,
            R.string.button_navigation_color_green,
            R.string.button_navigation_color_blue
    };

    public ButtonNavigationIconColorPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.dialog_button_navigation_icon_color);
        setWidgetLayoutResource(R.layout.preference_color);
        setPositiveButtonText(R.string.button_navigation_color_apply);
        setNegativeButtonText(android.R.string.cancel);
    }

    public void setValue(int value) {
        mValue = value;
        setSummary(value == 0 ? getContext().getString(R.string.button_navigation_color_automatic)
                : getContext().getString(R.string.button_navigation_color_rgb,
                        Color.red(value), Color.green(value), Color.blue(value)));
        notifyChanged();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        ImageView preview = (ImageView) holder.findViewById(R.id.color_preview);
        preview.setBackgroundResource(mValue == 0 ? R.drawable.transparency_tileable : 0);
        preview.setImageDrawable(new ColorDrawable(mValue));
        preview.setContentDescription(getSummary());
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder,
            DialogInterface.OnClickListener listener) {
        builder.setNeutralButton(R.string.button_navigation_color_automatic, listener);
    }

    @Override
    protected void onBindDialogView(View view) {
        int color = mValue == 0 ? Color.WHITE : mValue;
        mChannels = new SeekBar[] {
                view.findViewById(R.id.button_navigation_red),
                view.findViewById(R.id.button_navigation_green),
                view.findViewById(R.id.button_navigation_blue)
        };
        mChannelValues = new TextView[] {
                view.findViewById(R.id.button_navigation_red_value),
                view.findViewById(R.id.button_navigation_green_value),
                view.findViewById(R.id.button_navigation_blue_value)
        };
        mPreview = view.findViewById(R.id.button_navigation_color_preview);
        int[] values = {Color.red(color), Color.green(color), Color.blue(color)};
        for (int i = 0; i < mChannels.length; i++) {
            mChannels[i].setProgress(values[i]);
        }
        for (SeekBar channel : mChannels) {
            channel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updatePreview();
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {}

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
        updatePreview();
    }

    private int getSelectedColor() {
        return Color.rgb(mChannels[0].getProgress(), mChannels[1].getProgress(),
                mChannels[2].getProgress());
    }

    private void updatePreview() {
        mPreview.setBackgroundColor(getSelectedColor());
        for (int i = 0; i < mChannels.length; i++) {
            int value = mChannels[i].getProgress();
            mChannelValues[i].setText(getContext().getString(
                    R.string.button_navigation_color_channel_value,
                    getContext().getString(mChannelNames[i]), value));
            mChannels[i].setStateDescription(Integer.toString(value));
        }
    }

    private void applyColor(int color) {
        if (callChangeListener(color)) {
            setValue(color);
        }
    }

    @Override
    protected void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_NEUTRAL) {
            applyColor(0);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            applyColor(getSelectedColor());
        }
        mChannels = null;
        mChannelValues = null;
        mPreview = null;
    }
}
