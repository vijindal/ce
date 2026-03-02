package org.ce.workbench.backend.job;

import org.ce.core.CVMConfiguration;
import org.ce.core.CVMPipeline;
import org.ce.cvm.CMatrixResult;
import org.ce.workbench.backend.data.AllClusterData;
import org.ce.workbench.backend.registry.SystemRegistry;
import org.ce.workbench.model.SystemIdentity;
import org.ce.workbench.util.cache.AllClusterDataCache;
import org.ce.identification.geometry.Vector3D;

import java.util.UUID;

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
    }
    
    @Override
    public void run() {
        if (shouldStop()) return;
        
        try {
            running = true;
            setStatusMessage("Building CVM configuration...");
            setProgress(10);
            
            if (shouldStop()) return;
            
            // Build configuration
            CVMConfiguration config = CVMConfiguration.builder()
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
            
            setStatusMessage("Running identification pipeline (Stages 1-3)...");
            setProgress(30);
            
            if (shouldStop()) return;
            
            // ===== Run complete pipeline: Stages 1-3 in one call =====
            // No duplicate file parsing - all done inside CVMPipeline.identify()
            this.allData = CVMPipeline.identify(config);
            
            // Update with system ID (not available to CVMPipeline)
            this.allData = new AllClusterData(
                system.getId(),
                numComponents,
                allData.getStage1(),
                allData.getStage2(),
                allData.getStage3(),
                allData.getComputationTimeMs()
            );
            
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
                    System.out.println("[CFIdentificationJob] AllClusterData saved to key: " + clusterKey);
                    persistSuccess = true;
                } catch (Exception saveEx) {
                    System.err.println("[CFIdentificationJob] WARNING: cache save failed: " + saveEx.getMessage());
                    saveEx.printStackTrace();
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
            
            markCompleted();
            
        } catch (Exception e) {
            markFailed("Identification pipeline failed: " + e.getMessage());
            e.printStackTrace();
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
}
