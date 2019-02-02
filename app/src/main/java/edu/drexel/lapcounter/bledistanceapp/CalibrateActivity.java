package edu.drexel.lapcounter.bledistanceapp;

import android.annotation.SuppressLint;
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
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class CalibrateActivity extends AppCompatActivity {
    private final static String TAG = CalibrateActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "CalibrateActivity.DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "CalibrateActivity.DEVICE_ADDRESS";
    public static final String EXTRAS_CALIBRATED_THRESHOLD = "CalibrateActivity.CALIBRATED_THRESHOLD";

    private final static int RSSI_FREQ_MS = 250;
    private final static int PRINT_COLLECTOR_STATS_FREQ_MS = 5000;

    private TextView mCalibrateInfo;
    private Button mCalibrate;
    private Button mDone;

    private BLEService mBleService;
    private String mDeviceAddress;

    private Handler mHandler = new Handler();

    private final RssiCollector mRssiCollector = new RssiCollector();
    private long mLastPrintMillis;


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


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calibrate);

        mCalibrateInfo = findViewById(R.id.calibrate_info);
        mCalibrate = findViewById(R.id.calibrate);
        mDone = findViewById(R.id.done);

        Intent intent = getIntent();
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        Intent gattServiceIntent = new Intent(this, BLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BLEService.ACTION_GATT_CONNECTED.equals(action)) {
                onConnect();
            } else if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                onDisconnect();
            } else if (BLEService.ACTION_RSSI_AVAILABLE.equals(action)) {
                int rssi = intent.getIntExtra(BLEService.EXTRA_RSSI, 0);

                if (rssi != 0) {
                    onRssi(rssi);
                }
            }
        }
    };

    private void onConnect() {
        mCalibrateInfo.setText(R.string.label_calibrate_instructions);
        mCalibrate.setText(R.string.label_calibrate);
        mCalibrate.setEnabled(true);
    }

    private void onDisconnect() {
        mRssiCollector.disable();
        mRssiCollector.clear();
        mCalibrateInfo.setText(R.string.label_device_disconnected_try_reconnect);
        mBleService.connect(mDeviceAddress);
        mCalibrate.setEnabled(false);
        mDone.setEnabled(false);
    }

    private void onRssi(int rssi) {
        if (mRssiCollector.isEnabled()) {
            mRssiCollector.collect(rssi);

            long currentMillis = System.currentTimeMillis();
            long timeSinceLastPrint = currentMillis - mLastPrintMillis;

            if (timeSinceLastPrint >= PRINT_COLLECTOR_STATS_FREQ_MS) {
                mCalibrateInfo.setText(mRssiCollector.toString());
                mLastPrintMillis = currentMillis;
            }
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLEService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BLEService.ACTION_RSSI_AVAILABLE);
        return intentFilter;
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
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBleService = null;
    }

    private void scheduleRssiRequest() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mBleService != null) {
                    if (mBleService.getConnectionState() != BLEService.STATE_CONNECTED ||
                        !mRssiCollector.isEnabled()) {
                        return;
                    }

                    log_thread("scheduleRssiRequest()");
                    mBleService.requestRssi();
                }

                // Schedule another RSSI request.
                scheduleRssiRequest();
            }
        }, RSSI_FREQ_MS);
    }

    public void calibrate(View view) {
        if (mRssiCollector.isEnabled()) {
            mRssiCollector.disable();
            mDone.setEnabled(true);
            mCalibrate.setText(R.string.label_calibrate);
            mCalibrateInfo.setText(mRssiCollector.toString());
            return;
        }

        mDone.setEnabled(false);
        mRssiCollector.enable();
        scheduleRssiRequest();
        mCalibrate.setText(getString(R.string.label_stop));
    }

    public void done(View view) {
        double threshold = mRssiCollector.median();
        Intent result = getIntent();
        result.putExtra(CalibrateActivity.EXTRAS_CALIBRATED_THRESHOLD, threshold);
        setResult(RESULT_OK, result);
        finish();
    }

    @SuppressLint("DefaultLocale")
    private void log_thread(String format, Object... args) {
        String s = String.format(format, args);
        s = String.format("[Thread %d] %s", Thread.currentThread().getId(), s);
        Log.d(TAG, s);
    }
}
