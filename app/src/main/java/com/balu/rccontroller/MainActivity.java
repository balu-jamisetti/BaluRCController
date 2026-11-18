
package com.balu.rccontroller;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQUEST_BLUETOOTH_CONNECT = 100;
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;

    private final ArrayList<BluetoothDevice> pairedDevices =
            new ArrayList<>();
    private final ArrayList<String> deviceNames =
            new ArrayList<>();

    private final ExecutorService bluetoothExecutor =
            Executors.newSingleThreadExecutor();

    private Spinner deviceSpinner;
    private TextView statusText;
    private Button connectButton;
    private Button disconnectButton;
    private Button refreshButton;

    private volatile boolean connected = false;
    private volatile boolean connecting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        BluetoothManager manager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        if (manager != null) {
            bluetoothAdapter = manager.getAdapter();
        }

        createInterface();
        checkBluetoothPermission();
    }

    private void createInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView heading = new TextView(this);
        heading.setText("BALU RC CONTROLLER");
        heading.setTextSize(25);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setGravity(Gravity.CENTER);
        root.addView(heading, matchWidth(65));

        TextView subtitle = new TextView(this);
        subtitle.setText("Bluetooth Classic - HC-05");
        subtitle.setTextSize(16);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, matchWidth(45));

        TextView deviceLabel = new TextView(this);
        deviceLabel.setText("Select paired Bluetooth device");
        deviceLabel.setTextSize(16);
        root.addView(deviceLabel, matchWidth(45));

        deviceSpinner = new Spinner(this);
        root.addView(deviceSpinner, matchWidth(55));

        LinearLayout connectionRow = new LinearLayout(this);
        connectionRow.setOrientation(LinearLayout.HORIZONTAL);

        refreshButton = new Button(this);
        refreshButton.setText("REFRESH");
        connectionRow.addView(refreshButton, weightedWidth());

        connectButton = new Button(this);
        connectButton.setText("CONNECT");
        connectionRow.addView(connectButton, weightedWidth());

        disconnectButton = new Button(this);
        disconnectButton.setText("DISCONNECT");
        connectionRow.addView(disconnectButton, weightedWidth());

        root.addView(connectionRow, matchWidth(65));

        statusText = new TextView(this);
        statusText.setText("Status: Checking Bluetooth...");
        statusText.setTextSize(16);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(5, 15, 5, 15);
        root.addView(statusText, matchWidth(65));

        TextView controlTitle = new TextView(this);
        controlTitle.setText("CAR CONTROL");
        controlTitle.setTextSize(21);
        controlTitle.setTypeface(null, Typeface.BOLD);
        controlTitle.setGravity(Gravity.CENTER);
        root.addView(controlTitle, matchWidth(55));

        Button forwardButton = createDirectionButton("FORWARD", 'F');
        Button leftButton = createDirectionButton("LEFT", 'L');
        Button stopButton = new Button(this);
        stopButton.setText("STOP");
        Button rightButton = createDirectionButton("RIGHT", 'R');
        Button backwardButton = createDirectionButton("BACKWARD", 'B');

        LinearLayout forwardRow = new LinearLayout(this);
        forwardRow.setGravity(Gravity.CENTER);
        forwardRow.addView(forwardButton, weightedWidth());
        root.addView(forwardRow, matchWidth(75));

        LinearLayout middleRow = new LinearLayout(this);
        middleRow.setGravity(Gravity.CENTER);
        middleRow.addView(leftButton, weightedWidth());
        middleRow.addView(stopButton, weightedWidth());
        middleRow.addView(rightButton, weightedWidth());
        root.addView(middleRow, matchWidth(75));

        LinearLayout backwardRow = new LinearLayout(this);
        backwardRow.setGravity(Gravity.CENTER);
        backwardRow.addView(backwardButton, weightedWidth());
        root.addView(backwardRow, matchWidth(75));

        stopButton.setOnClickListener(v -> sendCommand('S'));

        TextView instruction = new TextView(this);
        instruction.setText(
                "Hold a direction to move. Release to stop.\n" +
                "Motor speed is fixed in the Arduino program."
        );
        instruction.setTextSize(14);
        instruction.setGravity(Gravity.CENTER);
        instruction.setPadding(5, 15, 5, 5);
        root.addView(instruction, matchWidth(70));

        refreshButton.setOnClickListener(v -> refreshPairedDevices());
        connectButton.setOnClickListener(v -> connectSelected());
        disconnectButton.setOnClickListener(v -> disconnectBluetooth());

        setContentView(root);
    }

    private LinearLayout.LayoutParams matchWidth(int height) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height);
    }

    private LinearLayout.LayoutParams weightedWidth() {
        return new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
        );
    }

    private Button createDirectionButton(String label, char command) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(15);

        button.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                sendCommand(command);
                return true;
            }

            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                sendCommand('S');
                return true;
            }

            return true;
        });

        return button;
    }

    private boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void checkBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !hasConnectPermission()) {

            requestPermissions(
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_BLUETOOTH_CONNECT
            );
        } else {
            initializeBluetooth();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeBluetooth();
            } else {
                showStatus("Bluetooth permission denied");
                Toast.makeText(this,
                        "Allow Nearby devices permission",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initializeBluetooth() {
        if (bluetoothAdapter == null) {
            showStatus("Bluetooth is not supported on this phone");
            return;
        }

        try {
            if (!bluetoothAdapter.isEnabled()) {
                showStatus("Please turn ON Bluetooth");
                return;
            }

            refreshPairedDevices();

        } catch (SecurityException e) {
            showStatus("Bluetooth permission error: " + e.getMessage());
        }
    }

    private void refreshPairedDevices() {
        if (!hasConnectPermission()) {
            checkBluetoothPermission();
            return;
        }

        if (bluetoothAdapter == null) {
            showStatus("Bluetooth adapter not available");
            return;
        }

        try {
            Set<BluetoothDevice> bonded =
                    bluetoothAdapter.getBondedDevices();

            pairedDevices.clear();
            deviceNames.clear();

            if (bonded != null) {
                for (BluetoothDevice device : bonded) {
                    pairedDevices.add(device);

                    String name = device.getName();
                    if (name == null || name.trim().isEmpty()) {
                        name = "Unknown device";
                    }

                    deviceNames.add(name + " - " + device.getAddress());
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    deviceNames
            );

            adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);

            deviceSpinner.setAdapter(adapter);

            if (pairedDevices.isEmpty()) {
                showStatus("No paired devices. Pair HC-05 in phone settings.");
            } else {
                showStatus("Found " + pairedDevices.size() +
                        " paired device(s). Select your HC-05.");
            }

        } catch (SecurityException e) {
            showStatus("Permission error: " + e.getMessage());
        }
    }

    private void connectSelected() {
        if (!hasConnectPermission()) {
            checkBluetoothPermission();
            return;
        }

        if (bluetoothAdapter == null) {
            showStatus("Bluetooth adapter unavailable");
            return;
        }

        if (connected) {
            showStatus("Already connected");
            return;
        }

        if (connecting) {
            showStatus("Connection attempt already running");
            return;
        }

        int selectedPosition = deviceSpinner.getSelectedItemPosition();

        if (selectedPosition < 0 ||
                selectedPosition >= pairedDevices.size()) {
            showStatus("Select a paired HC-05 device first");
            return;
        }

        BluetoothDevice device = pairedDevices.get(selectedPosition);

        connecting = true;
        showStatus("Connecting to " + device.getName() + "...");

        bluetoothExecutor.execute(() -> {
            BluetoothSocket newSocket = null;
            Exception secureError = null;

            try {
                // First try the standard secure Bluetooth SPP connection.
                try {
                    newSocket =
                            device.createRfcommSocketToServiceRecord(SPP_UUID);
                    newSocket.connect();

                } catch (Exception e) {
                    secureError = e;

                    if (newSocket != null) {
                        try {
                            newSocket.close();
                        } catch (IOException ignored) {
                        }
                    }

                    // Fallback for HC-05 modules that require insecure SPP.
                    newSocket =
                            device.createInsecureRfcommSocketToServiceRecord(
                                    SPP_UUID);
                    newSocket.connect();
                }

                OutputStream stream = newSocket.getOutputStream();

                bluetoothSocket = newSocket;
                outputStream = stream;
                connected = true;
                connecting = false;

                runOnUiThread(() -> {
                    showStatus("CONNECTED to " + device.getName());
                    Toast.makeText(this,
                            "HC-05 Connected",
                            Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                connecting = false;
                connected = false;

                if (newSocket != null) {
                    try {
                        newSocket.close();
                    } catch (IOException ignored) {
                    }
                }

                final String error =
                        e.getClass().getSimpleName() + ": " +
                        String.valueOf(e.getMessage());

                runOnUiThread(() -> {
                    showStatus("FAILED: " + error);
                    Toast.makeText(this,
                            "Connection failed: " + error,
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void sendCommand(char command) {
        if (!connected || outputStream == null) {
            if (command == 'S') {
                return;
            }
            showStatus("Not connected. Connect to HC-05 first.");
            return;
        }

        bluetoothExecutor.execute(() -> {
            try {
                OutputStream stream = outputStream;

                if (stream != null && connected) {
                    stream.write((byte) command);
                    stream.flush();
                }

            } catch (IOException e) {
                connected = false;
                runOnUiThread(() ->
                        showStatus("Send failed: " + e.getMessage()));
            }
        });
    }

    private void disconnectBluetooth() {
        connected = false;
        connecting = false;

        bluetoothExecutor.execute(() -> {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
            } catch (IOException ignored) {
            }

            try {
                if (bluetoothSocket != null) {
                    bluetoothSocket.close();
                }
            } catch (IOException ignored) {
            }

            outputStream = null;
            bluetoothSocket = null;

            runOnUiThread(() -> {
                showStatus("Disconnected");
                Toast.makeText(this,
                        "Bluetooth Disconnected",
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void showStatus(String message) {
        if (statusText != null) {
            statusText.setText("Status: " + message);
        }
    }

    @Override
    protected void onDestroy() {
        connected = false;
        connecting = false;

        try {
            if (outputStream != null) {
                outputStream.close();
            }
        } catch (IOException ignored) {
        }

        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
        } catch (IOException ignored) {
        }

        bluetoothExecutor.shutdownNow();
        super.onDestroy();
    }
}
