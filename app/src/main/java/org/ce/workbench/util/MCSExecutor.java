package org.ce.workbench.util;

import org.ce.mcs.MCSRunner;
import org.ce.mcs.MCResult;
import org.ce.workbench.gui.view.ResultsPanel;

/**
 * Executor for MCS calculations from GUI context.
 * Bridges between CalculationContext and MCSRunner.
 */
public class MCSExecutor {
    
    /**
     * Executes MCS calculation based on context.
     * 
     * @param context The calculation context with all required data
     * @param resultsPanel Panel to log progress and results
     * @return Optional MCResult if execution succeeds
     */
    public static MCResult executeMCS(CalculationContext context, ResultsPanel resultsPanel) {
        if (!context.isReady()) {
            resultsPanel.logMessage("⚠ Calculation context not ready (missing cluster data or ECI)");
            return null;
        }
        
        resultsPanel.logMessage("\n" + "=".repeat(60));
        resultsPanel.logMessage("Starting MCS Calculation");
        resultsPanel.logMessage("=".repeat(60));
        resultsPanel.logMessage(context.getSummary());
        
        try {
            // Log calculation parameters
            resultsPanel.logMessage("Building MCS runner with parameters:");
            resultsPanel.logMessage("  Temperature: " + context.getTemperature() + " K");
            resultsPanel.logMessage("  Composition (x): " + context.getComposition());
            resultsPanel.logMessage("  Supercell size (L): " + context.getSupercellSize());
            resultsPanel.logMessage("  Equilibration steps: " + context.getEquilibrationSteps());
            resultsPanel.logMessage("  Averaging steps: " + context.getAveragingSteps());
            resultsPanel.logMessage("  Random seed: " + context.getSeed());
            
            // Build and run MCS
            resultsPanel.logMessage("\nBuilding MCS configuration...");
            
            // Gas constant for J/(mol·K) energies at T in Kelvin
            // R = 8.314 J/(mol·K) ensures correct Boltzmann statistics
            final double GAS_CONSTANT = 8.314;  // J/(mol·K)
            
            MCSRunner runner = MCSRunner.builder()
                .clusterData(context.getClusterData())
                .eci(context.getECI())
                .numComp(context.getSystem().getNumComponents())
                .T(context.getTemperature())
                .compositionBinary(context.getComposition())
                .nEquil(context.getEquilibrationSteps())
                .nAvg(context.getAveragingSteps())
                .L(context.getSupercellSize())
                .seed(context.getSeed())
                .updateListener(resultsPanel::updateMCSData)
                .R(GAS_CONSTANT)  // Set correct gas constant for J/mol energies
                .build();
            
            resultsPanel.logMessage("Configuration built. Starting Monte Carlo simulation...");
            resultsPanel.setProgress(0.2);
            resultsPanel.initializeMCS(
                context.getEquilibrationSteps(),
                context.getAveragingSteps(),
                context.getSeed()
            );
            
            // Execute MCS
            long startTime = System.currentTimeMillis();
            MCResult result = runner.run();
            long endTime = System.currentTimeMillis();
            
            resultsPanel.setProgress(1.0);
            
            // Log results
            resultsPanel.logMessage("\n" + "-".repeat(60));
            resultsPanel.logMessage("MCS Calculation Complete");
            resultsPanel.logMessage("-".repeat(60));
            resultsPanel.logMessage("Execution time: " + (endTime - startTime) + " ms");
            resultsPanel.logMessage("\nResults:");
            resultsPanel.logMessage("  Average CFs: ");
            double[] avgCFs = result.getAvgCFs();
            for (int i = 0; i < Math.min(5, avgCFs.length); i++) {
                resultsPanel.logMessage("    CF[" + i + "]: " + String.format("%.6f", avgCFs[i]));
            }
            if (avgCFs.length > 5) {
                resultsPanel.logMessage("    ... (" + (avgCFs.length - 5) + " more CFs)");
            }
            
            resultsPanel.logMessage("  Average Energy (per site): " + String.format("%.6f", result.getEnergyPerSite()) + " eV");
            resultsPanel.logMessage("  Heat Capacity (per site): " + String.format("%.6f", result.getHeatCapacityPerSite()) + " eV/K");
            resultsPanel.logMessage("  Acceptance Rate: " + String.format("%.2f", result.getAcceptRate() * 100) + "%");
            
            resultsPanel.logMessage("\n✓ MCS calculation succeeded");
            resultsPanel.logMessage("=".repeat(60));
            
            return result;
            
        } catch (Exception ex) {
            resultsPanel.logMessage("\n✗ MCS Calculation Failed");
            resultsPanel.logMessage("Error: " + ex.getClass().getSimpleName());
            resultsPanel.logMessage("Message: " + ex.getMessage());
            
            // Log stack trace for debugging
            StringBuilder stackTrace = new StringBuilder();
            for (StackTraceElement element : ex.getStackTrace()) {
                stackTrace.append("  at ").append(element).append("\n");
            }
            resultsPanel.logMessage(stackTrace.toString());
            
            resultsPanel.setProgress(0);
            return null;
        }
    }
}
