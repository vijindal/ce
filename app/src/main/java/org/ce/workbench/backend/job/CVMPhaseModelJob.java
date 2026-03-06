package org.ce.workbench.backend.job;

import org.ce.cvm.CVMPhaseModel;
import org.ce.workbench.backend.service.CalculationProgressListener;

import java.util.UUID;

/**
 * Background job for CVM Phase Model (model-centric) calculations.
 * 
 * <p>CVMPhaseModelJob wraps a CVMPhaseModel for parameter scanning and
 * interactive querying.</p>
 * 
 * <p>The job initializes the model and reports completion, but does not
 * perform parameter mutations itself. Mutations are done externally
 * (e.g., by the GUI) and trigger automatic re-minimization in the model.</p>
 */
public class CVMPhaseModelJob extends AbstractBackgroundJob {
    
    private final CVMPhaseModel model;
    private final CalculationProgressListener externalListener;
    
    /**
     * Creates a new CVM Phase Model job.
     * 
     * @param model The CVMPhaseModel ready for queries
     * @param externalListener Optional external listener for progress updates
     */
    public CVMPhaseModelJob(
            CVMPhaseModel model,
            CalculationProgressListener externalListener) {
        super(
            "cvm-model-" + UUID.randomUUID(),
            "CVM: " + model.getSystem().getName() +
                " (T=" + model.getTemperature() + "K)",
            model.getSystem()
        );
        this.model = model;
        this.externalListener = externalListener;
    }
    
    @Override
    public void run() {
        if (shouldStop()) return;
        
        try {
            running = true;
            setStatusMessage("Initializing CVM Phase Model...");
            setProgress(10);
            
            if (shouldStop()) return;
            
            // Create bridge listener
            CalculationProgressListener bridgeListener = new CalculationProgressListener() {
                @Override
                public void logMessage(String message) {
                    if (externalListener != null) {
                        externalListener.logMessage(message);
                    }
                }
                
                @Override
                public void setProgress(double progress) {
                    int jobProgress = 10 + (int)(progress * 85);
                    CVMPhaseModelJob.this.setProgress(jobProgress);
                    if (externalListener != null) {
                        externalListener.setProgress(progress);
                    }
                }
            };
            
            if (shouldStop()) return;
            
            // Initialize model (model is already minimized in create())
            boolean ok = org.ce.workbench.util.cvm.CVMPhaseModelExecutor.initializeModel(
                model, bridgeListener);

            if (!ok) {
                markFailed("CVM Phase Model initialization failed");
                return;
            }
            
            if (shouldStop()) return;
            
            setProgress(100);
            setStatusMessage("CVM Phase Model ready for queries");
            markCompleted();
            
        } catch (Exception e) {
            markFailed("CVM Phase Model job failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            running = false;
        }
    }
    
    /**
     * Returns the CVMPhaseModel.
     * 
     * @return the CVM phase model ready for parameter queries
     */
    public CVMPhaseModel getModel() {
        return model;
    }
    
    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }
}
