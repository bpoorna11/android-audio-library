package com.github.axet.audiolibrary.encoders;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.github.axet.audiolibrary.app.Storage;

import java.io.File;
import java.io.FileDescriptor;
import java.util.Map;

@TargetApi(18)
public class FormatM4A extends MuxerMP4 {
    public static final String EXT = "m4a";

    public static final String CONTENTTYPE_MP4 = "audio/mp4";
    public static final String CONTENTTYPE_MP4A = "audio/mp4a-latm";

    public FormatM4A(Context context, EncoderInfo info, FileDescriptor out) {
        Map<String, MediaCodecInfo> map = MuxerMP4.findEncoder(CONTENTTYPE_MP4);
        if (map.isEmpty())
            throw new RuntimeException("mp4 not supported");
        MediaFormat format = MuxerMP4.getDefault(CONTENTTYPE_MP4A, map);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.hz);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, Factory.getBitrate(info.hz));
        create(context, info, format, out);
    }

}
