package org.ce.workbench.backend.job;

import org.ce.mcs.MCResult;
import org.ce.workbench.backend.service.CalculationProgressListener;
import org.ce.workbench.model.SystemIdentity;
import org.ce.workbench.util.context.MCSCalculationContext;
import org.ce.workbench.util.mcs.MCSExecutor;
import org.ce.workbench.util.mcs.MCSUpdate;

import java.util.UUID;
import java.util.concurrent.CancellationException;

/**
 * Background job for Monte Carlo Simulation (MCS) calculations.
 *
 * <p>Wraps {@link MCSExecutor} execution in a managed job that supports:</p>
 * <ul>
 *   <li>Progress tracking via {@link AbstractBackgroundJob}</li>
 *   <li>Cooperative cancellation via {@code cancelled} flag</li>
 *   <li>Proper lifecycle management through {@link BackgroundJobManager}</li>
 * </ul>
 *
 * <p>The job supports both cancellation and pause:</p>
 * <ul>
 *   <li>Cancellation: checked at the start of each Monte Carlo sweep via {@code cancelled} flag</li>
 *   <li>Pause: checked after each sweep in the progress callback via {@code checkPausePoint()}</li>
 * </ul>
 *
 * <p>Both allow clean interruption at natural boundaries without corrupting
 * calculation state.</p>
 */
public class MCSCalculationJob extends AbstractBackgroundJob {

    private final MCSCalculationContext context;
    private final CalculationProgressListener externalListener;
    private MCResult result;

    /**
     * Creates a new MCS calculation job.
     *
     * @param context The MCS calculation context containing all parameters
     * @param externalListener Optional external listener for progress updates (e.g., GUI)
     */
    public MCSCalculationJob(
            MCSCalculationContext context,
            CalculationProgressListener externalListener) {
        super(
            "mcs-" + context.getSystem().getId() + "-" + UUID.randomUUID(),
            "MCS: " + context.getSystem().getName() + 
                " (T=" + context.getTemperature() + "K, x=" + context.getComposition() + ")",
            context.getSystem()
        );
        this.context = context;
        this.externalListener = externalListener;
    }

    @Override
    public void run() {
        if (shouldStop()) return;

        try {
            running = true;
            setStatusMessage("Initializing MCS calculation...");
            setProgress(5);

            if (shouldStop()) return;

            // Create a bridge listener that updates both the job and external listener
            CalculationProgressListener bridgeListener = new CalculationProgressListener() {
                @Override
                public void logMessage(String message) {
                    if (externalListener != null) {
                        externalListener.logMessage(message);
                    }
                    // Could also log to job status if needed
                }

                @Override
                public void setProgress(double progress) {
                    // Map 0-1 to 5-95% (leave room for init and cleanup)
                    int jobProgress = 5 + (int)(progress * 90);
                    MCSCalculationJob.this.setProgress(jobProgress);
                    
                    if (externalListener != null) {
                        externalListener.setProgress(progress);
                    }
                }

                @Override
                public void initializeMCS(int equilibrationSteps, int averagingSteps, long seed) {
                    setStatusMessage("Running equilibration...");
                    if (externalListener != null) {
                        externalListener.initializeMCS(equilibrationSteps, averagingSteps, seed);
                    }
                }

                @Override
                public void updateMCSData(MCSUpdate update) {
                    // Check for pause at each sweep boundary
                    checkPausePoint();
                    
                    // Update status message based on phase
                    String phase = update.getPhase() == MCSUpdate.Phase.EQUILIBRATION 
                        ? "Equilibration" : "Averaging";
                    setStatusMessage(phase + " sweep " + update.getStep());
                    
                    if (externalListener != null) {
                        externalListener.updateMCSData(update);
                    }
                }
            };

            // Execute MCS with cancellation check
            setStatusMessage("Starting Monte Carlo simulation...");
            result = MCSExecutor.executeMCS(context, bridgeListener, () -> cancelled);

            if (shouldStop()) return;

            if (result != null) {
                setProgress(100);
                setStatusMessage("MCS calculation completed");
                markCompleted();
            } else {
                markFailed("MCS calculation returned null result");
            }

        } catch (CancellationException ex) {
            // Job was cancelled - this is expected behavior
            setStatusMessage("Calculation cancelled");
            // Don't call markFailed - let the cancel() method handle the state
            
        } catch (Exception e) {
            markFailed("MCS calculation failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            running = false;
        }
    }

    /**
     * Returns the calculation result.
     *
     * @return MCResult if completed successfully, null otherwise
     */
    public MCResult getResult() {
        return result;
    }

    /**
     * Returns the calculation context.
     *
     * @return the MCS calculation context
     */
    public MCSCalculationContext getContext() {
        return context;
    }

    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }
}
