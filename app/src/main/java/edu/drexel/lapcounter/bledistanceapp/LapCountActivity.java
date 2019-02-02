package edu.drexel.lapcounter.bledistanceapp;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

public class LapCountActivity extends AppCompatActivity {
    // For logging
    private final static String TAG = LapCountActivity.class.getSimpleName();

    // How often to poll for RSSI and update its TextView.
    public static final int RSSI_PERIOD_NORMAL = 500;
    public static final int RSSI_PERIOD_FAST = 250;

    // How often a reconnect should be attempted.
    public static final int RECONNECT_PERIOD = 1000;

    // Name and MAC address of the selected Bluetooth device
    private String mDeviceName;
    private String mDeviceAddress;

    // Text fields to populate
    private TextView mViewState;
    private TextView mViewRssi;
    private TextView mViewName;
    private TextView mViewAddress;
    private TextView mViewRssiFiltered;
    private TextView mViewLapCount;
    private TextView mViewThreshold;
    private TextView mSlidingWindowState;

    // Whether we are connected to the device
    private boolean mConnected = false;

    // Whether we disconnected by pressing the "Disconnect" menu item.
    private boolean mManuallyDisconnected = false;

    // Handler for requesting the RSSI from the BLE Service
    private Handler mHandler = new Handler();

    // The service for getting bluetooth updates
    private BLEService mBleService;

    // Filter for RSSI values since they are noisy
    private MovingAverage mRssiFilter = new MovingAverage(10);

    private static final double DEFAULT_THRESHOLD = 60;
    private static final int DEFAULT_WINDOW_SIZE = 3;

    private double mThreshold;

    private int mConnectionCount = 0;

    // Try our new sliding window lap counter. x ft threshold, sliding window size n
    private SlidingWindowCounter mLapCounter;

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
                mConnectionCount++;

                if (mConnectionCount > 1) {
                    String s = String.format("%s (%d)", getString(R.string.reconnected),
                                             mConnectionCount);
                    updateConnectionState(s);
                    log_thread("Reconnected.");
                } else {
                    updateConnectionState(R.string.connected);
                }

                invalidateOptionsMenu();
                scheduleRssiRequest();
            } else if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.d(TAG, "Received a disconnect event.");
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
                mRssiFilter.clear();
                mLapCounter.onDisconnect();


                if (mManuallyDisconnected) {
                    // So this disconnect event corresponds to us manually disconnecting.
                    // Reset this flag so we can track future manual disconnects.
                    mManuallyDisconnected = false;
                    Log.d(TAG, "Cleared flag for manual disconnect.");
                } else {
                    // Otherwise, this disconnect event corresponds to something else, e.g.,
                    // going out of range.
                    // Let's try to reconnect.
                    log_thread("Scheduling a reconnect.");
                    scheduleReconnect();
                }
            } else if (BLEService.ACTION_RSSI_AVAILABLE.equals(action)) {
                int rssi = intent.getIntExtra(BLEService.EXTRA_RSSI, 0);

                mViewRssi.setText(String.format("%d dBm", rssi));

                // Filter out null RSSI values
                if (rssi != 0)
                    updateLapCount(rssi);
            }
        }
    };

    private void log_thread(String format, Object... args) {
        String s = String.format(format, args);
        s = String.format("[Thread %d] %s", Thread.currentThread().getId(), s);
        Log.d(TAG, s);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lap_count);

        // Get the device info from the intent
        Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(CalibrateActivity.EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(CalibrateActivity.EXTRAS_DEVICE_ADDRESS);
        mThreshold = intent.getDoubleExtra(CalibrateActivity.EXTRAS_CALIBRATED_THRESHOLD,
                                           DEFAULT_THRESHOLD);

        mLapCounter = new SlidingWindowCounter(mThreshold, DEFAULT_WINDOW_SIZE);

        // Get the text fields we can update
        mViewState = findViewById(R.id.device_state);
        mViewAddress = findViewById(R.id.device_address);
        mViewName = findViewById(R.id.device_name);
        mViewRssi = findViewById(R.id.device_rssi);
        mViewRssiFiltered = findViewById(R.id.device_rssi_filtered);
        mViewLapCount = findViewById(R.id.lap_count);
        mViewThreshold = findViewById(R.id.threshold);
        mSlidingWindowState = findViewById(R.id.sliding_window_state);

        // Display the device name and address
        mViewName.setText(mDeviceName);
        mViewAddress.setText(mDeviceAddress);
        mViewThreshold.setText(Double.toString(mThreshold));

        // Set the title bar and add a back button
        getSupportActionBar().setTitle(R.string.title_lap_count);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent gattServiceIntent = new Intent(this, BLEService.class);
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
                mManuallyDisconnected = true;
                Log.d(TAG, "onOptionsItemSelected() - The user manually disconnected.");
                mBleService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void updateThreshold(View view){
        EditText thresholdEditor = findViewById(R.id.edit_threshold);
        Editable t = thresholdEditor.getText();

        if (TextUtils.isEmpty(t)) {
            return;
        }

        this.mThreshold = Double.parseDouble(t.toString());
        mLapCounter = new SlidingWindowCounter(mThreshold, DEFAULT_WINDOW_SIZE);

        thresholdEditor.setText("");
        mViewThreshold.setText(Double.toString(mThreshold));
    }

    private void scheduleRssiRequest() {
        int period = RSSI_PERIOD_NORMAL;
        if (mLapCounter.getState() == SlidingWindowCounter.State.UNKNOWN) {
            period = RSSI_PERIOD_FAST;
            log_thread("scheduleRssiRequest() - set RSSI period to fast (%d) because sliding " +
                       "window state is unknown.", RSSI_PERIOD_FAST);
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Stop if we are no longer connected
                if (!mConnected) {
                    Log.d(TAG, "mConnected is false. I won't be scheduling another RSSI " +
                               "request for now.");
                    return;
                }

                // Request an update of the RSSI.
                if (mBleService != null)
                    mBleService.requestRssi();

                // Schedule another RSSI request.
                scheduleRssiRequest();
            }
        }, period);
    }

    private void scheduleReconnect() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mConnected) {
                    Log.w(TAG, "scheduleReconnect() called when mConnected is true. _Probably_ " +
                               "harmless, as a connect event could have fired between calls to " +
                               "scheduleReconnect().");
                    return;
                }

                if (mBleService.connect(mDeviceAddress)) {
                    return;
                }

                Log.w(TAG, "scheduleReconnect() - Connection attempt failed. Scheduling another " +
                           "reconnect.");
                scheduleReconnect();
            }
        }, RECONNECT_PERIOD);
    }

    private void updateLapCount(int rssi) {
        double filteredRssi = mRssiFilter.filter(rssi);
        mViewRssiFiltered.setText(String.format("%.1f dBm", filteredRssi));

        // Note: I am taking the absolute value of the RSSI so I do not have to change
        // the logic of the underlying lap counter
        int lapCount = mLapCounter.updateCount(Math.abs(filteredRssi));
        mViewLapCount.setText(String.format("%d Laps", lapCount));

        boolean windowsFull = mLapCounter.windowIsFull() && mRssiFilter.windowIsFull();
        if (mLapCounter.getState() == SlidingWindowCounter.State.UNKNOWN && windowsFull) {
            log_thread("updateLapCount() - Windows are full. SlidingWindowCounter should now " +
                       "pick a state.");
            boolean isReconnect = mConnectionCount > 1;
            mLapCounter.pickZone(isReconnect);
        }

        mSlidingWindowState.setText(mLapCounter.getState().toString());
    }

    private void clearUI() {
        mViewRssi.setText(R.string.no_data);
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

    private void updateConnectionState(final String state) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mViewState.setText(state);
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
