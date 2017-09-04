package com.github.axet.audiolibrary.encoders;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.nayuki.flac.common.StreamInfo;
import io.nayuki.flac.encode.BitOutputStream;
import io.nayuki.flac.encode.FrameEncoder;
import io.nayuki.flac.encode.RandomAccessFileOutputStream;
import io.nayuki.flac.encode.SubframeEncoder;

public class FormatFLAC_nayuki implements Encoder {
    EncoderInfo info;
    RandomAccessFile raf;
    BitOutputStream bo;
    StreamInfo sinfo;
    int blockSize = 4096;
    SubframeEncoder.SearchOptions opt = SubframeEncoder.SearchOptions.SUBSET_BEST;
    MessageDigest hasher;

    public FormatFLAC_nayuki(EncoderInfo info, File out) {
        this.info = info;
        try {
            raf = new RandomAccessFile(out, "rw");
            raf.setLength(0);
            bo = new BitOutputStream(new BufferedOutputStream(new RandomAccessFileOutputStream(raf)));
            bo.writeInt(32, 0x664C6143);

            // Populate and write the stream info structure
            sinfo = new StreamInfo();
            sinfo.sampleRate = info.hz;
            sinfo.numChannels = info.channels;
            sinfo.sampleDepth = info.bps;
            sinfo.numSamples = 0;
            sinfo.md5Hash = new byte[16];
            sinfo.write(true, bo);

            try {
                hasher = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
                throw new AssertionError(e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int pos, int buflen) {
        try {
            sinfo.minBlockSize = blockSize;
            sinfo.maxBlockSize = blockSize;
            sinfo.minFrameSize = 0;
            sinfo.maxFrameSize = 0;

            ByteBuffer bb = ByteBuffer.allocate(buflen * Short.SIZE / Byte.SIZE);
            bb.asShortBuffer().put(buf, pos, buflen);
            hasher.update(bb);

            int cc = buflen / sinfo.numChannels;

            sinfo.numSamples += cc;

            long[][] subsamples = new long[sinfo.numChannels][cc];
            for (int c = 0; c < sinfo.numChannels; c++) {
                for (int i = 0; i < cc; i++) {
                    subsamples[c][i] = buf[c * i];
                }
            }
            FrameEncoder enc = FrameEncoder.computeBest(pos, subsamples, info.bps, info.hz, opt).encoder;
            long startByte = bo.getByteCount();
            enc.encode(subsamples, bo);
            long frameSize = bo.getByteCount() - startByte;
            if (frameSize < 0 || (int) frameSize != frameSize)
                throw new AssertionError();
            if (sinfo.minFrameSize == 0 || frameSize < sinfo.minFrameSize)
                sinfo.minFrameSize = (int) frameSize;
            if (frameSize > sinfo.maxFrameSize)
                sinfo.maxFrameSize = (int) frameSize;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            bo.flush();
            raf.seek(4);
            sinfo.md5Hash = hasher.digest();
            sinfo.write(true, bo);
            bo.close();
            raf.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
