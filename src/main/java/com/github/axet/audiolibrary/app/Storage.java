package com.github.axet.audiolibrary.app;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;

import com.github.axet.audiolibrary.encoders.Factory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Storage extends com.github.axet.androidlibrary.app.Storage {
    public static final String TMP_REC = "recorind.data";
    public static final String RECORDINGS = "recordings";

    protected Context context;

    public Storage(Context context) {
        this.context = context;
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public boolean permitted(String[] ss) {
        if (Build.VERSION.SDK_INT < 11)
            return true;
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(context, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public File getLocalStorage() {
        return new File(context.getApplicationInfo().dataDir, RECORDINGS);
    }

    public boolean isLocalStorageEmpty() {
        return getLocalStorage().listFiles().length == 0;
    }

    public boolean isExternalStoragePermitted() {
        return permitted(PERMISSIONS);
    }

    public boolean recordingPending() {
        return getTempRecording().exists();
    }

    public File getStoragePath() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");
        File file = new File(path);
        File parent = file.getParentFile();
        while (!parent.exists())
            parent = file.getParentFile();
        if (permitted(PERMISSIONS) && (file.canWrite() || parent.canWrite())) {
            return file;
        } else {
            return getLocalStorage();
        }
    }

    public void migrateLocalStorage() {
        if (!permitted(PERMISSIONS)) {
            return;
        }

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");

        File l = getLocalStorage();
        File t = new File(path);

        if (!t.mkdirs())
            return;

        File[] ff = l.listFiles();

        if (ff == null)
            return;

        for (File f : ff) {
            File tt = getNextFile(t, f);
            move(f, tt);
        }
    }

    public File getNewFile() {
        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        File parent = getStoragePath();
        if (!parent.exists()) {
            if (!parent.mkdirs())
                throw new RuntimeException("Unable to create: " + parent);
        }

        return getNextFile(parent, s.format(new Date()), ext);
    }

    public List<File> scan(File dir) {
        ArrayList<File> list = new ArrayList<>();

        File[] ff = dir.listFiles();
        if (ff == null)
            return list;

        for (File f : ff) {
            if (f.length() > 0) {
                String[] ee = Factory.getEncodingValues(context);
                String n = f.getName().toLowerCase();
                for (String e : ee) {
                    if (n.endsWith("." + e))
                        list.add(f);
                }
            }
        }

        return list;
    }

    // get average recording miliseconds based on compression format
    public long average(long free) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int rate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        int m = MainApplication.getChannels(context);
        long perSec = Factory.getEncoderRate(ext, rate) * m;
        return free / perSec * 1000;
    }

    public File getTempRecording() {
        File internal = new File(context.getApplicationInfo().dataDir, TMP_REC);

        if (internal.exists())
            return internal;

        // Starting in KITKAT, no permissions are required to read or write to the returned path;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(PERMISSIONS))
                return internal;
        }

        File c = context.getExternalCacheDir();
        if (c == null) // some old phones 11 with disabled sdcard return null
            return internal;

        File external = new File(c, TMP_REC);

        if (external.exists())
            return external;

        long freeI = getFree(internal);
        long freeE = getFree(external);

        if (freeI > freeE)
            return internal;
        else
            return external;
    }

    public FileOutputStream open(File f) {
        File tmp = f;
        File parent = tmp.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("unable to create: " + parent);
        }
        if (!parent.isDirectory())
            throw new RuntimeException("target is not a dir: " + parent);
        try {
            return new FileOutputStream(tmp, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
