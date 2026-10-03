//Vmax

package com.android.settings.display;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.UUID;

/** The kernel releases the calibration lock on process death; a leftover file is inert. */
final class GammaCalibrationSession implements Closeable {
    private static final Path SESSION = Path.of("/data/vendor/vmaxdisplay/calibration.session");
    private static final Path STATUS = Path.of("/data/vendor/vmaxdisplay/calibration.status");
    private static GammaCalibrationSession sActiveSession;
    private final String mToken = UUID.randomUUID().toString().replace("-", "");
    private FileChannel mChannel;
    private FileLock mLock;
    private volatile boolean mClosed;
    private volatile int mRevision;

    int[] connect() throws IOException {
        acquire();
        return status();
    }

    private synchronized void acquire() throws IOException {
        if (mClosed) throw new IOException("Session already closed");
        synchronized (GammaCalibrationSession.class) {
            if (sActiveSession != null) throw new IOException("Calibration already in use");
            sActiveSession = this;
        }
        try {
            mChannel = FileChannel.open(SESSION, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            mLock = mChannel.tryLock();
            if (mLock == null) throw new IOException("Calibration already in use");
            Files.setPosixFilePermissions(SESSION, PosixFilePermissions.fromString("rw-r--r--"));
            writePreview(0);
        } catch (IOException | OverlappingFileLockException e) {
            close();
            throw new IOException("Cannot acquire calibration session", e);
        }
    }

    int[] preview(int value) throws IOException {
        int revision;
        synchronized (this) {
            if (mClosed || mChannel == null) throw new IOException("Session closed");
            if (value < -255 || value > 255) throw new IOException("Invalid preview value");
            mRevision++;
            revision = mRevision;
            writePreview(value);
        }
        int[] reply = status(revision);
        if (reply[3] != value) throw new IOException("Preview acknowledgement mismatch");
        return reply;
    }

    private void writePreview(int value) throws IOException {
        // Fixed-length records are replaced in one write on the locked inode. Do not truncate
        // first: a temporary empty file would tell the daemon that the editor has exited.
        byte[] record = String.format(Locale.ROOT, "%s %010d %+04d\n", mToken, mRevision, value)
                .getBytes(StandardCharsets.US_ASCII);
        if (mChannel.write(ByteBuffer.wrap(record), 0) != record.length) {
            throw new IOException("Incomplete preview request");
        }
        mChannel.force(true);
    }

    int[] status() throws IOException {
        return status(mRevision);
    }

    private int[] status(int revision) throws IOException {
        // The daemon acknowledges the revision only after its curve is applied.
        // Retry an absent or concurrent status write.
        long deadline = System.nanoTime() + 1500000000L;
        while (!mClosed && System.nanoTime() < deadline) {
            try {
                String[] fields = new String(Files.readAllBytes(STATUS), StandardCharsets.US_ASCII).trim().split(" ");
                if (fields.length == 5 && fields[0].equals(mToken)) {
                    int max = Integer.parseInt(fields[1]);
                    int raw = Integer.parseInt(fields[2]);
                    int appliedRevision = Integer.parseInt(fields[3]);
                    int value = Integer.parseInt(fields[4]);
                    if (max >= 100 && max <= 65535 && raw >= 0 && raw <= max
                            && appliedRevision == revision && value >= -255 && value <= 255) {
                        return new int[]{max, raw, appliedRevision, value};
                    }
                }
            } catch (IOException | NumberFormatException ignored) {
                // An absent/incomplete acknowledgement is normal while the daemon applies the preview.
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Calibration interrupted", e);
            }
        }
        throw new IOException("Calibration acknowledgement unavailable");
    }

    @Override
    public synchronized void close() {
        mClosed = true;
        if (mChannel != null) {
            try {
                // Wake inotify on normal exit; the daemon also detects crash releases by polling.
                if (mLock != null && mLock.isValid()) mChannel.truncate(0);
            } catch (IOException ignored) {
                // Closing the channel releases the lock even if truncation fails.
            } finally {
                try {
                    mChannel.close();
                } catch (IOException ignored) { }
                mChannel = null;
                mLock = null;
            }
        }
        synchronized (GammaCalibrationSession.class) {
            if (sActiveSession == this) sActiveSession = null;
        }
    }
}
