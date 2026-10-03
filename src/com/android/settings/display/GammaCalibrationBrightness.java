//Vmax
package com.android.settings.display;

/** Inverts Android's backlight range and the joan light HAL's panel mapping. */
final class GammaCalibrationBrightness {
    private final int mMax;
    private final float mMinimum;
    private final float mMaximum;

    GammaCalibrationBrightness(int max, float minimum, float maximum) {
        mMax = max;
        mMinimum = minimum;
        mMaximum = maximum;
    }

    int raw(int level) {
        return level < 35 ? level * 2 : level * mMax / 255;
    }

    int percent(int raw) {
        return Math.max(0, Math.min(100, Math.round(raw * 100f / mMax)));
    }

    float windowBrightness(int level) {
        // BrightnessSynchronizer maps HAL integers 1..255 to backlight floats 0..1.
        float backlight = (level - 1) / 254f;
        return Math.max(0f, Math.min(1f,
                (backlight - mMinimum) / (mMaximum - mMinimum)));
    }

    int level(int percent) {
        int target = (Math.max(0, Math.min(100, percent)) * mMax + 50) / 100;
        int minimum = Math.max(1, Math.round(mMinimum * 254 + 1));
        int maximum = Math.min(255, Math.round(mMaximum * 254 + 1));
        int best = minimum;
        for (int level = minimum + 1; level <= maximum; level++) {
            if (Math.abs(raw(level) - target) < Math.abs(raw(best) - target)) {
                best = level;
            }
        }
        return best;
    }
}
