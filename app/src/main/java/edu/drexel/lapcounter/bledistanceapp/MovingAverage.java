package edu.drexel.lapcounter.bledistanceapp;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Compute a moving average of N values
 */
public class MovingAverage implements LowPassFilter {
    // the last N values seen
    private Deque<Double> mValues = new ArrayDeque<>();
    // The size of the buffer
    private int mMaxSize;

    /**
     * Create the averaging element with a given size N
     * @param maxSize Specify how many elements to store at a time.
     */
    public MovingAverage(int maxSize) {
        mMaxSize = maxSize;
    }

    private void addValue(double value) {
        mValues.addLast(value);

        // If the queue has filled up,
        // throw out the oldest value
        if (mValues.size() > mMaxSize)
            mValues.pollFirst();
    }

    /**
     * Average points in memory. If there are no points,
     * return 0 instead. Note that this will not be entirely accurate
     * until there are N points in memory.
     * @return the current average of the most recent points.
     */
    private double computeAverage() {
        // If we have no values, just return to avoid dividing by 0.
        if (mValues.size() == 0)
            return 0.0;

        // Sum up the current values
        double sum = 0.0;
        for (double x : mValues)
            sum += x;

        // return the average
        return sum / mValues.size();
    }

    @Override
    public double filter(double value) {
        addValue(value);
        return computeAverage();
    }
}
