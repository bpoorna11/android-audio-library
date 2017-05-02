package com.github.axet.audiolibrary.encoders;

import android.content.Context;
import android.os.Build;

import com.github.axet.androidlibrary.app.Native;
import com.github.axet.vorbisjni.Vorbis;
import com.github.axet.vorbisjni.Config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;

public class FormatOGG implements Encoder {
    FileOutputStream writer;
    Vorbis vorbis;

    static boolean natives = true;

    public static void natives(Context context) {
        if (natives) {
            try {
                System.loadLibrary("ogg"); // API16 failed to find ogg dependency
                System.loadLibrary("vorbis"); // API16 failed to find vorbis dependency
                System.loadLibrary("vorbisjni");
            } catch (ExceptionInInitializerError | UnsatisfiedLinkError e) {
                Native.loadLibrary(context, "ogg");
                Native.loadLibrary(context, "vorbis");
                Native.loadLibrary(context, "vorbisjni");
            }
            Config.natives = false;
            natives = false;
        }
    }

    public FormatOGG(Context context, EncoderInfo info, File out) {
        natives(context);
        vorbis = new Vorbis();
        vorbis.open(info.channels, info.sampleRate, 0.4f);
        try {
            writer = new FileOutputStream(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int len) {
        byte[] bb = vorbis.encode(buf, len);
        try {
            writer.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            byte[] bb = vorbis.encode(null, 0);
            writer.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        vorbis.close();
    }
}
