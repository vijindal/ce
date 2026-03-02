package org.ce.workbench.backend.job;

import org.ce.cvm.CVMSolverResult;
import org.ce.workbench.backend.service.CalculationProgressListener;
import org.ce.workbench.model.SystemIdentity;
import org.ce.workbench.util.context.CVMCalculationContext;
import org.ce.workbench.util.cvm.CVMExecutor;

import java.util.UUID;

/**
 * Background job for CVM (Cluster Variation Method) calculations.
 *
 * <p>Wraps {@link CVMExecutor} execution in a managed job that supports:</p>
 * <ul>
 *   <li>Progress tracking via {@link AbstractBackgroundJob}</li>
 *   <li>Cooperative pause/cancel via {@code checkPausePoint()} and {@code shouldStop()}</li>
 *   <li>Proper lifecycle management through {@link BackgroundJobManager}</li>
 * </ul>
 *
 * <p>CVM calculations are typically fast (single Newton-Raphson solve) so
 * pause checking happens at job boundaries rather than during iteration.</p>
 */
public class CVMCalculationJob extends AbstractBackgroundJob {

    private final CVMCalculationContext context;
    private final CalculationProgressListener externalListener;
    private CVMSolverResult result;

    /**
     * Creates a new CVM calculation job.
     *
     * @param context The CVM calculation context containing all parameters
     * @param externalListener Optional external listener for progress updates (e.g., GUI)
     */
    public CVMCalculationJob(
            CVMCalculationContext context,
            CalculationProgressListener externalListener) {
        super(
            "cvm-" + context.getSystem().getId() + "-" + UUID.randomUUID(),
            "CVM: " + context.getSystem().getName() + 
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
            setStatusMessage("Initializing CVM calculation...");
            setProgress(5);

            if (shouldStop()) return;

            // Create a bridge listener that updates both the job and external listener
            CalculationProgressListener bridgeListener = new CalculationProgressListener() {
                @Override
                public void logMessage(String message) {
                    if (externalListener != null) {
                        externalListener.logMessage(message);
                    }
                }

                @Override
                public void setProgress(double progress) {
                    // Map 0-1 to 5-95% (leave room for init and cleanup)
                    int jobProgress = 5 + (int)(progress * 90);
                    CVMCalculationJob.this.setProgress(jobProgress);
                    
                    if (externalListener != null) {
                        externalListener.setProgress(progress);
                    }
                }
            };

            if (shouldStop()) return;

            // Execute CVM
            setStatusMessage("Running Newton-Raphson solver...");
            result = CVMExecutor.executeCVM(context, bridgeListener);

            if (shouldStop()) return;

            if (result != null) {
                setProgress(100);
                if (result.isConverged()) {
                    setStatusMessage("CVM calculation converged");
                } else {
                    setStatusMessage("CVM finished (did not converge)");
                }
                markCompleted();
            } else {
                markFailed("CVM calculation returned null result");
            }

        } catch (Exception e) {
            markFailed("CVM calculation failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            running = false;
        }
    }

    /**
     * Returns the calculation result.
     *
     * @return CVMSolverResult if completed successfully, null otherwise
     */
    public CVMSolverResult getResult() {
        return result;
    }

    /**
     * Returns the calculation context.
     *
     * @return the CVM calculation context
     */
    public CVMCalculationContext getContext() {
        return context;
    }

    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }
}
