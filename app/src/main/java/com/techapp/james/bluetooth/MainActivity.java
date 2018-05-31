package com.techapp.james.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.UUID;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements DeviceListAdapter.OnItemClick {
    private int REQUEST_CODE_ASK_PERMISSIONS = 2;
    private Button discoverBtn, discoverableBtn, onOffBtn, connectionBtn, sendBtn;
    private EditText messageEditText;
    private RecyclerView deviceRecyclerView, chatRecyclerView;
    private DeviceListAdapter deviceListAdapter;
    private ChatListAdapter chatListAdapter;
    private ArrayList<Message> chatData = new ArrayList<>();
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            if (chatRecyclerView.getVisibility() == View.INVISIBLE) {
                chatRecyclerView.setVisibility(View.VISIBLE);
                deviceRecyclerView.setVisibility(View.INVISIBLE);
            }
            Message message = new Message();
            message.isRight = false;
            message.content = msg.getData().getString("text");
            chatData.add(message);
            chatListAdapter.notifyDataSetChanged();
        }
    };

    private BluetoothConnectService bluetoothConnect;
    private BluetoothAdapter btAdapter;
    private static final UUID BT_UUID = UUID.fromString("571e131a-6347-11e8-adc0-fa7ae01bbebc");
    private BluetoothDevice mBTDevice;
    private ArrayList<BluetoothDevice> btDeviceList = new ArrayList<>();
    private BroadcastReceiver deviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                btDeviceList.add(device);
                Timber.d("onReceice: " + device.getName() + ": " + device.getAddress());
                deviceListAdapter.notifyDataSetChanged();
            }
        }
    };
    //Pairing status changes
    private BroadcastReceiver pairReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    mBTDevice = device;
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        deviceListAdapter = null;
        chatListAdapter = null;
        unregisterReceiver(deviceReceiver);
        unregisterReceiver(pairReceiver);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            applyRight();
        }
        init();
    }

    private void init() {
        messageEditText = (EditText) findViewById(R.id.messageEditText);
        discoverBtn = (Button) findViewById(R.id.discoverBtn);
        discoverableBtn = (Button) findViewById(R.id.discoverableBtn);
        onOffBtn = (Button) findViewById(R.id.onOffBtn);
        connectionBtn = (Button) findViewById(R.id.connectionBtn);
        sendBtn = (Button) findViewById(R.id.sendBtn);
        chatRecyclerView = (RecyclerView) findViewById(R.id.chatRecyclerView);
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatListAdapter = new ChatListAdapter(this, chatData);
        chatRecyclerView.setAdapter(chatListAdapter);
        deviceRecyclerView = (RecyclerView) findViewById(R.id.deviceRecyclerView);
        deviceRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        deviceListAdapter = new DeviceListAdapter(this, btDeviceList);
        deviceRecyclerView.setAdapter(deviceListAdapter);
//Broadcast
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(pairReceiver, filter);
        IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(deviceReceiver, discoverDevicesIntent);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        onOffBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                enableDisableBT();
            }
        });
        connectionBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                startConnection();
            }
        });
        discoverBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btDeviceList.clear();
                deviceListAdapter.notifyDataSetChanged();
                discover();
            }
        });
        discoverableBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
                startActivity(discoverableIntent);
            }
        });
        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = messageEditText.getText().toString();
                byte[] bytes = text.getBytes(Charset.defaultCharset());
                messageEditText.setText("");
                Message message = new Message();
                message.isRight = true;
                message.content = text;
                chatData.add(message);
                chatListAdapter.notifyDataSetChanged();
                bluetoothConnect.write(bytes);
            }
        });
    }

    public void enableDisableBT() {
        if (btAdapter == null) {
            Timber.d("No BT");
        }
        if (!btAdapter.isEnabled()) {
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBTIntent);
        } else {
            btAdapter.disable();
        }
    }

    public void startConnection() {
        chatRecyclerView.setVisibility(View.VISIBLE);
        deviceRecyclerView.setVisibility(View.INVISIBLE);
        chatData.clear();
        bluetoothConnect.startClient(mBTDevice, BT_UUID);

    }

    public void discover() {
        chatRecyclerView.setVisibility(View.INVISIBLE);
        deviceRecyclerView.setVisibility(View.VISIBLE);
        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
            btAdapter.startDiscovery();

        }
        if (!btAdapter.isDiscovering()) {
            btAdapter.startDiscovery();
        }
    }

    public void applyRight() {
        ArrayList<Integer> allPermission = new ArrayList<Integer>();
        String[] permission = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_COARSE_LOCATION
        ,Manifest.permission.ACCESS_NETWORK_STATE};  //權限項目在此新增
        for (int i = 0; i < permission.length; i++) {
            int eachPermission = checkSelfPermission(permission[i]); //查看有無權限
            allPermission.add(eachPermission);
        }
        for (int i = 0; i < allPermission.size(); i++) {
            if (allPermission.get(i) != PackageManager.PERMISSION_GRANTED) { //無權限則詢問
                // System.out.println(permission[i]);
                requestPermissions(permission,
                        REQUEST_CODE_ASK_PERMISSIONS);
                return;
            }
        }
    }

    @Override
    public void onItemClick(int position) {
        mBTDevice = btDeviceList.get(position);
        Timber.d("itemClickPass");
        Log.d("Main","itemPass");
        mBTDevice.createBond();
        bluetoothConnect = new BluetoothConnectService(this, handler);
     //   bluetoothConnect.start();
    }
}
