package edu.drexel.lapcounter.bledistanceapp;

/* When a device disconnects abruptly, the BLE service will still emit RSSI events with
   a constant value for 10 to 20 seconds. This class keeps track of these consecutive RSSI values,
   so the consumer of the class can more quickly detect a disconnect. */
class DisconnectChecker {
    private static final int NUM_CONSECUTIVE_RSSI_TO_DISCONNECT = 5;

    private int mPreviousRssi = 0;
    private int mNumConsecutiveRssi = 0;

    boolean shouldDisconnect(int rssi) {
        if (rssi == mPreviousRssi) {
            ++mNumConsecutiveRssi;
        } else {
            mPreviousRssi = rssi;
            mNumConsecutiveRssi = 1;
        }

        return mNumConsecutiveRssi >= NUM_CONSECUTIVE_RSSI_TO_DISCONNECT;
    }

    void reset() {
        mPreviousRssi = 0;
        mNumConsecutiveRssi = 0;
    }
}
