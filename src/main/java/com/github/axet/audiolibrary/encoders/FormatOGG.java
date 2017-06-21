package com.github.axet.audiolibrary.encoders;

import android.content.Context;

import com.github.axet.androidlibrary.app.Native;
import com.github.axet.vorbisjni.Config;
import com.github.axet.vorbisjni.Vorbis;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ShortBuffer;

public class FormatOGG implements Encoder {
    FileOutputStream writer;
    Vorbis vorbis;

    public static void natives(Context context) {
        if (Config.natives) {
            Native.loadLibraries(context, new String[]{"ogg", "vorbis", "vorbisjni"});
            Config.natives = false;
        }
    }

    public static boolean supported(Context context) {
        try {
            FormatOGG.natives(context);
            Vorbis v = new Vorbis();
            return true;
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            return false;
        }
    }

    public FormatOGG(Context context, EncoderInfo info, File out) {
        natives(context);
        vorbis = new Vorbis();
        vorbis.open(info.channels, info.hz, 0.4f);
        try {
            writer = new FileOutputStream(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int pos, int len) {
        ShortBuffer s = ShortBuffer.allocate(len);
        s.put(buf, pos, len);
        byte[] bb = vorbis.encode(s.array(), len);
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
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        vorbis.close();
    }
}
