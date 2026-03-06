package org.ce.application.service;

import org.ce.domain.mcs.event.MCSUpdate;

/**
 * Progress listener for calculation operations.
 * 
 * <p>Abstracts the progress reporting mechanism so both GUI and CLI
 * can consume calculation progress without coupling to specific UI classes.</p>
 * 
 * <p>Implementations should be thread-safe as progress updates may come
 * from background calculation threads.</p>
 */
public interface CalculationProgressListener {
    
    /**
     * Logs a message during calculation.
     * 
     * @param message the message to log
     */
    void logMessage(String message);
    
    /**
     * Sets the overall progress of the calculation.
     * 
     * @param progress value between 0.0 and 1.0
     */
    void setProgress(double progress);
    
    /**
     * Called to initialize MCS-specific UI components before simulation starts.
     * Default implementation does nothing.
     * 
     * @param equilibrationSteps number of equilibration steps
     * @param averagingSteps number of averaging steps
     * @param seed random seed
     */
    default void initializeMCS(int equilibrationSteps, int averagingSteps, long seed) {
        // Default: no-op for non-GUI listeners
    }
    
    /**
     * Called with periodic MCS updates during simulation.
     * Default implementation does nothing.
     * 
     * @param update the MCS state update
     */
    default void updateMCSData(MCSUpdate update) {
        // Default: no-op for non-GUI listeners
    }
}

