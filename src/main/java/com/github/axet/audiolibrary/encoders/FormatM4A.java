package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.github.axet.audiolibrary.app.Storage;

import java.io.File;
import java.io.FileDescriptor;
import java.util.Map;

@TargetApi(18)
public class FormatM4A extends MuxerMP4 {

    public FormatM4A(Storage storage, EncoderInfo info, FileDescriptor out) {
        super(storage, out);
        Map<String, MediaCodecInfo> map = MuxerMP4.findEncoder(Factory.CONTENTTYPE_MP4);
        if (map.isEmpty())
            throw new RuntimeException("mp4 not supported");
        MediaFormat format = MuxerMP4.getDefault(Factory.CONTENTTYPE_MP4A, map);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.hz);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, Factory.getBitrate(info.hz));
        create(info, format, this.out);
    }

}
