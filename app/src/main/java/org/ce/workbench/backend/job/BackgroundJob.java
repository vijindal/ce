package org.ce.workbench.backend.job;

import org.ce.workbench.gui.model.SystemInfo;

/**
 * Interface for background jobs (cluster identification, CF identification, etc).
 * Jobs run asynchronously and report progress via listeners.
 */
public interface BackgroundJob extends Runnable {
    
    /**
     * Get unique job ID.
     */
    String getId();
    
    /**
     * Get job name for display.
     */
    String getName();
    
    /**
     * Get associated system.
     */
    SystemInfo getSystem();
    
    /**
     * Get current progress (0-100).
     */
    int getProgress();
    
    /**
     * Get status message.
     */
    String getStatusMessage();
    
    /**
     * Check if job is currently running.
     */
    boolean isRunning();
    
    /**
     * Check if job completed successfully.
     */
    boolean isCompleted();
    
    /**
     * Check if job failed.
     */
    boolean isFailed();
    
    /**
     * Get error message if job failed.
     */
    String getErrorMessage();
    
    /**
     * Pause the job (if supported).
     */
    void pause();
    
    /**
     * Resume the job (if paused).
     */
    void resume();
    
    /**
     * Cancel the job execution.
     */
    void cancel();
    
    /**
     * Add a listener for job progress updates.
     */
    void addListener(JobListener listener);
    
    /**
     * Remove a listener.
     */
    void removeListener(JobListener listener);
}
