package org.ce.application.job;

import org.ce.application.port.IdentificationProgressListener;
import org.ce.application.pipeline.IdentificationRequest;
import org.ce.application.pipeline.IdentificationPipeline;
import org.ce.domain.cvm.CMatrixResult;
import org.ce.domain.model.data.AllClusterData;
import org.ce.infrastructure.registry.SystemRegistry;
import org.ce.domain.system.SystemIdentity;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.ce.domain.identification.geometry.Vector3D;

import org.ce.infrastructure.logging.LoggingConfig;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Background job for the complete CVM identification pipeline.
 * 
 * <p>Executes all three stages in a single call to {@link CVMPipeline#identify}:</p>
 * <ul>
 *   <li><b>Stage 1</b>: Cluster identification</li>
 *   <li><b>Stage 2</b>: CF identification</li>
 *   <li><b>Stage 3</b>: C-matrix construction</li>
 * </ul>
 * 
 * <p>Supports cooperative pause and cancellation via {@code checkPausePoint()} and
 * {@code shouldStop()} at each progress checkpoint.</p>
 *
 * <p>All stages are bundled in the returned {@link AllClusterData}, ready for CVM calculations.</p>
 */
public class CFIdentificationJob extends AbstractBackgroundJob {

    private static final Logger LOG = LoggingConfig.getLogger(CFIdentificationJob.class);

    private AllClusterData allData;
    private final String clusterKey;
    private final String disorderedClusterFile;
    private final String orderedClusterFile;
    private final String disorderedSymmetryGroup;
    private final String orderedSymmetryGroup;
    private final double[][] transformationMatrix;
    private final Vector3D translationVector;
    private final int numComponents;
    private final SystemRegistry registry;  // For thread-safe status updates
    private final IdentificationProgressListener progressListener; // Optional; may be null
    
    public CFIdentificationJob(
            SystemIdentity system,
            SystemRegistry registry,
            String clusterKey,
            String disorderedClusterFile,
            String orderedClusterFile,
            String disorderedSymmetryGroup,
            String orderedSymmetryGroup,
            double[][] transformationMatrix,
            Vector3D translationVector,
            int numComponents) {
        this(system, registry, clusterKey, disorderedClusterFile, orderedClusterFile,
             disorderedSymmetryGroup, orderedSymmetryGroup, transformationMatrix,
             translationVector, numComponents, null);
    }

    /** Full constructor including an optional {@link IdentificationProgressListener}. */
    public CFIdentificationJob(
            SystemIdentity system,
            SystemRegistry registry,
            String clusterKey,
            String disorderedClusterFile,
            String orderedClusterFile,
            String disorderedSymmetryGroup,
            String orderedSymmetryGroup,
            double[][] transformationMatrix,
            Vector3D translationVector,
            int numComponents,
            IdentificationProgressListener progressListener) {
        
        super(
            "cf-" + system.getId() + "-" + UUID.randomUUID(),
            "CF Identification: " + system.getName() + " (" + numComponents + " components)",
            system
        );
        
        this.registry = registry;
        this.clusterKey = clusterKey;
        this.disorderedClusterFile = disorderedClusterFile;
        this.orderedClusterFile = orderedClusterFile;
        this.disorderedSymmetryGroup = disorderedSymmetryGroup;
        this.orderedSymmetryGroup = orderedSymmetryGroup;
        this.transformationMatrix = transformationMatrix;
        this.translationVector = translationVector;
        this.numComponents = numComponents;
        this.progressListener = progressListener;
    }
    
    @Override
    public void run() {
        long jStart = System.currentTimeMillis();
        LOG.info("CFIdentificationJob.run — ENTER: job=" + getId() + ", system=" + system.getId()
                + ", numComponents=" + numComponents + ", clusterKey=" + clusterKey);
        if (shouldStop()) return;
        
        try {
            running = true;
            setStatusMessage("Building CVM configuration...");
            setProgress(10);
            
            if (shouldStop()) return;
            
            // Build configuration
            IdentificationRequest config = IdentificationRequest.builder()
                .disorderedClusterFile(disorderedClusterFile)
                .orderedClusterFile(orderedClusterFile)
                .disorderedSymmetryGroup(disorderedSymmetryGroup)
                .orderedSymmetryGroup(orderedSymmetryGroup)
                .transformationMatrix(transformationMatrix)
                .translationVector(translationVector)
                .numComponents(numComponents)
                .build();
            
            setProgress(20);
            
            if (shouldStop()) return;
            
            fireStageStarted(1, "Cluster identification");
            setStatusMessage("Running identification pipeline (Stages 1-3)...");
            setProgress(30);

            if (shouldStop()) return;

            // ===== Run complete pipeline: Stages 1-3 in one call =====
            // No duplicate file parsing - all done inside CVMPipeline.identify()
            this.allData = IdentificationPipeline.identify(config);
            
            // Update with system ID (not available to CVMPipeline)
            this.allData = new AllClusterData(
                system.getId(),
                numComponents,
                allData.getStage1(),
                allData.getStage2(),
                allData.getStage3(),
                allData.getComputationTimeMs()
            );

            // Fire per-stage completion callbacks with summary info
            fireStageCompleted(1, "Stage 1: tcdis=" + allData.getStage1().getTcdis());
            fireStageStarted(2, "CF identification");
            fireStageCompleted(2, "Stage 2: ncf=" + allData.getStage2().getNcf()
                    + ", tcf=" + allData.getStage2().getTcf());
            fireStageStarted(3, "C-matrix construction");
            fireStageCompleted(3, "Stage 3: C-matrix built");
            
            setProgress(90);
            
            if (shouldStop()) return;
            
            setStatusMessage("Finalizing cluster data...");
            setProgress(95);
            
            if (shouldStop()) return;
            
            // ===== Persist computed data atomically =====
            boolean persistSuccess = false;
            if (clusterKey != null && !clusterKey.isEmpty()) {
                try {
                    // Save AllClusterData - the single source of truth for both CVM and MCS.
                    // MCS extracts ClusCoordListResult via stage1.getDisClusterData().
                    AllClusterDataCache.save(allData, clusterKey);
                    LOG.info("AllClusterData saved to key: " + clusterKey);
                    persistSuccess = true;
                } catch (Exception saveEx) {
                    LOG.log(Level.WARNING, "Cache save failed for key: " + clusterKey, saveEx);
                    // Data is still in memory, but cfsComputed won't be set
                }
            } else {
                // No clusterKey - data is only in memory
                persistSuccess = true;
            }
            
            // Only mark CFs computed if persistence succeeded (or no persistence was needed)
            if (persistSuccess && registry != null) {
                registry.markCfsComputed(system.getId(), true);
            }
            setProgress(100);
            setStatusMessage("All identification stages completed");
            LOG.info("CFIdentificationJob.run — EXIT: COMPLETED — job=" + getId()
                    + ", tcdis=" + allData.getStage1().getTcdis()
                    + ", tcf=" + allData.getStage2().getTcf()
                    + ", ncf=" + allData.getStage2().getNcf()
                    + ", elapsed=" + (System.currentTimeMillis() - jStart) + " ms");
            markCompleted();
            firePipelineCompleted(allData);
            
        } catch (Exception e) {
            LOG.log(Level.WARNING, "CFIdentificationJob.run — EXCEPTION in identification pipeline: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            String errMsg = "Identification pipeline failed: " + e.getMessage();
            markFailed(errMsg);
            firePipelineFailed(errMsg);
        } finally {
            running = false;
        }
    }
    
    /**
     * Returns the element-independent cluster key (e.g. "BCC_A2_T_bin").
     */
    public String getClusterKey() {
        return clusterKey;
    }
    
    /**
     * Returns the CMatrixResult from Stage 3 (C-matrix construction).
     *
     * @return CMatrixResult; {@code null} if job failed or not yet completed
     */
    public CMatrixResult getCMatrixResult() {
        return (allData != null) ? allData.getStage3() : null;
    }
    
    /**
     * Returns all cluster data bundled together (Stages 1-3).
     *
     * <p>This is the complete data package ready for CVM calculations.</p>
     *
     * @return AllClusterData; {@code null} if job failed or not yet completed
     */
    public AllClusterData getAllClusterData() {
        return allData;
    }
    
    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }

    // -----------------------------------------------------------------------
    // Listener fire helpers — guarded against null listener
    // -----------------------------------------------------------------------

    private void fireStageStarted(int stage, String description) {
        if (progressListener != null) {
            try { progressListener.onStageStarted(stage, description); }
            catch (Exception ex) { LOG.warning("IdentificationProgressListener.onStageStarted threw: " + ex.getMessage()); }
        }
    }

    private void fireStageCompleted(int stage, String summary) {
        if (progressListener != null) {
            try { progressListener.onStageCompleted(stage, summary); }
            catch (Exception ex) { LOG.warning("IdentificationProgressListener.onStageCompleted threw: " + ex.getMessage()); }
        }
    }

    private void firePipelineCompleted(AllClusterData result) {
        if (progressListener != null) {
            try { progressListener.onPipelineCompleted(result); }
            catch (Exception ex) { LOG.warning("IdentificationProgressListener.onPipelineCompleted threw: " + ex.getMessage()); }
        }
    }

    private void firePipelineFailed(String errorMessage) {
        if (progressListener != null) {
            try { progressListener.onPipelineFailed(errorMessage); }
            catch (Exception ex) { LOG.warning("IdentificationProgressListener.onPipelineFailed threw: " + ex.getMessage()); }
        }
    }
}


