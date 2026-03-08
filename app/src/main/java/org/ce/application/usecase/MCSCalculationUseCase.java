package org.ce.application.usecase;

import org.ce.application.port.CalculationProgressPort;
import org.ce.application.port.MCSProgressPort;
import org.ce.application.port.MCSRunnerPort;
import org.ce.domain.model.result.CalculationFailure;
import org.ce.domain.model.result.CalculationResult;
import org.ce.domain.model.result.MCSResult;
import org.ce.infrastructure.context.MCSCalculationContext;
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
 *   <li>Delegate to {@link MCSRunner} for numerical computation</li>
 *   <li>Support cancellation via {@link BooleanSupplier}</li>
 *   <li>Translate engine results into domain {@link CalculationResult}</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * MCSCalculationUseCase useCase = new MCSCalculationUseCase(mcsProgressPort);
 * CalculationResult result = useCase.execute(context, () -> cancelled.get());
 * switch (result) {
 *     case MCSResult success -> handleSuccess(success);
 *     case CalculationFailure fail -> handleFailure(fail);
 *     default -> throw new IllegalStateException("Unexpected result type");
 * }
 * }</pre>
 *
 * @author CVM Project
 * @version 1.0
 * @since Phase 3 - Application Layer
 * @see MCSCalculationContext
 */
public final class MCSCalculationUseCase {

    private static final Logger LOG = LoggingConfig.getLogger(MCSCalculationUseCase.class);
    private static final String METHOD_NAME = "MCS";

    private final CalculationProgressPort progressPort;
    private final MCSProgressPort mcsPort;
    private final MCSRunnerPort runnerPort;

    /**
     * Constructs the use case with a progress port.
     *
     * @param progressPort port for reporting progress (never null)
     */
    public MCSCalculationUseCase(
            CalculationProgressPort progressPort,
            MCSRunnerPort runnerPort) {
        this.progressPort = progressPort != null
                ? progressPort
                : CalculationProgressPort.NO_OP;
        // If the progress port is also an MCS port, use it; otherwise use NO_OP
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
     *
     * @param context prepared calculation context with cluster data and ECI
     * @return MCSResult on success, CalculationFailure on error
     */
    public CalculationResult execute(MCSCalculationContext context) {
        return execute(context, null);
    }

    /**
     * Executes Monte Carlo simulation with optional cancellation support.
     *
     * @param context           prepared calculation context
     * @param cancellationCheck returns true if cancelled (may be null)
     * @return MCSResult on success, CalculationFailure on error or cancellation
     */
    public CalculationResult execute(MCSCalculationContext context,
                                     BooleanSupplier cancellationCheck) {
        LOG.info("MCSCalculationUseCase.execute — STARTED: system=" + context.getSystem().getId()
                + ", T=" + context.getTemperature() + " K, x=" + context.getComposition()
                + ", nEquil=" + context.getEquilibrationSteps()
                + ", nAvg=" + context.getAveragingSteps()
                + ", L=" + context.getSupercellSize()
                + ", seed=" + context.getSeed());
        if (!context.isReady()) {
            String error = "MCS context not ready: " + context.getReadinessError();
            LOG.warning(error);
            progressPort.logMessage("âš  " + error);
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
                    context.getSeed()
            );

            long startTime = System.currentTimeMillis();
            MCSResult mcResult = runnerPort.run(context, mcsPort, cancellationCheck);
            long elapsedMs = System.currentTimeMillis() - startTime;

            progressPort.onCalculationCompleted(METHOD_NAME, elapsedMs);
            double[] _cfs = mcResult.correlationFunctions();
            int _cfN = Math.min(5, _cfs.length);
            StringBuilder _cfStr = new StringBuilder("[");
            for (int _i = 0; _i < _cfN; _i++) {
                if (_i > 0) _cfStr.append(", ");
                _cfStr.append(String.format("%.4f", _cfs[_i]));
            }
            if (_cfs.length > _cfN) _cfStr.append(", ...");
            _cfStr.append("]");
            LOG.info("MCSCalculationUseCase.execute — DONE: T=" + context.getTemperature() + " K"
                    + ", elapsed=" + elapsedMs + " ms"
                    + ", acceptRate=" + String.format("%.3f", mcResult.acceptRate())
                    + ", <E>/site=" + String.format("%.6f", mcResult.energyPerSite()) + " eV"
                    + ", Cv/site=" + String.format("%.4e", mcResult.heatCapacityPerSite()) + " eV/K"
                    + ", CFs=" + _cfStr);
            logResults(mcResult, elapsedMs);
            return mcResult;

        } catch (CancellationException ex) {
            LOG.info("MCSCalculationUseCase.execute — CANCELLED");
            progressPort.logMessage("\nâŠ˜ MCS Calculation Cancelled");
            progressPort.logMessage(ex.getMessage());
            return CalculationFailure.of("Cancelled: " + ex.getMessage(), METHOD_NAME);

        } catch (Exception ex) {
            LOG.log(java.util.logging.Level.WARNING, "MCSCalculationUseCase.execute — EXCEPTION: " + ex.getClass().getSimpleName() + ": " + ex.getMessage(), ex);
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

    private void logResults(MCSResult result, long elapsedMs) {
        progressPort.logMessage("\n" + "-".repeat(60));
        progressPort.logMessage("MCS Calculation Complete");
        progressPort.logMessage("-".repeat(60));
        progressPort.logMessage("Execution time: " + elapsedMs + " ms");

        progressPort.logMessage("\nResults:");
        progressPort.logMessage("  Average CFs: ");
        double[] avgCFs = result.correlationFunctions();
        for (int i = 0; i < Math.min(5, avgCFs.length); i++) {
            progressPort.logMessage("    CF[" + i + "]: "
                    + String.format("%.6f", avgCFs[i]));
        }
        if (avgCFs.length > 5) {
            progressPort.logMessage("    ... (" + (avgCFs.length - 5) + " more CFs)");
        }

        progressPort.logMessage("  Average Energy (per site): "
            + String.format("%.6f", result.energyPerSite()) + " eV");
        progressPort.logMessage("  Heat Capacity (per site): "
            + String.format("%.6f", result.heatCapacityPerSite()) + " eV/K");
        progressPort.logMessage("  Acceptance Rate: "
            + String.format("%.2f", result.acceptRate() * 100) + "%");

        progressPort.logMessage("\nâœ“ MCS calculation succeeded");
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

