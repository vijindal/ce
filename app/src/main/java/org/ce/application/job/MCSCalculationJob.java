package org.ce.application.job;

import org.ce.application.dto.MCSCalculationRequest;
import org.ce.application.port.DataManagementPort;
import org.ce.application.port.MCSRunnerPort;
import org.ce.application.usecase.MCSCalculationUseCase;
import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.model.result.CalculationFailure;
import org.ce.domain.model.result.CalculationResult;
import org.ce.domain.model.result.MCSResult;
import org.ce.application.port.CalculationProgressListener;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.context.MCSCalculationContext;
import org.ce.infrastructure.data.ECIMapper;
import org.ce.infrastructure.logging.LoggingConfig;
import org.ce.infrastructure.mcs.MCSRunnerAdapter;
import org.ce.infrastructure.registry.KeyUtils;
import org.ce.infrastructure.service.MCSProgressListenerAdapter;

import java.util.Optional;
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
 *   <li>Load cluster identification data from cache</li>
 *   <li>Load CEC/ECI from database</li>
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
public class MCSCalculationJob extends AbstractBackgroundJob {

    private static final Logger LOG = LoggingConfig.getLogger(MCSCalculationJob.class);

    private final MCSCalculationRequest request;
    private final DataManagementPort dataPort;
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
            null // System will be loaded in run()
        );
        this.request = request;
        this.dataPort = dataPort;
        this.externalListener = externalListener;
    }

    @Override
    public void run() {
        LOG.info("MCSCalculationJob.run — ENTER: systemId=" + request.getSystemId()
                + ", T=" + request.getTemperature() + "K");
        if (shouldStop()) return;

        try {
            running = true;

            // ========== PHASE 1: Load System & Cluster Data ==========
            setStatusMessage("Loading system metadata...");
            setProgress(5);
            if (shouldStop()) return;

            SystemIdentity system = dataPort.getSystem(request.getSystemId());
            if (system == null) {
                markFailed("System not found: " + request.getSystemId());
                return;
            }

            setStatusMessage("Loading cluster data...");
            setProgress(10);
            if (shouldStop()) return;

            String clusterKey = KeyUtils.clusterKey(system);
            Optional<AllClusterData> allDataOpt = dataPort.loadClusterData(clusterKey);
            if (allDataOpt.isEmpty()) {
                markFailed("Cluster data not found for key: " + clusterKey);
                return;
            }
            AllClusterData allData = allDataOpt.get();

            // ========== PHASE 2: Load CEC/ECI ==========
            setStatusMessage("Loading CEC/ECI database...");
            setProgress(20);
            if (shouldStop()) return;

            String cecKey = KeyUtils.cecKey(system);

            Optional<double[]> nciEciOpt = dataPort.loadECI(
                String.join("-", system.getComponents()),
                system.getStructure(),
                system.getPhase(),
                system.getModel(),
                request.getTemperature(),
                allData.getStage2().getNcf()  // Require ncf-length
            );
            if (nciEciOpt.isEmpty()) {
                markFailed("CEC not found for key: " + cecKey
                    + ". Use Data > CEC Database to add it.");
                return;
            }

            // Expand from ncf to tc length (zero-pad point/empty clusters)
            double[] tcEci = ECIMapper.expandECIForMCS(nciEciOpt.get(), allData.getStage1().getTc());

            // ========== PHASE 3: Build Context ==========
            setStatusMessage("Building MCS context...");
            setProgress(25);
            if (shouldStop()) return;

            double[] composition = request.getCompositionArray() != null
                    ? request.getCompositionArray()
                    : new double[] { request.getComposition(), 1.0 - request.getComposition() };

            context = new MCSCalculationContext(
                system,
                request.getTemperature(),
                composition,
                system.getNumComponents(),
                request.getSupercellSize(),
                request.getEquilibrationSteps(),
                request.getAveragingSteps(),
                request.getSeed()
            );
            context.setAllClusterData(allData);
            context.setClusterData(allData.getStage1().getDisClusterData());
            context.setECI(tcEci);

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
            if (result instanceof MCSResult) {
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

