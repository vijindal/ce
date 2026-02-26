package org.ce.app.gui.backend.jobs;

import org.ce.app.CVMConfiguration;
import org.ce.app.gui.models.SystemInfo;
import org.ce.input.InputLoader;
import org.ce.identification.engine.Cluster;
import org.ce.identification.engine.ClusCoordListGenerator;
import org.ce.identification.engine.ClusCoordListResult;
import org.ce.identification.engine.Vector3D;

import java.util.List;
import java.util.UUID;

/**
 * Background job for cluster identification.
 * Wraps the CVM pipeline's cluster identification stage.
 */
@SuppressWarnings("unchecked")
public class ClusterIdentificationJob extends AbstractBackgroundJob {
    
    private ClusCoordListResult result;
    private final String disorderedClusterFile;
    private final String orderedClusterFile;
    private final String disorderedSymmetryGroup;
    private final String orderedSymmetryGroup;
    private final double[][] transformationMatrix;
    private final Vector3D translationVector;
    
    public ClusterIdentificationJob(
            SystemInfo system,
            String disorderedClusterFile,
            String orderedClusterFile,
            String disorderedSymmetryGroup,
            String orderedSymmetryGroup,
            double[][] transformationMatrix,
            Vector3D translationVector) {
        
        super(
            "cluster-" + system.getId() + "-" + UUID.randomUUID(),
            "Cluster Identification: " + system.getName(),
            system
        );
        
        this.disorderedClusterFile = disorderedClusterFile;
        this.orderedClusterFile = orderedClusterFile;
        this.disorderedSymmetryGroup = disorderedSymmetryGroup;
        this.orderedSymmetryGroup = orderedSymmetryGroup;
        this.transformationMatrix = transformationMatrix;
        this.translationVector = translationVector;
    }
    
    @Override
    public void run() {
        if (cancelled) return;
        
        try {
            running = true;
            setStatusMessage("Loading cluster files...");
            setProgress(10);
            
            if (cancelled) return;
            
            // Load disordered clusters
            List<Cluster> disorderedClusters = InputLoader.parseClusterFile(disorderedClusterFile);
            setProgress(20);
            
            if (cancelled) return;
            
            // Load ordered clusters
            List<Cluster> orderedClusters = InputLoader.parseClusterFile(orderedClusterFile);
            setProgress(30);
            
            if (cancelled) return;
            
            // Load symmetry groups
            setStatusMessage("Loading symmetry operations...");
            List<?> disorderedSymOps = InputLoader.parseSymmetryFile(disorderedSymmetryGroup);
            setProgress(40);
            
            if (cancelled) return;
            
            List<?> orderedSymOps = InputLoader.parseSymmetryFile(orderedSymmetryGroup);
            setProgress(50);
            
            if (cancelled) return;
            
            // Generate cluster coordinate list (HSP / disordered)
            setStatusMessage("Identifying cluster types...");
            // Use raw type to avoid type mismatch issue
            @SuppressWarnings("rawtypes")
            List rawSymOps = disorderedSymOps;
            this.result = ClusCoordListGenerator.generate(disorderedClusters, rawSymOps);
            setProgress(75);
            
            if (cancelled) return;
            
            setStatusMessage("Computing multiplicities and Kikuchi-Baker coefficients...");
            setProgress(90);
            
            if (cancelled) return;
            
            // Update system info
            system.setClustersComputed(true);
            setProgress(100);
            setStatusMessage("Cluster identification completed");
            
            markCompleted();
            
        } catch (Exception e) {
            markFailed("Cluster identification failed: " + e.getMessage());
            e.printStackTrace();
        } finally {
            running = false;
        }
    }
    
    public ClusCoordListResult getResult() {
        return result;
    }
    
    @Override
    public String toString() {
        return name + " [" + getProgress() + "%]";
    }
}
