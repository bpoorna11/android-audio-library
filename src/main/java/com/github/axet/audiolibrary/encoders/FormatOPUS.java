package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.content.Context;

import com.github.axet.androidlibrary.app.Native;
import com.github.axet.opusjni.Config;
import com.github.axet.opusjni.Opus;

import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusInfo;
import org.gagravarr.opus.OpusTags;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

// https://wiki.xiph.org/MatroskaOpus
@TargetApi(21)
public class FormatOPUS implements Encoder {
    public static final String TAG = FormatOPUS.class.getSimpleName();

    EncoderInfo info;
    Opus opus;
    long NumSamples;
    ShortBuffer left;
    int frameSize;
    int hz;
    Resample resample;

    public static void natives(Context context) {
        if (Config.natives) {
            Native.loadLibraries(context, new String[]{"opus", "opusjni"});
            Config.natives = false;
        }
    }

    public static boolean supported(Context context) {
        try {
            FormatOPUS.natives(context);
            Opus v = new Opus();
            return true;
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            return false;
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
        this.hz = match(info.sampleRate);

        if (hz != info.sampleRate) {
            resample = new Resample(info.sampleRate, info.channels, hz);
        }

        int b = 128000;
        if (info.sampleRate < 16000) {
            b = 32000;
        } else if (info.sampleRate < 44100) {
            b = 64000;
        }
        opus = new Opus();
        opus.open(info.channels, hz, b);
    }

    @Override
    public void encode(short[] buf, int len) {
        if (resample != null) {
            resample.write(buf, len);
            resample();
            return;
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
        int frameSizeStereo = frameSize * info.channels;
        int lenEncode = len / frameSizeStereo * frameSizeStereo;
        for (int pos = 0; pos < lenEncode; pos += frameSizeStereo) {
            byte[] bb = opus.encode(buf, pos, frameSizeStereo);
            encode(ByteBuffer.wrap(bb), frameSize);
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

    void resample() {
        ByteBuffer bb;
        while ((bb = resample.read()) != null) {
            int len = bb.position() / (Short.SIZE / Byte.SIZE);
            short[] b = new short[len];
            bb.flip();
            bb.asShortBuffer().get(b, 0, len);
            encode2(b, len);
        }
    }

    void encode(ByteBuffer bb, long dur) {
    }

    public void close() {
        if (resample != null) {
            resample.end();
            resample();
            resample.close();
            resample = null;
        }
        opus.close();
    }

    long getCurrentTimeStamp() {
        return NumSamples * 1000 / info.sampleRate;
    }

    public EncoderInfo getInfo() {
        return info;
    }
}
