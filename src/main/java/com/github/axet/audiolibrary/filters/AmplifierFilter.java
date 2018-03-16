package com.github.axet.audiolibrary.filters;

import com.github.axet.audiolibrary.app.Sound;

public class AmplifierFilter extends Filter {

    public static final int MAX = 5;

    double db;

    public AmplifierFilter(float amp) {
        this.db = Sound.log1(amp, MAX) * (MAX - 1);
    }

    public short[] filter(short[] buf, int pos, int len) {
        int end = pos + len;
        for (int i = pos; i < end; i++) {
            double d = (buf[i] * db);
            short s;
            if (d > Short.MAX_VALUE)
                s = Short.MAX_VALUE;
            else
                s = (short) d;
            buf[i] = s;
        }
        return buf;
    }
}
