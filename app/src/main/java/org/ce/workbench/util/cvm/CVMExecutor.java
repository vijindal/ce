package org.ce.workbench.util.cvm;

import org.ce.cvm.CVMEngine;
import org.ce.cvm.CVMSolverResult;
import org.ce.workbench.backend.service.CalculationProgressListener;
import org.ce.workbench.util.context.CVMCalculationContext;

/**
 * Executor for CVM calculations.
 * Bridges between CVMCalculationContext and CVMEngine.
 * 
 * <p>Mirrors the MCSExecutor pattern: takes a context + listener,
 * calls the underlying solver, and logs results.</p>
 * 
 * @deprecated Use {@link org.ce.application.calculation.CVMCalculationUseCase} instead.
 *             This class will be removed in a future release.
 */
@Deprecated(since = "2.0", forRemoval = true)
public class CVMExecutor {
    
    /**
     * Executes CVM free-energy minimization based on context.
     * 
     * @param context The CVM calculation context with AllClusterData + ECI + T + x
     * @param listener Progress listener for logging and updates
     * @return CVMSolverResult if execution succeeds, null otherwise
     */
    public static CVMSolverResult executeCVM(CVMCalculationContext context, CalculationProgressListener listener) {
        if (!context.isReady()) {
            listener.logMessage("⚠ CVM context not ready: " + context.getReadinessError());
            return null;
        }
        
        listener.logMessage("\n" + "=".repeat(60));
        listener.logMessage("Starting CVM Free-Energy Minimization");
        listener.logMessage("=".repeat(60));
        listener.logMessage(context.getSummary());
        
        try {
            // Log calculation parameters
            listener.logMessage("CVM parameters:");
            listener.logMessage("  Temperature: " + context.getTemperature() + " K");
            listener.logMessage("  Composition (x): " + context.getComposition());
            listener.logMessage("  Tolerance: " + context.getTolerance());
            listener.logMessage("  Number of CFs (ncf): " + context.getAllClusterData().getStage2().getNcf());
            listener.logMessage("  Total CFs (tcf): " + context.getAllClusterData().getStage2().getTcf());
            listener.logMessage("  Cluster types (tcdis): " + context.getAllClusterData().getStage1().getTcdis());
            
            listener.logMessage("\nRunning Newton-Raphson solver...");
            listener.setProgress(0.3);
            
            // Execute CVM solver
            long startTime = System.currentTimeMillis();
            CVMSolverResult result = CVMEngine.solve(context);
            long endTime = System.currentTimeMillis();
            
            listener.setProgress(1.0);
            
            // Log results
            listener.logMessage("\n" + "-".repeat(60));
            if (result.isConverged()) {
                listener.logMessage("CVM Calculation Complete - CONVERGED");
            } else {
                listener.logMessage("CVM Calculation Complete - DID NOT CONVERGE");
            }
            listener.logMessage("-".repeat(60));
            listener.logMessage("Execution time: " + (endTime - startTime) + " ms");
            listener.logMessage("Iterations: " + result.getIterations());
            listener.logMessage("Final gradient norm: " + String.format("%.6e", result.getGradientNorm()));
            
            listener.logMessage("\nThermodynamic Results:");
            listener.logMessage("  Gibbs Energy (G): " + String.format("%.8e", result.getGibbsEnergy()));
            listener.logMessage("  Enthalpy (H):     " + String.format("%.8e", result.getEnthalpy()));
            listener.logMessage("  Entropy (S):      " + String.format("%.8e", result.getEntropy()));
            listener.logMessage("  -T·S:             " + String.format("%.8e", -context.getTemperature() * result.getEntropy()));
            
            listener.logMessage("\nEquilibrium Correlation Functions:");
            double[] eqCFs = result.getEquilibriumCFs();
            for (int i = 0; i < Math.min(5, eqCFs.length); i++) {
                listener.logMessage("  CF[" + i + "]: " + String.format("%.10f", eqCFs[i]));
            }
            if (eqCFs.length > 5) {
                listener.logMessage("  ... (" + (eqCFs.length - 5) + " more CFs)");
            }
            
            if (result.isConverged()) {
                listener.logMessage("\n✓ CVM calculation succeeded");
            } else {
                listener.logMessage("\n⚠ CVM calculation finished but did not converge to tolerance");
            }
            listener.logMessage("=".repeat(60));
            
            return result;
            
        } catch (Exception ex) {
            listener.logMessage("\n✗ CVM Calculation Failed");
            listener.logMessage("Error: " + ex.getClass().getSimpleName());
            listener.logMessage("Message: " + ex.getMessage());
            
            // Log stack trace for debugging
            StringBuilder stackTrace = new StringBuilder();
            for (StackTraceElement element : ex.getStackTrace()) {
                stackTrace.append("  at ").append(element).append("\n");
            }
            listener.logMessage(stackTrace.toString());
            
            listener.setProgress(0);
            return null;
        }
    }
}
