package edu.drexel.lapcounter.bledistanceapp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class DistanceActivity extends AppCompatActivity {
    // For logging
    private final static String TAG = DistanceActivity.class.getSimpleName();

    // Labels for data from the intent
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    // Update RSSI field every second
    public static final int RSSI_PERIOD = 500;

    // Name and MAC address of the selected Bluetooth device
    private String mDeviceName;
    private String mDeviceAddress;

    // Text fields to populate
    private TextView mViewState;
    private TextView mViewRssi;
    private TextView mViewName;
    private TextView mViewAddress;
    private TextView mViewDistance;
    private TextView mViewRssiFiltered;
    private TextView mViewLapCount;

    // Whether we are connected to the device
    private boolean mConnected = false;

    // Handler for requesting the RSSI from the BLE Service
    private Handler mHandler = new Handler();

    // The service for getting bluetooth updates
    private BLEService mBleService;

    // Objects for distance estimation
    private LowPassFilter mRssiFilter = new MovingAverage(10);
    // These coefficients are from the regression curve for Peter's phone vs Puck.js (Air to Air)
    private DistanceEstimator mDistEstimator = new LogarithmicModel(-64.1, -7.47);

    // Try our new sliding window lap counter. 12 ft threshold, sliding window size 5
    private LapCounter mLapCounter = new SlidingWindowCounter(3.0, 5);

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBleService = ((BLEService.LocalBinder) service).getService();
            if (!mBleService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBleService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBleService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BLEService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
                scheduleRssiRequest();
            } else if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BLEService.ACTION_RSSI_AVAILABLE.equals(action)) {
                int rssi = intent.getIntExtra(BLEService.EXTRA_RSSI, 0);
                mViewRssi.setText(String.format("%d dBm", rssi));
                updateDistance(rssi);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_distance);

        // Get the device info from the intent
        Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Get the text fields we can update
        mViewState = findViewById(R.id.device_state);
        mViewAddress = findViewById(R.id.device_address);
        mViewName = findViewById(R.id.device_name);
        mViewRssi = findViewById(R.id.device_rssi);
        mViewDistance = findViewById(R.id.device_dist);
        mViewRssiFiltered = findViewById(R.id.device_rssi_filtered);
        mViewLapCount = findViewById(R.id.lap_count);

        // Display the device name and address
        mViewName.setText(mDeviceName);
        mViewAddress.setText(mDeviceAddress);

        // Set the title bar and add a back button
        getSupportActionBar().setTitle(R.string.title_distance);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent gattServiceIntent = new Intent(this,BLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBleService != null) {
            final boolean result = mBleService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBleService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.device_connection, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBleService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBleService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void scheduleRssiRequest() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Stop if we are no longer connected
                if (!mConnected) {
                    Log.d(TAG, "Stop Reading RSSI");
                    return;
                }

                // Request an update of the RSSI.
                if (mBleService != null)
                    mBleService.requestRssi();

                // Schedule another RSSI request.
                scheduleRssiRequest();
            }
        }, RSSI_PERIOD);
    }

    private void updateDistance(int rssi) {
        // TODO: Determine what size is best for filtering out noise
        // while keeping update frequency high.
        double filteredRssi = mRssiFilter.filter(rssi);
        mViewRssiFiltered.setText(String.format("%.1f dBm", filteredRssi));

        double distEstimate = mDistEstimator.getDistance(filteredRssi);
        mViewDistance.setText(String.format("%.2f ft", distEstimate));

        int lapCount = mLapCounter.updateCount(distEstimate);
        mViewLapCount.setText(String.format("%d Laps", lapCount));
    }

    private void clearUI() {
        mViewRssi.setText(R.string.no_data);
        mViewDistance.setText(R.string.no_data);
        mViewRssiFiltered.setText(R.string.no_data);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mViewState.setText(resourceId);
            }
        });
    }

    // Subscribe to different BLE events from our custom service
    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_RSSI_AVAILABLE);
        return intentFilter;
    }
}
