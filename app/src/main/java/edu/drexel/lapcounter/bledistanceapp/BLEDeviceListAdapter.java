package edu.drexel.lapcounter.bledistanceapp;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * This class keeps track of BLE devices and creates the list of views for displaying them
 */
public class BLEDeviceListAdapter extends BaseAdapter {

    // In order to call get
    private Context mContext;
    private ArrayList<BluetoothDevice> mDevices;
    private ArrayList<Integer> mRssi;
    private LayoutInflater mInflater;

    public BLEDeviceListAdapter(LayoutInflater inflater, Context context) {
        super();
        mDevices = new ArrayList<>();
        mRssi = new ArrayList<>();
        mInflater = inflater;
        mContext = context;
    }

    public void addDevice(BluetoothDevice device, int rssi) {
        if (!mDevices.contains(device)) {
            mDevices.add(device);
            mRssi.add(rssi);
        }
    }

    public BluetoothDevice getDevice(int position) {
        return mDevices.get(position);
    }

    public void clear() {
        mDevices.clear();
        mRssi.clear();
    }

    @Override
    public int getCount() {
        return mDevices.size();
    }

    @Override
    public Object getItem(int position) {
        return mDevices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder viewHolder;
        View view = convertView;

        // Get a ViewHolder that contains the TextViews to populate
        if (view == null) {
            view = mInflater.inflate(R.layout.listitem_device, null);
            viewHolder = new ViewHolder();
            viewHolder.deviceAddress = view.findViewById(R.id.device_address);
            viewHolder.deviceName = view.findViewById(R.id.device_name);
            viewHolder.deviceRssi = view.findViewById(R.id.device_rssi);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        // Get info about this device
        BluetoothDevice device = mDevices.get(position);
        int rssi = mRssi.get(position);
        String name = device.getName();
        String address = device.getAddress();

        // convert null -> "Unknown Device"
        if (name == null)
            name = mContext.getString(R.string.unknown_device);

        // Display the values in the view
        viewHolder.deviceName.setText(name);
        viewHolder.deviceAddress.setText(address);
        viewHolder.deviceRssi.setText(String.format("%d", rssi));

        return view;
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceRssi;
    }
}
