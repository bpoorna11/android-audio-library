package com.github.axet.audiolibrary.encoders;

import net.sourceforge.javaflacencoder.FLACEncoder;
import net.sourceforge.javaflacencoder.FLACFileOutputStream;
import net.sourceforge.javaflacencoder.StreamConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FormatFLAC implements Encoder {
    int NumSamples;
    EncoderInfo info;
    int BytesPerSample;
    FLACEncoder flacEncoder;
    FLACFileOutputStream flacOutputStream;

    ByteOrder order = ByteOrder.LITTLE_ENDIAN;

    public FormatFLAC(EncoderInfo info, File out) {
        this.info = info;
        NumSamples = 0;
        BytesPerSample = info.bps / 8;

        StreamConfiguration streamConfiguration = new StreamConfiguration();
        streamConfiguration.setSampleRate(info.sampleRate);
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

    public void encode(short[] buf, int buflen) {
        try {
            int[] ii = new int[buflen];
            for (int i = 0; i < buflen; i++)
                ii[i] = buf[i];
            flacEncoder.addSamples(ii, buflen);
            flacEncoder.encodeSamples(buflen, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            flacEncoder.encodeSamples(flacEncoder.samplesAvailableToEncode(), true);
            flacOutputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}