package org.ce.app.gui.backend.jobs;

import org.ce.app.gui.models.SystemInfo;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Base abstract class for background jobs.
 * Provides common functionality like progress tracking, listener management, etc.
 */
public abstract class AbstractBackgroundJob implements BackgroundJob {
    
    protected final String id;
    protected final String name;
    protected final SystemInfo system;
    
    protected volatile int progress = 0;
    protected volatile String statusMessage = "Initializing...";
    protected volatile boolean running = false;
    protected volatile boolean completed = false;
    protected volatile boolean failed = false;
    protected volatile boolean cancelled = false;
    protected volatile boolean paused = false;
    
    protected String errorMessage;
    
    protected final List<JobListener> listeners = new CopyOnWriteArrayList<>();
    
    public AbstractBackgroundJob(String id, String name, SystemInfo system) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.system = Objects.requireNonNull(system, "system");
    }
    
    @Override
    public String getId() { return id; }
    
    @Override
    public String getName() { return name; }
    
    @Override
    public SystemInfo getSystem() { return system; }
    
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
            listeners.forEach(l -> l.onJobPaused(id));
        }
    }
    
    @Override
    public void resume() {
        if (running && paused) {
            paused = false;
            listeners.forEach(l -> l.onJobResumed(id));
        }
    }
    
    @Override
    public void cancel() {
        if (running || !completed) {
            cancelled = true;
            running = false;
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
        listeners.forEach(l -> l.onJobCompleted(id));
    }
    
    protected void markFailed(String error) {
        running = false;
        failed = true;
        errorMessage = error;
        listeners.forEach(l -> l.onJobFailed(id, error));
    }
}
