package com.tgwgroup.MiRearScreenSwitcher;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Bounded subprocess execution. Always drains both pipes; never treats a timeout as success. */
final class CommandRunner {
    static final int OUTPUT_LIMIT = 256 * 1024;

    static final class Result {
        final int exitCode;
        final String output;
        final boolean truncated;
        Result(int exitCode, String output, boolean truncated) {
            this.exitCode = exitCode;
            this.output = output;
            this.truncated = truncated;
        }
        boolean succeeded() { return exitCode == 0 && !truncated; }
    }

    static Result run(long timeoutMs, String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ExecutorService reader = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "MRSS-command-output");
            thread.setDaemon(true);
            return thread;
        });
        Future<Result> result = reader.submit(() -> {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            boolean truncated = false;
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    int retain = Math.min(count, OUTPUT_LIMIT - output.size());
                    output.write(buffer, 0, retain);
                    truncated |= retain != count;
                }
            }
            return new Result(process.waitFor(),
                    new String(output.toByteArray(), StandardCharsets.UTF_8), truncated);
        });
        try {
            return result.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            process.destroy();
            result.cancel(true);
            reader.shutdownNow();
        }
    }

    static String quote(String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }
}
