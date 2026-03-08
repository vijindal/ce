package org.ce.application.usecase;

import org.ce.application.port.CalculationProgressPort;
import org.ce.application.port.CVMSolverPort;
import org.ce.domain.cvm.CVMModelInput;
import org.ce.domain.model.result.CVMResult;
import org.ce.domain.model.result.CalculationFailure;
import org.ce.domain.model.result.CalculationResult;
import org.ce.infrastructure.context.CVMCalculationContext;

/**
 * Application use case for CVM free-energy minimization.
 *
 * <p>This use case orchestrates a single CVM calculation: validating inputs,
 * invoking the Newton-Raphson solver, and returning a domain result.
 * It decouples the presentation layer from the underlying engine.</p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Validate calculation context readiness</li>
 *   <li>Report progress through {@link CalculationProgressPort}</li>
 *   <li>Delegate to {@link CVMEngine} for numerical computation</li>
 *   <li>Translate engine results into domain {@link CalculationResult}</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CVMCalculationUseCase useCase = new CVMCalculationUseCase(progressPort);
 * CalculationResult result = useCase.execute(context);
 * switch (result) {
 *     case CVMResult success -> handleSuccess(success);
 *     case CalculationFailure fail -> handleFailure(fail);
 *     default -> throw new IllegalStateException("Unexpected result type");
 * }
 * }</pre>
 *
 * @author CVM Project
 * @version 1.0
 * @since Phase 3 - Application Layer
 * @see CVMEngine
 * @see CVMCalculationContext
 */
public final class CVMCalculationUseCase {

    private static final String METHOD_NAME = "CVM";

    private final CalculationProgressPort progressPort;
    private final CVMSolverPort solverPort;

    /**
     * Constructs the use case with a progress port.
     *
     * @param progressPort port for reporting progress (never null)
     */
    public CVMCalculationUseCase(
            CalculationProgressPort progressPort,
            CVMSolverPort solverPort) {
        this.progressPort = progressPort != null
                ? progressPort
                : CalculationProgressPort.NO_OP;
        if (solverPort == null) {
            throw new IllegalArgumentException("solverPort must not be null");
        }
        this.solverPort = solverPort;
    }

    /**
     * Executes CVM free-energy minimization.
     *
     * @param context prepared calculation context with cluster data and ECI
     * @return CVMResult on success, CalculationFailure on error
     */
    public CalculationResult execute(CVMCalculationContext context) {
        if (!context.isReady()) {
            String error = "CVM context not ready: " + context.getReadinessError();
            progressPort.logMessage("âš  " + error);
            return CalculationFailure.of(error, METHOD_NAME);
        }

        progressPort.onCalculationStarted(METHOD_NAME);
        logCalculationDetails(context);

        try {
            progressPort.logMessage("Running Newton-Raphson solver...");
            progressPort.reportProgress(0.3);

            long startTime = System.currentTimeMillis();
                CVMResult solverResult = solverPort.solve(
                    toModelInput(context),
                    context.getECI(),
                    context.getTemperature(),
                    context.getComposition(),
                    context.getTolerance());
            long elapsedMs = System.currentTimeMillis() - startTime;

            progressPort.onCalculationCompleted(METHOD_NAME, elapsedMs);
            logResults(context, solverResult, elapsedMs);

            return solverResult;

        } catch (Exception ex) {
            progressPort.onCalculationFailed(METHOD_NAME, ex);
            logStackTrace(ex);
            return CalculationFailure.fromException(ex);
        }
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private void logCalculationDetails(CVMCalculationContext context) {
        progressPort.logMessage("\n" + "=".repeat(60));
        progressPort.logMessage("Starting CVM Free-Energy Minimization");
        progressPort.logMessage("=".repeat(60));
        progressPort.logMessage(context.getSummary());
        progressPort.logMessage("CVM parameters:");
        progressPort.logMessage("  Temperature: " + context.getTemperature() + " K");
        progressPort.logMessage("  Composition (x): " + context.getComposition());
        progressPort.logMessage("  Tolerance: " + context.getTolerance());
        progressPort.logMessage("  Number of CFs (ncf): "
                + context.getAllClusterData().getStage2().getNcf());
        progressPort.logMessage("  Total CFs (tcf): "
                + context.getAllClusterData().getStage2().getTcf());
        progressPort.logMessage("  Cluster types (tcdis): "
                + context.getAllClusterData().getStage1().getTcdis());
    }

    private void logResults(CVMCalculationContext context,
                            CVMResult result,
                            long elapsedMs) {
        progressPort.logMessage("\n" + "-".repeat(60));
        if (result.converged()) {
            progressPort.logMessage("CVM Calculation Complete - CONVERGED");
        } else {
            progressPort.logMessage("CVM Calculation Complete - DID NOT CONVERGE");
        }
        progressPort.logMessage("-".repeat(60));
        progressPort.logMessage("Execution time: " + elapsedMs + " ms");
        progressPort.logMessage("Iterations: " + result.iterations());
        progressPort.logMessage("Final gradient norm: "
            + String.format("%.6e", result.gradientNorm()));

        progressPort.logMessage("\nThermodynamic Results:");
        progressPort.logMessage("  Gibbs Energy (G): "
            + String.format("%.8e", result.gibbsEnergy()));
        progressPort.logMessage("  Enthalpy (H):     "
            + String.format("%.8e", result.enthalpy()));
        progressPort.logMessage("  Entropy (S):      "
            + String.format("%.8e", result.entropy()));
        progressPort.logMessage("  -TÂ·S:             "
            + String.format("%.8e", -context.getTemperature() * result.entropy()));

        progressPort.logMessage("\nEquilibrium Correlation Functions:");
        double[] eqCFs = result.equilibriumCFs();
        for (int i = 0; i < Math.min(5, eqCFs.length); i++) {
            progressPort.logMessage("  CF[" + i + "]: "
                    + String.format("%.10f", eqCFs[i]));
        }
        if (eqCFs.length > 5) {
            progressPort.logMessage("  ... (" + (eqCFs.length - 5) + " more CFs)");
        }

        if (result.converged()) {
            progressPort.logMessage("\nâœ“ CVM calculation succeeded");
        } else {
            progressPort.logMessage("\nâš  CVM calculation finished but did not converge to tolerance");
        }
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

    private CVMModelInput toModelInput(CVMCalculationContext context) {
        return new CVMModelInput(
                context.getSystem().getId(),
                context.getSystem().getName(),
                context.getAllClusterData().getNumComponents(),
                context.getAllClusterData().getStage1(),
                context.getAllClusterData().getStage2(),
                context.getAllClusterData().getStage3());
    }
}

