package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioFormat;
import android.util.Log;

import com.github.axet.androidlibrary.app.MainApplication;
import com.github.axet.androidlibrary.app.Native;
import com.github.axet.androidlibrary.sound.Sound;
import com.github.axet.opusjni.Config;
import com.github.axet.opusjni.Opus;

import org.ebml.io.FileDataWriter;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;
import org.ebml.matroska.MatroskaFileWriter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import vavi.sound.pcm.resampling.ssrc.SSRC;

// https://wiki.xiph.org/MatroskaOpus
@TargetApi(21)
public class FormatOPUS implements Encoder {
    public static final String TAG = FormatOPUS.class.getSimpleName();

    EncoderInfo info;
    Opus opus;
    long NumSamples;
    MatroskaFileWriter writer;
    MatroskaFileTrack track;
    MatroskaFileTrack.MatroskaAudioTrack audio;
    ShortBuffer left;
    int frameSize;
    int hz;
    Thread thread = null;
    PipedOutputStream is;
    PipedInputStream os;

    public static void natives(Context context) {
        if (Config.natives) {
            try {
                System.loadLibrary("opus"); // API16 failed to find ogg dependency
                System.loadLibrary("opusjni");
            } catch (ExceptionInInitializerError | UnsatisfiedLinkError e) {
                Native.loadLibrary(context, "opus");
                Native.loadLibrary(context, "opusjni");
            }
            Config.natives = false;
        }
    }

    public FormatOPUS(Context context, EncoderInfo info, File out) {
        natives(context);
        create(info, out);
    }

    int match(int hz) {
        int[] hh = new int[]{
                8000,
                12000,
                16000,
                24000,
                48000,
        };
        int i = Integer.MAX_VALUE;
        int r = 0;
        for (int h : hh) {
            int d = Math.abs(hz - h);
            if (d <= i) { // higher is better
                i = d;
                r = h;
            }
        }
        return r;
    }

    public void create(final EncoderInfo info, File out) {
        this.info = info;
        try {
            writer = new MatroskaFileWriter(new FileDataWriter(out.getAbsolutePath()));
            audio = new MatroskaFileTrack.MatroskaAudioTrack();
            audio.setSamplingFrequency(info.sampleRate);
            audio.setOutputSamplingFrequency(info.sampleRate);
            audio.setBitDepth(info.bps);
            audio.setChannels((short) info.channels);
            track = new MatroskaFileTrack();
            track.setCodecID("A_OPUS");
            track.setAudio(audio);
            track.setTrackType(MatroskaFileTrack.TrackType.AUDIO);
            writer.addTrack(track);

            hz = match(info.sampleRate);

            if (hz != info.sampleRate) {
                this.is = new PipedOutputStream();
                this.os = new PipedInputStream(100 * 1024);
                final PipedInputStream is = new PipedInputStream(this.is);
                final PipedOutputStream os = new PipedOutputStream(this.os);
                final int c = com.github.axet.audiolibrary.app.Sound.DEFAULT_AUDIOFORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1;
                thread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SSRC ssrc = new SSRC(is, os, info.sampleRate, hz, c, c, info.channels, Integer.MAX_VALUE, 0, 0, true);
                        } catch (RuntimeException e) {
                            Log.d(TAG, "SSRC", e);
                            throw e;
                        } catch (IOException e) {
                            Log.d(TAG, "SSRC", e);
                            throw new RuntimeException(e);
                        }
                    }
                }, "SSRC");
                thread.start();
            }

            int b = 128000;
            if (info.sampleRate < 16000) {
                b = 32000;
            } else if (info.sampleRate < 44100) {
                b = 64000;
            }
            opus = new Opus();
            opus.open(info.channels, hz, b);
            track.setCodecPrivate(opusHead());
            writer.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // https://tools.ietf.org/html/rfc7845#page-12
    ByteBuffer opusHead() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream os = new DataOutputStream(bos);

            // head
            os.write(new byte[]{'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'}); // Magic Signature, This is an 8-octet (64-bit) field
            os.writeByte(1); // Version (8 bits, unsigned)
            os.writeByte(info.channels); // Output Channel Count 'C' (8 bits, unsigned):
            os.writeShort(0); // Pre-skip (16 bits, unsigned, little endian)
            os.write(ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).order(ByteOrder.LITTLE_ENDIAN).putInt(info.sampleRate).array()); // Input Sample Rate (32 bits, unsigned, little endian)
            os.writeShort(0); // Output Gain (16 bits, signed, little endian)
            os.writeByte(0); // Channel Mapping Family (8 bits, unsigned)

            // tags
            os.write(new byte[]{'O', 'p', 'u', 's', 'T', 'a', 'g', 's'}); // Magic Signature, This is an 8-octet (64-bit)
            os.writeShort(0); // Vendor String Length (32 bits, unsigned, little endian)
            os.writeShort(0); // User Comment List Length (32 bits, unsigned, little endian)

            os.close();
            return ByteBuffer.wrap(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int len) {
        if (thread != null) {
            try {
                ByteBuffer bb = ByteBuffer.allocate(len * (Short.SIZE / Byte.SIZE));
                bb.order(ByteOrder.LITTLE_ENDIAN);
                bb.asShortBuffer().put(buf, 0, len);
                is.write(bb.array());
                is.flush();
                while (true) {
                    int blen = os.available();
                    if (blen <= 0)
                        return;
                    int max = buf.length * (Short.SIZE / Byte.SIZE);
                    if (blen > max)
                        blen = max;
                    byte[] b = new byte[blen];
                    int read = os.read(b);
                    bb = ByteBuffer.allocate(read);
                    bb.order(ByteOrder.LITTLE_ENDIAN);
                    bb.put(b, 0, read);
                    bb.flip();
                    len = read / (Short.SIZE / Byte.SIZE);
                    bb.asShortBuffer().get(buf, 0, len);
                    encode2(buf, len);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        encode2(buf, len);
    }

    void encode2(short[] buf, int len) {
        if (left != null) {
            ShortBuffer ss = ShortBuffer.allocate(left.position() + len);
            left.flip();
            ss.put(left);
            ss.put(buf, 0, len);
            buf = ss.array();
            len = ss.position();
        }
        if (frameSize == 0) {
            if (len < 240) {
                frameSize = 120;
            } else if (len < 480) {
                frameSize = 240;
            } else if (len < 960) {
                frameSize = 480;
            } else if (len < 1920) {
                frameSize = 960;
            } else if (len < 2880) {
                frameSize = 1920;
            } else {
                frameSize = 2880;
            }
        }
        long dur = frameSize * 1000 / info.sampleRate;
        int frameSizeStereo = frameSize * info.channels;
        int lenEncode = len / frameSizeStereo * frameSizeStereo;
        for (int pos = 0; pos < lenEncode; pos += frameSizeStereo) {
            byte[] bb = opus.encode(buf, pos, frameSizeStereo);
            encode(ByteBuffer.wrap(bb), dur);
            NumSamples += frameSizeStereo / info.channels;
        }
        int diff = len - lenEncode;
        if (diff > 0) {
            left = ShortBuffer.allocate(diff);
            left.put(buf, lenEncode, diff);
        } else {
            left = null;
        }
    }

    void encode(ByteBuffer bb, long dur) {
        MatroskaFileFrame frame = new MatroskaFileFrame();
        frame.setKeyFrame(true);
        frame.setTimecode(getCurrentTimeStamp());
        frame.setTrackNo(track.getTrackNo());
        frame.setData(bb);
        frame.setDuration(dur);
        writer.addFrame(frame);
        writer.flush();
    }

    public void close() {
        if (thread != null) {
            try {
                is.close();
                while (true) {
                    int len = os.available();
                    if (len <= 0)
                        break;
                    byte[] b = new byte[len];
                    int read = os.read(b);
                    int ss = read / (Short.SIZE / Byte.SIZE);
                    short[] buf = new short[ss];
                    ByteBuffer bb = ByteBuffer.allocate(read);
                    bb.order(ByteOrder.LITTLE_ENDIAN);
                    bb.put(b);
                    bb.flip();
                    bb.asShortBuffer().get(buf, 0, ss);
                    encode2(buf, ss);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        opus.close();
        writer.setDuration(getCurrentTimeStamp());
        writer.close();
    }

    long getCurrentTimeStamp() {
        return NumSamples * 1000 / info.sampleRate;
    }

    public EncoderInfo getInfo() {
        return info;
    }
}
