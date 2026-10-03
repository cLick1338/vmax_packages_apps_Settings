//Vmax

package com.android.settings.display;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.TreeMap;

/** File format and interpolation shared with vmaxdisplay.c. Percentages are raw panel levels. */
final class GammaCalibration {
    static final Path CUSTOM = Path.of("/data/vendor/vmaxdisplay/calibration.txt");
    static final Path DEFAULT = Path.of("/vendor/etc/vmaxdisplay/default_calibration.txt");
    static final int MAX_BYTES = 16384;

    final TreeMap<Integer, Integer> points;
    final boolean custom;

    GammaCalibration(TreeMap<Integer, Integer> points, boolean custom) {
        this.points = points;
        this.custom = custom;
    }

    static GammaCalibration load() throws IOException {
        if (Files.exists(CUSTOM)) {
            TreeMap<Integer, Integer> points = read(CUSTOM);
            if (!points.isEmpty()) return new GammaCalibration(points, true);
        }
        return new GammaCalibration(defaults(), false);
    }

    static TreeMap<Integer, Integer> defaults() throws IOException {
        TreeMap<Integer, Integer> points = read(DEFAULT);
        if (points.isEmpty()) throw new IOException("No default calibration points");
        return points;
    }

    private static TreeMap<Integer, Integer> read(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return parse(readText(in), false);
        }
    }

    static String readText(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) throw new IOException("Calibration file is too large");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static TreeMap<Integer, Integer> parse(String text, boolean strict) throws IOException {
        TreeMap<Integer, Integer> points = new TreeMap<>();
        int count = 0;
        for (String line : text.split("\\R")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] parts = line.split("\\s+");
            try {
                if (parts.length < 2 || (strict && parts.length != 2)) {
                    throw new NumberFormatException();
                }
                int percent = Integer.parseInt(parts[0]);
                int value = Integer.parseInt(parts[1]);
                if (strict && (percent < 0 || percent > 100 || value < -255 || value > 255
                        || points.containsKey(percent))) throw new NumberFormatException();
                points.put(Math.max(0, Math.min(100, percent)),
                        Math.max(-255, Math.min(255, value)));
                if (++count == 101 && !strict) break;
            } catch (NumberFormatException e) {
                if (strict) throw new IOException("Invalid or duplicate calibration point", e);
            }
        }
        if (strict && points.isEmpty()) throw new IOException("No calibration points");
        return points;
    }

    static String format(TreeMap<Integer, Integer> points) {
        StringBuilder text = new StringBuilder("# Brightness percent | Darkest visible gray\n");
        for (Map.Entry<Integer, Integer> point : points.entrySet()) {
            text.append(point.getKey()).append(' ').append(point.getValue()).append('\n');
        }
        return text.toString();
    }

    static int rawLevel(int percent, int max) {
        return (percent * max + 50) / 100;
    }

    /** Reproduce native integer LUT interpolation, then round to an editable 8-bit value. */
    static int interpolate(TreeMap<Integer, Integer> points, int raw, int max) {
        if (points.isEmpty()) return 0;
        Map.Entry<Integer, Integer> previous = points.firstEntry();
        if (raw <= rawLevel(previous.getKey(), max)) return previous.getValue();
        for (Map.Entry<Integer, Integer> point : points.entrySet()) {
            int high = rawLevel(point.getKey(), max);
            if (raw <= high) {
                int low = rawLevel(previous.getKey(), max);
                long value = previous.getValue() * 4L
                        + (point.getValue() - previous.getValue()) * 4L * (raw - low)
                        / (high - low);
                return (int) Math.round(value / 4.0);
            }
            previous = point;
        }
        return points.lastEntry().getValue();
    }

    static void save(TreeMap<Integer, Integer> points) throws IOException {
        if (points.isEmpty()) throw new IOException("No calibration points");
        Path temporary = Files.createTempFile(CUSTOM.getParent(), "calibration-", ".tmp");
        try {
            Files.write(temporary, format(points).getBytes(StandardCharsets.UTF_8));
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-r--r--"));
            Files.move(temporary, CUSTOM, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void reset() throws IOException {
        // Match vmaxdisplay's default source, while preserving the user's gamma toggle.
        Files.deleteIfExists(CUSTOM);
    }
}
