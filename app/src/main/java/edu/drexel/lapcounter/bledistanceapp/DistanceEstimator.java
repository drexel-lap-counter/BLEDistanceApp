package edu.drexel.lapcounter.bledistanceapp;

public interface DistanceEstimator {
    /**
     * Estimate the distance from phone to Bluetooth device
     * @param rssi the Received Signal Strength Indicator (RSSI). Use a LowPassFilter first :)
     * @return an estimated distance in meters
     */
    double getDistance(double rssi);
}
