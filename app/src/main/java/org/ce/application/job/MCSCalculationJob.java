package org.ce.application.job;

import org.ce.application.dto.MCSCalculationRequest;
import org.ce.application.port.DataManagementPort;
import org.ce.application.port.MCSRunnerPort;
import org.ce.application.usecase.MCSCalculationUseCase;
import org.ce.domain.model.result.CalculationFailure;
import org.ce.domain.model.result.CalculationResult;
import org.ce.domain.model.result.EquilibriumState;
import org.ce.application.port.CalculationProgressListener;
import org.ce.application.dto.MCSCalculationContext;
import org.ce.infrastructure.logging.LoggingConfig;
import org.ce.infrastructure.mcs.MCSRunnerAdapter;
import org.ce.infrastructure.service.MCSProgressListenerAdapter;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background job for Monte Carlo Simulation (MCS) calculations.
 *
 * <p><strong>Type 2 Job — Thermodynamic Calculation</strong></p>
 *
 * This job encapsulates the full MCS pipeline including data loading:
 * <ol>
 *   <li>Load cluster identification data from cache (via base class)</li>
 *   <li>Load CEC/ECI from database (via base class)</li>
 *   <li>Build MCSCalculationContext</li>
 *   <li>Run MCS simulation via {@link MCSCalculationUseCase}</li>
 * </ol>
 *
 * <p>All data loading happens on the background thread (NOT UI thread)
 * via {@link DataManagementPort}, ensuring clean separation of Type 1 (data)
 * from Type 2 (calculation) concerns.</p>
 *
 * <p>Supports cancellation and pause at MC sweep boundaries.</p>
 */
public class MCSCalculationJob extends AbstractThermodynamicJob {

    private static final Logger LOG = LoggingConfig.getLogger(MCSCalculationJob.class);

    private final MCSCalculationRequest request;
    private final CalculationProgressListener externalListener;
    private CalculationResult result;
    private MCSCalculationContext context;

    /**
     * Creates a new MCS calculation job.
     *
     * <p>The job defers all data loading until {@link #run()} is called
     * (on the background thread). The request is a pure value object with
     * no disk I/O.</p>
     *
     * @param request The MCS calculation request (systemId, T, x, L, steps, etc.)
     * @param dataPort Port for loading cluster data and CEC from disk
     * @param externalListener Optional listener for progress updates (e.g., GUI)
     */
    public MCSCalculationJob(
            MCSCalculationRequest request,
            DataManagementPort dataPort,
            CalculationProgressListener externalListener) {
        super(
            "mcs-" + request.getSystemId() + "-" + UUID.randomUUID(),
            "MCS: " + request.getSystemId() +
                " (T=" + request.getTemperature() + "K)",
            dataPort
        );
        this.request = request;
        this.externalListener = externalListener;
    }

    @Override
    public void run() {
        LOG.info("MCSCalculationJob.run — ENTER: systemId=" + request.getSystemId()
                + ", T=" + request.getTemperature() + "K");
        if (shouldStop()) return;

        try {
            running = true;

            // ========== PHASES 1+2: Load System, Cluster Data, ECI ==========
            ThermodynamicJobData jobData = loadSystemData(request);
            if (jobData == null) return; // markFailed already called

            // ========== PHASE 3: Build Context ==========
            setStatusMessage("Building MCS context...");
            setProgress(25);
            if (shouldStop()) return;

            double[] composition = request.getCompositionArray() != null
                    ? request.getCompositionArray()
                    : new double[] { request.getComposition(), 1.0 - request.getComposition() };

            context = new MCSCalculationContext(
                jobData.system(),
                request.getTemperature(),
                composition,
                jobData.system().getNumComponents(),
                request.getSupercellSize(),
                request.getEquilibrationSteps(),
                request.getAveragingSteps(),
                request.getSeed()
            );
            context.setAllClusterData(jobData.clusterData());
            context.setClusterData(jobData.clusterData().getStage1().getDisClusterData());
            context.setECI(jobData.ncfEci());

            if (!context.isReady()) {
                markFailed("MCS context validation failed: " + context.getReadinessError());
                return;
            }

            // ========== PHASE 4: Run MCS Simulation ==========
            setStatusMessage("Starting Monte Carlo simulation...");
            setProgress(30);
            if (shouldStop()) return;

            MCSProgressListenerAdapter progressPort = new MCSProgressListenerAdapter(externalListener);
            MCSRunnerPort runnerPort = new MCSRunnerAdapter();
            MCSCalculationUseCase useCase = new MCSCalculationUseCase(progressPort, runnerPort);

            result = useCase.execute(context, () -> cancelled);

            if (shouldStop()) return;

            // ========== PHASE 5: Handle Result ==========
            if (result instanceof EquilibriumState) {
                setProgress(100);
                setStatusMessage("MCS calculation completed");
                LOG.info("MCSCalculationJob.run — EXIT: COMPLETED");
                markCompleted();
            } else if (result instanceof CalculationFailure failure) {
                LOG.warning("MCSCalculationJob.run — EXIT: FAILED — " + failure.errorMessage());
                markFailed(failure.errorMessage());
            } else {
                LOG.warning("MCSCalculationJob.run — EXIT: FAILED — unexpected result type");
                markFailed("MCS calculation returned unexpected result type");
            }

        } catch (CancellationException ex) {
            LOG.info("MCSCalculationJob.run — EXIT: CANCELLED");
            setStatusMessage("Calculation cancelled");
            // Don't call markFailed; cancel() handles state

        } catch (Exception e) {
            LOG.log(Level.WARNING, "MCSCalculationJob.run — EXCEPTION: " + e.getMessage(), e);
            markFailed("MCS calculation failed: " + e.getMessage());

        } finally {
            running = false;
        }
    }

    /**
     * Returns the calculation result.
     */
    public CalculationResult getResult() {
        return result;
    }

    /**
     * Returns the MCS context (only available after run() completes).
     */
    public MCSCalculationContext getContext() {
        return context;
    }

    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }
}
