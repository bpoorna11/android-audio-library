package com.github.axet.audiolibrary.encoders;

import android.content.Context;
import android.media.AudioFormat;
import android.os.Build;

import com.github.axet.androidlibrary.app.Native;
import com.github.axet.audiolibrary.R;
import com.github.axet.audiolibrary.app.RawSamples;
import com.github.axet.audiolibrary.app.Sound;
import com.github.axet.lamejni.Lame;
import com.github.axet.vorbisjni.Vorbis;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class Factory {

    public static String MP4 = "audio/mp4";
    public static String MP4A = "audio/mp4a-latm";

    public static CharSequence[] getEncodingTexts(Context context) {
        String[] aa = context.getResources().getStringArray(R.array.encodings_text);
        ArrayList<String> ll = new ArrayList<>(Arrays.asList(aa));
        ll.add(".flac");
        if (Build.VERSION.SDK_INT >= 18)
            ll.add(".m4a");
        if (Build.VERSION.SDK_INT >= 16)
            ll.add(".mka");
        try {
            FormatOGG.natives(context);
            Vorbis v = new Vorbis();
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            ll.remove(".ogg");
        }
        try {
            FormatMP3.natives(context);
            Lame v = new Lame();
            ll.add(".mp3");
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            ll.remove(".mp3");
        }
        return ll.toArray(new String[]{});
    }

    public static String[] getEncodingValues(Context context) {
        String[] aa = context.getResources().getStringArray(R.array.encodings_values);
        ArrayList<String> ll = new ArrayList<>(Arrays.asList(aa));
        ll.add("flac");
        if (Build.VERSION.SDK_INT >= 18)
            ll.add("m4a");
        if (Build.VERSION.SDK_INT >= 16)
            ll.add("mka");
        try {
            FormatOGG.natives(context);
            Vorbis v = new Vorbis();
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            ll.remove("ogg");
        }
        try {
            FormatMP3.natives(context);
            Lame v = new Lame();
            ll.add("mp3");
        } catch (NoClassDefFoundError | ExceptionInInitializerError | UnsatisfiedLinkError e) {
            ll.remove("mp3");
        }
        return ll.toArray(new String[]{});
    }

    public static Encoder getEncoder(Context context, String ext, EncoderInfo info, File out) {
        if (ext.equals("wav")) {
            return new FormatWAV(info, out);
        }
        if (ext.equals("3gp")) {
            return new Format3GP(info, out);
        }
        if (ext.equals("m4a")) {
            return new FormatM4A(info, out);
        }
        if (ext.equals("mka")) {
            return new FormatMKA(info, out);
        }
        if (ext.equals("ogg")) {
            return new FormatOGG(context, info, out);
        }
        if (ext.equals("mp3")) {
            return new FormatMP3(context, info, out);
        }
        if (ext.equals("flac")) {
            return new FormatFLAC(info, out);
        }
        return null;
    }

    public static long getEncoderRate(String ext, int rate) {
        if (ext.equals("m4a")) {
            long y1 = 365723; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 493743; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        if (ext.equals("mka")) { // same codec as m4a, but different container
            long y1 = 365723; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 493743; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        if (ext.equals("ogg")) {
            long y1 = 174892; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 405565; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        if (ext.equals("mp3")) {
            long y1 = 485568; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 965902; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        if (ext.equals("flac")) {
            long y1 = 1060832; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 1296766; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        // default raw
        int c = Sound.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1;
        return c * rate;
    }
}
