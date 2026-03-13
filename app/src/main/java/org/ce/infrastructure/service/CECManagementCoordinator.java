package org.ce.infrastructure.service;

import javafx.application.Platform;
import org.ce.application.job.AssemblyResult;
import org.ce.application.job.CECAssemblyJob;
import org.ce.application.port.CECOperationListener;
import org.ce.application.service.CECAssemblyService;
import org.ce.application.service.CECManagementService;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.logging.LoggingConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Coordinator for the Type 1(b) CEC assembly job lifecycle.
 *
 * <p>Closes the job-symmetry gap: assembly previously ran synchronously on the
 * JavaFX thread inside a button handler.  This coordinator submits
 * {@link CECAssemblyJob} through {@link BackgroundJobManager}, tracks active
 * jobs by ID, and delivers results back to the UI via
 * {@code Platform.runLater()} when the job finishes.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>{@link #startAssembly} — builds and submits the background job</li>
 *   <li>{@link #saveAssembledCEC} — calls {@link CECManagementService} after assembly completes</li>
 *   <li>Registered as {@link BackgroundJobManager.JobManagerListener} to receive
 *       {@code onJobFinished} and dispatch to the UI callback on the FX thread</li>
 * </ul>
 *
 * <p>{@code CECManagementPanel} delegates to this coordinator — it no longer
 * touches {@link org.ce.infrastructure.persistence.AllClusterDataCache},
 * {@link SystemDataLoader}, or workspace paths directly.</p>
 */
public class CECManagementCoordinator implements BackgroundJobManager.JobManagerListener {

    private static final Logger LOG = LoggingConfig.getLogger(CECManagementCoordinator.class);

    private final BackgroundJobManager jobManager;
    private final CECManagementService cecManagementService;

    /** Active assembly jobs keyed by job ID. */
    private final Map<String, CECAssemblyJob> activeAssemblyJobs = new ConcurrentHashMap<>();

    /**
     * UI-side callback invoked on the JavaFX thread when assembly completes successfully.
     * The panel sets this once at construction time.
     */
    private Consumer<AssemblyResult> onAssemblyCompleted;

    /**
     * UI-side callback invoked on the JavaFX thread when assembly fails.
     */
    private Consumer<String> onAssemblyFailed;

    // -----------------------------------------------------------------------
    // Construction
    // -----------------------------------------------------------------------

    /**
     * @param jobManager            shared job manager — coordinator registers itself as a listener
     * @param cecManagementService  service used for all CEC I/O
     */
    public CECManagementCoordinator(BackgroundJobManager jobManager,
                                    CECManagementService cecManagementService) {
        this.jobManager = jobManager;
        this.cecManagementService = cecManagementService;
        jobManager.addManagerListener(this);
    }

    // -----------------------------------------------------------------------
    // UI callback wiring
    // -----------------------------------------------------------------------

    /**
     * Registers the callback that will be called (on the FX thread) when an
     * assembly job completes successfully.
     *
     * @param callback receives the immutable {@link AssemblyResult}
     */
    public void setOnAssemblyCompleted(Consumer<AssemblyResult> callback) {
        this.onAssemblyCompleted = callback;
    }

    /**
     * Registers the callback that will be called (on the FX thread) when an
     * assembly job fails.
     *
     * @param callback receives a human-readable error message
     */
    public void setOnAssemblyFailed(Consumer<String> callback) {
        this.onAssemblyFailed = callback;
    }

    // -----------------------------------------------------------------------
    // Start assembly
    // -----------------------------------------------------------------------

    /**
     * Builds a {@link CECAssemblyJob} for the given target system and submits it
     * to the {@link BackgroundJobManager}.
     *
     * <p>Assembly runs entirely on the executor thread — the JavaFX thread is
     * not blocked.  The UI receives the result via the callbacks registered with
     * {@link #setOnAssemblyCompleted} and {@link #setOnAssemblyFailed}.</p>
     *
     * @param target the system whose CECs should be assembled
     */
    public void startAssembly(SystemIdentity target) {
        LOG.info("CECManagementCoordinator.startAssembly — target=" + target.getId());

        CECOperationListener listener = new CECOperationListener() {
            @Override
            public void onAssemblyCompleted(AssemblyResult result) {
                LOG.info("CECManagementCoordinator — assembly completed for " + target.getId());
                // result will also be delivered via onJobFinished; we fire the callback here
                // for immediate response (onJobFinished may arrive slightly later).
                if (onAssemblyCompleted != null) {
                    Platform.runLater(() -> onAssemblyCompleted.accept(result));
                }
            }

            @Override
            public void onAssemblyFailed(String errorMessage) {
                LOG.warning("CECManagementCoordinator — assembly failed for " + target.getId()
                        + ": " + errorMessage);
                if (onAssemblyFailed != null) {
                    Platform.runLater(() -> onAssemblyFailed.accept(errorMessage));
                }
            }
        };

        CECAssemblyJob job = new CECAssemblyJob(target, listener);
        activeAssemblyJobs.put(job.getId(), job);
        jobManager.submitJob(job);
        LOG.info("CECManagementCoordinator — submitted job=" + job.getId());
    }

    // -----------------------------------------------------------------------
    // Save assembled CEC
    // -----------------------------------------------------------------------

    /**
     * Builds a {@link SystemDataLoader.CECData} from an {@link AssemblyResult} and
     * user-supplied pure-K ECI values, then persists it via {@link CECManagementService}.
     *
     * <p>This method may be called from the FX thread (save-button handler) — all
     * I/O is delegated to {@code CECManagementService} which uses the pre-configured
     * {@link org.ce.infrastructure.registry.WorkspaceManager} root; no raw path
     * construction happens here.</p>
     *
     * @param result       the completed assembly result
     * @param pureKValues  user-entered ECI values for the pure-K correlation functions
     */
    public void saveAssembledCEC(AssemblyResult result, double[] pureKValues) {
        LOG.info("CECManagementCoordinator.saveAssembledCEC — system=" + result.targetSystem().getId());

        int K        = result.targetSystem().getNumComponents();
        int tcf      = result.targetData().getStage2().getTcf();
        int[] cfOrderMap = result.cfOrderMap();

        // Build a single-order "transformed" map from the derived ECIs so we can
        // pass them through CECAssemblyService.assemble() together with pureKValues.
        Map<Integer, double[]> transformedByOrder = new java.util.TreeMap<>();
        transformedByOrder.put(0, result.derivedECIs());  // order key 0 = "already accumulated"

        double[] finalECIs = CECAssemblyService.assemble(
                transformedByOrder, pureKValues, cfOrderMap, result.targetData());

        SystemDataLoader.CECData cecData = new SystemDataLoader.CECData();
        cecData.elements  = String.join("-", result.targetSystem().getComponents());
        cecData.structure = result.targetSystem().getStructure();
        cecData.phase     = result.targetSystem().getPhase();
        cecData.model     = result.targetSystem().getModel();
        cecData.cecValues = finalECIs;
        cecData.cecUnits  = "J/mol";
        cecData.reference = result.targetSystem().getId();
        cecData.tc        = tcf;

        cecManagementService.saveCEC(cecData);
        LOG.info("CECManagementCoordinator.saveAssembledCEC — saved for system="
                + result.targetSystem().getId() + " tcf=" + tcf);
    }

    // -----------------------------------------------------------------------
    // BackgroundJobManager.JobManagerListener
    // -----------------------------------------------------------------------

    @Override
    public void onJobQueued(String jobId, int queueSize) {
        // Not used by coordinator
    }

    @Override
    public void onJobStarted(String jobId) {
        // Not used by coordinator
    }

    /**
     * Called by {@link BackgroundJobManager} when any job finishes.
     * Ignores non-assembly jobs; for assembly jobs, retrieves the result
     * and removes the job from the active map.
     */
    @Override
    public void onJobFinished(String jobId) {
        CECAssemblyJob job = activeAssemblyJobs.remove(jobId);
        if (job == null) return;  // not an assembly job managed by this coordinator

        LOG.fine("CECManagementCoordinator.onJobFinished — job=" + jobId);

        AssemblyResult result = job.getResult();
        if (result != null && onAssemblyCompleted != null) {
            // The CECOperationListener.onAssemblyCompleted already fired; this is a
            // safety net in case the listener fired before the UI callback was wired.
            Platform.runLater(() -> onAssemblyCompleted.accept(result));
        } else if (result == null && job.isFailed() && onAssemblyFailed != null) {
            String err = job.getErrorMessage() != null ? job.getErrorMessage() : "Assembly failed";
            Platform.runLater(() -> onAssemblyFailed.accept(err));
        }
    }

    @Override
    public void onJobCancelled(String jobId) {
        activeAssemblyJobs.remove(jobId);
    }
}
