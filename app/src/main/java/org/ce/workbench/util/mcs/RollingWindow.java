package org.ce.workbench.util.mcs;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Efficient rolling window statistics calculator.
 * Tracks standard deviation and mean of the last N values.
 * Used for computing σ(ΔE) and mean(ΔE) for convergence analysis.
 */
public class RollingWindow {
    
    private final Deque<Double> window;
    private final int maxSize;
    private double sum = 0.0;
    private double sumSquares = 0.0;
    
    /**
     * Create a rolling window with specified capacity.
     * @param maxSize maximum number of values to retain
     */
    public RollingWindow(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.maxSize = maxSize;
        this.window = new ArrayDeque<>();
    }
    
    /**
     * Add a value to the window, removing oldest if needed.
     * @param value the value to add
     */
    public void add(double value) {
        // Remove oldest if at capacity
        if (window.size() >= maxSize) {
            double removed = window.removeFirst();
            sum -= removed;
            sumSquares -= removed * removed;
        }
        
        // Add new value
        window.addLast(value);
        sum += value;
        sumSquares += value * value;
    }
    
    /**
     * Get the mean (average) of values in the window.
     * @return mean value
     */
    public double getMean() {
        if (window.isEmpty()) return 0.0;
        return sum / window.size();
    }
    
    /**
     * Get the standard deviation of values in the window.
     * σ(ΔE) is the key convergence metric.
     * 
     * Small σ(ΔE) = system converged (narrow energy fluctuations)
     * Large σ(ΔE) = system far from equilibrium (wide fluctuations)
     * 
     * @return standard deviation
     */
    public double getStdDev() {
        if (window.size() < 2) return 0.0;
        
        double mean = getMean();
        double variance = (sumSquares / window.size()) - (mean * mean);
        
        // Clamp to prevent numerical errors
        if (variance < 0) variance = 0;
        
        return Math.sqrt(variance);
    }
    
    /**
     * Get the current size of the window.
     * @return number of values currently in window
     */
    public int size() {
        return window.size();
    }
    
    /**
     * Get the maximum capacity of the window.
     * @return maximum size
     */
    public int getMaxSize() {
        return maxSize;
    }
    
    /**
     * Check if window is at capacity.
     * @return true if size == maxSize
     */
    public boolean isFull() {
        return window.size() >= maxSize;
    }
    
    /**
     * Clear all values from the window.
     */
    public void clear() {
        window.clear();
        sum = 0.0;
        sumSquares = 0.0;
    }
    
    /**
     * Get minimum value in the window.
     * @return minimum value, or Double.NaN if window is empty
     */
    public double getMin() {
        if (window.isEmpty()) return Double.NaN;
        return window.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
    }
    
    /**
     * Get maximum value in the window.
     * @return maximum value, or Double.NaN if window is empty
     */
    public double getMax() {
        if (window.isEmpty()) return Double.NaN;
        return window.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
    }
    
    @Override
    public String toString() {
        return String.format(
            "RollingWindow{size=%d, mean=%.6f, stdev=%.6f, min=%.6f, max=%.6f}",
            window.size(), getMean(), getStdDev(), getMin(), getMax());
    }
}
