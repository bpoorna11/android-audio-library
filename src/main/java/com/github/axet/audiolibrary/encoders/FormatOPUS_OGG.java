package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.content.Context;

import com.github.axet.androidlibrary.app.Native;
import com.github.axet.opusjni.Config;
import com.github.axet.opusjni.Opus;

import org.ebml.io.FileDataWriter;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;
import org.ebml.matroska.MatroskaFileWriter;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusInfo;
import org.gagravarr.opus.OpusTags;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

// https://wiki.xiph.org/MatroskaOpus | https://wiki.xiph.org/OggOpus
@TargetApi(21)
public class FormatOPUS_OGG extends FormatOPUS {
    public static final String TAG = FormatOPUS_OGG.class.getSimpleName();

    OpusFile writer;

    public FormatOPUS_OGG(Context context, EncoderInfo info, File out) {
        super(context, info, out);
    }

    @Override
    public void create(final EncoderInfo info, File out) {
        super.create(info, out);
        try {
            OpusInfo o = new OpusInfo();
            o.setNumChannels(info.channels);
            o.setOutputGain(0);
            o.setPreSkip(0);
            o.setSampleRate(info.hz);
            writer = new OpusFile(new FileOutputStream(out), o, new OpusTags());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    void encode(ByteBuffer bb, long dur) {
        OpusAudioData frame = new OpusAudioData(bb.array());
        long gr = NumSamples + dur;
        gr = 48000 * gr / info.hz; // Ogg gr always at 48000hz
        frame.setGranulePosition(gr);
        writer.writeAudioData(frame);
    }

    @Override
    public void close() {
        super.close();
        try {
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
