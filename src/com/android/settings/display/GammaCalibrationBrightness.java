//Vmax
package com.android.settings.display;

import java.util.function.DoubleUnaryOperator;

/** Inverts Android's brightness-to-backlight mapping and the joan light HAL's panel mapping. */
final class GammaCalibrationBrightness {
    private final int mMax;
    private final DoubleUnaryOperator mBacklight;
    private final int mMinimumLevel;

    // Map the full calibration range independently of the current manual brightness cap.
    GammaCalibrationBrightness(int max, DoubleUnaryOperator backlight) {
        mMax = max;
        mBacklight = backlight;
        mMinimumLevel = Math.max(1, halLevel(backlight.applyAsDouble(0)));
    }

    /** Brightness linear in backlight between the setting minimum and maximum (AOSP default). */
    static DoubleUnaryOperator linear(float minimum, float maximum) {
        return brightness -> minimum + brightness * (maximum - minimum);
    }

    // BrightnessSynchronizer maps backlight floats 0..1 to HAL integers 1..255.
    private static int halLevel(double backlight) {
        return (int) Math.round(backlight * 254 + 1);
    }

    int raw(int level) {
        return level < 35 ? level * 2 : level * mMax / 255;
    }

    int percent(int raw) {
        return Math.max(0, Math.min(100, Math.round(raw * 100f / mMax)));
    }

    float windowBrightness(int level) {
        // The mapping is monotonic, so bisect for the brightness that lands on this level.
        double target = (level - 1) / 254.0;
        double low = 0, high = 1;
        for (int i = 0; i < 40; i++) {
            double mid = (low + high) / 2;
            if (mBacklight.applyAsDouble(mid) < target) low = mid; else high = mid;
        }
        return (float) high;
    }

    int level(int percent) {
        int target = (Math.max(0, Math.min(100, percent)) * mMax + 50) / 100;
        int best = mMinimumLevel;
        for (int level = mMinimumLevel + 1; level <= 255; level++) {
            if (Math.abs(raw(level) - target) < Math.abs(raw(best) - target)) {
                best = level;
            }
        }
        return best;
    }
}
