package edu.drexel.lapcounter.bledistanceapp;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

public class ScanActivity extends AppCompatActivity {
    // Tag for logging
    public static final String TAG = ScanActivity.class.getSimpleName();

    // Unique IDs for requesting permissions
    private static final int REQUEST_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 2;

    // Scanning lasts 10 seconds
    private static final int SCAN_PERIOD = 10000;

    // Bluetooth adapter for sccanning for devices
    private BluetoothAdapter mBluetoothAdapter;

    // This manages the ListView
    private BLEDeviceListAdapter mDeviceListAdapter;

    // Reference to the ListView to store the devices.
    private ListView mDeviceList;

    // Handle scan schedule
    private boolean mScanning = false;
    private Handler mHandler = new Handler();
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String logMessage = String.format("Found Device %s %d", device.getName(), rssi);
                    Log.d(TAG, logMessage);

                    // Maybe add a device to the list
                    mDeviceListAdapter.addDevice(device, rssi);
                    mDeviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    // Callbacks for list items.
    private AdapterView.OnItemClickListener mOnItemClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            BluetoothDevice device = mDeviceListAdapter.getDevice(position);
            if (device == null)
                return;

            // Stop scanning if we are still scanning
            if (mScanning) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
                mScanning = false;
            }

            // Launch the distance estimation app
            launchDistanceActivity(device);

        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        getSupportActionBar().setTitle(R.string.title_devices);


        // Set the title of the activity
        if (getActionBar() != null)
            getActionBar().setTitle(R.string.title_devices);

        mDeviceList = findViewById(R.id.list_devices);

        bindListCallbacks();

        getLocationPermission();
        checkBLESupported();
        getBluetoothAdapter();
        checkBluetoothSupported();
    }

    @Override
    protected void onResume() {
        super.onResume();

        getBluetoothPermission();

        mDeviceListAdapter = new BLEDeviceListAdapter(getLayoutInflater(), this);
        mDeviceList.setAdapter(mDeviceListAdapter);

        scanBLEDevices(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        // User chose not to enable Bluetooth. Exit gracefully.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mDeviceListAdapter.clear();
                scanBLEDevices(true);
                break;
            case R.id.menu_stop_scan:
                scanBLEDevices(false);
                break;
        }
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanBLEDevices(false);
        mDeviceListAdapter.clear();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop_scan).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
        } else {
            menu.findItem(R.id.menu_stop_scan).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
        }
        return super.onCreateOptionsMenu(menu);
    }

    void bindListCallbacks() {
        mDeviceList.setOnItemClickListener(mOnItemClick);
    }

    void launchDistanceActivity(BluetoothDevice device) {
        Intent intent = new Intent(this, LapCountActivity.class);
        intent.putExtra(LapCountActivity.EXTRAS_DEVICE_NAME, device.getName());
        intent.putExtra(LapCountActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
        startActivity(intent);
    }

    /**
     * BLE requires at least course location permissions. Creepy.
     */
    private void getLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION);
        } else {
            Log.e(TAG, "No location, sorry");
        }
    }

    private void getBluetoothPermission() {
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }


    /**
     * Check if this phone has BLE. If not, stop the activity
     */
    private void checkBLESupported() {
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();

            // Quit if we don't have BLE
            finish();
        }
    }

    /**
     * Get a reference to the Bluetooth adapter
     */
    private void getBluetoothAdapter() {
        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    /**
     * Check if Bluetooth is supported
     */
    private void checkBluetoothSupported() {
        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /**
     * Enable/disable scanning for BLE devices
     * @param enable true to start the scan, false to stop the scan
     */
    private void scanBLEDevices(boolean enable) {
        if (enable) {
            // Stop scanning after a delay
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            // Start scanning
            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            // Stop scanning
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }

        invalidateOptionsMenu();
    }
}
