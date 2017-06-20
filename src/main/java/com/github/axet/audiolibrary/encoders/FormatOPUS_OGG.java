package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.content.Context;

import com.github.axet.androidlibrary.app.Native;
import com.github.axet.opusjni.Config;
import com.github.axet.opusjni.Opus;

import org.ebml.io.FileDataWriter;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;
import org.ebml.matroska.MatroskaFileWriter;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusInfo;
import org.gagravarr.opus.OpusTags;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

// https://wiki.xiph.org/MatroskaOpus
@TargetApi(21)
public class FormatOPUS_OGG implements Encoder {
    public static final String TAG = FormatOPUS_OGG.class.getSimpleName();

    EncoderInfo info;
    Opus opus;
    long NumSamples;
    OpusFile writer;
    ShortBuffer left;
    int frameSize;
    int hz;
    Resample resample;

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

    public static boolean supported(Context context) {
        try {
            FormatOPUS_OGG.natives(context);
            Opus v = new Opus();
            return true;
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            return false;
        }
    }

    public FormatOPUS_OGG(Context context, EncoderInfo info, File out) {
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
            OpusInfo o = new OpusInfo();
            o.setNumChannels(info.channels);
            o.setOutputGain(0);
            o.setPreSkip(0);
            o.setSampleRate(info.sampleRate);
            writer = new OpusFile(new FileOutputStream(out), o, new OpusTags());

            hz = match(info.sampleRate);

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

            os.close();
            return ByteBuffer.wrap(bos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int len) {
        if (resample != null) {
            resample.write(buf, len);
            ByteBuffer bb;
            while ((bb = resample.read()) != null) {
                len = bb.position() / (Short.SIZE / Byte.SIZE);
                short[] b = new short[len];
                bb.flip();
                bb.asShortBuffer().get(b, 0, len);
                encode2(b, len);
            }
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

    void encode(ByteBuffer bb, long dur) {
        OpusAudioData frame = new OpusAudioData(bb.array());
        long gr = NumSamples + dur;
        gr = 48000 / info.sampleRate * gr; // gr always at 48000hz
        frame.setGranulePosition(gr);
        writer.writeAudioData(frame);
    }

    public void close() {
        if (resample != null) {
            resample.end();
            ByteBuffer bb;
            while ((bb = resample.read()) != null) {
                int len = bb.position() / (Short.SIZE / Byte.SIZE);
                short[] buf = new short[len];
                bb.flip();
                bb.asShortBuffer().get(buf, 0, len);
                encode2(buf, len);
            }
            resample.close();
            resample = null;
        }
        opus.close();
        try {
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    long getCurrentTimeStamp() {
        return NumSamples * 1000 / info.sampleRate;
    }

    public EncoderInfo getInfo() {
        return info;
    }
}
