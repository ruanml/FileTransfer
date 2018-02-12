package com.example.ruan.transfer;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.angads25.filepicker.controller.DialogSelectionListener;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final int REQUEST_CODE_FILE_PICKER = 101;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler();
    private EditText addressView;
    private EditText portView;
    private TextView fileNameView;
    private Button selectButton;
    private Button transferButton;
    private ProgressBar progressBar;
    private String filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        addressView = findViewById(R.id.address);
        portView = findViewById(R.id.port);
        fileNameView = findViewById(R.id.fileName);
        selectButton = findViewById(R.id.select);
        transferButton = findViewById(R.id.transfer);
        transferButton.setEnabled(false);
        progressBar = findViewById(R.id.progressBar);
        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilePicker();
            }
        });

        transferButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startTransfer();
            }
        });
    }

    private void showFilePicker() {
        DialogProperties properties = new DialogProperties();
        properties.root = Environment.getExternalStorageDirectory();
        FilePickerDialog dialog = new FilePickerDialog(this, properties);
        dialog.setTitle("Select file");
        dialog.setDialogSelectionListener(new DialogSelectionListener() {
            @Override
            public void onSelectedFilePaths(String[] files) {
                if (files != null && files.length > 0) {
                    filePath  = files[0];
                    fileNameView.setText(filePath);
                    transferButton.setEnabled(true);
                }
            }
        });
        dialog.show();
    }

    private void startTransfer() {
        String address = addressView.getText().toString();
        int port = Integer.parseInt(portView.getText().toString());
        executorService.submit(new TransferTask(address, port, filePath, new ProgressCallback() {
            @Override
            public void updateProgress(final int value) {
                Log.d(TAG, "updateProgress: " + value);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateDetail(value);
                    }
                });
            }
        }));
    }

    private void updateDetail(int value) {
        progressBar.setProgress(value);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        //super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case FilePickerDialog.EXTERNAL_READ_PERMISSION_GRANT:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    showFilePicker();
                } else {
                    Toast.makeText(MainActivity.this, "Permission is required for getting file list", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private class TransferTask implements Runnable {
        private static final String TAG = "TransferTask";
        private String address;
        private int port;
        private String filePath;
        private ProgressCallback progressCallback;

        TransferTask(String address, int port, String filePath, ProgressCallback progressCallback) {
            this.address = address;
            this.port = port;
            this.filePath = filePath;
            this.progressCallback = progressCallback;
        }

        @Override
        public void run() {
            Socket socket = null;
            OutputStream os = null;
            InputStream is = null;
            try {
                socket = new Socket(address, port);
                boolean isConnected = socket.isConnected();
                Log.d(TAG, "socket.isConnected() is " + isConnected);
                os = socket.getOutputStream();
                File file = new File(filePath);
                long length = file.length();
                byte[] buffer = new byte[4096];
                is = new FileInputStream(file);
                int count = 0;
                int sentCount = 0;
                while ((count = is.read(buffer)) > 0) {
                    os.write(buffer, 0, count);
                    sentCount += count;
                    progressCallback.updateProgress((int) (((sentCount * 1.00f) / length) * 100));
                    os.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                closeCloseableSafety(os);
                closeCloseableSafety(is);
                closeSocketSafety(socket);
            }
        }

        private void closeSocketSafety(Socket socket) {
            if (socket == null) {
                return;
            }
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        private void closeCloseableSafety(Closeable c) {
            if (c == null) {
                return;
            }
            try {
                c.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private interface ProgressCallback {
        void updateProgress(int value);
    }
}
