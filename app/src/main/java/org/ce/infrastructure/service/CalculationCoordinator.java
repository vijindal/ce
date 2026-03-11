package org.ce.infrastructure.service;

import org.ce.application.dto.CVMCalculationRequest;
import org.ce.application.dto.DataReadinessStatus;
import org.ce.application.dto.MCSCalculationRequest;
import org.ce.application.job.CVMPhaseModelJob;
import org.ce.application.job.MCSCalculationJob;
import org.ce.application.port.CalculationProgressListener;
import org.ce.application.port.DataManagementPort;
import org.ce.infrastructure.logging.LoggingConfig;
import org.ce.infrastructure.registry.KeyUtils;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.domain.system.SystemIdentity;

import java.util.logging.Logger;

/**
 * Orchestrates the submission of MCS and CVM calculation jobs.
 *
 * <p><strong>Role:</strong> This coordinator acts as a high-level job submission interface
 * between presentation controllers (GUI, CLI) and the background job execution layer.
 * It enforces data readiness preconditions before allowing job submission, providing
 * clear, actionable error messages when data is missing.</p>
 *
 * <p><strong>Architecture Position:</strong>
 * <ul>
 *   <li><strong>Above:</strong> Presentation controllers (GUI panels, CLI commands)</li>
 *   <li><strong>Below:</strong> BackgroundJobManager (job execution infrastructure)</li>
 *   <li><strong>Uses:</strong> DataManagementPort (readiness checks), BackgroundJobManager (submission)</li>
 * </ul>
 *
 * <p><strong>Key Design Principles:</strong>
 * <ul>
 *   <li><strong>Fail-Fast:</strong> Checks data availability before job submission</li>
 *   <li><strong>Clear Errors:</strong> Provides specific, actionable messages when data is missing</li>
 *   <li><strong>Type 1/2 Boundary:</strong> Jobs receive requests + data port, not pre-built contexts.
 *       All data loading happens on the background thread.</li>
 *   <li><strong>Listener Pattern:</strong> Jobs report progress via listeners without blocking the caller</li>
 * </ul>
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * DataManagementPort dataPort = new DataManagementAdapter(registry);
 * CalculationCoordinator coordinator = new CalculationCoordinator(
 *     registry, jobManager, dataPort);
 *
 * // Before submission, check readiness (optional, but recommended)
 * DataReadinessStatus status = coordinator.checkMCSReadiness(systemId);
 * if (!status.isReadyForMCS()) {
 *     System.err.println(status.message());
 *     return;
 * }
 *
 * // Submit job (will throw IllegalStateException if not ready)
 * MCSCalculationRequest request = MCSCalculationRequest.builder()
 *     .systemId(systemId)
 *     .temperature(1000.0)
 *     .compositionArray(new double[]{0.5, 0.5})
 *     .numComponents(2)
 *     .supercellSize(4)
 *     .equilibrationSteps(5000)
 *     .averagingSteps(10000)
 *     .build();
 * coordinator.submitMCS(request, listener);
 * }</pre>
 *
 * @see DataReadinessStatus
 * @see DataManagementPort
 * @see BackgroundJobManager
 * @since Phase 3
 */
public class CalculationCoordinator {

    private static final Logger LOG = LoggingConfig.getLogger(CalculationCoordinator.class);

    private final BackgroundJobManager jobManager;
    private final DataManagementPort dataPort;

    /**
     * Creates a new calculation coordinator.
     *
     * @param jobManager the job manager for submitting background jobs
     * @param dataPort the data management port for readiness checks
     */
    public CalculationCoordinator(BackgroundJobManager jobManager, DataManagementPort dataPort) {
        this.jobManager = jobManager;
        this.dataPort = dataPort;
    }

    /**
     * Checks if a system is ready for MCS calculations.
     *
     * <p>MCS requires cluster identification to be complete and CEC data to be available.</p>
     *
     * @param systemId the system ID to check
     * @return status object indicating readiness and detailed message
     */
    public DataReadinessStatus checkMCSReadiness(String systemId) {
        LOG.fine("CalculationCoordinator.checkMCSReadiness — ENTER: systemId=" + systemId);

        // Check system exists
        SystemIdentity system = dataPort.getSystem(systemId);
        if (system == null) {
            LOG.warning("CalculationCoordinator.checkMCSReadiness — system not found: " + systemId);
            return new DataReadinessStatus(false, false, false,
                "System '" + systemId + "' not found in registry.");
        }

        // Check clusters computed
        boolean clustersComputed = dataPort.isClustersComputed(systemId);
        if (!clustersComputed) {
            LOG.fine("CalculationCoordinator.checkMCSReadiness — clusters not computed for " + systemId);
            return DataReadinessStatus.missingClusters(systemId);
        }

        // Check cluster data available
        String clusterKey = KeyUtils.clusterKey(system);
        boolean clusterDataAvailable = dataPort.isClusterDataAvailable(clusterKey);
        if (!clusterDataAvailable) {
            LOG.warning("CalculationCoordinator.checkMCSReadiness — cluster cache missing for key=" + clusterKey);
            return new DataReadinessStatus(false, false, false,
                "Cluster cache missing for '" + clusterKey + "'. " +
                "Run the identification pipeline again.");
        }

        // Check CEC data available
        String cecKey = KeyUtils.cecKey(system);
        boolean cecAvailable = dataPort.isCecAvailable(cecKey);
        if (!cecAvailable) {
            LOG.fine("CalculationCoordinator.checkMCSReadiness — CEC not available for key=" + cecKey);
            return DataReadinessStatus.missingCEC(systemId);
        }

        LOG.fine("CalculationCoordinator.checkMCSReadiness — EXIT: system=" + systemId + " ready for MCS");
        return DataReadinessStatus.ready();
    }

    /**
     * Checks if a system is ready for CVM calculations.
     *
     * <p>CVM requires cluster identification, CF identification, and CEC data to all be complete.</p>
     *
     * @param systemId the system ID to check
     * @return status object indicating readiness and detailed message
     */
    public DataReadinessStatus checkCVMReadiness(String systemId) {
        LOG.fine("CalculationCoordinator.checkCVMReadiness — ENTER: systemId=" + systemId);

        // Check system exists
        SystemIdentity system = dataPort.getSystem(systemId);
        if (system == null) {
            LOG.warning("CalculationCoordinator.checkCVMReadiness — system not found: " + systemId);
            return new DataReadinessStatus(false, false, false,
                "System '" + systemId + "' not found in registry.");
        }

        // Check clusters computed
        boolean clustersComputed = dataPort.isClustersComputed(systemId);
        if (!clustersComputed) {
            LOG.fine("CalculationCoordinator.checkCVMReadiness — clusters not computed for " + systemId);
            return DataReadinessStatus.missingClusters(systemId);
        }

        // Check CFs computed
        boolean cfsComputed = dataPort.isCfsComputed(systemId);
        if (!cfsComputed) {
            LOG.fine("CalculationCoordinator.checkCVMReadiness — CFs not computed for " + systemId);
            return DataReadinessStatus.missingCFs(systemId);
        }

        // Check cluster data available
        String clusterKey = KeyUtils.clusterKey(system);
        boolean clusterDataAvailable = dataPort.isClusterDataAvailable(clusterKey);
        if (!clusterDataAvailable) {
            LOG.warning("CalculationCoordinator.checkCVMReadiness — cluster cache missing for key=" + clusterKey);
            return new DataReadinessStatus(false, false, true,
                "Cluster cache missing for '" + clusterKey + "'. " +
                "Run the identification pipeline again.");
        }

        // Check CEC data available
        String cecKey = KeyUtils.cecKey(system);
        boolean cecAvailable = dataPort.isCecAvailable(cecKey);
        if (!cecAvailable) {
            LOG.fine("CalculationCoordinator.checkCVMReadiness — CEC not available for key=" + cecKey);
            return new DataReadinessStatus(true, false, true,
                "CEC data not found for '" + cecKey + "'. " +
                "Use Data > CEC Database to add it.");
        }

        LOG.fine("CalculationCoordinator.checkCVMReadiness — EXIT: system=" + systemId + " ready for CVM");
        return DataReadinessStatus.ready();
    }

    /**
     * Submits an MCS calculation job after verifying readiness.
     *
     * <p>This method enforces that:
     * <ul>
     *   <li>The system exists in the registry</li>
     *   <li>Cluster identification has been completed</li>
     *   <li>Cluster data is available in cache</li>
     *   <li>CEC data is available in database</li>
     * </ul>
     *
     * @param request the MCS calculation request
     * @param listener progress listener for job updates
     * @throws IllegalStateException if any precondition is not met
     */
    public void submitMCS(MCSCalculationRequest request, CalculationProgressListener listener) {
        LOG.info("CalculationCoordinator.submitMCS — ENTER: systemId=" + request.getSystemId());

        DataReadinessStatus status = checkMCSReadiness(request.getSystemId());
        if (!status.isReadyForMCS()) {
            LOG.warning("CalculationCoordinator.submitMCS — precondition failed: " + status.message());
            throw new IllegalStateException(status.message());
        }

        // All preconditions met; submit job
        MCSCalculationJob job = new MCSCalculationJob(request, dataPort, listener);
        jobManager.submitJob(job);

        LOG.info("CalculationCoordinator.submitMCS — EXIT: job submitted, id=" + job.getId());
    }

    /**
     * Submits a CVM calculation job after verifying readiness.
     *
     * <p>This method enforces that:
     * <ul>
     *   <li>The system exists in the registry</li>
     *   <li>Both cluster and CF identification have been completed</li>
     *   <li>Cluster data is available in cache</li>
     *   <li>CEC data is available in database</li>
     * </ul>
     *
     * @param request the CVM calculation request
     * @param listener progress listener for job updates
     * @throws IllegalStateException if any precondition is not met
     */
    public void submitCVM(CVMCalculationRequest request, CalculationProgressListener listener) {
        LOG.info("CalculationCoordinator.submitCVM — ENTER: systemId=" + request.getSystemId());

        DataReadinessStatus status = checkCVMReadiness(request.getSystemId());
        if (!status.isReadyForCVM()) {
            LOG.warning("CalculationCoordinator.submitCVM — precondition failed: " + status.message());
            throw new IllegalStateException(status.message());
        }

        // All preconditions met; submit job
        CVMPhaseModelJob job = new CVMPhaseModelJob(request, dataPort, listener);
        jobManager.submitJob(job);

        LOG.info("CalculationCoordinator.submitCVM — EXIT: job submitted, id=" + job.getId());
    }
}
