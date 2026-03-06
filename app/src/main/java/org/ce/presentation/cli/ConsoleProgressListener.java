package org.ce.presentation.cli;

import org.ce.application.service.CalculationProgressListener;
import org.ce.domain.mcs.event.MCSUpdate;

/**
 * Console-based progress listener for CLI calculations.
 * 
 * <p>Writes progress updates to standard output, suitable for
 * command-line usage of the calculation service.</p>
 */
public class ConsoleProgressListener implements CalculationProgressListener {
    
    private static final int PROGRESS_BAR_WIDTH = 30;
    private double lastProgress = -1;
    
    @Override
    public void logMessage(String message) {
        System.out.println(message);
    }
    
    @Override
    public void setProgress(double progress) {
        // Only update if progress changed significantly (avoid flooding console)
        if (progress - lastProgress >= 0.05 || progress >= 1.0) {
            lastProgress = progress;
            int filled = (int) (progress * PROGRESS_BAR_WIDTH);
            int empty = PROGRESS_BAR_WIDTH - filled;
            String bar = "â–ˆ".repeat(Math.max(0, filled)) + "â–‘".repeat(Math.max(0, empty));
            System.out.printf("\rProgress: [%s] %3.0f%%", bar, progress * 100);
            if (progress >= 1.0) {
                System.out.println(); // Newline when complete
            }
        }
    }
    
    @Override
    public void initializeMCS(int equilibrationSteps, int averagingSteps, long seed) {
        System.out.println("\n--- MCS Parameters ---");
        System.out.println("Equilibration steps: " + equilibrationSteps);
        System.out.println("Averaging steps: " + averagingSteps);
        System.out.println("Random seed: " + seed);
        System.out.println("----------------------");
    }
    
    @Override
    public void updateMCSData(MCSUpdate update) {
        // Print periodic updates (not every single one to avoid flooding)
        if (update.getStep() % 1000 == 0) {
            System.out.printf("  Step %d [%s]: E=%.6f, acceptance=%.2f%%\n",
                update.getStep(), update.getPhase(), update.getE_total(), update.getAcceptanceRate() * 100);
        }
    }
}

