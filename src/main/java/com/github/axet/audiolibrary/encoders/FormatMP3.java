package com.github.axet.audiolibrary.encoders;

import android.content.Context;

import com.github.axet.androidlibrary.app.Native;
import com.github.axet.lamejni.Lame;
import com.github.axet.vorbisjni.Config;
import com.github.axet.vorbisjni.Vorbis;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ShortBuffer;

public class FormatMP3 implements Encoder {
    RandomAccessFile writer;
    File out;
    Lame lame;

    public static void natives(Context context) {
        if (Config.natives) {
            Native.loadLibraries(context, new String[]{"lame", "lamejni"});
            Config.natives = false;
        }
    }

    public static boolean supported(Context context) {
        try {
            FormatMP3.natives(context);
            Lame v = new Lame();
            return true;
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            return false;
        }
    }

    public FormatMP3(Context context, EncoderInfo info, File out) {
        natives(context);
        this.out = out;
        lame = new Lame();
        int b = Factory.getBitrate(info.hz) / 1000;
        lame.open(info.channels, info.hz, b);
        try {
            writer = new RandomAccessFile(out, "rw");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int pos, int len) {
        byte[] bb = lame.encode(buf, pos, len);
        try {
            writer.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            byte[] bb = lame.encode(null, 0, 0);
            writer.write(bb);
            bb = lame.close();
            writer.seek(0);
            writer.write(bb);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
