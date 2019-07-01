package com.github.axet.audiolibrary.widgets;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.View;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.audiolibrary.app.RawSamples;
import com.github.axet.audiolibrary.app.Sound;
import com.github.axet.audiolibrary.encoders.MediaDecoderCompat;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

public class MoodbarView extends View {
    double[] data;
    int p;
    int s;
    int ps;
    Paint paint;

    public static double[] getMoodbar(Context context, Uri uri) throws IOException {
        final int wps = 1000;
        final double[] dd = new double[wps];
        final MediaDecoderCompat decoder = new MediaDecoderCompat(context, uri);
        final long part = decoder.duration * decoder.hz * decoder.channels / wps / 1000 / 1000;
        OutputStream os = new OutputStream() {
            double sum = 0;
            int samples = 0;
            int total = 0;
            int i = 0;

            @Override
            public void write(int b) throws IOException {
                push(b);
            }

            @Override
            public void write(@NonNull byte[] b, int off, int len) throws IOException {
                short[] ss = MediaDecoderCompat.asShortBuffer(b, off, len);
                for (short s : ss)
                    push(s);
            }

            void push(double s) {
                sum += s * s;
                samples++;
                total++;
                if (samples >= part) {
                    if (i < dd.length)
                        dd[i++] = Math.sqrt(sum / samples);
                    sum = 0;
                    samples = 0;
                }
            }

            @Override
            public void close() throws IOException {
                if (i < dd.length)
                    dd[i++] = Math.sqrt(sum / samples);
                sum = 0;
                samples = 0;
            }
        };
        decoder.decode(os);
        os.close();
        decoder.close();
        return dd;
    }

    public static void saveMoodbar(double[] data, File cover) throws Exception {
        JSONArray a = new JSONArray();
        for (double d : data)
            a.put(d);
        FileUtils.write(cover, a.toString(), Charset.defaultCharset());
    }

    public static double[] loadMoodbar(File cover) throws Exception {
        String s = FileUtils.readFileToString(cover, Charset.defaultCharset());
        JSONArray a = new JSONArray(s);
        double[] data = new double[a.length()];
        for (int i = 0; i < a.length(); i++)
            data[i] = a.optDouble(i);
        return data;
    }

    public MoodbarView(Context context) {
        super(context);
        create();
    }

    public MoodbarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        create();
    }

    public MoodbarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        create();
    }

    @TargetApi(21)
    public MoodbarView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        create();
    }

    void create() {
        p = ThemeUtils.dp2px(getContext(), 2);
        s = ThemeUtils.dp2px(getContext(), 1);
        ps = p + s;
        paint = new Paint();
        paint.setColor(ThemeUtils.getThemeColor(getContext(), android.R.attr.colorForeground));
        paint.setStyle(Paint.Style.FILL);
    }

    public void setData(double[] dd) {
        this.data = dd;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int steps = getWidth() / ps;
        int step = data.length / steps;
        Rect r = new Rect();
        int off = 0;
        int index = 0;
        for (int i = 0; i < steps; i++) {
            double sum = 0;
            int ke = Math.min(index + step, data.length);
            for (int k = index; k < ke; k++)
                sum = data[k] * data[k];
            index += step;
            sum = Math.sqrt(sum / (ke - i));
            double db = RawSamples.getDB(sum);
            db = Sound.MAXIMUM_DB + db;
            db = db / Sound.MAXIMUM_DB;
            int hh = getHeight() / 2;
            int dh = (int) (hh * db);
            r.left = off;
            r.right = off + p;
            r.top = hh - dh;
            r.bottom = hh + dh;
            off = off + ps;
            canvas.drawRect(r, paint);
        }
    }
}