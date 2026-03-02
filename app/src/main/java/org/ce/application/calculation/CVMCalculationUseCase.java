package org.ce.application.calculation;

import org.ce.application.port.CalculationProgressPort;
import org.ce.cvm.CVMEngine;
import org.ce.cvm.CVMSolverResult;
import org.ce.domain.model.result.CVMResult;
import org.ce.domain.model.result.CalculationFailure;
import org.ce.domain.model.result.CalculationResult;
import org.ce.workbench.util.context.CVMCalculationContext;

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

    /**
     * Constructs the use case with a progress port.
     *
     * @param progressPort port for reporting progress (never null)
     */
    public CVMCalculationUseCase(CalculationProgressPort progressPort) {
        this.progressPort = progressPort != null
                ? progressPort
                : CalculationProgressPort.NO_OP;
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
            progressPort.logMessage("⚠ " + error);
            return CalculationFailure.of(error, METHOD_NAME);
        }

        progressPort.onCalculationStarted(METHOD_NAME);
        logCalculationDetails(context);

        try {
            progressPort.logMessage("Running Newton-Raphson solver...");
            progressPort.reportProgress(0.3);

            long startTime = System.currentTimeMillis();
            CVMSolverResult solverResult = CVMEngine.solve(context);
            long elapsedMs = System.currentTimeMillis() - startTime;

            progressPort.onCalculationCompleted(METHOD_NAME, elapsedMs);
            logResults(context, solverResult, elapsedMs);

            return toDomainResult(context, solverResult);

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
                            CVMSolverResult result,
                            long elapsedMs) {
        progressPort.logMessage("\n" + "-".repeat(60));
        if (result.isConverged()) {
            progressPort.logMessage("CVM Calculation Complete - CONVERGED");
        } else {
            progressPort.logMessage("CVM Calculation Complete - DID NOT CONVERGE");
        }
        progressPort.logMessage("-".repeat(60));
        progressPort.logMessage("Execution time: " + elapsedMs + " ms");
        progressPort.logMessage("Iterations: " + result.getIterations());
        progressPort.logMessage("Final gradient norm: "
                + String.format("%.6e", result.getGradientNorm()));

        progressPort.logMessage("\nThermodynamic Results:");
        progressPort.logMessage("  Gibbs Energy (G): "
                + String.format("%.8e", result.getGibbsEnergy()));
        progressPort.logMessage("  Enthalpy (H):     "
                + String.format("%.8e", result.getEnthalpy()));
        progressPort.logMessage("  Entropy (S):      "
                + String.format("%.8e", result.getEntropy()));
        progressPort.logMessage("  -T·S:             "
                + String.format("%.8e", -context.getTemperature() * result.getEntropy()));

        progressPort.logMessage("\nEquilibrium Correlation Functions:");
        double[] eqCFs = result.getEquilibriumCFs();
        for (int i = 0; i < Math.min(5, eqCFs.length); i++) {
            progressPort.logMessage("  CF[" + i + "]: "
                    + String.format("%.10f", eqCFs[i]));
        }
        if (eqCFs.length > 5) {
            progressPort.logMessage("  ... (" + (eqCFs.length - 5) + " more CFs)");
        }

        if (result.isConverged()) {
            progressPort.logMessage("\n✓ CVM calculation succeeded");
        } else {
            progressPort.logMessage("\n⚠ CVM calculation finished but did not converge to tolerance");
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

    private CVMResult toDomainResult(CVMCalculationContext context,
                                     CVMSolverResult solverResult) {
        return CVMResult.fromSolver(
                context.getTemperature(),
                context.getComposition(),
                solverResult.getEquilibriumCFs(),
                solverResult.getGibbsEnergy(),
                solverResult.getEnthalpy(),
                solverResult.getEntropy(),
                solverResult.getIterations(),
                solverResult.getGradientNorm(),
                solverResult.isConverged()
        );
    }
}
