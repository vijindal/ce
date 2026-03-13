package org.ce.infrastructure.service;

import javafx.application.Platform;
import org.ce.application.job.CFIdentificationJob;
import org.ce.application.port.IdentificationProgressListener;
import org.ce.domain.identification.geometry.Vector3D;
import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.logging.LoggingConfig;
import org.ce.infrastructure.registry.KeyUtils;
import org.ce.infrastructure.registry.SystemRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Coordinator for the Type 1(a) cluster-data job lifecycle.
 *
 * <p>Previously, {@code SystemRegistryPanel} (~600 lines) directly resolved keys,
 * built {@link CFIdentificationJob}s, submitted them to {@link BackgroundJobManager},
 * and polled for completion on a raw background thread.  This coordinator owns all
 * of that orchestration.  The panel becomes a thin form + delegation layer.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>{@link #createSystem} — check cache, register system, build and submit job</li>
 *   <li>{@link #createClusterData} — pure cluster-key pipeline (no system registration)</li>
 *   <li>Track active jobs in {@code Map<jobId, CFIdentificationJob>}</li>
 *   <li>Deliver completion/failure to the UI via {@code Platform.runLater()} when
 *       {@link BackgroundJobManager.JobManagerListener#onJobFinished} fires —
 *       <strong>no polling thread</strong></li>
 * </ul>
 */
public class IdentificationCoordinator implements BackgroundJobManager.JobManagerListener {

    private static final Logger LOG = LoggingConfig.getLogger(IdentificationCoordinator.class);

    private final BackgroundJobManager jobManager;
    private final SystemRegistry registry;

    /** Active identification jobs, keyed by job ID. */
    private final Map<String, CFIdentificationJob> activeJobs = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // UI callbacks (wired by the panel after construction)
    // -----------------------------------------------------------------------

    /** Called (FX thread) when a job completes; receives the job and the clusterKey. */
    private BiConsumer<CFIdentificationJob, String> onJobCompleted;

    /** Called (FX thread) on failure; receives jobId and error message. */
    private BiConsumer<String, String> onJobFailed;

    /** Called (FX thread) for log lines from the pipeline. */
    private Consumer<String> logger;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /**
     * @param jobManager  shared job manager
     * @param registry    system registry for registering new systems and marking CFs computed
     */
    public IdentificationCoordinator(BackgroundJobManager jobManager, SystemRegistry registry) {
        this.jobManager = jobManager;
        this.registry   = registry;
        jobManager.addManagerListener(this);
    }

    // -----------------------------------------------------------------------
    // UI callback wiring
    // -----------------------------------------------------------------------

    /** Panel sets this to receive the completed job when the pipeline finishes. */
    public void setOnJobCompleted(BiConsumer<CFIdentificationJob, String> callback) {
        this.onJobCompleted = callback;
    }

    /** Panel sets this to receive error messages when the pipeline fails. */
    public void setOnJobFailed(BiConsumer<String, String> callback) {
        this.onJobFailed = callback;
    }

    /** Panel sets this to route log lines to the results/log panel. */
    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    // -----------------------------------------------------------------------
    // Create System (Type 1(a) — full path: register + identification)
    // -----------------------------------------------------------------------

    /**
     * Registers a new system in the registry and, if cluster data is absent from the
     * cache, submits a {@link CFIdentificationJob} through {@link BackgroundJobManager}.
     *
     * <p>All parameters are provided by the panel's form fields after input validation.</p>
     *
     * @param system                 fully built {@link SystemIdentity} to register
     * @param cecAvailable           whether CEC data was found at registration time
     * @param clusterKey             element-independent cache key (e.g. {@code "BCC_A2_T_bin"})
     * @param clusterDataAlreadyExists {@code true} if the cache already has data for clusterKey
     * @param disorderedClusterFile  path to the disordered-phase cluster file
     * @param orderedClusterFile     path to the ordered-phase cluster file
     * @param disorderedSymmetryGroup symmetry group name for the disordered phase
     * @param orderedSymmetryGroup   symmetry group name for the ordered phase
     * @param transformationMatrix   3×3 orientation matrix
     * @param translationVector      translation vector
     */
    public void createSystem(
            SystemIdentity system,
            boolean cecAvailable,
            String clusterKey,
            boolean clusterDataAlreadyExists,
            String disorderedClusterFile,
            String orderedClusterFile,
            String disorderedSymmetryGroup,
            String orderedSymmetryGroup,
            double[][] transformationMatrix,
            Vector3D translationVector) {

        LOG.info("IdentificationCoordinator.createSystem — system=" + system.getId()
                + " clusterKey=" + clusterKey + " cacheHit=" + clusterDataAlreadyExists);

        registry.registerSystem(system);
        registry.markCecAvailable(system.getId(), cecAvailable);
        registry.markClustersComputed(system.getId(), clusterDataAlreadyExists);
        registry.markCfsComputed(system.getId(), clusterDataAlreadyExists);

        log("  System registered: " + system.getId());

        if (!clusterDataAlreadyExists) {
            log("→ Starting identification pipeline  clusterKey=" + clusterKey);
            submitIdentificationJob(system, clusterKey, disorderedClusterFile, orderedClusterFile,
                    disorderedSymmetryGroup, orderedSymmetryGroup,
                    transformationMatrix, translationVector);
        } else {
            log("✓ Cluster cache hit (" + clusterKey + ") — no identification needed.");
        }
    }

    // -----------------------------------------------------------------------
    // Create Cluster Data (pure cluster-key pipeline, no system registration)
    // -----------------------------------------------------------------------

    /**
     * Submits a {@link CFIdentificationJob} for a cluster key without registering
     * a system (used by the <em>Create Cluster</em> button).
     *
     * @param tempSystem             temporary {@link SystemIdentity} used to name/identify the job
     * @param clusterKey             element-independent cache key
     * @param disorderedClusterFile  path to the disordered-phase cluster file
     * @param orderedClusterFile     path to the ordered-phase cluster file
     * @param disorderedSymmetryGroup symmetry group name for the disordered phase
     * @param orderedSymmetryGroup   symmetry group name for the ordered phase
     * @param transformationMatrix   3×3 orientation matrix
     * @param translationVector      translation vector
     * @param numComponents          number of components
     */
    public void createClusterData(
            SystemIdentity tempSystem,
            String clusterKey,
            String disorderedClusterFile,
            String orderedClusterFile,
            String disorderedSymmetryGroup,
            String orderedSymmetryGroup,
            double[][] transformationMatrix,
            Vector3D translationVector,
            int numComponents) {

        LOG.info("IdentificationCoordinator.createClusterData — clusterKey=" + clusterKey);
        log("→ Submitting cluster identification job...");

        submitIdentificationJob(tempSystem, clusterKey, disorderedClusterFile, orderedClusterFile,
                disorderedSymmetryGroup, orderedSymmetryGroup, transformationMatrix, translationVector);
    }

    // -----------------------------------------------------------------------
    // BackgroundJobManager.JobManagerListener
    // -----------------------------------------------------------------------

    @Override
    public void onJobQueued(String jobId, int queueSize) {
        if (activeJobs.containsKey(jobId)) {
            log(jobId + " — queued (queue size: " + queueSize + ")");
        }
    }

    @Override
    public void onJobStarted(String jobId) {
        if (activeJobs.containsKey(jobId)) {
            log(jobId + " — started");
        }
    }

    /**
     * Event-driven completion: called by the job manager when any job finishes.
     * Maps jobId back to the {@link CFIdentificationJob} and fires the UI callback
     * on the FX thread — no polling thread needed.
     */
    @Override
    public void onJobFinished(String jobId) {
        CFIdentificationJob job = activeJobs.remove(jobId);
        if (job == null) return;   // not an identification job managed by this coordinator

        LOG.fine("IdentificationCoordinator.onJobFinished — job=" + jobId);

        if (job.isCompleted() && !job.isFailed()) {
            registry.persistIfDirty();
            if (onJobCompleted != null) {
                Platform.runLater(() -> onJobCompleted.accept(job, job.getClusterKey()));
            }
        } else if (job.isFailed()) {
            String err = job.getErrorMessage() != null ? job.getErrorMessage() : "Unknown error";
            log("✗ Job failed: " + err);
            if (onJobFailed != null) {
                Platform.runLater(() -> onJobFailed.accept(jobId, err));
            }
        }
    }

    @Override
    public void onJobCancelled(String jobId) {
        activeJobs.remove(jobId);
        log(jobId + " — cancelled");
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void submitIdentificationJob(
            SystemIdentity system,
            String clusterKey,
            String disorderedClusterFile,
            String orderedClusterFile,
            String disorderedSymmetryGroup,
            String orderedSymmetryGroup,
            double[][] transformationMatrix,
            Vector3D translationVector) {

        IdentificationProgressListener progressListener = new IdentificationProgressListener() {
            @Override
            public void onStageStarted(int stage, String description) {
                log("  [Stage " + stage + "] " + description + "...");
            }

            @Override
            public void onStageCompleted(int stage, String summary) {
                log("  [Stage " + stage + "] ✓ " + summary);
            }

            @Override
            public void onPipelineCompleted(AllClusterData result) {
                log("  Pipeline complete: tcdis=" + result.getStage1().getTcdis()
                        + " tcf=" + result.getStage2().getTcf()
                        + " ncf=" + result.getStage2().getNcf());
            }

            @Override
            public void onPipelineFailed(String errorMessage) {
                log("  ✗ Pipeline failed: " + errorMessage);
            }

            @Override
            public void logMessage(String message) {
                log(message);
            }
        };

        CFIdentificationJob job = new CFIdentificationJob(
                system,
                registry,
                clusterKey,
                disorderedClusterFile,
                orderedClusterFile,
                disorderedSymmetryGroup,
                orderedSymmetryGroup,
                transformationMatrix,
                translationVector,
                system.getNumComponents(),
                progressListener
        );

        activeJobs.put(job.getId(), job);
        jobManager.submitJob(job);
        log("✓ Job submitted: " + job.getId());
    }

    private void log(String message) {
        if (logger != null) {
            Platform.runLater(() -> logger.accept(message));
        } else {
            LOG.fine(message);
        }
    }
}
