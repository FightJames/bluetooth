package com.techapp.james.bluetooth;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

import timber.log.Timber;

public class BluetoothConnectService {
    private static final String appName = "BluetoothAPP";
    private static final UUID BT_UUID = UUID.fromString("571e131a-6347-11e8-adc0-fa7ae01bbebc");
    private final BluetoothAdapter bluetoothAdapter;
    Context context;
    private AcceptThread insecureAcceptThread;
    private ConnectThread connectThread;
    private BluetoothDevice bluetoothDevice;
    private UUID deviceUUID;
    private ProgressDialog progressDialog;
    private ConnectedThread connectedThread;

    public BluetoothConnectService(Context context) {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.context=context;
    }

    private class AcceptThread extends Thread {
        //local server socket
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            //listening server socket
            try {
                tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, BT_UUID);
                Timber.d("AcceptThread Setting up Server using: " + BT_UUID);
            } catch (IOException e) {
                Timber.d("AcceptThread IQException " + e.getMessage());
            }
            serverSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                Timber.d("AcceptThread IOException " + e.getMessage());
            }
            if (socket != null) {
                connected(socket);
            }
        }

        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Timber.d("Cancel " + e.getMessage());
            }
        }
    }

    private class ConnectThread extends Thread {
        private BluetoothSocket socket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            Timber.d("ConnectThread started");
            bluetoothDevice = device;
            deviceUUID = uuid;
        }

        public void run() {
            BluetoothSocket tmp = null;
            try {
                tmp = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(BT_UUID);
            } catch (IOException e) {
                Timber.d("ConnectThread " + e.getMessage());
            }
            socket = tmp;
            bluetoothAdapter.cancelDiscovery();
            //connect to the BluetoothSocket
            try {
                socket.connect();
            } catch (IOException e) {
                //close socket
                try {
                    socket.close();
                } catch (IOException el) {
                    Timber.d("ConnectThread can't close connect in socket " + e.getMessage());
                }
                Timber.d("ConnectThread Couldn't connect to UUID  " + BT_UUID);
            }
            connected(socket);
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
                Timber.d("Cancel close of socket in ConnectThread failed " + e.getMessage());
            }
        }
    }

    //start service
    public synchronized void start() {
        //cancel thread attemp to make a connection
        if (connectedThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (insecureAcceptThread == null) {
            insecureAcceptThread = new AcceptThread();
            insecureAcceptThread.start();
        }
    }

    /*
     * acceptThreat starts and sits waiting for a connection.
     * ConnectThread starts and attempts to make a connection with other devices
     * */
    public void startClient(BluetoothDevice device, UUID uuid) {
        progressDialog = ProgressDialog.show(context, "Connecting Bluetooth", "Please Wait...", true);
        connectThread = new ConnectThread(device, uuid);
        connectThread.start();
    }

    /**
     * ConnectedThread is responsible for maintaining Bluetooth Connection, sending data
     * ans receiving data
     **/
    private class ConnectedThread extends Thread {
        private final BluetoothSocket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public ConnectedThread(BluetoothSocket socket) {
            this.socket = socket;
            //dismiss progressDialog when it is connected
            if (progressDialog != null) {
                progressDialog.dismiss();
            }
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            byte[] buffer = new byte[1024]; //buffer store stream
            int bytes; //return from read()

            //keep listening to the inputStream until exception
            while (true) {
                //read from inputStream
                try {
                    bytes = inputStream.read(buffer);
                    String incomingMessage = new String(buffer, 0, bytes);
                } catch (IOException e) {
                    Timber.d("Error reading inputStream " + e.getMessage());
                    break;
                }
            }
        }

        //send data to remote device
        public void write(byte[] bytes) {
            String text = new String(bytes, Charset.defaultCharset());
            Timber.d("Write data " + text);
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                Timber.d("Error writing to outputStream " + e.getMessage());
            }
        }

        //shutdown the connection
        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {

            }
        }
    }

    private void connected(BluetoothSocket socket) {
        Timber.d("Connected Start");
        //manage connection and transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
    }

    public void write(byte[] out) {

        connectedThread.write(out);
    }
}
