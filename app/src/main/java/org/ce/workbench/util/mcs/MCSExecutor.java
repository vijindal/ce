package org.ce.workbench.util.mcs;

import org.ce.mcs.MCSRunner;
import org.ce.mcs.MCResult;
import org.ce.workbench.backend.service.CalculationProgressListener;
import org.ce.workbench.util.context.MCSCalculationContext;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * Executor for MCS calculations.
 * Bridges between MCSCalculationContext and MCSRunner.
 * 
 * @deprecated Use {@link org.ce.application.calculation.MCSCalculationUseCase} instead.
 *             This class will be removed in a future release.
 */
@Deprecated(since = "2.0", forRemoval = true)
public class MCSExecutor {
    
    /**
     * Executes MCS calculation based on context.
     * 
     * @param context The calculation context with all required data
     * @param listener Progress listener for logging and updates
     * @return Optional MCResult if execution succeeds
     */
    public static MCResult executeMCS(MCSCalculationContext context, CalculationProgressListener listener) {
        return executeMCS(context, listener, null);
    }
    
    /**
     * Executes MCS calculation based on context with optional cancellation support.
     * 
     * @param context The calculation context with all required data
     * @param listener Progress listener for logging and updates
     * @param cancellationCheck Optional supplier that returns true if cancelled
     * @return Optional MCResult if execution succeeds
     */
    public static MCResult executeMCS(
            MCSCalculationContext context, 
            CalculationProgressListener listener,
            BooleanSupplier cancellationCheck) {
        if (!context.isReady()) {
            listener.logMessage("⚠ Calculation context not ready (missing cluster data or ECI)");
            return null;
        }
        
        listener.logMessage("\n" + "=".repeat(60));
        listener.logMessage("Starting MCS Calculation");
        listener.logMessage("=".repeat(60));
        listener.logMessage(context.getSummary());
        
        try {
            // Log calculation parameters
            listener.logMessage("Building MCS runner with parameters:");
            listener.logMessage("  Temperature: " + context.getTemperature() + " K");
            listener.logMessage("  Composition (x): " + context.getComposition());
            listener.logMessage("  Supercell size (L): " + context.getSupercellSize());
            listener.logMessage("  Equilibration steps: " + context.getEquilibrationSteps());
            listener.logMessage("  Averaging steps: " + context.getAveragingSteps());
            listener.logMessage("  Random seed: " + context.getSeed());
            
            // Build and run MCS
            listener.logMessage("\nBuilding MCS configuration...");
            
            // Gas constant for J/(mol·K) energies at T in Kelvin
            // R = 8.314 J/(mol·K) ensures correct Boltzmann statistics
            final double GAS_CONSTANT = 8.314;  // J/(mol·K)
            
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
                .updateListener(listener::updateMCSData)
                .R(GAS_CONSTANT);  // Set correct gas constant for J/mol energies
            
            if (cancellationCheck != null) {
                builder.cancellationCheck(cancellationCheck);
            }
            
            MCSRunner runner = builder.build();
            
            listener.logMessage("Configuration built. Starting Monte Carlo simulation...");
            listener.setProgress(0.2);
            listener.initializeMCS(
                context.getEquilibrationSteps(),
                context.getAveragingSteps(),
                context.getSeed()
            );
            
            // Execute MCS
            long startTime = System.currentTimeMillis();
            MCResult result = runner.run();
            long endTime = System.currentTimeMillis();
            
            listener.setProgress(1.0);
            
            // Log results
            listener.logMessage("\n" + "-".repeat(60));
            listener.logMessage("MCS Calculation Complete");
            listener.logMessage("-".repeat(60));
            listener.logMessage("Execution time: " + (endTime - startTime) + " ms");
            listener.logMessage("\nResults:");
            listener.logMessage("  Average CFs: ");
            double[] avgCFs = result.getAvgCFs();
            for (int i = 0; i < Math.min(5, avgCFs.length); i++) {
                listener.logMessage("    CF[" + i + "]: " + String.format("%.6f", avgCFs[i]));
            }
            if (avgCFs.length > 5) {
                listener.logMessage("    ... (" + (avgCFs.length - 5) + " more CFs)");
            }
            
            listener.logMessage("  Average Energy (per site): " + String.format("%.6f", result.getEnergyPerSite()) + " eV");
            listener.logMessage("  Heat Capacity (per site): " + String.format("%.6f", result.getHeatCapacityPerSite()) + " eV/K");
            listener.logMessage("  Acceptance Rate: " + String.format("%.2f", result.getAcceptRate() * 100) + "%");
            
            listener.logMessage("\n✓ MCS calculation succeeded");
            listener.logMessage("=".repeat(60));
            
            return result;
            
        } catch (CancellationException ex) {
            // Rethrow cancellation so the job handler can mark it as cancelled
            listener.logMessage("\n⊘ MCS Calculation Cancelled");
            listener.logMessage(ex.getMessage());
            throw ex;
            
        } catch (Exception ex) {
            listener.logMessage("\n✗ MCS Calculation Failed");
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
