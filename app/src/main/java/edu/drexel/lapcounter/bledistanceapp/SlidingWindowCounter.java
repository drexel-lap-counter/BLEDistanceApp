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

    public static final int LAP_COUNT_INCREMENT = 2;


    /**
     * Swimmer is either near or far.
     */
    public enum State {
        NEAR,
        FAR,
        UNKNOWN,
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
    private State mState = State.UNKNOWN;
    private State mDisconnectState = State.UNKNOWN;

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

    @Override
    public void onDisconnect() {
        mDeltaWindow.clear();
        mDisconnectState = mState;
        mState = State.UNKNOWN;
        log_thread("onDisconnect() - Cleared delta window. State on disconnect was %s. State is " +
                   "now unknown.", mDisconnectState);
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

    boolean windowIsFull() {
        return mDeltaWindow.size() == mWindowSize;
    }

    void updateState() {
        // If the sliding window is not yet full,
        // don't do anything
        if (!windowIsFull())
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

            // Using swimming terminology, out and back is 2 laps, not 1
            mLapCount += LAP_COUNT_INCREMENT;

            Log.d(TAG, "Far -> Near");
        }
    }

    State getState() {
        return mState;
    }

    public void pickZone(boolean isReconnect) {
        log_thread("pickZone(%b) - Previous state == %s, mPrevDist == %.2f, mThreshold == %.2f",
                   isReconnect, mState, mPrevDist, mThreshold);

        if (mPrevDist < mThreshold)
            mState = State.NEAR;
        else
            mState = State.FAR;

        log_thread("pickZone(%b) - mDisconnectState == %s, New state == %s", isReconnect,
                   mDisconnectState, mState);

        if (isReconnect && mDisconnectState == State.FAR && mState == State.NEAR) {
            mLapCount += LAP_COUNT_INCREMENT;
            log_thread("pickZone() - increased lap count to %d on reconnect.", mLapCount);
        }
    }

    private void log_thread(String format, Object... args) {
        String s = String.format(format, args);
        s = String.format("[Thread %d] %s", Thread.currentThread().getId(), s);
        Log.d(TAG, s);
    }

}
