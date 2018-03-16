package com.github.axet.audiolibrary.filters;

import com.github.axet.audiolibrary.encoders.EncoderInfo;

import java.util.ArrayList;

import uk.me.berndporr.iirj.Butterworth;

public class VoiceFilter extends Filter {
    EncoderInfo info;
    ArrayList<Butterworth> bb = new ArrayList<>();

    public VoiceFilter(EncoderInfo info) {
        this.info = info;
        for (int i = 0; i < info.channels; i++) {
            Butterworth b = new Butterworth();
            b.bandPass(2, info.hz, 1650, 2700);
            bb.add(b);
        }
    }

    @Override
    public short[] filter(short[] buf, int pos, int len) {
        int end = pos + len;
        for (int i = pos; i < end; ) {
            for (int c = 0; c < info.channels; c++, i++) {
                Butterworth b = bb.get(c);
                double d = buf[i + c] / (double) Short.MAX_VALUE;
                d = b.filter(d);
                buf[i + c] = (short) (d * Short.MAX_VALUE);
            }
        }
        return buf;
    }
}
