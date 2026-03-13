package org.ce.application.job;

import org.ce.application.dto.MCSCalculationRequest;
import org.ce.application.port.DataManagementPort;
import org.ce.domain.mcs.MCSPhaseModel;
import org.ce.domain.model.result.EngineMetrics;
import org.ce.domain.model.result.EquilibriumState;
import org.ce.application.port.CalculationProgressListener;
import org.ce.infrastructure.logging.LoggingConfig;

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
 *   <li>Create {@link MCSPhaseModel} — unified domain model (mirrors CVMPhaseModelJob)</li>
 *   <li>Query {@link EquilibriumState} — first MC run happens here</li>
 * </ol>
 *
 * <p>All data loading happens on the background thread (NOT UI thread)
 * via {@link DataManagementPort}, ensuring clean separation of Type 1 (data)
 * from Type 2 (calculation) concerns.</p>
 *
 * <p><strong>Unified Pattern:</strong> This job mirrors {@link CVMPhaseModelJob} exactly.
 * Both jobs create a PhaseModel, store it, and expose it via {@code getModel()}.
 * The GUI can then call {@code model.setTemperature(T)} / {@code model.getEquilibriumState()}
 * for parameter scanning without re-running the full pipeline.</p>
 *
 * <p><strong>Cancellation:</strong> The MCS simulation runs synchronously inside
 * {@code model.getEquilibriumState()}. Intra-simulation cancellation is not yet wired
 * (Phase 10.6). The job can be cancelled before or after the simulation runs.</p>
 */
public class MCSCalculationJob extends AbstractThermodynamicJob {

    private static final Logger LOG = LoggingConfig.getLogger(MCSCalculationJob.class);

    private final MCSCalculationRequest request;
    private final CalculationProgressListener externalListener;

    // Result state — mirrors CVMPhaseModelJob pattern
    private MCSPhaseModel model;
    private EquilibriumState result;

    /**
     * Creates a new MCS calculation job.
     *
     * <p>The job defers all data loading until {@link #run()} is called
     * (on the background thread). The request is a pure value object with
     * no disk I/O.</p>
     *
     * @param request          The MCS calculation request (systemId, T, x, L, steps, etc.)
     * @param dataPort         Port for loading cluster data and ECI from disk
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

            // ========== PHASE 3: Create MCS Phase Model ==========
            setStatusMessage("Creating MCS Phase Model...");
            setProgress(25);
            if (shouldStop()) return;

            double[] composition = request.getCompositionArray() != null
                    ? request.getCompositionArray()
                    : new double[] { request.getComposition(), 1.0 - request.getComposition() };

            model = MCSPhaseModel.create(
                    jobData.clusterData(),
                    jobData.ncfEci(),
                    jobData.system().getNumComponents(),
                    request.getTemperature(),
                    composition
            );

            // Apply engine parameters from request only when they differ from model defaults.
            // Each setter invalidates the cache — avoiding unnecessary calls prevents a
            // redundant MC re-run. (MCSPhaseModel.create() already runs the simulation once
            // with default engine params and its own seed; we correct them here if needed.)
            if (request.getSupercellSize() != model.getSupercellSize()) {
                model.setSupercellSize(request.getSupercellSize());
            }
            if (request.getEquilibrationSteps() != model.getEquilibrationSweeps()) {
                model.setEquilibrationSweeps(request.getEquilibrationSteps());
            }
            if (request.getAveragingSteps() != model.getAveragingSweeps()) {
                model.setAveragingSweeps(request.getAveragingSteps());
            }
            // Always sync the seed — request carries an explicit seed (auto-generated
            // in the builder if not set by caller). This ensures reproducibility.
            model.setSeed(request.getSeed());

            // ========== PHASE 4: Run Simulation ==========
            setStatusMessage("Running Monte Carlo simulation...");
            setProgress(30);
            if (shouldStop()) return;

            // This call runs the full MC simulation synchronously.
            // Intra-simulation cancellation will be wired in Phase 10.6 (D8).
            result = model.getEquilibriumState();

            if (shouldStop()) return;

            // ========== PHASE 5: Log Result ==========
            logResult(result);
            setProgress(100);
            setStatusMessage("MCS calculation completed");
            LOG.info("MCSCalculationJob.run — EXIT: COMPLETED");
            markCompleted();

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

    // =========================================================================
    // RESULT ACCESS — mirrors CVMPhaseModelJob.getModel()
    // =========================================================================

    /**
     * Returns the MCS phase model (only available after run() completes).
     *
     * <p>Callers can use the model for parameter scanning:
     * <pre>{@code
     *   model.setTemperature(T);
     *   EquilibriumState state = model.getEquilibriumState();
     * }</pre>
     */
    public MCSPhaseModel getModel() {
        return model;
    }

    /**
     * Returns the equilibrium state from the completed calculation.
     * Equivalent to {@code getModel().getEquilibriumState()} for the initial parameters.
     */
    public EquilibriumState getResult() {
        return result;
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private void logResult(EquilibriumState state) {
        if (externalListener == null) return;
        if (state == null) return;

        externalListener.logMessage("\n" + "=".repeat(60));
        externalListener.logMessage("MCS Phase Model - Calculation Complete");
        externalListener.logMessage("=".repeat(60));
        externalListener.logMessage("\nThermodynamic State:");
        externalListener.logMessage("  H (Enthalpy/site):  "
                + String.format("%.8e", state.enthalpy()));
        state.heatCapacity().ifPresent(cv ->
                externalListener.logMessage("  Cv (Heat Capacity): "
                        + String.format("%.8e", cv)));
        externalListener.logMessage("  (G and S: not available from MCS — physics boundary)");

        externalListener.logMessage("\nEngine Diagnostics:");
        if (state.metrics() instanceof EngineMetrics.McsMetrics mcs) {
            externalListener.logMessage("  Accept Rate:      "
                    + String.format("%.3f%%", mcs.acceptRate() * 100));
            externalListener.logMessage("  Equil. Sweeps:    " + mcs.nEquilSweeps());
            externalListener.logMessage("  Avg. Sweeps:      " + mcs.nAvgSweeps());
            externalListener.logMessage("  Supercell L:      " + mcs.supercellSize());
            externalListener.logMessage("  Sites:            " + mcs.nSites());
        }

        externalListener.logMessage("\n  Equilibrium CFs:");
        double[] cfs = state.correlationFunctions();
        for (int i = 0; i < cfs.length; i++) {
            externalListener.logMessage("    CF[" + i + "]: "
                    + String.format("%.10f", cfs[i]));
        }

        externalListener.logMessage("\n\u2714 MCS Phase Model ready for parameter scanning");
        externalListener.logMessage("=".repeat(60));
    }

    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }
}
