package edu.drexel.lapcounter.bledistanceapp;

public interface LapCounter {
    /**
     * Given the current distance value in meters,
     * update the state of the lap counter.
     * @param dist the most recent filtered distance in meters.
     * @return the updated lap count.
     */
    int updateCount(double dist);

    /**
     * Reset state, but not the lap count.
     */
    void onDisconnect();
}
