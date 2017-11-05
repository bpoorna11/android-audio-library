package com.github.axet.audiolibrary.services;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.webkit.MimeTypeMap;

import com.github.axet.androidlibrary.services.FileProvider;
import com.github.axet.audiolibrary.app.Storage;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;

// <application>
//   <provider
//     android:name="com.github.axet.torrentclient.services.RecordingContentProvider"
//     android:authorities="com.github.axet.audiolibrary"
//     android:exported="false"
//     android:grantUriPermissions="true">
//   </provider>
// </application>
//
// url example:
// content://com.github.axet.audiolibrary/778811221de5b06a33807f4c80832ad93b58016e/123.mp3
public class RecordingContentProvider extends ContentProvider {
    public static String TAG = RecordingContentProvider.class.getSimpleName();

    public static long TIMEOUT = 1 * 1000 * 60;

    protected static ProviderInfo info;

    public static HashMap<String, Uri> hashs = new HashMap<>(); // hash -> original url
    public static HashMap<Uri, Long> uris = new HashMap<>(); // original url -> time

    Runnable refresh = new Runnable() {
        @Override
        public void run() {
            freeUris();
        }
    };
    Handler handler = new Handler();
    Storage storage;

    public static String toHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    public static String md5(String str) {
        try {
            byte[] bytesOfMessage = str.getBytes("UTF-8");
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytesOfMessage);
            return toHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Uri share(Uri u) { // original uri -> hased uri
        long now = System.currentTimeMillis();
        uris.put(u, now);
        String hash = md5(u.toString());
        hashs.put(hash, u);
        File path = new File(hash, Storage.getDocumentName(u));
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(info.authority).path(path.toString()).build();
    }

    Uri find(Uri uri) { // hashed uri -> original uri
        String hash = uri.getPathSegments().get(0);
        Uri f = hashs.get(hash);
        if (f == null)
            return null;
        long now = System.currentTimeMillis();
        uris.put(f, now);
        return f;
    }

    public static String getAuthority() {
        return info.authority;
    }

    void freeUris() {
        long now = System.currentTimeMillis();
        for (Uri p : new HashSet<>(uris.keySet())) {
            long l = uris.get(p);
            if (l + TIMEOUT < now) {
                uris.remove(p);
                String hash = md5(p.toString());
                hashs.remove(hash);
            }
        }
        if (uris.size() == 0)
            return;
        handler.removeCallbacks(refresh);
        handler.postDelayed(refresh, TIMEOUT);
    }

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        RecordingContentProvider.info = info;
        // Sanity check our security
        if (info.exported) {
            throw new SecurityException("Provider must not be exported");
        }
        if (!info.grantUriPermissions) {
            throw new SecurityException("Provider must grant uri permissions");
        }
    }

    @Override
    public boolean onCreate() {
        storage = new Storage(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal) {
        if (projection == null) {
            projection = FileProvider.COLUMNS;
        }

        Uri f = find(uri);
        if (f == null)
            return null;

        String[] cols = new String[projection.length];
        Object[] values = new Object[projection.length];
        int i = 0;
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                cols[i] = OpenableColumns.DISPLAY_NAME;
                values[i++] = storage.getName(f);
            } else if (OpenableColumns.SIZE.equals(col)) {
                cols[i] = OpenableColumns.SIZE;
                values[i++] = storage.getLength(f);
            }
        }

        cols = FileProvider.copyOf(cols, i);
        values = FileProvider.copyOf(values, i);

        final MatrixCursor cursor = new MatrixCursor(cols, 1);
        cursor.addRow(values);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        Uri f = find(uri);
        if (f == null)
            return null;
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(storage.getExt(f));
    }

    @Nullable
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        Uri f = find(uri);
        if (f == null)
            return null;

        freeUris();

        final int fileMode = FileProvider.modeToMode(mode);

        try {
            Uri u = f;
            String s = u.getScheme();
            if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                ContentResolver resolver = getContext().getContentResolver();
                return resolver.openFileDescriptor(u, mode);
            } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
                File ff = new File(u.getPath());
                return ParcelFileDescriptor.open(ff, fileMode);
            } else {
                throw new RuntimeException("unknown uri");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
