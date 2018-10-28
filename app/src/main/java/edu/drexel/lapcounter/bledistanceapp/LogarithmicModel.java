package edu.drexel.lapcounter.bledistanceapp;

/**
 * When we took distance measurements, we obtained regression curves of the form
 *
 * rssi = offset + scale * ln(dist)
 *
 * Taking the inverse, we have:
 *
 * exp((rssi - offset)/scale) = dist
 */
public class LogarithmicModel implements DistanceEstimator {

    private double mOffset;
    private double mScale;

    public LogarithmicModel(double offset, double scale) {
        mOffset = offset;
        mScale = scale;
    }

    @Override
    public double getDistance(double rssi) {
        double exponent = (rssi - mOffset) / mScale;
        return Math.exp(exponent);
    }
}
