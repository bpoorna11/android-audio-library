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
    public static final String TMP_REC = "recording.data";

    public static final SimpleDateFormat SIMPLE = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
    public static final SimpleDateFormat ISO8601 = new SimpleDateFormat("yyyyMMdd'T'HHmmss");

    public Storage(Context context) {
        super(context);
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public boolean isLocalStorageEmpty() {
        File[] ff = getLocalStorage().listFiles();
        if (ff == null)
            return true;
        return ff.length == 0;
    }

    public boolean isExternalStoragePermitted() {
        return permitted(context, PERMISSIONS);
    }

    public boolean recordingPending() {
        return getTempRecording().exists();
    }

    public File getStoragePath() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");
        if (!permitted(context, PERMISSIONS)) {
            return getLocalStorage();
        } else {
            return super.getStoragePath(new File(path));
        }
    }

    public void migrateLocalStorage() {
        if (!permitted(context, PERMISSIONS)) {
            return;
        }
        migrateLocalStorage(new File(context.getApplicationInfo().dataDir, "recordings")); // old recordings folder
        migrateLocalStorage(new File(context.getApplicationInfo().dataDir)); // old recordings folder
        migrateLocalStorage(getLocalInternal());
        migrateLocalStorage(getLocalExternal());
    }

    public void migrateLocalStorage(File l) {
        if (l == null)
            return;

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");

        File dir = new File(path);

        if (isSame(l, dir))
            return;

        if (!dir.exists() && !dir.mkdirs())
            return;

        File[] ff = l.listFiles();

        if (ff == null)
            return;

        for (File f : ff) {
            if (f.isFile()) { // skip directories (we didn't create one)
                File t = getNextFile(new File(dir, f.getName()));
                move(f, t);
            }
        }
    }

    public File getNewFile() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        File parent = getStoragePath();
        if (!parent.exists() && !parent.mkdirs())
            throw new RuntimeException("Unable to create: " + parent);

        return getNextFile(parent, SIMPLE.format(new Date()), ext);
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
    public static long average(Context context, long free) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int rate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        int m = MainApplication.getChannels(context);
        long perSec = Factory.getEncoderRate(ext, rate) * m;
        return free / perSec * 1000;
    }

    public File getTempRecording() {
        File internalOld = new File(context.getApplicationInfo().dataDir, "recorind.data");
        if (internalOld.exists())
            return internalOld;
        internalOld = new File(context.getApplicationInfo().dataDir, TMP_REC);
        if (internalOld.exists())
            return internalOld;

        File internal = new File(context.getCacheDir(), TMP_REC);
        if (internal.exists())
            return internal;

        // Starting in KITKAT, no permissions are required to read or write to the returned path;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(context, PERMISSIONS))
                return internal;
        }

        File c = context.getExternalCacheDir();
        if (c == null) // some old phones <15API with disabled sdcard return null
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
