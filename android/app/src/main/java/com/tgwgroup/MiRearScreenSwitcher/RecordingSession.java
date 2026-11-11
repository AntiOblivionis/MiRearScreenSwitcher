package com.tgwgroup.MiRearScreenSwitcher;

import android.media.MediaMetadataRetriever;
import android.os.IBinder;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.*;

/** Owns one actual child process; no global PID files, process-name scans or guessed save delay. */
final class RecordingSession {
    final String path;
    private final Process process;
    private final int pid;
    private final IRecordingObserver observer;
    private final BufferedReader output;
    private final IBinder.DeathRecipient ownerDied = this::stop;
    private final long startedAt;
    private long stoppedAt;
    private boolean stopping;

    RecordingSession(String displayId, IRecordingObserver observer) throws Exception {
        if (observer == null || displayId == null || !displayId.matches("[0-9]+")) {
            throw new IllegalArgumentException("Recording requires an owner and a physical display");
        }
        this.observer = observer;
        File folder = new File("/storage/emulated/0/Movies");
        if (!folder.isDirectory() && !folder.mkdirs()) throw new IllegalStateException("Movies directory unavailable");
        path = new File(folder, "MRSS_" + UUID.randomUUID() + ".mp4").getAbsolutePath();
        // exec preserves the PID printed by this owned shell. stdout remains attached to this Process.
        startedAt = SystemClock.elapsedRealtime();
        process = new ProcessBuilder("sh", "-c", "echo $$; exec screenrecord --display-id " + displayId
                + " --time-limit 180 --bit-rate 20000000 " + CommandRunner.quote(path)).redirectErrorStream(true).start();
        output = new BufferedReader(new InputStreamReader(process.getInputStream()));
        ExecutorService handshake = Executors.newSingleThreadExecutor();
        Future<String> firstLine = handshake.submit(output::readLine);
        try {
            pid = Integer.parseInt(firstLine.get(3, TimeUnit.SECONDS));
            if (pid <= 0) throw new IllegalStateException("Recorder PID missing");
            observer.asBinder().linkToDeath(ownerDied, 0);
        } catch (Exception e) {
            process.destroy();
            output.close();
            throw e;
        } finally {
            firstLine.cancel(true);
            handshake.shutdownNow();
        }
    }

    void watch(Runnable completed) {
        new Thread(() -> {
            boolean saved = false;
            try {
                // Drain encoder diagnostics, but do not retain unlimited output or log private data.
                while (output.readLine() != null) { }
                int exit = process.waitFor();
                File file = new File(path);
                if (exit == 0 && file.isFile() && file.length() > 0) {
                    MediaMetadataRetriever metadata = new MediaMetadataRetriever();
                    try {
                        metadata.setDataSource(path);
                        String duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                        long durationMs = duration == null ? 0 : Long.parseLong(duration);
                        long end;
                        synchronized (this) { end = stoppedAt != 0 ? stoppedAt : SystemClock.elapsedRealtime(); }
                        long expectedMs = Math.max(0, end - startedAt);
                        if (expectedMs >= 1000 && durationMs + 750 < expectedMs) {
                            saved = RecordingDuration.extendStaticTail(file, expectedMs);
                            if (saved) Log.i("RecordingSession", "Static recording tail extended to " + expectedMs + " ms");
                        } else saved = durationMs > 0;
                    } finally { metadata.release(); }
                }
                if (saved) CommandRunner.run(5000, "am", "broadcast", "-a",
                        "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file://" + path);
            } catch (Exception e) { Log.w("RecordingSession", "Recorder completion failed", e); }
            finally {
                try { output.close(); } catch (Exception ignored) { }
                observer.asBinder().unlinkToDeath(ownerDied, 0);
                completed.run();
                try { observer.onRecordingStopped(path, saved); } catch (Exception ignored) { }
            }
        }, "MRSS-recorder-exit").start();
    }

    synchronized boolean stop() {
        if (stopping) return true;
        try {
            process.exitValue(); // Already exited: the completion watcher owns finalization.
            return true;
        } catch (IllegalThreadStateException running) {
            try {
                // Only the PID from this still-owned process; never kill a process found by name.
                long signalTime = SystemClock.elapsedRealtime();
                Os.kill(pid, OsConstants.SIGINT);
                stoppedAt = signalTime;
                stopping = true;
                return true;
            } catch (Exception e) {
                Log.w("RecordingSession", "Recorder stop rejected", e);
                return false;
            }
        }
    }
}
