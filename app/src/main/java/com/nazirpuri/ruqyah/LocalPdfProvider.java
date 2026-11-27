package com.nazirpuri.ruqyah;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** Small read-only provider for opening downloaded PDFs in an external reader. */
public class LocalPdfProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File resolve(Uri uri) throws FileNotFoundException {
        if (uri == null || uri.getPathSegments().size() != 2) {
            throw new FileNotFoundException("Invalid file path");
        }
        String kind = uri.getPathSegments().get(0);
        if ("installed_apk".equals(kind) && "nazirpuri.apk".equals(uri.getLastPathSegment())) {
            try {
                File installedApk = new File(getContext().getApplicationInfo().sourceDir).getCanonicalFile();
                if (!installedApk.isFile() || installedApk.length() == 0)
                    throw new FileNotFoundException("Installed APK not found");
                return installedApk;
            } catch (java.io.IOException e) {
                throw new FileNotFoundException("Unable to resolve installed APK");
            }
        }
        if (!"kitab_books".equals(kind)) throw new FileNotFoundException("Invalid kitab path");
        File directory = new File(getContext().getFilesDir(), "kitab_books");
        File file = new File(directory, uri.getLastPathSegment()).getAbsoluteFile();
        try {
            if (!file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile()) ||
                !file.isFile() || file.length() < 1000) {
                throw new FileNotFoundException("Kitab file not found");
            }
        } catch (java.io.IOException e) {
            throw new FileNotFoundException("Unable to resolve kitab file");
        }
        return file;
    }

    @Override public String getType(Uri uri) {
        return uri != null && uri.getPathSegments().size() == 2 &&
            "installed_apk".equals(uri.getPathSegments().get(0))
            ? "application/vnd.android.package-archive" : "application/pdf";
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        try {
            File file = resolve(uri);
            String[] columns = projection == null
                ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}
                : projection;
            MatrixCursor cursor = new MatrixCursor(columns);
            Object[] values = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) {
                if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) values[i] = file.getName();
                else if (OpenableColumns.SIZE.equals(columns[i])) values[i] = file.length();
            }
            cursor.addRow(values);
            return cursor;
        } catch (FileNotFoundException e) {
            return null;
        }
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws FileNotFoundException {
        if (!"r".equals(mode)) throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(resolve(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new UnsupportedOperationException(); }
}
