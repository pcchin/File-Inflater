package com.pcchin.fileinflater;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class MainActivity extends AppCompatActivity {
    private static final String LOG_APP_NAME = "FileInflater";
    private static final int SELECT_COMPRESS_FILE = 300;
    private static final int SELECT_DECOMPRESS_FILE = 301;
    private static final int EXTERNAL_STORAGE_PERMISSION = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Get permission to read and write files
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && ContextCompat.checkSelfPermission(
                MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    EXTERNAL_STORAGE_PERMISSION);
        }
        // Set onClickListeners
        findViewById(R.id.compress).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.pickFile(SELECT_COMPRESS_FILE);
            }
        });
        findViewById(R.id.decompress).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                MainActivity.this.pickFile(SELECT_DECOMPRESS_FILE);
            }
        });
    }

    private void pickFile(int code) {
        // Set up file chooser
        Intent fileSelectIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileSelectIntent.setType("*/*");
        startActivityForResult(fileSelectIntent, code);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == EXTERNAL_STORAGE_PERMISSION) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, R.string.error_write_permission_denied,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data.getData() != null) {
            if (requestCode == SELECT_COMPRESS_FILE) {
                compressFile(data.getData());
            } else {
                decompressFile(data.getData());
            }
        }
    }

    private void compressFile(Uri data) {
        try (InputStream inputStream = getContentResolver().openInputStream(data)) {
            if (inputStream == null) {
                return;
            }
            byte[] contents = getRemainingBytesFromFile(inputStream);
            String outputFilePath = getOutputFilePath(true, data);
            try (FileOutputStream outputStream = new FileOutputStream(outputFilePath);
                 DeflaterOutputStream deflatedOutput = new DeflaterOutputStream(outputStream)) {
                deflatedOutput.write(Objects.requireNonNull(contents));
            }
            Toast.makeText(MainActivity.this, "File decompressed to " + outputFilePath, Toast.LENGTH_SHORT).show();
        } catch (NullPointerException | IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, R.string.file_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void decompressFile(Uri data) {
        try (InputStream inputStream = getContentResolver().openInputStream(data);
             InflaterInputStream inputInflater = new InflaterInputStream(inputStream)) {
            byte[] contents = getRemainingBytesFromFile(inputInflater);
            String outputFile = getOutputFilePath(false, data);
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                outputStream.write(Objects.requireNonNull(contents));
            }
            Toast.makeText(MainActivity.this, "File decompressed to " + outputFile, Toast.LENGTH_SHORT).show();
        } catch (NullPointerException | IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, R.string.file_error, Toast.LENGTH_SHORT).show();
        }
    }

    private String getOutputFilePath(boolean isCompression, Uri data) {
        try {
            String originalName = getFileName(getFileNameFromUri(data));
            if (isCompression) {
                return generateValidFile(getExternalDownloadDir() + "/" + originalName, ".zlib");
            } else {
                return generateValidFile(getExternalDownloadDir() + "/" + originalName, ".txt");
            }
        } catch (Exception e) {
            if (isCompression) {
                return generateValidFile(getExternalDownloadDir() + "compressed", ".zlib");
            } else {
                return generateValidFile(getExternalDownloadDir() + "decompressed", ".txt");
            }
        }
    }

    @NonNull
    public String getExternalDownloadDir() {
        File downloadDir = new File("/storage/emulated/0/Download");
        File downloadDir2 = new File("/storage/emulated/0/Downloads");
        return downloadDir.exists() && downloadDir.isDirectory() && downloadDir.canWrite()
                ? "/storage/emulated/0/Download/" : downloadDir2.exists()
                && downloadDir2.isDirectory() && downloadDir2.canWrite() ?
                "/storage/emulated/0/Downloads/" : getInternalDownloadDir();
    }

    @NonNull
    public String getInternalDownloadDir() {
        File downloadDirFile = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDirFile == null) {
            downloadDirFile = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        }
        return downloadDirFile == null ? "/storage/emulated/0/" : downloadDirFile.getAbsolutePath() + "/";
    }

    @Nullable
    public static byte[] getRemainingBytesFromFile(@NonNull InputStream stream) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            while(stream.available() != 0){
                buffer.write(stream.read());
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            Log.w(LOG_APP_NAME, "File Error: Remaining bytes of input stream of file "
                    + stream + " not able to be read. Stack trace is");
            e.printStackTrace();
            return null;
        }
    }

    public static String generateValidFile(String filename, String extension) {
        String returnFile = filename + extension;
        int i = 1;
        while (new File(returnFile).exists() && i < Integer.MAX_VALUE) {
            returnFile = filename + "(" + i + ")" + extension;
            i++;
        }
        return returnFile;
    }

    private String getFileNameFromUri(@NonNull Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null && uri.getPath() != null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    @NonNull
    public static String getFileName(String fileName) {
        String[] splitString = new StringBuilder(fileName)
                .reverse().toString()
                .split("\\.", 2);
        if (splitString.length > 1) {
            return new StringBuilder(splitString[1]).reverse().toString();
        } else if (splitString.length == 1) {
            return new StringBuilder(splitString[0]).reverse().toString();
        } else {
            return "";
        }
    }
}