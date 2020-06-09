package com.pcchin.fileinflater;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
            String outputFilePath = generateValidFile(getExternalDownloadDir() + "compressed", ".zlib");
            try (FileOutputStream outputStream = new FileOutputStream(outputFilePath);
                 DeflaterOutputStream deflatedOutput = new DeflaterOutputStream(outputStream)) {
                deflatedOutput.write(contents);
            }
            Toast.makeText(MainActivity.this, "File decompressed to " + outputFilePath, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, R.string.file_error, Toast.LENGTH_SHORT).show();
        }
    }

    private void decompressFile(Uri data) {
        try (InputStream inputStream = getContentResolver().openInputStream(data);
             InflaterInputStream inputInflater = new InflaterInputStream(inputStream)) {
            byte[] contents = getRemainingBytesFromFile(inputInflater);
            String outputFile = generateValidFile(getExternalDownloadDir() + "decompressed", ".txt");
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                outputStream.write(contents);
            }
            Toast.makeText(MainActivity.this, "File decompressed to " + outputFile, Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(MainActivity.this, R.string.file_error, Toast.LENGTH_SHORT).show();
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

    @NonNull
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
            return new byte[0];
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
}