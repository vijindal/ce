package org.ce.domain.system;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable identity of a physico-chemical system.
 *
 * <p>Contains only identity and configuration fields that never change
 * after construction. Runtime status (computed flags, dates) is managed
 * separately by {@link org.ce.infrastructure.registry.SystemRegistry}.</p>
 */
public final class SystemIdentity {

    private final String id;
    private final String name;
    private final String structure;     // e.g., "BCC", "FCC"
    private final String phase;         // e.g., "A2", "B2", "L12"
    private final String model;         // e.g., "T" (tetrahedron), "P" (pair)
    private final List<String> components;  // e.g., ["Fe", "Ni"]

    // Transformation configuration (set once during system setup)
    private final double[][] transformationMatrix;
    private final double[] translationVector;
    private final String clusterFilePath;
    private final String symmetryGroupName;

    private SystemIdentity(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.name = Objects.requireNonNull(builder.name, "name");
        this.structure = Objects.requireNonNull(builder.structure, "structure");
        this.phase = Objects.requireNonNull(builder.phase, "phase");
        this.model = Objects.requireNonNull(builder.model, "model");
        this.components = List.copyOf(Arrays.asList(
                Objects.requireNonNull(builder.components, "components")));

        // Optional configuration (may be null)
        this.transformationMatrix = builder.transformationMatrix != null
                ? deepCopy(builder.transformationMatrix)
                : null;
        this.translationVector = builder.translationVector != null
                ? builder.translationVector.clone()
                : null;
        this.clusterFilePath = builder.clusterFilePath;
        this.symmetryGroupName = builder.symmetryGroupName;
    }

    // -------------------------------------------------------------------------
    // Accessors (all read-only)
    // -------------------------------------------------------------------------

    public String getId() { return id; }
    public String getName() { return name; }
    public String getStructure() { return structure; }
    public String getPhase() { return phase; }
    public String getModel() { return model; }

    /** Returns an immutable list of components. */
    public List<String> getComponents() { return components; }

    /** Returns component array (for compatibility). */
    public String[] getComponentsArray() { return components.toArray(new String[0]); }

    public int getNumComponents() { return components.size(); }

    public double[][] getTransformationMatrix() {
        return transformationMatrix != null ? deepCopy(transformationMatrix) : null;
    }

    public double[] getTranslationVector() {
        return translationVector != null ? translationVector.clone() : null;
    }

    public String getClusterFilePath() { return clusterFilePath; }
    public String getSymmetryGroupName() { return symmetryGroupName; }

    // -------------------------------------------------------------------------
    // Utility Methods
    // -------------------------------------------------------------------------

    /**
     * Returns a human-readable status string combining component info.
     */
    public String getStatusSummary() {
        return String.join(", ", components) + " (" + phase + ")";
    }

    /**
     * Returns the directory name for this system's data folder.
     */
    public String getSystemDataDir() {
        return String.join("-", components) + "_" + structure + "_" + phase + "_" + model;
    }

    /**
     * Generates a system ID from components, structure, phase, and model.
     * Format: Elements_Structure_Phase_Model (e.g., "Ti-Nb_BCC_A2_T")
     */
    public static String generateSystemId(String elements, String structure, String phase, String model) {
        return elements.replace(",", "-").replace(" ", "") + "_" + structure + "_" + phase + "_" + model;
    }

    @Override
    public String toString() {
        return name + " [" + structure + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SystemIdentity that)) return false;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String name;
        private String structure;
        private String phase;
        private String model;
        private String[] components;
        private double[][] transformationMatrix;
        private double[] translationVector;
        private String clusterFilePath;
        private String symmetryGroupName;

        private Builder() {}

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder structure(String structure) { this.structure = structure; return this; }
        public Builder phase(String phase) { this.phase = phase; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder components(String[] components) { this.components = components; return this; }
        public Builder transformationMatrix(double[][] matrix) { this.transformationMatrix = matrix; return this; }
        public Builder translationVector(double[] vector) { this.translationVector = vector; return this; }
        public Builder clusterFilePath(String path) { this.clusterFilePath = path; return this; }
        public Builder symmetryGroupName(String name) { this.symmetryGroupName = name; return this; }

        public SystemIdentity build() {
            return new SystemIdentity(this);
        }
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private static double[][] deepCopy(double[][] matrix) {
        if (matrix == null) return null;
        double[][] copy = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            copy[i] = matrix[i].clone();
        }
        return copy;
    }
}

