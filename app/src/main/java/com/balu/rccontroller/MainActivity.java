package com.balu.rccontroller;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int REQUEST_CONNECT = 41;
    private static final UUID SPP_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private OutputStream output;
    private Spinner deviceSpinner;
    private TextView statusText;
    private ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private boolean connecting = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        buildScreen();
        checkPermissionAndLoad();
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(18), dp(14), dp(18), dp(18));
        return l;
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = vertical();
        root.setBackgroundColor(Color.rgb(242, 245, 249));

        TextView title = label("BALU RC CONTROLLER", 24, Color.rgb(20, 39, 65));
        title.setTypeface(null, Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(54)));

        TextView subtitle = label("4-WHEEL BLUETOOTH ROBOT  •  FIXED SPEED 160", 12,
                Color.rgb(75, 91, 110));
        root.addView(subtitle, new LinearLayout.LayoutParams(-1, dp(32)));

        deviceSpinner = new Spinner(this);
        root.addView(deviceSpinner, new LinearLayout.LayoutParams(-1, dp(52)));

        LinearLayout connectionRow = new LinearLayout(this);
        connectionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = makeButton("REFRESH", Color.rgb(75, 91, 110));
        Button connect = makeButton("CONNECT", Color.rgb(27, 117, 190));
        Button disconnect = makeButton("DISCONNECT", Color.rgb(190, 65, 65));
        connectionRow.addView(refresh, new LinearLayout.LayoutParams(0, dp(52), 1));
        connectionRow.addView(connect, new LinearLayout.LayoutParams(0, dp(52), 1));
        connectionRow.addView(disconnect, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(connectionRow);

        statusText = label("DISCONNECTED — select your paired HC-05", 14,
                Color.rgb(150, 55, 55));
        root.addView(statusText, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView speed = label("MOTOR SPEED 160 / 255", 18, Color.rgb(20, 39, 65));
        speed.setTypeface(null, Typeface.BOLD);
        root.addView(speed, new LinearLayout.LayoutParams(-1, dp(48)));

        root.addView(label("Press and hold a direction. Release to STOP.", 13,
                Color.rgb(75, 91, 110)), new LinearLayout.LayoutParams(-1, dp(35)));

        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setGravity(Gravity.CENTER);

        LinearLayout row1 = new LinearLayout(this);
        row1.setGravity(Gravity.CENTER);
        row1.addView(new View(this), new LinearLayout.LayoutParams(dp(100), dp(82)));
        row1.addView(directionButton("▲\nFORWARD", 'F'), new LinearLayout.LayoutParams(dp(112), dp(82)));
        row1.addView(new View(this), new LinearLayout.LayoutParams(dp(100), dp(82)));

        LinearLayout row2 = new LinearLayout(this);
        row2.setGravity(Gravity.CENTER);
        row2.addView(directionButton("◀\nLEFT", 'L'), new LinearLayout.LayoutParams(dp(100), dp(88)));
        row2.addView(stopButton(), new LinearLayout.LayoutParams(dp(112), dp(88)));
        row2.addView(directionButton("RIGHT\n▶", 'R'), new LinearLayout.LayoutParams(dp(100), dp(88)));

        LinearLayout row3 = new LinearLayout(this);
        row3.setGravity(Gravity.CENTER);
        row3.addView(new View(this), new LinearLayout.LayoutParams(dp(100), dp(82)));
        row3.addView(directionButton("▼\nBACKWARD", 'B'), new LinearLayout.LayoutParams(dp(112), dp(82)));
        row3.addView(new View(this), new LinearLayout.LayoutParams(dp(100), dp(82)));

        pad.addView(row1);
        pad.addView(row2);
        pad.addView(row3);
        root.addView(pad);

        TextView note = label("HC-05 Bluetooth Classic SPP • 9600 baud • Commands F/B/L/R/S",
                11, Color.rgb(85, 98, 115));
        root.addView(note, new LinearLayout.LayoutParams(-1, dp(52)));

        refresh.setOnClickListener(v -> checkPermissionAndLoad());
        connect.setOnClickListener(v -> connectSelected());
        disconnect.setOnClickListener(v -> disconnect());

        scroll.addView(root);
        setContentView(scroll);
    }

    private Button makeButton(String text, int color) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color));
        return b;
    }

    private Button directionButton(String text, char command) {
        Button b = makeButton(text, Color.rgb(35, 112, 190));
        b.setTextSize(16);
        b.setTypeface(null, Typeface.BOLD);
        b.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                send(command);
                return true;
            }
            if (event.getAction() == MotionEvent.ACTION_UP ||
                    event.getAction() == MotionEvent.ACTION_CANCEL) {
                send('S');
                return true;
            }
            return true;
        });
        return b;
    }

    private Button stopButton() {
        Button b = makeButton("■\nSTOP", Color.rgb(195, 54, 54));
        b.setTextSize(17);
        b.setTypeface(null, Typeface.BOLD);
        b.setOnClickListener(v -> send('S'));
        return b;
    }

    private boolean hasConnectPermission() {
        return Build.VERSION.SDK_INT < 31 ||
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        == PackageManager.PERMISSION_GRANTED;
    }

    private void checkPermissionAndLoad() {
        if (Build.VERSION.SDK_INT >= 31 && !hasConnectPermission()) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    REQUEST_CONNECT);
        } else {
            loadPairedDevices();
        }
    }

    private void loadPairedDevices() {
        if (bluetoothAdapter == null) {
            setStatus("This phone does not support Bluetooth", true);
            return;
        }
        if (!bluetoothAdapter.isEnabled()) {
            setStatus("Turn ON Bluetooth in Android settings", true);
            return;
        }
        try {
            devices.clear();
            ArrayList<String> names = new ArrayList<>();
            Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice d : bonded) {
                devices.add(d);
                String name = d.getName();
                names.add((name == null ? "Bluetooth device" : name) + "  •  " + d.getAddress());
            }
            if (names.isEmpty()) names.add("No paired devices — pair HC-05 in Android Settings first");
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, names);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            deviceSpinner.setAdapter(adapter);
            setStatus(devices.isEmpty() ? "No paired devices found" : "Select your paired HC-05",
                    devices.isEmpty());
        } catch (SecurityException e) {
            setStatus("Bluetooth permission not granted", true);
        }
    }


private void connectSelected() {

    if (!hasConnectPermission()) {
        checkPermissionAndLoad();
        return;
    }

    if (devices.isEmpty() ||
        deviceSpinner.getSelectedItemPosition() >= devices.size()) {

        Toast.makeText(this,
            "Pair HC-05 in Bluetooth settings first",
            Toast.LENGTH_LONG).show();
        return;
    }

    if (connecting) return;

    disconnect();

    BluetoothDevice device =
        devices.get(deviceSpinner.getSelectedItemPosition());

    connecting = true;
    setStatus("Connecting to HC-05...", false);

    new Thread(() -> {

        BluetoothSocket newSocket = null;
        Exception firstError = null;

        try {

            bluetoothAdapter.cancelDiscovery();

            // First try secure Bluetooth SPP
            try {
                newSocket =
                    device.createRfcommSocketToServiceRecord(SPP_UUID);

                newSocket.connect();

            } catch (Exception e) {

                firstError = e;

                if (newSocket != null) {
                    try {
                        newSocket.close();
                    } catch (Exception ignored) {}
                }

                // Fallback: insecure Bluetooth SPP
                newSocket =
                    device.createInsecureRfcommSocketToServiceRecord(
                        SPP_UUID
                    );

                newSocket.connect();
            }

            BluetoothSocket connectedSocket = newSocket;
            OutputStream stream =
                connectedSocket.getOutputStream();

            runOnUiThread(() -> {

                socket = connectedSocket;
                output = stream;
                connecting = false;

                setStatus("CONNECTED TO HC-05", false);

                Toast.makeText(this,
                    "Bluetooth connected successfully",
                    Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {

            if (newSocket != null) {
                try {
                    newSocket.close();
                } catch (Exception ignored) {}
            }

            String error = e.getClass().getSimpleName()
                    + ": " + e.getMessage();

            runOnUiThread(() -> {

                connecting = false;

                setStatus("FAILED: " + error, true);

                Toast.makeText(this,
                    error,
                    Toast.LENGTH_LONG).show();
            });
        }

    }).start();
}
    private void send(char command) {
        try {
            if (output != null && socket != null && socket.isConnected()) {
                output.write((byte) command);
                output.flush();
            } else {
                Toast.makeText(this, "Connect to HC-05 first", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            setStatus("Bluetooth send failed — reconnect", true);
            disconnect();
        }
    }

    private void disconnect() {
        try { if (output != null) output.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        output = null;
        socket = null;
        connecting = false;
        if (statusText != null) setStatus("DISCONNECTED", true);
    }

    private void setStatus(String message, boolean error) {
        if (statusText != null) {
            statusText.setText(message);
            statusText.setTextColor(error ? Color.rgb(175, 45, 45) : Color.rgb(20, 130, 75));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_CONNECT) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED)
                loadPairedDevices();
            else
                setStatus("Nearby devices permission is required", true);
        }
    }

    @Override
    protected void onDestroy() {
        disconnect();
        super.onDestroy();
    }
}
