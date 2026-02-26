package org.ce.app.gui.backend.jobs;

/**
 * Listener interface for background job progress and completion events.
 */
public interface JobListener {
    
    /**
     * Called when job progress changes.
     */
    void onProgressChanged(String jobId, int progress, String statusMessage);
    
    /**
     * Called when job completes successfully.
     */
    void onJobCompleted(String jobId);
    
    /**
     * Called when job fails.
     */
    void onJobFailed(String jobId, String errorMessage);
    
    /**
     * Called when job is cancelled.
     */
    void onJobCancelled(String jobId);
    
    /**
     * Called when job is paused.
     */
    void onJobPaused(String jobId);
    
    /**
     * Called when job is resumed.
     */
    void onJobResumed(String jobId);
}
