package com.tgwgroup.MiRearScreenSwitcher;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.system.Os;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

/** Extend a static last frame without re-encoding or replacing the original on failure. */
final class RecordingDuration {
    private static final String TAG = "RecordingDuration";
    private static final int MAX_SAMPLE_BYTES = 32 * 1024 * 1024;

    static boolean extendStaticTail(File original, long expectedMs) {
        if (original == null || expectedMs <= 0 || original.getParentFile() == null) return false;
        File temp = new File(original.getParentFile(), "." + original.getName() + "." + UUID.randomUUID() + ".tmp");
        MediaExtractor extractor = new MediaExtractor();
        MediaMuxer muxer = null;
        boolean started = false;
        try {
            extractor.setDataSource(original.getAbsolutePath());
            int count = extractor.getTrackCount();
            if (count < 1 || count > 16) return false;
            int video = -1;
            int orientation = 0;
            int[] mapping = new int[count];
            long[] lastPts = new long[count];
            Arrays.fill(lastPts, -1);
            muxer = new MediaMuxer(temp.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            for (int i = 0; i < count; i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null) return false;
                if (mime.startsWith("video/")) {
                    if (video >= 0) return false;
                    video = i;
                    if (format.containsKey(MediaFormat.KEY_ROTATION)) orientation = format.getInteger(MediaFormat.KEY_ROTATION);
                }
                mapping[i] = muxer.addTrack(format);
                extractor.selectTrack(i);
            }
            if (video < 0) return false;
            if (orientation == 90 || orientation == 180 || orientation == 270) muxer.setOrientationHint(orientation);
            muxer.start();
            started = true;
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int videoSamples = 0;
            while (true) {
                int track = extractor.getSampleTrackIndex();
                if (track < 0) break;
                if (track >= count) return false;
                long sizeHint = Build.VERSION.SDK_INT >= 28 ? extractor.getSampleSize() : -1;
                if (sizeHint > MAX_SAMPLE_BYTES) return false;
                if (sizeHint > buffer.capacity()) buffer = ByteBuffer.allocateDirect((int) sizeHint);
                buffer.clear();
                int size = extractor.readSampleData(buffer, 0);
                if (size < 0) return false;
                int sampleFlags = extractor.getSampleFlags();
                if ((sampleFlags & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0
                        || (Build.VERSION.SDK_INT >= 26 && (sampleFlags & MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME) != 0)) return false;
                long pts = extractor.getSampleTime();
                if (pts < 0 || pts < lastPts[track]) return false;
                lastPts[track] = pts;
                int flags = (sampleFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0;
                info.set(0, size, pts, flags);
                muxer.writeSampleData(mapping[track], buffer, info);
                if (track == video) videoSamples++;
                extractor.advance();
            }
            if (videoSamples == 0 || expectedMs > Long.MAX_VALUE / 1000) return false;
            long endUs = Math.max(expectedMs * 1000, lastPts[video] + 1);
            info.set(0, 0, endUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            muxer.writeSampleData(mapping[video], ByteBuffer.allocate(0), info);
            muxer.stop();
            started = false;
            muxer.release();
            muxer = null;
            extractor.release();
            extractor = null;
            if (!temp.isFile() || temp.length() == 0 || !hasDuration(temp, expectedMs)) return false;
            // Same-directory rename preserves the original if remuxing or validation fails.
            Os.rename(temp.getAbsolutePath(), original.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Could not extend last frame; original recording retained", e);
            return false;
        } finally {
            if (extractor != null) extractor.release();
            if (muxer != null) {
                if (started) try { muxer.stop(); } catch (Exception ignored) { }
                muxer.release();
            }
            if (temp.exists() && !temp.delete()) Log.w(TAG, "Could not remove temporary remux file");
        }
    }

    private static boolean hasDuration(File file, long expectedMs) {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/") && format.containsKey(MediaFormat.KEY_DURATION)) {
                    long actualMs = format.getLong(MediaFormat.KEY_DURATION) / 1000;
                    return actualMs >= expectedMs - 100 && actualMs <= expectedMs + 1000;
                }
            }
        } catch (Exception e) { Log.w(TAG, "Could not verify remuxed recording", e); }
        finally { extractor.release(); }
        return false;
    }
}
