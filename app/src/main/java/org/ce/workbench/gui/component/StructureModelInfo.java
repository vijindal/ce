package org.ce.workbench.gui.component;

import java.util.Set;

/**
 * Immutable data class representing metadata for a structure-phase-model combination.
 * Contains information about crystal structure, phase, CVM approximation, and associated files.
 */
public class StructureModelInfo {
    private final String structure;           // "BCC", "FCC", "HCP"
    private final String phase;               // "A2", "B2", "L12", etc.
    private final String cvmModel;            // "T", "O", "T+O"
    private final String description;         // Human-readable description
    private final boolean ordered;            // Is this an ordered phase?
    private final String clusterFile;         // Relative path to cluster file
    private final String symGroup;            // Symmetry group name
    private final String symMatFile;          // Symmetry matrix file (optional)
    private final int maxClusterSize;         // Maximum cluster size for this approximation
    private final Set<Integer> supportedComponentCounts; // e.g., {2, 3} for binary/ternary
    private final double[][] transformMatrix; // Lattice transformation matrix (optional)
    private final double[] translationVector; // Translation vector (optional)

    public StructureModelInfo(String structure, String phase, String cvmModel, 
                             String description, boolean ordered,
                             String clusterFile, String symGroup, String symMatFile,
                             int maxClusterSize, Set<Integer> supportedComponentCounts,
                             double[][] transformMatrix, double[] translationVector) {
        this.structure = structure;
        this.phase = phase;
        this.cvmModel = cvmModel;
        this.description = description;
        this.ordered = ordered;
        this.clusterFile = clusterFile;
        this.symGroup = symGroup;
        this.symMatFile = symMatFile;
        this.maxClusterSize = maxClusterSize;
        this.supportedComponentCounts = supportedComponentCounts;
        this.transformMatrix = transformMatrix;
        this.translationVector = translationVector;
    }

    public String getStructure() {
        return structure;
    }

    public String getPhase() {
        return phase;
    }

    public String getCvmModel() {
        return cvmModel;
    }

    public String getDescription() {
        return description;
    }

    public boolean isOrdered() {
        return ordered;
    }

    public String getClusterFile() {
        return clusterFile;
    }

    public String getSymGroup() {
        return symGroup;
    }

    public String getSymMatFile() {
        return symMatFile;
    }

    public int getMaxClusterSize() {
        return maxClusterSize;
    }

    public Set<Integer> getSupportedComponentCounts() {
        return supportedComponentCounts;
    }

    public double[][] getTransformMatrix() {
        return transformMatrix;
    }

    public double[] getTranslationVector() {
        return translationVector;
    }

    /**
     * Get a full identifier for this model (e.g., "BCC_A2_T")
     */
    public String getModelId() {
        return structure + "_" + phase + "_" + cvmModel;
    }

    /**
     * Get a display name (e.g., "BCC A2 (Tetrahedron)")
     */
    public String getDisplayName() {
        String cvmName = getCvmModelName(cvmModel);
        return structure + " " + phase + " (" + cvmName + ")";
    }

    /**
     * Check if this model supports the given number of components
     */
    public boolean supportsComponentCount(int count) {
        return supportedComponentCounts.contains(count);
    }

    private static String getCvmModelName(String model) {
        switch (model) {
            case "T": return "Tetrahedron";
            case "O": return "Octahedron";
            case "TO": return "Tetrahedron + Octahedron";
            default: return model;
        }
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
