package com.github.axet.audiolibrary.encoders;

import net.sourceforge.javaflacencoder.FLACEncoder;
import net.sourceforge.javaflacencoder.FLACFileOutputStream;
import net.sourceforge.javaflacencoder.StreamConfiguration;

import java.io.File;
import java.io.IOException;

public class FormatFLAC implements Encoder {
    int NumSamples;
    EncoderInfo info;
    FLACEncoder flacEncoder;
    FLACFileOutputStream flacOutputStream;

    public FormatFLAC(EncoderInfo info, File out) {
        this.info = info;
        this.NumSamples = 0;

        StreamConfiguration streamConfiguration = new StreamConfiguration();
        streamConfiguration.setSampleRate(info.hz);
        streamConfiguration.setBitsPerSample(info.bps);
        streamConfiguration.setChannelCount(info.channels);

        try {
            flacEncoder = new FLACEncoder();
            flacOutputStream = new FLACFileOutputStream(out);
            flacEncoder.setStreamConfiguration(streamConfiguration);
            flacEncoder.setOutputStream(flacOutputStream);
            flacEncoder.openFLACStream();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int pos, int buflen) {
        try {
            int[] ii = new int[buflen];
            int end = pos + buflen;
            for (int i = pos; i < end; i++)
                ii[i] = buf[i];
            int count = buflen / info.channels;
            flacEncoder.addSamples(ii, count);
            flacEncoder.encodeSamples(count, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            flacEncoder.encodeSamples(flacEncoder.samplesAvailableToEncode(), true);
            flacOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}