package org.ce.application.job;

import org.ce.domain.cvm.CVMPhaseModel;
import org.ce.application.port.CalculationProgressListener;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.logging.LoggingConfig;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Logger LOG = LoggingConfig.getLogger(CVMPhaseModelJob.class);

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
            SystemIdentity system,
            CalculationProgressListener externalListener) {
        super(
            "cvm-model-" + UUID.randomUUID(),
            "CVM: " + system.getName() +
                " (T=" + model.getTemperature() + "K)",
            system
        );
        this.model = model;
        this.externalListener = externalListener;
    }
    
    @Override
    public void run() {
        if (shouldStop()) return;

        LOG.info("CVMPhaseModelJob.run — ENTER: job=" + getId()
                + ", system=" + system.getId() + ", T=" + model.getTemperature() + " K");

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
            boolean ok = org.ce.infrastructure.cvm.CVMPhaseModelExecutor.initializeModel(
                model, bridgeListener);

            if (!ok) {
                LOG.warning("CVMPhaseModelJob.run — EXIT: FAILED — CVM Phase Model initialization failed");
                markFailed("CVM Phase Model initialization failed");
                return;
            }
            
            if (shouldStop()) return;
            
            setProgress(100);
            setStatusMessage("CVM Phase Model ready for queries");
            LOG.info("CVMPhaseModelJob.run — EXIT: COMPLETED — system=" + system.getId()
                    + ", T=" + model.getTemperature() + " K");
            markCompleted();
            
        } catch (Exception e) {
            LOG.log(Level.WARNING, "CVMPhaseModelJob.run — EXCEPTION: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            markFailed("CVM Phase Model job failed: " + e.getMessage());
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

