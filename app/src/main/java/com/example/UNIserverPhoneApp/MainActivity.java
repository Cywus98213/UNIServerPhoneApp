package com.example.UNIserverPhoneApp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;


import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private Button startButton, closeButton, playAudioButton, sendTextButton;
    private ImageView imageView;
    private EditText messageInput;
    private TextView statusView, debugLog;

    private ServerSocket serverSocket;
    private Socket piSocket, glassesSocket;
    private Thread serverThread;

    private AudioTrack audioTrack;

    // RFCOMM (classic Bluetooth) server fields
    private BluetoothAdapter bluetoothAdapter;
    private static final UUID RFCOMM_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothServerSocket rfcommServerSocket;
    private Thread rfcommThread;
    private volatile boolean rfcommRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        setupButtonListeners();
    }

    public void getDeviceInfo(){
        try {
            WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
            int ip = dhcpInfo.gateway; // gateway = hotspot IP
            String ipString = String.format("%d.%d.%d.%d",
                    (ip & 0xff),
                    (ip >> 8 & 0xff),
                    (ip >> 16 & 0xff),
                    (ip >> 24 & 0xff));
            addDebugLog("Hotspot Gateway IP: " + ipString);
        } catch (Exception e) {
            addDebugLog("Error getting hotspot IP: " + e.getMessage());
        }
    }


    private void initializeViews() {
        startButton = findViewById(R.id.startButton);
        closeButton = findViewById(R.id.closeButton);
        playAudioButton = findViewById(R.id.playAudioButton);
        sendTextButton = findViewById(R.id.sendTextButton);
        imageView = findViewById(R.id.imageView);
        messageInput = findViewById(R.id.messageInput);
        statusView = findViewById(R.id.statusView);
        debugLog = findViewById(R.id.debugLog);
    }

    private void setupButtonListeners() {
        startButton.setOnClickListener(v -> startServer());
        closeButton.setOnClickListener(v -> closeServer());
        playAudioButton.setOnClickListener(v -> initAudioPlayer());
        sendTextButton.setOnClickListener(v -> sendMessageToGlasses());
    }

    // ==================== SERVER START ====================
    private void startServer() {
        serverThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(8888);
                getDeviceInfo();
                addDebugLog("Local server socket bound to: " + serverSocket.getLocalSocketAddress());
                addDebugLog("Server started on port 8888");
                setStatusText("waiting for connection...");
                while (true) {
                    addDebugLog("Waiting for client connection on port 8888...");
                    Socket client = serverSocket.accept();
                    addDebugLog("Client accepted: " + client.getInetAddress().getHostAddress());
                    String addr = client.getInetAddress().toString();
                    addDebugLog("Client connected: " + addr);
                    setStatusText("Connected");

                    // Decide if Pi or Glasses
                    if (addr.contains("Pi")) {
                        piSocket = client;
                        handlePiConnection(piSocket);
                    } else {
                        glassesSocket = client;
                        handleGlassesConnection(glassesSocket);
                    }
                }
            } catch (IOException e) {
                setStatusText("failed to start server");
                addDebugLog("Server error: " + e.getMessage());
            }
        });
        serverThread.start();
        startRfcommServer();
    }

    // ==================== SERVER CLOSE ====================
    private void closeServer() {
        try {
            if (piSocket != null) piSocket.close();
            if (glassesSocket != null) glassesSocket.close();
            if (serverSocket != null) serverSocket.close();
            if (rfcommRunning) stopRfcommServer();
            addDebugLog("Server closed");
        } catch (IOException e) {
            addDebugLog("Error closing: " + e.getMessage());
        }
    }

    // ==================== PI IMAGE HANDLER ====================
    private void handlePiConnection(Socket socket) {
        new Thread(() -> {
            try {
                DataInputStream din = new DataInputStream(socket.getInputStream());
                while (true) {
                    int length = din.readInt();
                    byte[] imageBytes = new byte[length];
                    din.readFully(imageBytes);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, length);
                    runOnUiThread(() -> imageView.setImageBitmap(bitmap));
                }
            } catch (IOException e) {
                addDebugLog("Pi disconnected");
            }
        }).start();
    }
    // ==================== PI RFC SERVER====================
    private void startRfcommServer() {
        if (rfcommRunning) return;
        if (bluetoothAdapter == null) {
            setStatusText("Bluetooth adapter null, cannot start RFCOMM");
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            setStatusText("Bluetooth disabled, cannot start RFCOMM");
            return;
        }

        rfcommRunning = true;
        rfcommThread = new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    addDebugLog("Bluetooth permission not granted.");
                    ActivityCompat.requestPermissions(
                            this,
                            new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                            101
                    );
                    return;
                }
                rfcommServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("PiStream", RFCOMM_UUID);
                addDebugLog("RFCOMM server socket created, waiting for Pi...");

                while (rfcommRunning) {
                    BluetoothSocket socket = null;
                    try {
                        socket = rfcommServerSocket.accept(); // blocking
                        if (socket != null) {
                            addDebugLog("Pi connected: " + socket.getRemoteDevice().getAddress());
                            handlePiSocket(socket);
                        }
                    } catch (IOException e) {
                        if (rfcommRunning) addDebugLog("RFCOMM accept error: " + e.getMessage());
                        break;
                    } finally {
                        if (socket != null) {
                            try { socket.close(); } catch (IOException ignored) {}
                        }
                    }
                }
            } catch (IOException e) {
                addDebugLog("Failed to create RFCOMM server socket: " + e.getMessage());
            } finally {
                try {
                    if (rfcommServerSocket != null) {
                        rfcommServerSocket.close();
                        rfcommServerSocket = null;
                    }
                } catch (IOException ignored) {}
                addDebugLog("RFCOMM server stopped");
                rfcommRunning = false;
            }
        }, "RfcommServerThread");
        rfcommThread.start();
    }

    private void stopRfcommServer() {
        rfcommRunning = false;
        try {
            if (rfcommServerSocket != null) {
                rfcommServerSocket.close();
                rfcommServerSocket = null;
            }
        } catch (IOException e) {
            addDebugLog("Error closing rfcommServerSocket: " + e.getMessage());
        }
        if (rfcommThread != null) {
            rfcommThread.interrupt();
            rfcommThread = null;
        }
        setStatusText("RFCOMM server stopped/cleaned up");
    }

    private void handlePiSocket(BluetoothSocket socket) {
        setStatusText("Starting to receive frames from Pi...");
        try (DataInputStream din = new DataInputStream(socket.getInputStream())) {
            while (rfcommRunning && socket.isConnected()) {
                int length;
                try {
                    length = din.readInt();
                } catch (IOException e) {
                    addDebugLog("Stream ended or readInt failed: " + e.getMessage());
                    break;
                }

                if (length <= 0) {
                    addDebugLog("Invalid frame length: " + length);
                    continue;
                }

                byte[] imageBytes = new byte[length];
                try {
                    din.readFully(imageBytes);
                } catch (IOException e) {
                    addDebugLog("Failed to read full frame: " + e.getMessage());
                    break;
                }

                addDebugLog("Received frame: " + length + " bytes");

                final Bitmap bmp = BitmapFactory.decodeByteArray(imageBytes, 0, length);
                if (bmp != null) {
                    runOnUiThread(() -> {
                        imageView.setImageBitmap(bmp);
                        addDebugLog("Status: Frame received (" + length + " bytes)");
                    });
                } else {
                    addDebugLog("Failed to decode bitmap");
                }
            }
        } catch (IOException e) {
            addDebugLog("Pi socket IO error: " + e.getMessage());
            setStatusText("Status: Pi disconnected");
        } catch (Exception e) {
            addDebugLog("Unexpected error handling Pi socket: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
            addDebugLog("Pi socket closed");
        }
    }
    // ==================== GLASSES AUDIO HANDLER ====================
    private byte[] lastAudioClip;

    private void handleGlassesConnection(Socket socket) {
        new Thread(() -> {
            try {
                DataInputStream din = new DataInputStream(socket.getInputStream());
                while (true) {
                    int length = din.readInt(); // read clip length
                    byte[] audioBytes = new byte[length];
                    din.readFully(audioBytes);
                    lastAudioClip = audioBytes; // store for playback
                    addDebugLog("Received audio clip of " + length + " bytes");
                    setStatusText("Audio clip ready to play");
                }
            } catch (IOException e) {
                addDebugLog("Glasses disconnected: " + e.getMessage());
            }
        }).start();
    }



    // ==================== AUDIO PLAYER ====================
    private void initAudioPlayer() {
        if (lastAudioClip == null) {
            addDebugLog("No audio clip received yet");
            return;
        }

        int bufferSize = AudioTrack.getMinBufferSize(
                16000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);

        addDebugLog("Initializing AudioTrack with buffer size: " + bufferSize);

        audioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                16000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM);

        audioTrack.play();
        audioTrack.write(lastAudioClip, 0, lastAudioClip.length);
        addDebugLog("Played audio clip of " + lastAudioClip.length + " bytes");
    }



    // ==================== SEND MESSAGE TO GLASSES ====================
    private void sendMessageToGlasses() {
        if (glassesSocket == null) {
            addDebugLog("No glasses connected");
            return;
        }
        new Thread(() -> {
            try {
                OutputStream out = glassesSocket.getOutputStream();
                String msg = messageInput.getText().toString();
                out.write(msg.getBytes());
                addDebugLog("Message sent: " + msg);
            } catch (IOException e) {
                addDebugLog("Error sending message: " + e.getMessage());
            }
        }).start();
    }

    private void addDebugLog(String msg) {
        runOnUiThread(() -> debugLog.append("\n" + msg));
    }
    private void setStatusText(String msg) {
        runOnUiThread(() -> statusView.setText(msg));
    }
}
