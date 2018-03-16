package com.github.axet.audiolibrary.filters;

import uk.me.berndporr.iirj.Butterworth;

public class VoiceFilter extends Filter {
    Butterworth butterworth = new Butterworth();

    public VoiceFilter(int sr) {
        // butterworth.bandPass(2, sr, 1850, 1550);
        // butterworth.highPass(2, sr, 3300);
        butterworth.lowPass(2, sr, 3000);
    }

    @Override
    public void filter(short[] buf, int pos, int len) {
        int end = pos + len;
        for (int i = pos; i < end; i++) {
            double d = buf[i] / (double) Short.MAX_VALUE;
            d = butterworth.filter(d);
            buf[i] = (short) (d * Short.MAX_VALUE);
        }
    }
}
