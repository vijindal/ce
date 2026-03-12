package org.ce.application.job;

import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.logging.LoggingConfig;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Base abstract class for background jobs.
 * Provides common functionality like progress tracking, listener management, etc.
 */
public abstract class AbstractBackgroundJob implements BackgroundJob {

    private static final Logger LOG = LoggingConfig.getLogger(AbstractBackgroundJob.class);

    protected final String id;
    protected final String name;
    protected final SystemIdentity system;
    
    protected volatile int progress = 0;
    protected volatile String statusMessage = "Initializing...";
    protected volatile boolean running = false;
    protected volatile boolean completed = false;
    protected volatile boolean failed = false;
    protected volatile boolean cancelled = false;
    protected volatile boolean paused = false;
    
    protected String errorMessage;
    
    protected final List<JobListener> listeners = new CopyOnWriteArrayList<>();
    
    public AbstractBackgroundJob(String id, String name, SystemIdentity system) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.system = system;  // Nullable: jobs may load system dynamically in run()
    }
    
    @Override
    public String getId() { return id; }
    
    @Override
    public String getName() { return name; }
    
    @Override
    public SystemIdentity getSystem() { return system; }
    
    @Override
    public int getProgress() { return progress; }
    
    @Override
    public String getStatusMessage() { return statusMessage; }
    
    @Override
    public boolean isRunning() { return running && !paused; }
    
    @Override
    public boolean isCompleted() { return completed; }
    
    @Override
    public boolean isFailed() { return failed; }
    
    @Override
    public String getErrorMessage() { return errorMessage; }
    
    @Override
    public void pause() {
        if (running && !paused) {
            paused = true;
            LOG.fine("AbstractBackgroundJob.pause — job=" + id + " PAUSED");
            listeners.forEach(l -> l.onJobPaused(id));
        }
    }

    @Override
    public void resume() {
        if (running && paused) {
            paused = false;
            LOG.fine("AbstractBackgroundJob.resume — job=" + id + " RESUMED");
            listeners.forEach(l -> l.onJobResumed(id));
        }
    }

    @Override
    public void cancel() {
        if (running || !completed) {
            cancelled = true;
            running = false;
            LOG.info("AbstractBackgroundJob.cancel — job=" + id + " CANCELLED");
            listeners.forEach(l -> l.onJobCancelled(id));
        }
    }
    
    @Override
    public void addListener(JobListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    @Override
    public void removeListener(JobListener listener) {
        listeners.remove(listener);
    }
    
    protected void setProgress(int progress) {
        this.progress = Math.max(0, Math.min(100, progress));
    }
    
    protected void setStatusMessage(String message) {
        this.statusMessage = message;
        listeners.forEach(l -> l.onProgressChanged(id, progress, message));
    }
    
    protected void markCompleted() {
        running = false;
        completed = true;
        progress = 100;
        LOG.info("AbstractBackgroundJob.markCompleted — job=" + id + " COMPLETED");
        listeners.forEach(l -> l.onJobCompleted(id));
    }

    protected void markFailed(String error) {
        running = false;
        failed = true;
        errorMessage = error;
        LOG.warning("AbstractBackgroundJob.markFailed — job=" + id + " FAILED: " + error);
        listeners.forEach(l -> l.onJobFailed(id, error));
    }
    
    /**
     * Cooperative pause point for jobs to call at natural boundaries.
     * 
     * <p>Blocks the calling thread while the job is paused. Returns immediately
     * if the job is cancelled (allowing cancel to interrupt a paused job).
     * Jobs should call this at natural pause points such as:</p>
     * <ul>
     *   <li>After each major computation step</li>
     *   <li>At the start/end of loops</li>
     *   <li>In progress update callbacks</li>
     * </ul>
     * 
     * <p>This method is interruptible - if the thread is interrupted while
     * waiting, it will return and restore the interrupt status.</p>
     */
    protected void checkPausePoint() {
        while (paused && !cancelled) {
            try {
                Thread.sleep(100); // Check every 100ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
    
    /**
     * Checks if the job should stop (either cancelled or failed).
     * 
     * <p>Combines pause check with cancellation check. Jobs can use this
     * as a single check point:</p>
     * <pre>{@code
     * if (shouldStop()) return;
     * }</pre>
     * 
     * @return true if the job should stop execution
     */
    protected boolean shouldStop() {
        checkPausePoint();
        return cancelled || failed;
    }
}

