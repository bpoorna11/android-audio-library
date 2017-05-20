package com.github.axet.audiolibrary.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.preference.PreferenceManager;

import com.github.axet.androidlibrary.sound.AudioTrack;

public class Sound extends com.github.axet.androidlibrary.sound.Sound {
    public static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // quite root gives me 20db
    public static int NOISE_DB = 20;
    // max 90 dB detection for android mic
    public static int MAXIMUM_DB = 90;

    public Sound(Context context) {
        super(context);
    }

    public void silent() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            super.silent();
        }
    }

    public void unsilent() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            super.unsilent();
        }
    }

    public AudioTrack generateTrack(int sampleRate, short[] buf, int len) {
        int last;
        int c;

        switch (MainApplication.getChannels(context)) {
            case 1:
                c = AudioFormat.CHANNEL_OUT_MONO;
                last = len - 1;
                break;
            case 2:
                c = AudioFormat.CHANNEL_OUT_STEREO;
                last = len / 2 - 1;
                break;
            default:
                throw new RuntimeException("unknown mode");
        }

        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, c, AUDIO_FORMAT, len * (Short.SIZE / 8));
        track.write(buf, 0, len);
        track.setNotificationMarkerPosition(last);

        return track;
    }
}
