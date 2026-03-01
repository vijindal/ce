package org.ce.workbench.backend.job;

import org.ce.core.CVMConfiguration;
import org.ce.core.CVMPipeline;
import org.ce.core.CVMResult;
import org.ce.cvm.CMatrixBuilder;
import org.ce.cvm.CMatrixResult;
import org.ce.identification.geometry.Cluster;
import org.ce.input.InputLoader;
import org.ce.workbench.backend.data.AllClusterData;
import org.ce.workbench.gui.model.SystemInfo;
import org.ce.workbench.util.cache.AllClusterDataCache;
import org.ce.workbench.util.cache.ClusterDataCache;
import org.ce.identification.geometry.Vector3D;

import java.util.List;
import java.util.UUID;

/**
 * Background job for correlation function (CF) identification and C-matrix construction.
 * 
 * <p>Executes the three-stage identification pipeline:</p>
 * <ul>
 *   <li><b>Stage 1</b>: Cluster identification (via {@link CVMPipeline#identify})</li>
 *   <li><b>Stage 2</b>: CF identification (via {@link CVMPipeline#identify})</li>
 *   <li><b>Stage 3</b>: C-matrix construction (via {@link CMatrixBuilder#build})</li>
 * </ul>
 * 
 * <p>All three stages are required for a complete cluster data set ready for CVM calculations.</p>
 */
public class CFIdentificationJob extends AbstractBackgroundJob {
    
    private CVMResult result;
    private CMatrixResult cmatrixResult;
    private AllClusterData allData;
    private final String clusterKey;
    private final String disorderedClusterFile;
    private final String orderedClusterFile;
    private final String disorderedSymmetryGroup;
    private final String orderedSymmetryGroup;
    private final double[][] transformationMatrix;
    private final Vector3D translationVector;
    private final int numComponents;
    
    public CFIdentificationJob(
            SystemInfo system,
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
        long startTime = System.currentTimeMillis();
        
        if (cancelled) return;
        
        try {
            running = true;
            setStatusMessage("Building CVM configuration...");
            setProgress(10);
            
            if (cancelled) return;
            
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
            
            if (cancelled) return;
            
            setStatusMessage("Stage 1-2: Cluster and CF identification...");
            setProgress(30);
            
            if (cancelled) return;
            
            // ===== Stages 1-2: Run CVM pipeline (cluster + CF identification) =====
            this.result = CVMPipeline.identify(config);
            setProgress(70);
            
            if (cancelled) return;
            
            setStatusMessage("Stage 3: Constructing C-matrix...");
            setProgress(75);
            
            if (cancelled) return;
            
            // ===== Stage 3: Build C-matrix =====
            // Load ordered cluster data for C-matrix construction
            List<Cluster> ordMaxClus = InputLoader.parseClusterFile(orderedClusterFile);
            
            this.cmatrixResult = CMatrixBuilder.build(
                result.getClusterIdentification(),
                result.getCorrelationFunctionIdentification(),
                ordMaxClus,
                numComponents
            );
            
            setProgress(90);
            
            if (cancelled) return;
            
            // ===== Bundle all results =====
            long computationTime = System.currentTimeMillis() - startTime;
            this.allData = new AllClusterData(
                system.getId(),
                numComponents,
                result.getClusterIdentification(),
                result.getCorrelationFunctionIdentification(),
                cmatrixResult,
                computationTime
            );
            
            setStatusMessage("Finalizing cluster data...");
            setProgress(95);
            
            if (cancelled) return;
            
            // ===== Persist computed data =====
            if (clusterKey != null && !clusterKey.isEmpty()) {
                try {
                    // Save full AllClusterData (for CVM)
                    AllClusterDataCache.save(allData, clusterKey);
                    System.out.println("[CFIdentificationJob] AllClusterData saved to key: " + clusterKey);

                    // Also save the ClusCoordListResult (for MCS)
                    var disClus = result.getClusterIdentification().getDisClusterData();
                    ClusterDataCache.saveClusterData(disClus, clusterKey);
                    System.out.println("[CFIdentificationJob] ClusCoordListResult saved to key: " + clusterKey);
                } catch (Exception saveEx) {
                    System.err.println("[CFIdentificationJob] WARNING: save failed: " + saveEx.getMessage());
                    saveEx.printStackTrace();
                    // Don't fail the job for a save error — data is still in memory
                }
            }
            
            // Update system info
            system.setCfsComputed(true);
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
     * Returns the CVMResult from Stages 1-2 (cluster and CF identification).
     *
     * @return CVMResult; {@code null} if job failed or not yet completed
     */
    public CVMResult getResult() {
        return result;
    }
    
    /**
     * Returns the CMatrixResult from Stage 3 (C-matrix construction).
     *
     * @return CMatrixResult; {@code null} if job failed or not yet completed
     */
    public CMatrixResult getCMatrixResult() {
        return cmatrixResult;
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
