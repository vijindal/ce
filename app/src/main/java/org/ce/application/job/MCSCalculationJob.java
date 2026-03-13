package org.ce.application.job;

import org.ce.application.dto.MCSCalculationRequest;
import org.ce.application.port.CalculationProgressListener;
import org.ce.application.port.DataManagementPort;
import org.ce.domain.mcs.MCSPhaseModel;
import org.ce.domain.model.result.EquilibriumState;
import org.ce.infrastructure.logging.LoggingConfig;

import java.util.Arrays;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background job for Monte Carlo Simulation (MCS) calculations.
 *
 * <p><strong>Type 2 Job — Thermodynamic Calculation</strong></p>
 *
 * <p>Follows the unified pipeline pattern shared with {@link CVMPhaseModelJob}:</p>
 * <ol>
 *   <li>Load system, cluster data, and ECI (via base class)</li>
 *   <li>Resolve composition array from request</li>
 *   <li>{@link MCSPhaseModel#buildOnly} — model constructed, simulation NOT yet run</li>
 *   <li>Wire update listener → GUI chart (so live sweep data flows during the run)</li>
 *   <li>{@link MCSPhaseModel#getEquilibriumState} — triggers exactly one MC simulation</li>
 *   <li>Log final results to listener text panel</li>
 * </ol>
 *
 * <p>All data loading and simulation run on the background thread (NOT UI thread)
 * via {@link DataManagementPort}.</p>
 */
public class MCSCalculationJob extends AbstractThermodynamicJob {

    private static final Logger LOG = LoggingConfig.getLogger(MCSCalculationJob.class);

    private final MCSCalculationRequest request;
    private final CalculationProgressListener externalListener;
    private MCSPhaseModel model;
    private EquilibriumState result;

    /**
     * Creates a new MCS calculation job.
     *
     * @param request          MCS calculation parameters (systemId, T, x, L, sweeps, seed)
     * @param dataPort         port for loading cluster data and ECI from disk
     * @param externalListener optional listener for progress updates (e.g. GUI); may be null
     */
    public MCSCalculationJob(
            MCSCalculationRequest request,
            DataManagementPort dataPort,
            CalculationProgressListener externalListener) {
        super(
            "mcs-" + request.getSystemId() + "-" + UUID.randomUUID(),
            "MCS: " + request.getSystemId() + " (T=" + request.getTemperature() + "K)",
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

            // ===== PHASES 1+2: Load system, cluster data, ECI =====
            ThermodynamicJobData jobData = loadSystemData(request);
            if (jobData == null) return; // markFailed already called

            // ===== PHASE 3: Build MCSPhaseModel (no run yet — parameters set first) =====
            setStatusMessage("Building MCS model...");
            setProgress(25);
            if (shouldStop()) return;

            double[] composition = request.getCompositionArray();

            // Build model without running: set all engine parameters before the first
            // MC simulation so the simulation runs exactly once with the correct settings.
            model = MCSPhaseModel.buildOnly(
                    jobData.clusterData(),
                    jobData.ncfEci(),
                    jobData.system().getNumComponents(),
                    request.getTemperature(),
                    composition,
                    request.getSupercellSize(),
                    request.getEquilibrationSteps(),
                    request.getAveragingSteps(),
                    request.getSeed());

            // ===== PHASE 4: Wire live-update listener → GUI chart =====
            if (externalListener != null) {
                externalListener.initializeMCS(
                        request.getEquilibrationSteps(),
                        request.getAveragingSteps(),
                        request.getSeed());
                model.setUpdateListener(externalListener::updateMCSData);
            }

            // ===== PHASE 5: Run MC simulation =====
            setStatusMessage("Running Monte Carlo simulation...");
            setProgress(30);
            if (shouldStop()) return;

            result = model.getEquilibriumState();

            // ===== PHASE 6: Log results to GUI text panel =====
            if (externalListener != null) {
                logResultsToListener(result, externalListener);
            }

            setProgress(100);
            setStatusMessage("MCS calculation completed");
            LOG.info("MCSCalculationJob.run — EXIT: COMPLETED");
            markCompleted();

        } catch (Exception e) {
            LOG.log(Level.WARNING, "MCSCalculationJob.run — EXCEPTION: " + e.getMessage(), e);
            markFailed("MCS calculation failed: " + e.getMessage());

        } finally {
            running = false;
        }
    }

    /**
     * Formats the completed EquilibriumState and writes it to the GUI text panel.
     * Mirrors the output style of CVMPhaseModelExecutor.initializeModel().
     */
    private void logResultsToListener(EquilibriumState state, CalculationProgressListener listener) {
        listener.logMessage("");
        listener.logMessage("=".repeat(60));
        listener.logMessage("MCS Equilibrium Result");
        listener.logMessage("=".repeat(60));
        listener.logMessage("  Temperature : " + String.format("%.2f", state.temperature()) + " K");
        listener.logMessage("  Composition : " + Arrays.toString(state.compositionArray()));
        listener.logMessage("");
        listener.logMessage("  H_mix/site  : " + String.format("%.8e", state.enthalpyOfMixing()) + " J/mol");
        state.heatCapacity().ifPresent(cv ->
            listener.logMessage("  Cv/site     : " + String.format("%.8e", cv) + " J/(mol·K)"));

        if (state.metrics() instanceof org.ce.domain.model.result.EngineMetrics.McsMetrics m) {
            listener.logMessage("  Accept rate : " + String.format("%.4f", m.acceptRate()));
            listener.logMessage("  Supercell L : " + m.supercellSize()
                    + "  (N = " + m.nSites() + " sites)");
            listener.logMessage("  Equil sweeps: " + m.nEquilSweeps());
            listener.logMessage("  Avg sweeps  : " + m.nAvgSweeps());
            listener.logMessage("  E/site      : " + String.format("%.8e", m.energyPerSite()) + " J/mol");
        }

        listener.logMessage("");
        listener.logMessage("  Avg CFs (ncf-indexed):");
        double[] cfs = state.correlationFunctions();
        for (int i = 0; i < cfs.length; i++) {
            listener.logMessage("    CF[" + i + "] = " + String.format("%.10f", cfs[i]));
        }
        listener.logMessage("=".repeat(60));
        listener.logMessage("\u2714 MCS calculation complete");
    }

    /**
     * Returns the MCSPhaseModel (available after successful completion).
     * Callers can use the model for further parameter sweeps or queries.
     */
    public MCSPhaseModel getModel() {
        return model;
    }

    /**
     * Returns the equilibrium result (available after successful completion).
     */
    public EquilibriumState getResult() {
        return result;
    }

    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }
}