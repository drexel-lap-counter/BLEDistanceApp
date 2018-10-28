package edu.drexel.lapcounter.bledistanceapp;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

public class DistanceActivity extends AppCompatActivity {
    // Labels for data from the intent
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    // Name and MAC address of the selected Bluetooth device
    private String mDeviceName;
    private String mDeviceAddress;

    // Text fields to populate
    private TextView mViewState;
    private TextView mViewRssi;
    private TextView mViewName;
    private TextView mViewAddress;

    // Whether we are connected to the device
    private boolean mConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_distance);

        // Get the device info from the intent
        Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Get the four text fields we can update
        mViewState = findViewById(R.id.device_state);
        mViewAddress = findViewById(R.id.device_address);
        mViewName = findViewById(R.id.device_name);
        mViewRssi = findViewById(R.id.device_rssi);

        // Display the device name and address
        mViewName.setText(mDeviceName);
        mViewAddress.setText(mDeviceAddress);

        // Set the title bar and add a back button
        getSupportActionBar().setTitle(R.string.title_distance);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // TODO: Start BLE Service

    }

    @Override
    protected void onResume() {
        super.onResume();

        // TODO: Register receiver
        // TODO: Connect bluetooth servicce
    }

    @Override
    protected void onPause() {
        super.onPause();
        //TODO:  Unregister receiver
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // TODO:: Unbind service
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
                // TODO: Connect BLE service
                return true;
            case R.id.menu_disconnect:
                // TODO: Disconnect BLE service
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
