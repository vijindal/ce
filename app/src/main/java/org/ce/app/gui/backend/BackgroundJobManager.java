package org.ce.app.gui.backend;

import org.ce.app.gui.backend.jobs.BackgroundJob;
import org.ce.app.gui.backend.jobs.JobListener;
import java.util.*;
import java.util.concurrent.*;

/**
 * Manager for background job execution.
 * Handles job queuing, concurrent execution, and progress tracking.
 */
public class BackgroundJobManager {
    
    private final ExecutorService executor;
    private final Queue<BackgroundJob> jobQueue;
    private final Map<String, BackgroundJob> activeJobs;
    private final ScheduledExecutorService scheduler;
    
    private final int maxConcurrentJobs;
    private int currentlyRunning = 0;
    
    private final List<JobManagerListener> managerListeners = new CopyOnWriteArrayList<>();
    
    public BackgroundJobManager(int maxConcurrentJobs) {
        this.maxConcurrentJobs = Math.max(1, maxConcurrentJobs);
        this.executor = Executors.newFixedThreadPool(maxConcurrentJobs);
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.jobQueue = new ConcurrentLinkedQueue<>();
        this.activeJobs = new ConcurrentHashMap<>();
        
        // Start job scheduling thread
        startJobScheduler();
    }
    
    /**
     * Submit a job to the queue.
     */
    public void submitJob(BackgroundJob job) {
        Objects.requireNonNull(job, "job");
        
        jobQueue.add(job);
        activeJobs.put(job.getId(), job);
        
        managerListeners.forEach(l -> l.onJobQueued(job.getId(), jobQueue.size()));
        
        // Try to start job if there's capacity
        tryStartNextJob();
    }
    
    /**
     * Get a job by ID.
     */
    public BackgroundJob getJob(String jobId) {
        return activeJobs.get(jobId);
    }
    
    /**
     * Get all active jobs (queued and running).
     */
    public Collection<BackgroundJob> getActiveJobs() {
        return new ArrayList<>(activeJobs.values());
    }
    
    /**
     * Get all queued (not yet running) jobs.
     */
    public List<BackgroundJob> getQueuedJobs() {
        return new ArrayList<>(jobQueue);
    }
    
    /**
     * Get queue size.
     */
    public int getQueueSize() {
        return jobQueue.size();
    }
    
    /**
     * Pause a job.
     */
    public void pauseJob(String jobId) {
        BackgroundJob job =activeJobs.get(jobId);
        if (job != null) {
            job.pause();
        }
    }
    
    /**
     * Resume a job.
     */
    public void resumeJob(String jobId) {
        BackgroundJob job = activeJobs.get(jobId);
        if (job != null) {
            job.resume();
        }
    }
    
    /**
     * Cancel a job.
     */
    public void cancelJob(String jobId) {
        BackgroundJob job = activeJobs.get(jobId);
        if (job != null) {
            jobQueue.remove(job);
            job.cancel();
            activeJobs.remove(jobId);
            
            managerListeners.forEach(l -> l.onJobCancelled(jobId));
            tryStartNextJob();
        }
    }
    
    /**
     * Cancel all jobs for a specific system.
     */
    public void cancelSystemJobs(String systemId) {
        List<String> jobsToCancel = new ArrayList<>();
        
        activeJobs.forEach((id, job) -> {
            if (job.getSystem().getId().equals(systemId)) {
                jobsToCancel.add(id);
            }
        });
        
        jobsToCancel.forEach(this::cancelJob);
    }
    
    /**
     * Add a manager listener.
     */
    public void addManagerListener(JobManagerListener listener) {
        if (!managerListeners.contains(listener)) {
            managerListeners.add(listener);
        }
    }
    
    /**
     * Remove a manager listener.
     */
    public void removeManagerListener(JobManagerListener listener) {
        managerListeners.remove(listener);
    }
    
    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        executor.shutdownNow();
        scheduler.shutdownNow();
    }
    
    // ===================== PRIVATE HELPERS =====================
    
    private void startJobScheduler() {
        scheduler.scheduleAtFixedRate(this::tryStartNextJob, 100, 100, TimeUnit.MILLISECONDS);
    }
    
    private synchronized void tryStartNextJob() {
        if (currentlyRunning >= maxConcurrentJobs) {
            return;
        }
        
        BackgroundJob nextJob = jobQueue.poll();
        if (nextJob == null) {
            return;
        }
        
        currentlyRunning++;
        
        executor.execute(() -> {
            try {
                nextJob.run();
            } catch (Exception e) {
                System.err.println("Job execution failed: " + e.getMessage());
                e.printStackTrace();
            } finally {
                currentlyRunning--;
                activeJobs.remove(nextJob.getId());
                managerListeners.forEach(l -> l.onJobFinished(nextJob.getId()));
                tryStartNextJob();
            }
        });
    }
    
    // ===================== LISTENER INTERFACE =====================
    
    /**
     * Listener for job manager events.
     */
    public interface JobManagerListener {
        void onJobQueued(String jobId, int queueSize);
        void onJobStarted(String jobId);
        void onJobFinished(String jobId);
        void onJobCancelled(String jobId);
    }
}
