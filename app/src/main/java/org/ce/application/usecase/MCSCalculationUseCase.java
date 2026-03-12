package org.ce.application.usecase;

import org.ce.application.port.CalculationProgressPort;
import org.ce.application.port.MCSProgressPort;
import org.ce.application.port.MCSRunnerPort;
import org.ce.domain.model.result.CalculationFailure;
import org.ce.domain.model.result.CalculationResult;
import org.ce.domain.model.result.EngineMetrics;
import org.ce.domain.model.result.EquilibriumState;
import org.ce.application.dto.MCSCalculationContext;
import org.ce.infrastructure.logging.LoggingConfig;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * Application use case for Monte Carlo simulation (MCS).
 *
 * <p>This use case orchestrates a single Monte Carlo simulation: validating
 * inputs, invoking the Metropolis sampler, and returning a domain result.
 * It decouples the presentation layer from the underlying MCS engine.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Validate calculation context readiness</li>
 *   <li>Report progress through {@link CalculationProgressPort}</li>
 *   <li>Provide real-time MCS updates through {@link MCSProgressPort}</li>
 *   <li>Support cancellation via {@link BooleanSupplier}</li>
 *   <li>Translate engine results into domain {@link CalculationResult}</li>
 * </ul>
 *
 * @since Phase 3 - Application Layer
 */
public final class MCSCalculationUseCase {

    private static final Logger LOG = LoggingConfig.getLogger(MCSCalculationUseCase.class);
    private static final String METHOD_NAME = "MCS";

    private final CalculationProgressPort progressPort;
    private final MCSProgressPort mcsPort;
    private final MCSRunnerPort runnerPort;

    public MCSCalculationUseCase(
            CalculationProgressPort progressPort,
            MCSRunnerPort runnerPort) {
        this.progressPort = progressPort != null
                ? progressPort
                : CalculationProgressPort.NO_OP;
        this.mcsPort = (progressPort instanceof MCSProgressPort mp)
                ? mp
                : MCSProgressPort.NO_OP_MCS;
        if (runnerPort == null) {
            throw new IllegalArgumentException("runnerPort must not be null");
        }
        this.runnerPort = runnerPort;
    }

    /**
     * Executes Monte Carlo simulation without cancellation support.
     */
    public CalculationResult execute(MCSCalculationContext context) {
        return execute(context, null);
    }

    /**
     * Executes Monte Carlo simulation with optional cancellation support.
     *
     * @return EquilibriumState on success, CalculationFailure on error or cancellation
     */
    public CalculationResult execute(MCSCalculationContext context,
                                     BooleanSupplier cancellationCheck) {
        LOG.info("MCSCalculationUseCase.execute \u2014 STARTED: system=" + context.getSystem().getId()
                + ", T=" + context.getTemperature() + " K, x=" + context.getComposition()
                + ", nEquil=" + context.getEquilibrationSteps()
                + ", nAvg=" + context.getAveragingSteps()
                + ", L=" + context.getSupercellSize()
                + ", seed=" + context.getSeed());

        if (!context.isReady()) {
            String error = "MCS context not ready: " + context.getReadinessError();
            LOG.warning(error);
            progressPort.logMessage("\u26a0 " + error);
            return CalculationFailure.of(error, METHOD_NAME);
        }

        progressPort.onCalculationStarted(METHOD_NAME);
        logCalculationDetails(context);

        try {
            progressPort.logMessage("\nStarting Monte Carlo simulation...");
            progressPort.reportProgress(0.2);
            mcsPort.initializeMCS(
                    context.getEquilibrationSteps(),
                    context.getAveragingSteps(),
                    context.getSeed());

            long startTime = System.currentTimeMillis();
            EquilibriumState mcResult = runnerPort.run(context, mcsPort, cancellationCheck);
            long elapsedMs = System.currentTimeMillis() - startTime;

            progressPort.onCalculationCompleted(METHOD_NAME, elapsedMs);

            // Log summary with MCS-specific metrics
            if (mcResult.metrics() instanceof EngineMetrics.McsMetrics m) {
                double[] cfs = mcResult.correlationFunctions();
                int cfN = Math.min(5, cfs.length);
                StringBuilder cfStr = new StringBuilder("[");
                for (int i = 0; i < cfN; i++) {
                    if (i > 0) cfStr.append(", ");
                    cfStr.append(String.format("%.4f", cfs[i]));
                }
                if (cfs.length > cfN) cfStr.append(", ...");
                cfStr.append("]");
                LOG.info("MCSCalculationUseCase.execute \u2014 DONE: T=" + context.getTemperature() + " K"
                        + ", elapsed=" + elapsedMs + " ms"
                        + ", acceptRate=" + String.format("%.3f", m.acceptRate())
                        + ", <E>/site=" + String.format("%.6f", m.energyPerSite()) + " J/mol"
                        + ", Hmix/site=" + String.format("%.6f", mcResult.enthalpy()) + " J/mol"
                        + ", Cv/site=" + (mcResult.heatCapacity().isPresent()
                                ? String.format("%.4e", mcResult.heatCapacity().getAsDouble())
                                : "n/a") + " J/(mol\u00b7K)"
                        + ", CFs=" + cfStr);
            }

            logResults(mcResult, elapsedMs);
            return mcResult;

        } catch (CancellationException ex) {
            LOG.info("MCSCalculationUseCase.execute \u2014 CANCELLED");
            progressPort.logMessage("\n\u2298 MCS Calculation Cancelled");
            progressPort.logMessage(ex.getMessage());
            return CalculationFailure.of("Cancelled: " + ex.getMessage(), METHOD_NAME);

        } catch (Exception ex) {
            LOG.log(java.util.logging.Level.WARNING,
                    "MCSCalculationUseCase.execute \u2014 EXCEPTION: "
                    + ex.getClass().getSimpleName() + ": " + ex.getMessage(), ex);
            progressPort.onCalculationFailed(METHOD_NAME, ex);
            logStackTrace(ex);
            return CalculationFailure.fromException(ex);
        }
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private void logCalculationDetails(MCSCalculationContext context) {
        progressPort.logMessage("\n" + "=".repeat(60));
        progressPort.logMessage("Starting MCS Calculation");
        progressPort.logMessage("=".repeat(60));
        progressPort.logMessage(context.getSummary());
        progressPort.logMessage("Building MCS runner with parameters:");
        progressPort.logMessage("  Temperature: " + context.getTemperature() + " K");
        progressPort.logMessage("  Composition (x): " + context.getComposition());
        progressPort.logMessage("  Supercell size (L): " + context.getSupercellSize());
        progressPort.logMessage("  Equilibration steps: " + context.getEquilibrationSteps());
        progressPort.logMessage("  Averaging steps: " + context.getAveragingSteps());
        progressPort.logMessage("  Random seed: " + context.getSeed());
    }

    private void logResults(EquilibriumState result, long elapsedMs) {
        progressPort.logMessage("\n" + "-".repeat(60));
        progressPort.logMessage("MCS Calculation Complete");
        progressPort.logMessage("-".repeat(60));
        progressPort.logMessage("Execution time: " + elapsedMs + " ms");

        progressPort.logMessage("\nResults:");
        progressPort.logMessage("  Average CFs:");
        double[] avgCFs = result.correlationFunctions();
        for (int i = 0; i < Math.min(5, avgCFs.length); i++) {
            progressPort.logMessage("    CF[" + i + "]: " + String.format("%.6f", avgCFs[i]));
        }
        if (avgCFs.length > 5) {
            progressPort.logMessage("    ... (" + (avgCFs.length - 5) + " more CFs)");
        }

        progressPort.logMessage("  Hmix/site (CE formula): "
                + String.format("%.6f", result.enthalpy()) + " J/mol");
        result.heatCapacity().ifPresent(cv ->
                progressPort.logMessage("  Heat Capacity (per site): "
                        + String.format("%.6f", cv) + " J/(mol\u00b7K)"));

        if (result.metrics() instanceof EngineMetrics.McsMetrics m) {
            progressPort.logMessage("  Average Energy (per site): "
                    + String.format("%.6f", m.energyPerSite()) + " J/mol");
            progressPort.logMessage("  Acceptance Rate: "
                    + String.format("%.2f", m.acceptRate() * 100) + "%");
        }

        progressPort.logMessage("\n\u2713 MCS calculation succeeded");
        progressPort.logMessage("=".repeat(60));
    }

    private void logStackTrace(Exception ex) {
        progressPort.logMessage("Error: " + ex.getClass().getSimpleName());
        progressPort.logMessage("Message: " + ex.getMessage());
        StringBuilder stackTrace = new StringBuilder();
        for (StackTraceElement element : ex.getStackTrace()) {
            stackTrace.append("  at ").append(element).append("\n");
        }
        progressPort.logMessage(stackTrace.toString());
    }
}
