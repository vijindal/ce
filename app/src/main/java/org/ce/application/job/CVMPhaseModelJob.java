package org.ce.application.job;

import org.ce.application.dto.CVMCalculationRequest;
import org.ce.application.port.CalculationProgressListener;
import org.ce.application.port.DataManagementPort;
import org.ce.domain.cvm.CVMPhaseModel;
import org.ce.domain.cvm.CVMModelInput;
import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.cvm.CVMPhaseModelExecutor;
import org.ce.infrastructure.data.ECIMapper;
import org.ce.infrastructure.logging.LoggingConfig;
import org.ce.infrastructure.registry.KeyUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background job for CVM Phase Model calculations.
 *
 * <p><strong>Type 2 Job — Thermodynamic Calculation</strong></p>
 *
 * This job encapsulates the full CVM pipeline including data loading:
 * <ol>
 *   <li>Load cluster identification data from cache</li>
 *   <li>Load CEC/ECI from database</li>
 *   <li>Create CVMPhaseModel (first Newton-Raphson minimization)</li>
 *   <li>Initialize model and report completion</li>
 * </ol>
 *
 * <p>All data loading and the first N-R minimization happen on the background
 * thread (NOT UI thread) via {@link DataManagementPort}. This ensures clean
 * separation of Type 1 (data) from Type 2 (calculation) concerns.</p>
 *
 * <p>After completion, the model is ready for parameter scanning and external
 * queries via the GUI.</p>
 */
public class CVMPhaseModelJob extends AbstractBackgroundJob {

    private static final Logger LOG = LoggingConfig.getLogger(CVMPhaseModelJob.class);

    private final CVMCalculationRequest request;
    private final DataManagementPort dataPort;
    private final CalculationProgressListener externalListener;
    private CVMPhaseModel model;

    /**
     * Creates a new CVM Phase Model job.
     *
     * <p>The job defers all data loading and model creation until {@link #run()}
     * is called (on the background thread). The request is a pure value object
     * with no disk I/O.</p>
     *
     * @param request The CVM calculation request (systemId, T, x, tolerance, etc.)
     * @param dataPort Port for loading cluster data and CEC from disk
     * @param externalListener Optional listener for progress updates (e.g., GUI)
     */
    public CVMPhaseModelJob(
            CVMCalculationRequest request,
            DataManagementPort dataPort,
            CalculationProgressListener externalListener) {
        super(
            "cvm-" + request.getSystemId() + "-" + UUID.randomUUID(),
            "CVM: " + request.getSystemId() +
                " (T=" + request.getTemperature() + "K)",
            null // System will be loaded in run()
        );
        this.request = request;
        this.dataPort = dataPort;
        this.externalListener = externalListener;
    }

    @Override
    public void run() {
        LOG.info("CVMPhaseModelJob.run — ENTER: systemId=" + request.getSystemId()
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

            Optional<double[]> nciEciOpt = dataPort.loadECI(
                String.join("-", system.getComponents()),
                system.getStructure(),
                system.getPhase(),
                system.getModel(),
                request.getTemperature(),
                allData.getStage2().getNcf()  // Require ncf-length
            );
            if (nciEciOpt.isEmpty()) {
                markFailed("CEC not found for key: " + KeyUtils.cecKey(system)
                    + ". Use Data > CEC Database to add it.");
                return;
            }

            // Map ncf-length ECI to CVM format (strip point/empty if needed)
            double[] cvmEci = ECIMapper.mapCECToCvmECI(nciEciOpt.get(), allData);

            // ========== PHASE 3: Create CVMPhaseModel (First Minimization) ==========
            setStatusMessage("Creating CVM Phase Model (first minimization)...");
            setProgress(30);
            if (shouldStop()) return;

            double[] composition = request.getCompositionArray() != null
                    ? request.getCompositionArray()
                    : new double[] { request.getComposition(), 1.0 - request.getComposition() };

            CVMModelInput cvmInput = new CVMModelInput(
                system.getId(),
                system.getName(),
                system.getNumComponents(),
                allData.getStage1(),
                allData.getStage2(),
                allData.getStage3()
            );

            // CVMPhaseModel.create() expects scalar composition for binary systems
            // For multi-component, we'll use the first composition value (simplified)
            double scalarComposition = composition[0];
            model = CVMPhaseModel.create(cvmInput, cvmEci, request.getTemperature(),
                scalarComposition);

            if (model.getGradientNorm() > request.getTolerance() * 10) {
                LOG.warning("CVMPhaseModelJob.run — WARNING — First minimization has high gradient norm: "
                    + model.getGradientNorm());
                // Don't fail; model is still usable
            }

            // ========== PHASE 4: Initialize & Report ==========
            setStatusMessage("Initializing CVM Phase Model...");
            setProgress(50);
            if (shouldStop()) return;

            CalculationProgressListener bridgeListener = createBridgeListener();
            boolean ok = CVMPhaseModelExecutor.initializeModel(model, bridgeListener);

            if (!ok) {
                LOG.warning("CVMPhaseModelJob.run — EXIT: FAILED — CVM Phase Model initialization failed");
                markFailed("CVM Phase Model initialization failed");
                return;
            }

            if (shouldStop()) return;

            setProgress(100);
            setStatusMessage("CVM Phase Model ready for queries");
            LOG.info("CVMPhaseModelJob.run — EXIT: COMPLETED");
            markCompleted();

        } catch (Exception e) {
            LOG.log(Level.WARNING, "CVMPhaseModelJob.run — EXCEPTION: " + e.getMessage(), e);
            markFailed("CVM Phase Model job failed: " + e.getMessage());

        } finally {
            running = false;
        }
    }

    /**
     * Creates a bridge listener that forwards progress to external listener while
     * updating job progress.
     */
    private CalculationProgressListener createBridgeListener() {
        return new CalculationProgressListener() {
            @Override
            public void logMessage(String message) {
                if (externalListener != null) {
                    externalListener.logMessage(message);
                }
            }

            @Override
            public void setProgress(double progress) {
                // Job progress: 50% (after model creation) + 50% (during init) = 100%
                int jobProgress = 50 + (int)(progress * 50);
                CVMPhaseModelJob.this.setProgress(jobProgress);
                if (externalListener != null) {
                    externalListener.setProgress(progress);
                }
            }
        };
    }

    /**
     * Returns the CVMPhaseModel.
     */
    public CVMPhaseModel getModel() {
        return model;
    }

    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }
}

