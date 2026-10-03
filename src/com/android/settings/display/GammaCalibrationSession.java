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
            mChannel.truncate(0);
            ByteBuffer token = ByteBuffer.wrap(mToken.getBytes(StandardCharsets.US_ASCII));
            while (token.hasRemaining()) mChannel.write(token);
            mChannel.force(true);
        } catch (IOException | OverlappingFileLockException e) {
            close();
            throw new IOException("Cannot acquire calibration session", e);
        }
    }

    int[] status() throws IOException {
        // The daemon acknowledges only after identity is applied. Retry a concurrent status write.
        long deadline = System.nanoTime() + 1500000000L;
        while (!mClosed && System.nanoTime() < deadline) {
            try {
                String[] fields = new String(Files.readAllBytes(STATUS), StandardCharsets.US_ASCII).trim().split(" ");
                if (fields.length == 3 && fields[0].equals(mToken)) {
                    int max = Integer.parseInt(fields[1]);
                    int raw = Integer.parseInt(fields[2]);
                    if (max >= 100 && max <= 65535 && raw >= 0 && raw <= max) {
                        return new int[]{max, raw};
                    }
                }
            } catch (IOException | NumberFormatException ignored) {
                // An absent/incomplete acknowledgement is normal while the daemon applies identity.
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
