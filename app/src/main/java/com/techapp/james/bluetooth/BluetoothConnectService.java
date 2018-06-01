package com.techapp.james.bluetooth;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;

import timber.log.Timber;

public class BluetoothConnectService {
    private static final UUID BT_UUID = UUID.fromString("571e131a-6347-11e8-adc0-fa7ae01bbebc");
    private static String appName = "Bluetooth";
    private final BluetoothAdapter bluetoothAdapter;
    private static final UUID INSECURE_UUID =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    private Context context;
    private ConnectThread connectThread;
    private BluetoothDevice bluetoothDevice;
    private ProgressDialog progressDialog;
    private ConnectedThread connectedThread;
    private Handler handler;
    private AcceptThread acceptThread;

    public BluetoothConnectService(Context context, Handler handler) {
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.context = context;
        this.handler = handler;
        start();
    }


    private class ConnectThread extends Thread {
        private BluetoothSocket socket;

        public ConnectThread(BluetoothDevice device, UUID uuid) {
            Timber.d("ConnectThread started");
            bluetoothDevice = device;
        }

        public void run() {
            bluetoothAdapter.cancelDiscovery();
            BluetoothSocket tmp = null;
            try {
                tmp = bluetoothDevice.createRfcommSocketToServiceRecord(BT_UUID);

            } catch (IOException e) {
                Timber.d("ConnectThread " + e.getMessage());
            }
            socket = tmp;
            //connect to the BluetoothSocket
            try {
                if (!socket.isConnected()) {
                    socket.connect();
                }
            } catch (IOException e) {
                Timber.d("ConnectThread to UUID " + bluetoothDevice.getName());
                try {
                    Timber.d("ConnectThread  trying fallback...");

                    socket = (BluetoothSocket) bluetoothDevice.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(bluetoothDevice, 1);

                    socket.connect();

                    Timber.d("ConnectThread ConnectedBluetoothConnect");
                } catch (Exception e2) {
                    Timber.d("ConnectThread Couldn't establish Bluetooth connection!  " + e2.getMessage());
                    Timber.d("ConnectThread " + e2.getLocalizedMessage());
                    ((Activity) context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, "Connect Fail", Toast.LENGTH_LONG).show();
                        }
                    });
                }

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


    /*
     * acceptThreat starts and sits waiting for a connection.
     * ConnectThread starts and attempts to make a connection with other devices
     * */
    public void startClient(BluetoothDevice device, UUID uuid) {
        progressDialog = ProgressDialog.show(context, "Connecting Bluetooth", "Please Wait...", true);
        if (connectThread != null) {
            connectThread.cancel();
        }
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
                    Timber.d("message  " + incomingMessage);
                    Message msg = new Message();
                    Bundle bundle = new Bundle();
                    bundle.putString("text", incomingMessage);
                    msg.setData(bundle);
                    handler.sendMessage(msg);
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
                Timber.d("Error outputStream" + e.getMessage());
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
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
    }

    public void write(byte[] out) {

        connectedThread.write(out);
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {

        // The local server socket
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;

            // Create a new listening server socket
            try {

                //   tmp = bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, INSECURE_UUID);
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(appName, BT_UUID);

                Timber.d("AcceptThread: Setting up Server using: " + INSECURE_UUID);
            } catch (IOException e) {
                Timber.e("AcceptThread: IOException: " + e.getMessage());
            }

            serverSocket = tmp;
        }

        public void run() {
            Timber.d("run: AcceptThread Running.");

            BluetoothSocket socket = null;

            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                Timber.d("run: RFCOM server socket start.....");

                socket = serverSocket.accept();

                Timber.d("run: RFCOM server socket accepted connection.");

            } catch (IOException e) {
                Timber.e("AcceptThread: IOException: " + e.getMessage());
            }

            //talk about this is in the 3rd
            if (socket != null) {
                connected(socket);
            }

            Timber.i("END mAcceptThread ");
        }

        public void cancel() {
            Timber.d("cancel: Canceling AcceptThread.");
            try {
                serverSocket.close();
            } catch (IOException e) {
                Timber.e("cancel: Close of AcceptThread ServerSocket failed. " + e.getMessage());
            }
        }

    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    private synchronized void start() {
        Timber.d("start");

        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }
}
