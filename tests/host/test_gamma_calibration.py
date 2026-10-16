#!/usr/bin/env python3
# Vmax
"""Run calibration format, interpolation and persistence checks without an Android device."""

from pathlib import Path
import shutil
import subprocess
import tempfile


settings = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="gamma-editor-") as directory:
    root = Path(directory)
    (root / "data").mkdir()
    source = (settings / "src/com/android/settings/display/GammaCalibration.java").read_text()
    source = source.replace("/data/vendor/vmaxdisplay", str(root / "data"))
    source = source.replace("/vendor/etc/vmaxdisplay/default_calibration.txt", str(root / "default"))
    (root / "GammaCalibration.java").write_text(source)
    (root / "GammaCalibrationBrightness.java").write_text((settings /
            "src/com/android/settings/display/GammaCalibrationBrightness.java").read_text())
    (root / "GammaCalibrationTest.java").write_text(r'''
package com.android.settings.display;

import java.io.IOException;
import java.nio.file.Files;
import java.util.TreeMap;
import java.util.function.DoubleUnaryOperator;

public class GammaCalibrationTest {
    static void check(boolean condition) {
        if (!condition) throw new AssertionError();
    }

    static void invalid(String text) throws Exception {
        try {
            GammaCalibration.parse(text, true);
            throw new AssertionError("Accepted invalid calibration: " + text);
        } catch (IOException expected) { }
    }

    public static void main(String[] args) throws Exception {
        // Model the complete forward path: window -> Android backlight range -> HAL -> panel.
        // In particular, a HAL level must not be passed directly as a window brightness.
        float minimum = 19 / 254f; // joan minimum HAL brightness is 20.
        DoubleUnaryOperator linear = GammaCalibrationBrightness.linear(minimum, 1f);
        // Nits-linear brightness: backlight rises roughly as the square root of brightness.
        DoubleUnaryOperator nits = b -> minimum + Math.sqrt(b) * (1f - minimum);
        for (DoubleUnaryOperator backlight : new DoubleUnaryOperator[]{linear, nits})
        for (int max : new int[]{511, 2047, 4095}) {
            GammaCalibrationBrightness mapping =
                    new GammaCalibrationBrightness(max, backlight);
            int previous = -1;
            for (int percent = 0; percent <= 100; percent++) {
                int level = mapping.level(percent);
                float window = mapping.windowBrightness(level);
                int deliveredLevel = (int) Math.round(
                        backlight.applyAsDouble(window) * 254 + 1);
                int deliveredRaw = deliveredLevel < 35 ? deliveredLevel * 2
                        : deliveredLevel * max / 255;
                check(deliveredLevel == level);
                check(deliveredRaw == mapping.raw(level));
                check(deliveredRaw >= previous);
                previous = deliveredRaw;
                // One-percent steps are represented within half a percent outside HAL gaps.
                if (percent >= 14) check(mapping.percent(deliveredRaw) == percent);
            }
            for (int level = 20; level <= 255; level++) {
                int raw = mapping.raw(level);
                int percent = mapping.percent(raw);
                if (percent > 0) check(mapping.raw(mapping.level(percent - 1)) <= raw);
                if (percent < 100) check(mapping.raw(mapping.level(percent + 1)) >= raw);
            }
        }
        // Joan's configured endpoint is 207/255; calibration must still reach 100%.
        GammaCalibrationBrightness capped = new GammaCalibrationBrightness(511,
                GammaCalibrationBrightness.linear(minimum, 206 / 254f));
        for (int percent = 81; percent <= 100; percent++) {
            int level = capped.level(percent);
            check(capped.percent(capped.raw(level)) == percent);
            check(capped.windowBrightness(level) <= 1f);
        }
        check(capped.level(100) == 255);
        check(capped.windowBrightness(capped.level(100)) == 1f);
        String defaults = "# ROM defaults\n0 44\n10 16\n20 15\n30 12\n40 8\n50 0\n100 0\n";
        Files.writeString(GammaCalibration.DEFAULT, defaults);
        check(!GammaCalibration.load().custom);
        TreeMap<Integer, Integer> points = GammaCalibration.load().points;
        check(points.size() == 7);
        check(GammaCalibration.interpolate(points, 0, 511) == 44);
        check(GammaCalibration.interpolate(points, 102, 511) == 15);
        check(GammaCalibration.interpolate(points, 128, 511) == 14);
        check(GammaCalibration.interpolate(points, 511, 511) == 0);
        TreeMap<Integer, Integer> negative = GammaCalibration.parse("20 -15\n40 -8\n", true);
        check(GammaCalibration.interpolate(negative, 0, 511) == -15);
        check(GammaCalibration.interpolate(negative, 153, 511) == -11);
        check(GammaCalibration.interpolate(negative, 511, 511) == -8);
        check(GammaCalibration.parse(GammaCalibration.format(negative), true).equals(negative));
        invalid("20 15\n20 16\n");
        invalid("101 1\n");
        invalid("20 256\n");
        invalid("hello\n");
        invalid("# no points\n");
        invalid("20 15 trailing\n");
        Files.writeString(GammaCalibration.CUSTOM.getParent().resolve("disabled"), "");
        GammaCalibration.save(negative);
        check(GammaCalibration.load().custom);
        check(GammaCalibration.load().points.equals(negative));
        check(Files.exists(GammaCalibration.CUSTOM.getParent().resolve("disabled")));
        GammaCalibration.reset();
        check(!GammaCalibration.load().custom);
        check(GammaCalibration.load().points.equals(points));
        check(Files.exists(GammaCalibration.CUSTOM.getParent().resolve("disabled")));
        Files.writeString(GammaCalibration.CUSTOM, "# empty custom file\n");
        check(!GammaCalibration.load().custom);
        check(GammaCalibration.load().points.equals(points));
        try {
            GammaCalibration.save(new TreeMap<>());
            throw new AssertionError("Empty calibration saved");
        } catch (IOException expected) { }
        System.out.println("Calibration format, interpolation, save/reset checks passed");
    }
}
''')
    javac = shutil.which("javac")
    java = shutil.which("java")
    if not javac:
        jdk = settings.parents[2] / "prebuilts/jdk/jdk21/linux-x86/bin"
        javac, java = str(jdk / "javac"), str(jdk / "java")
    subprocess.run([javac, "-d", str(root), str(root / "GammaCalibration.java"), str(root / "GammaCalibrationBrightness.java"),
                    str(root / "GammaCalibrationTest.java")], check=True)
    subprocess.run([java, "-cp", str(root), "com.android.settings.display.GammaCalibrationTest"],
                   check=True)
