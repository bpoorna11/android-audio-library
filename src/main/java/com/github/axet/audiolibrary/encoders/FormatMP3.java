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

public class FormatMP3 implements Encoder {
    RandomAccessFile writer;
    File out;
    Lame lame;

    static boolean natives = true;

    public static void natives(Context context) {
        if (natives) {
            try {
                System.loadLibrary("lame"); // API16 failed to find ogg dependency
                System.loadLibrary("lamejni");
            } catch (ExceptionInInitializerError | UnsatisfiedLinkError e) {
                Native.loadLibrary(context, "lame");
                Native.loadLibrary(context, "lamejni");
            }
            Config.natives = false;
            natives = false;
        }
    }

    public FormatMP3(Context context, EncoderInfo info, File out) {
        natives(context);
        this.out = out;
        lame = new Lame();
        int b = 128;
        if (info.sampleRate < 16000) {
            b = 32;
        } else if (info.sampleRate < 44100) {
            b = 64;
        }
        lame.open(info.channels, info.sampleRate, b);
        try {
            writer = new RandomAccessFile(out, "rw");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int len) {
        byte[] bb = lame.encode(buf, len);
        try {
            writer.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            byte[] bb = lame.encode(null, 0);
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
