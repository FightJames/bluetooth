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
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.UUID;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements DeviceListAdapter.OnItemClick {
    private int REQUEST_CODE_ASK_PERMISSIONS = 2;
    private Button discoverBtn, discoverableBtn, onOffBtn, connectionBtn, sendBtn,chatRoomBtn;
    private EditText messageEditText;
    private RecyclerView deviceRecyclerView;
    private DeviceListAdapter deviceListAdapter;

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
        chatRoomBtn=(Button)findViewById(R.id.chatRoomBtn);
        discoverBtn = (Button) findViewById(R.id.discoverBtn);
        discoverableBtn = (Button) findViewById(R.id.discoverableBtn);
        //visibleBtn = (Button) findViewById(R.id.visibleBtn);
        onOffBtn = (Button) findViewById(R.id.onOffBtn);
        connectionBtn = (Button) findViewById(R.id.connectionBtn);
        sendBtn = (Button) findViewById(R.id.sendBtn);
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
                byte[] bytes = messageEditText.getText().toString().getBytes(Charset.defaultCharset());
                messageEditText.setText("");
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
        bluetoothConnect.startClient(mBTDevice, BT_UUID);
    }

    public void discover() {
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
        String[] permission = {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_COARSE_LOCATION};  //權限項目在此新增
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
        mBTDevice.createBond();
        bluetoothConnect = new BluetoothConnectService(this);
    }
}
