package org.ce.app.gui.backend.jobs;

import org.ce.app.CVMConfiguration;
import org.ce.app.CVMPipeline;
import org.ce.app.CVMResult;
import org.ce.app.gui.models.SystemInfo;
import org.ce.identification.engine.Vector3D;

import java.util.UUID;

/**
 * Background job for correlation function (CF) identification.
 * Wraps the full CVM pipeline up to CF identification.
 */
public class CFIdentificationJob extends AbstractBackgroundJob {
    
    private CVMResult result;
    private final String disorderedClusterFile;
    private final String orderedClusterFile;
    private final String disorderedSymmetryGroup;
    private final String orderedSymmetryGroup;
    private final double[][] transformationMatrix;
    private final Vector3D translationVector;
    private final int numComponents;
    
    public CFIdentificationJob(
            SystemInfo system,
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
            
            setStatusMessage("Stage 1: Cluster identification...");
            setProgress(30);
            
            if (cancelled) return;
            
            // Run CVM pipeline (cluster + CF identification)
            this.result = CVMPipeline.identify(config);
            setProgress(80);
            
            if (cancelled) return;
            
            setStatusMessage("Finalizing correlation function data...");
            setProgress(95);
            
            if (cancelled) return;
            
            // Update system info
            system.setCfsComputed(true);
            setProgress(100);
            setStatusMessage("CF identification completed");
            
            markCompleted();
            
        } catch (Exception e) {
            markFailed("CF identification failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            running = false;
        }
    }
    
    public CVMResult getResult() {
        return result;
    }
    
    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }
}
