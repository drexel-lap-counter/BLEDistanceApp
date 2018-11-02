package edu.drexel.lapcounter.bledistanceapp;

import android.util.Log;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Lap counter that uses a sliding window of
 * distance deltas along with a distance threshold to
 * determine when the swimmer returns into range.
 */
public class SlidingWindowCounter implements LapCounter {

    // Tag for debugging
    public static final String TAG = SlidingWindowCounter.class.getSimpleName();

    /**
     * Swimmer is either near or far.
     */
    public enum State {
        NEAR,
        FAR
    }

    /**
     * The current lap count.
     */
    private int mLapCount = 0;
    /**
     * The distance above which the swimmer is no longer "near" the phone.
     */
    private double mThreshold;
    /**
     * Previous distance value. This is
     * needed for calculating the current delta and
     * for comparing to the threshold.
     */
    private double mPrevDist = 0.0;

    /**
     * Sliding window of deltas between
     * previous distance value and the current one.
     */
    private Deque<Double> mDeltaWindow = new ArrayDeque<>();

    /**
     * Size of the sliding window
     */
    private int mWindowSize;

    /**
     * Current state of the swimmer (near/far)
     */
    private State mState = State.NEAR;

    public SlidingWindowCounter(double threshold, int windowSize) {
        mThreshold = threshold;
        mWindowSize = windowSize;
    }

    @Override
    public int updateCount(double dist) {
        updateWindow(dist);
        updateState();
        return mLapCount;
    }

    /**
     * Add a new value to the sliding window
     * @param dist the new distance to add.
     */
    void updateWindow(double dist) {
        // Add a new delta to the window
        double delta = dist - mPrevDist;
        mDeltaWindow.addLast(delta);
        mPrevDist = dist;

        //Trim the sliding window to size
        if (mDeltaWindow.size() > mWindowSize)
            mDeltaWindow.pollFirst();
    }

    /**
     * Sum up the sliding window of deltas and look at the sign.
     * + means outwards direction (away from phone)
     * - means inwards direction (towards phone)
     * @return the direction, either +1, -1 or 0
     */
    int getDirection() {
        double sum = 0.0;
        for (double dx : mDeltaWindow) {
            sum += dx;
        }
        return (int)Math.signum(sum);
    }

    void updateState() {
        // If the sliding window is not yet full,
        // don't do anything
        if (mDeltaWindow.size() < mWindowSize)
            return;

        // Determine if the swimmer is moving inwards or outwards
        int direction = getDirection();

        Log.d(TAG, String.format("%s: %.2f %.2f %d", mState.toString(), mPrevDist, mThreshold, direction));


        if (mState == State.NEAR && mPrevDist > mThreshold && direction == 1) {
            // If we cross the threshold in the outward direction while currently near,
            // we are now FAR away.
            mState = State.FAR;
            Log.d(TAG, "Near -> FAR");
        } else if (mState == State.FAR && mPrevDist <= mThreshold && direction == -1) {
            // If we cross cross the threshold in the other direction while currently far,
            // we are now near and have completed a lap.
            mState = State.NEAR;
            mLapCount++;
            Log.d(TAG, "Far -> Near");
        }
    }
}
