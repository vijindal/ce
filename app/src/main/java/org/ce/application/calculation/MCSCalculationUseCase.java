package org.ce.application.calculation;

import org.ce.application.port.CalculationProgressPort;
import org.ce.application.port.MCSProgressPort;
import org.ce.domain.model.result.CalculationFailure;
import org.ce.domain.model.result.CalculationResult;
import org.ce.domain.model.result.MCSResult;
import org.ce.mcs.MCResult;
import org.ce.mcs.MCSRunner;
import org.ce.workbench.util.context.MCSCalculationContext;
import org.ce.workbench.util.mcs.MCSUpdate;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

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
 * @see MCSRunner
 * @see MCSCalculationContext
 */
public final class MCSCalculationUseCase {

    private static final String METHOD_NAME = "MCS";

    /**
     * Gas constant R = 8.314 J/(mol·K) for correct Boltzmann statistics
     * when ECI units are in J/mol.
     */
    private static final double GAS_CONSTANT = 8.314;

    private final CalculationProgressPort progressPort;
    private final MCSProgressPort mcsPort;

    /**
     * Constructs the use case with a progress port.
     *
     * @param progressPort port for reporting progress (never null)
     */
    public MCSCalculationUseCase(CalculationProgressPort progressPort) {
        this.progressPort = progressPort != null
                ? progressPort
                : CalculationProgressPort.NO_OP;
        // If the progress port is also an MCS port, use it; otherwise use NO_OP
        this.mcsPort = (progressPort instanceof MCSProgressPort mp)
                ? mp
                : MCSProgressPort.NO_OP_MCS;
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
        if (!context.isReady()) {
            String error = "MCS context not ready: " + context.getReadinessError();
            progressPort.logMessage("⚠ " + error);
            return CalculationFailure.of(error, METHOD_NAME);
        }

        progressPort.onCalculationStarted(METHOD_NAME);
        logCalculationDetails(context);

        try {
            progressPort.logMessage("\nBuilding MCS configuration...");

            MCSRunner.Builder builder = MCSRunner.builder()
                    .clusterData(context.getClusterData())
                    .eci(context.getECI())
                    .numComp(context.getSystem().getNumComponents())
                    .T(context.getTemperature())
                    .compositionBinary(context.getComposition())
                    .nEquil(context.getEquilibrationSteps())
                    .nAvg(context.getAveragingSteps())
                    .L(context.getSupercellSize())
                    .seed(context.getSeed())
                    .updateListener(this::handleMCSUpdate)
                    .R(GAS_CONSTANT);

            if (cancellationCheck != null) {
                builder.cancellationCheck(cancellationCheck);
            }

            MCSRunner runner = builder.build();

            progressPort.logMessage("Configuration built. Starting Monte Carlo simulation...");
            progressPort.reportProgress(0.2);
            mcsPort.initializeMCS(
                    context.getEquilibrationSteps(),
                    context.getAveragingSteps(),
                    context.getSeed()
            );

            long startTime = System.currentTimeMillis();
            MCResult mcResult = runner.run();
            long elapsedMs = System.currentTimeMillis() - startTime;

            progressPort.onCalculationCompleted(METHOD_NAME, elapsedMs);
            logResults(mcResult, elapsedMs);

            return toDomainResult(mcResult);

        } catch (CancellationException ex) {
            progressPort.logMessage("\n⊘ MCS Calculation Cancelled");
            progressPort.logMessage(ex.getMessage());
            return CalculationFailure.of("Cancelled: " + ex.getMessage(), METHOD_NAME);

        } catch (Exception ex) {
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

    private void logResults(MCResult result, long elapsedMs) {
        progressPort.logMessage("\n" + "-".repeat(60));
        progressPort.logMessage("MCS Calculation Complete");
        progressPort.logMessage("-".repeat(60));
        progressPort.logMessage("Execution time: " + elapsedMs + " ms");

        progressPort.logMessage("\nResults:");
        progressPort.logMessage("  Average CFs: ");
        double[] avgCFs = result.getAvgCFs();
        for (int i = 0; i < Math.min(5, avgCFs.length); i++) {
            progressPort.logMessage("    CF[" + i + "]: "
                    + String.format("%.6f", avgCFs[i]));
        }
        if (avgCFs.length > 5) {
            progressPort.logMessage("    ... (" + (avgCFs.length - 5) + " more CFs)");
        }

        progressPort.logMessage("  Average Energy (per site): "
                + String.format("%.6f", result.getEnergyPerSite()) + " eV");
        progressPort.logMessage("  Heat Capacity (per site): "
                + String.format("%.6f", result.getHeatCapacityPerSite()) + " eV/K");
        progressPort.logMessage("  Acceptance Rate: "
                + String.format("%.2f", result.getAcceptRate() * 100) + "%");

        progressPort.logMessage("\n✓ MCS calculation succeeded");
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

    private void handleMCSUpdate(MCSUpdate update) {
        MCSProgressPort.MCSSnapshot snapshot = new MCSProgressPort.MCSSnapshot(
                update.getStep(),
                update.getE_total(),
                update.getDeltaE(),
                update.getSigmaDE(),
                update.getMeanDE(),
                switch (update.getPhase()) {
                    case EQUILIBRATION -> MCSProgressPort.SimulationPhase.EQUILIBRATION;
                    case AVERAGING -> MCSProgressPort.SimulationPhase.AVERAGING;
                },
                update.getAcceptanceRate(),
                update.getElapsedMs()
        );
        mcsPort.onMCSUpdate(snapshot);
    }

    private MCSResult toDomainResult(MCResult mcResult) {
        return MCSResult.fromEngine(
                mcResult.getTemperature(),
                mcResult.getComposition(),
                mcResult.getAvgCFs(),
                mcResult.getEnergyPerSite(),
                mcResult.getHeatCapacityPerSite(),
                mcResult.getAcceptRate(),
                mcResult.getNEquilSweeps(),
                mcResult.getNAvgSweeps(),
                mcResult.getSupercellSize(),
                mcResult.getNSites()
        );
    }
}
