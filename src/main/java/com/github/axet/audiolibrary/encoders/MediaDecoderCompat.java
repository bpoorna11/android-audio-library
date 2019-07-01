package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import com.github.axet.androidlibrary.sound.MediaPlayerCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@TargetApi(16)
public class MediaDecoderCompat {
    public static ByteOrder ORDER = ByteOrder.LITTLE_ENDIAN;

    public MediaExtractor m;
    public MediaCodec mediaCodec;
    public long duration; // micro seconds
    public int hz;
    public int bps; // bits per sample, signed integer
    public int channels;

    public static short[] asShortBuffer(byte[] buf, int off, int len) {
        short[] ss = new short[len / 2];
        ByteBuffer.wrap(buf, off, len).order(ORDER).asShortBuffer().get(ss);
        return ss;
    }

    public MediaDecoderCompat(Context context, Uri uri) throws IOException {
        m = new MediaExtractor();
        ParcelFileDescriptor fd = MediaPlayerCompat.getFD(context, uri);
        m.setDataSource(fd.getFileDescriptor());

        for (int index = 0; index < m.getTrackCount(); index++) {
            MediaFormat format = m.getTrackFormat(index);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                m.selectTrack(index);
                mediaCodec = MediaCodec.createDecoderByType(mime);
                mediaCodec.configure(format, null, null, 0);
                duration = format.getLong(MediaFormat.KEY_DURATION);
                hz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE); // out format has incorrect sample rate
                MediaFormat out = mediaCodec.getOutputFormat();
                int encoding;
                if (Build.VERSION.SDK_INT >= 24)
                    encoding = out.getInteger(MediaFormat.KEY_PCM_ENCODING);
                else
                    encoding = AudioFormat.ENCODING_PCM_16BIT;
                bps = encoding == AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;
                channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                break;
            }
        }

        if (mediaCodec == null)
            return;

        mediaCodec.start();
    }

    public void decode(OutputStream os) throws IOException {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        while (encode())
            decode(info, os);

        while ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0)
            decode(info, os);
    }

    boolean encode() {
        int inputIndex = mediaCodec.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer buffer = mediaCodec.getInputBuffers()[inputIndex];
            buffer.clear();
            int sampleSize = m.readSampleData(buffer, 0);
            if (sampleSize < 0) {
                mediaCodec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                return false;
            } else {
                mediaCodec.queueInputBuffer(inputIndex, 0, sampleSize, m.getSampleTime(), 0);
                m.advance();
            }
        }
        return true;
    }

    void decode(MediaCodec.BufferInfo info, OutputStream os) throws IOException {
        int outputIndex = mediaCodec.dequeueOutputBuffer(info, 0);
        if (outputIndex >= 0) {
            ByteBuffer buffer = mediaCodec.getOutputBuffers()[outputIndex];
            buffer.position(info.offset);
            buffer.limit(info.offset + info.size);
            byte[] b = new byte[info.size];
            buffer.get(b);
            os.write(b, 0, b.length);
            mediaCodec.releaseOutputBuffer(outputIndex, false);
        }
    }

    public void close() {
        mediaCodec.release();
        m.release();
    }
}
