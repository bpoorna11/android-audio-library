package com.github.axet.audiolibrary.encoders;

import com.github.axet.audiolibrary.app.Storage;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class FileMuxer {
    public static String TAG = FileMuxer.class.getSimpleName();

    public Storage storage;
    public FileDescriptor fd;
    public File out;

    public FileMuxer(Storage storage, FileDescriptor fd) {
        this.storage = storage;
        this.fd = fd;

        out = storage.getTempEncoding();

        File parent = out.getParentFile();

        if (!parent.exists() && !parent.mkdirs()) { // in case if it were manually deleted
            throw new RuntimeException("Unable to create: " + parent);
        }
    }

    public void close() {
        if (out != null && out.exists() && out.length() > 0) {
            try {
                FileInputStream fis = new FileInputStream(out);
                FileOutputStream fos = new FileOutputStream(fd);
                IOUtils.copy(fis, fos);
                fos.close();
                fis.close();
                Storage.delete(out);
            } catch (IOException e) {
                Storage.delete(out); // delete tmp encoding file
                throw new RuntimeException(e);
            }
            out = null;
        }
    }

}
