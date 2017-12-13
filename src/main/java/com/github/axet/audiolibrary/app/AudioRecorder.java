package com.github.axet.audiolibrary.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioRecord;
import android.preference.PreferenceManager;
import android.util.Log;

public class AudioRecorder {
    public static String TAG = AudioRecorder.class.getSimpleName();

    public static int indexOf(int[] ss, int s) {
        for (int i = 0; i < ss.length; i++) {
            if (ss[i] == s)
                return i;
        }
        return -1;
    }

    public static int getSampleRate(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int sampleRate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));
        sampleRate = Sound.getValidRecordRate(MainApplication.getInMode(context), sampleRate);
        if (sampleRate == -1)
            sampleRate = Sound.DEFAULT_RATE;
        return sampleRate;
    }

    public static AudioRecord createAudioRecorder(Context context, int sampleRate, int[] ss, int i) {
        AudioRecord r = null;

        int c = MainApplication.getInMode(context);
        final int min = AudioRecord.getMinBufferSize(sampleRate, c, Sound.DEFAULT_AUDIOFORMAT);
        if (min <= 0)
            throw new RuntimeException("Unable to initialize AudioRecord: Bad audio values");

        for (; i < ss.length; i++) {
            int s = ss[i];
            try {
                r = new AudioRecord(s, sampleRate, c, Sound.DEFAULT_AUDIOFORMAT, min);
                if (r.getState() == AudioRecord.STATE_INITIALIZED)
                    return r;
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "Recorder Create Failed: " + s, e);
            }
        }
        if (r == null || r.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new RuntimeException("Unable to initialize AudioRecord");
        }

        return r;
    }

    public static void throwError(int readSize) {
        switch (readSize) {
            case AudioRecord.ERROR:
                throw new RuntimeException("AudioRecord.ERROR");
            case AudioRecord.ERROR_BAD_VALUE:
                throw new RuntimeException("AudioRecord.ERROR_BAD_VALUE");
            case AudioRecord.ERROR_INVALID_OPERATION:
                throw new RuntimeException("AudioRecord.ERROR_INVALID_OPERATION");
            case AudioRecord.ERROR_DEAD_OBJECT:
                throw new RuntimeException("AudioRecord.ERROR_DEAD_OBJECT");
        }
    }

}
