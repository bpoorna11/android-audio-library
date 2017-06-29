package com.github.axet.audiolibrary.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.util.Log;
import android.webkit.MimeTypeMap;

import com.github.axet.audiolibrary.encoders.Factory;

import org.apache.commons.io.IOUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
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
    public static String TAG = Storage.class.getSimpleName();

    public static final String TMP_REC = "recording.data";
    public static final String TMP_ENC = "encoding.data";

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

    public Uri getStoragePath() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");
        return getStoragePath(path);
    }

    public File fallbackStorage() {
        File internal = getLocalInternal();

        // Starting in KITKAT, no permissions are required to read or write to the returned path;
        // it's always accessible to the calling app.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            if (!permitted(context, PERMISSIONS))
                return internal;
        }

        File external = getLocalExternal();

        if (external == null)
            return internal;

        return external;
    }

    @Override
    public Uri getStoragePath(String path) {
        if (Build.VERSION.SDK_INT >= 21 && path.startsWith(ContentResolver.SCHEME_CONTENT)) {
            Uri uri = Uri.parse(path);
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            ContentResolver resolver = context.getContentResolver();
            try {
                final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                resolver.takePersistableUriPermission(uri, takeFlags);
                Cursor c = resolver.query(doc, null, null, null, null);
                if (c != null) {
                    c.close();
                    return uri;
                }
            } catch (SecurityException e) {
                Log.d(TAG, "open SAF failed", e);
            }
            path = fallbackStorage().getAbsolutePath(); // we need to fallback to local storage internal or exernal
        }
        if (!permitted(context, PERMISSIONS)) {
            return Uri.fromFile(getLocalStorage());
        } else {
            return Uri.fromFile(super.getStoragePath(new File(path)));
        }
    }

    public void migrateLocalStorage() {
        migrateLocalStorage(new File(context.getApplicationInfo().dataDir, "recordings")); // old recordings folder
        migrateLocalStorage(new File(context.getApplicationInfo().dataDir)); // old recordings folder
        migrateLocalStorage(getLocalInternal());
        migrateLocalStorage(getLocalExternal());
    }

    public void migrateLocalStorage(File l) {
        if (l == null)
            return;

        Uri path = getStoragePath();
        String s = path.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            if (!permitted(context, PERMISSIONS))
                return;
        }

        File[] ff = l.listFiles();

        if (ff == null)
            return;

        for (File f : ff) {
            if (f.isFile()) { // skip directories (we didn't create one)
                String name = getNameNoExt(f);
                String ext = getExt(f);
                Uri t = getNextFile(path, name, ext);
                move(f, t);
            }
        }
    }

    @Override
    public Uri move(File f, Uri t) {
        Uri u = super.move(f, t);
        if (u == null)
            return null;
        boolean star = MainApplication.getStar(context, Uri.fromFile(f));
        MainApplication.setStar(context, u, star); // copy star to migrated file
        return u;
    }

    @Override
    public Uri rename(Uri f, String t) {
        Uri u = super.rename(f, t);
        if (u == null)
            return null;
        boolean star = MainApplication.getStar(context, f);
        MainApplication.setStar(context, u, star); // copy star to renamed name
        return u;
    }

    public Uri getNewFile() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        Uri parent = getStoragePath();

        String s = parent.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File p = new File(parent.getPath());
            if (!p.exists() && !p.mkdirs())
                throw new RuntimeException("Unable to create: " + parent);
        }

        return getNextFile(parent, SIMPLE.format(new Date()), ext);
    }

    public List<Uri> scan(Uri uri) {
        String s = uri.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            ArrayList<Uri> list = new ArrayList<>();

            ContentResolver contentResolver = context.getContentResolver();
            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
            Cursor childCursor = contentResolver.query(childrenUri, null, null, null, null);
            if (childCursor != null) {
                try {
                    while (childCursor.moveToNext()) {
                        String id = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                        String t = childCursor.getString(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME));
                        long size = childCursor.getLong(childCursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE));
                        if (size > 0) {
                            String[] ee = Factory.getEncodingValues(context);
                            String n = t.toLowerCase();
                            for (String e : ee) {
                                if (n.endsWith("." + e)) {
                                    Uri d = DocumentsContract.buildDocumentUriUsingTree(uri, id);
                                    list.add(d);
                                }
                            }
                        }
                    }
                } finally {
                    childCursor.close();
                }
            }

            return list;
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File dir = new File(uri.getPath());
            ArrayList<Uri> list = new ArrayList<>();

            File[] ff = dir.listFiles();
            if (ff == null)
                return list;

            for (File f : ff) {
                if (f.length() > 0) {
                    String[] ee = Factory.getEncodingValues(context);
                    String n = f.getName().toLowerCase();
                    for (String e : ee) {
                        if (n.endsWith("." + e))
                            list.add(Uri.fromFile(f));
                    }
                }
            }

            return list;
        } else {
            throw new RuntimeException("unknow uri");
        }
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

    public File getTempEncoding() {
        File internal = new File(context.getCacheDir(), TMP_ENC);
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

        File external = new File(c, TMP_ENC);

        if (external.exists())
            return external;

        long freeI = getFree(internal);
        long freeE = getFree(external);

        if (freeI > freeE)
            return internal;
        else
            return external;
    }
}
