package edu.drexel.lapcounter.bledistanceapp;

public interface LowPassFilter {
    /**
     * Add a data point to the filter. This should update
     * the LPF's state and calculate the new filtered value.
     *
     * @param value the value to filter
     * @return a new filtered value
     */
    double filter(double value);

    /**
     * In the case of a disconnect or other case of bad data
     * clear the underlying filter state if it exists
     */
    void clear();
}
