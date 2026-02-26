package org.ce.app.gui.models;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Metadata for a system (structure + components combination).
 * Represents a unique physico-chemical system that may be cached.
 */
public class SystemInfo {
    private final String id;
    private final String name;
    private final String structure; // e.g., "BCC", "FCC"
    private final String phase;     // e.g., "A2", "B2", "L12"
    private final String[] components; // e.g., ["Fe", "Ni"]
    
    private boolean clustersComputed;
    private boolean cfsComputed;
    private LocalDateTime clustersComputedDate;
    private LocalDateTime cfsComputedDate;
    
    private double[][] transformationMatrix;
    private double[] translationVector;
    
    private String clusterFilePath;
    private String symmetryGroupName;
    
    public SystemInfo(String id, String name, String structure, String phase, String[] components) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.structure = Objects.requireNonNull(structure, "structure");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.components = Objects.requireNonNull(components, "components");
        
        this.clustersComputed = false;
        this.cfsComputed = false;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getStructure() { return structure; }
    public String getPhase() { return phase; }
    public String[] getComponents() { return components; }
    public int getNumComponents() { return components.length; }
    
    public boolean isClustersComputed() { return clustersComputed; }
    public boolean isCfsComputed() { return cfsComputed; }
    public LocalDateTime getClustersComputedDate() { return clustersComputedDate; }
    public LocalDateTime getCfsComputedDate() { return cfsComputedDate; }
    
    public double[][] getTransformationMatrix() { return transformationMatrix; }
    public double[] getTranslationVector() { return translationVector; }
    public String getClusterFilePath() { return clusterFilePath; }
    public String getSymmetryGroupName() { return symmetryGroupName; }
    
    // Setters
    public void setClustersComputed(boolean computed) {
        this.clustersComputed = computed;
        if (computed) {
            this.clustersComputedDate = LocalDateTime.now();
        }
    }
    
    public void setCfsComputed(boolean computed) {
        this.cfsComputed = computed;
        if (computed) {
            this.cfsComputedDate = LocalDateTime.now();
        }
    }
    
    public void setTransformationMatrix(double[][] matrix) {
        this.transformationMatrix = matrix;
    }
    
    public void setTranslationVector(double[] vector) {
        this.translationVector = vector;
    }
    
    public void setClusterFilePath(String path) {
        this.clusterFilePath = path;
    }
    
    public void setSymmetryGroupName(String name) {
        this.symmetryGroupName = name;
    }
    
    /**
     * Returns a human-readable status string combining component info.
     */
    public String getStatusSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(", ", components));
        sb.append(" (").append(phase).append(")");
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return name + " [" + structure + "]";
    }
}
