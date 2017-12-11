package com.github.axet.audiolibrary.encoders;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import com.github.axet.audiolibrary.app.RawSamples;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class FileEncoder {
    public static final String TAG = FileEncoder.class.getSimpleName();

    Context context;
    Handler handler;

    File in;
    Encoder encoder;
    RawSamples rs;
    Thread thread;
    long samples;
    long cur;
    Throwable t;
    final AtomicBoolean pause = new AtomicBoolean(false);

    public FileEncoder(Context context, File in, Encoder encoder) {
        this.context = context;
        this.in = in;
        this.encoder = encoder;
        handler = new Handler();
    }

    public void run(final Runnable progress, final Runnable done, final Runnable error) {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                cur = 0;

                rs = new RawSamples(in);

                try {
                    samples = rs.getSamples();

                    short[] buf = new short[1000];

                    rs.open(buf.length); // FileNotFoundException if sdcard removed

                    while (true) {
                        try {
                            synchronized (pause) {
                                if (pause.get()) {
                                    pause.wait();
                                }
                            }
                        } catch (InterruptedException e) {
                            return;
                        }
                        int len = rs.read(buf);
                        if (len <= 0) {
                            break;
                        } else {
                            encoder.encode(buf, 0, len);
                            handler.post(progress);
                            synchronized (thread) {
                                cur += len;
                            }
                        }
                    }
                    handler.post(done);
                } catch (RuntimeException e) {
                    Log.e(TAG, "Exception", e);
                    t = e;
                    handler.post(error);
                } finally {
                    if (encoder != null) {
                        encoder.close();
                        encoder = null;
                    }
                    if (rs != null) {
                        rs.close();
                        rs = null;
                    }
                }
            }
        }, "FileEncoder");
        thread.start();
    }

    public int getProgress() {
        synchronized (thread) {
            return (int) (cur * 100 / samples);
        }
    }

    public Throwable getException() {
        return t;
    }

    public void pause() {
        synchronized (pause) {
            pause.set(true);
        }
    }

    public void resume() {
        synchronized (pause) {
            pause.set(false);
            pause.notifyAll();
        }
    }

    public void close() {
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        if (encoder != null) {
            encoder.close();
            encoder = null;
        }
        if (rs != null) {
            rs.close();
            rs = null;
        }
        handler.removeCallbacksAndMessages(null); // prevent call progress/done after encoder closed()
    }
}
