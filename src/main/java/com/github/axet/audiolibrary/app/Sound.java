package com.github.axet.audiolibrary.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.preference.PreferenceManager;

import com.github.axet.androidlibrary.sound.AudioTrack;

public class Sound extends com.github.axet.androidlibrary.sound.Sound {
    public static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    // quite room gives me 20db
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

    public AudioTrack generateTrack(AudioTrack.AudioBuffer buffer) {
        int last = buffer.len / buffer.getChannels() - 1;

        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, buffer);
        track.setNotificationMarkerPosition(last);

        return track;
    }
}
