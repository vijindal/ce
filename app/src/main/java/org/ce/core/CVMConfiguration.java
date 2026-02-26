package org.ce.core;

import org.ce.identification.engine.Vector3D;
import java.util.Objects;

/**
 * Configuration object for the two-stage CVM pipeline.
 *
 * <p>Encapsulates all input parameters required to run the CVM identification
 * procedure: cluster files, symmetry groups, transformation matrices, and
 * component information.  Use the builder pattern to construct instances.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * CVMConfiguration config = CVMConfiguration.builder()
 *     .disorderedClusterFile("cluster/A2-T.txt")
 *     .orderedClusterFile("cluster/B2-T.txt")
 *     .disorderedSymmetryGroup("A2-SG")
 *     .orderedSymmetryGroup("B2-SG")
 *     .transformationMatrix(new double[][]{{1,0,0},{0,1,0},{0,0,1}})
 *     .translationVector(new double[]{0,0,0})
 *     .numComponents(2)
 *     .build();
 *
 * CVMPipeline pipeline = new CVMPipeline();
 * CVMResult result = pipeline.identify(config);
 * }</pre>
 *
 * @author  CVM Project
 * @version 1.0
 * @see     CVMPipeline
 * @see     CVMResult
 */
public class CVMConfiguration {

    private final String disorderedClusterFile;
    private final String orderedClusterFile;
    private final String disorderedSymmetryGroup;
    private final String orderedSymmetryGroup;
    private final double[][] transformationMatrix;
    private final Vector3D translationVector;
    private final int numComponents;

    /**
     * Constructs a fully configured CVMConfiguration.
     *
     * @param disorderedClusterFile path to the disordered reference phase cluster file
     * @param orderedClusterFile path to the ordered phase cluster file
     * @param disorderedSymmetryGroup name of the disordered phase symmetry group (e.g., "A2-SG")
     * @param orderedSymmetryGroup name of the ordered phase symmetry group (e.g., "B2-SG")
     * @param transformationMatrix 3×3 orientation matrix mapping disordered → ordered
     * @param translationVector translation vector for the transformation
     * @param numComponents number of chemical components in the system
     */
    private CVMConfiguration(
            String disorderedClusterFile,
            String orderedClusterFile,
            String disorderedSymmetryGroup,
            String orderedSymmetryGroup,
            double[][] transformationMatrix,
            Vector3D translationVector,
            int numComponents) {

        this.disorderedClusterFile = Objects.requireNonNull(disorderedClusterFile, "disorderedClusterFile");
        this.orderedClusterFile = Objects.requireNonNull(orderedClusterFile, "orderedClusterFile");
        this.disorderedSymmetryGroup = Objects.requireNonNull(disorderedSymmetryGroup, "disorderedSymmetryGroup");
        this.orderedSymmetryGroup = Objects.requireNonNull(orderedSymmetryGroup, "orderedSymmetryGroup");
        this.transformationMatrix = Objects.requireNonNull(transformationMatrix, "transformationMatrix");
        this.translationVector = Objects.requireNonNull(translationVector, "translationVector");

        if (numComponents < 1) {
            throw new IllegalArgumentException("numComponents must be >= 1, got " + numComponents);
        }
        this.numComponents = numComponents;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /** @return path to disordered phase cluster file */
    public String getDisorderedClusterFile() { return disorderedClusterFile; }

    /** @return path to ordered phase cluster file */
    public String getOrderedClusterFile() { return orderedClusterFile; }

    /** @return name or path to disordered phase symmetry group */
    public String getDisorderedSymmetryGroup() { return disorderedSymmetryGroup; }

    /** @return name or path to ordered phase symmetry group */
    public String getOrderedSymmetryGroup() { return orderedSymmetryGroup; }

    /** @return the 3×3 transformation matrix (row-major array) */
    public double[][] getTransformationMatrix() { return transformationMatrix; }

    /** @return the translation vector */
    public Vector3D getTranslationVector() { return translationVector; }

    /** @return number of chemical components */
    public int getNumComponents() { return numComponents; }

    // =========================================================================
    // Builder
    // =========================================================================

    /**
     * Creates a new builder for constructing a {@link CVMConfiguration}.
     *
     * @return a new {@link Builder}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CVMConfiguration}.
     */
    public static class Builder {
        private String disorderedClusterFile;
        private String orderedClusterFile;
        private String disorderedSymmetryGroup;
        private String orderedSymmetryGroup;
        private double[][] transformationMatrix;
        private Vector3D translationVector;
        private int numComponents = 2;

        /** Set the disordered phase cluster file path. */
        public Builder disorderedClusterFile(String file) {
            this.disorderedClusterFile = file;
            return this;
        }

        /** Set the ordered phase cluster file path. */
        public Builder orderedClusterFile(String file) {
            this.orderedClusterFile = file;
            return this;
        }

        /** Set the name/path of the disordered phase symmetry group. */
        public Builder disorderedSymmetryGroup(String sg) {
            this.disorderedSymmetryGroup = sg;
            return this;
        }

        /** Set the name/path of the ordered phase symmetry group. */
        public Builder orderedSymmetryGroup(String sg) {
            this.orderedSymmetryGroup = sg;
            return this;
        }

        /** Set the transformation matrix (3×3 array, row-major). */
        public Builder transformationMatrix(double[][] matrix) {
            this.transformationMatrix = matrix;
            return this;
        }

        /** Set the translation vector. */
        public Builder translationVector(Vector3D vec) {
            this.translationVector = vec;
            return this;
        }

        /** Set the number of chemical components (default: 2). */
        public Builder numComponents(int n) {
            this.numComponents = n;
            return this;
        }

        /** Build the {@link CVMConfiguration}. */
        public CVMConfiguration build() {
            if (transformationMatrix == null) {
                transformationMatrix = new double[][]{{1,0,0},{0,1,0},{0,0,1}};
            }
            if (translationVector == null) {
                translationVector = new Vector3D(0, 0, 0);
            }
            return new CVMConfiguration(
                    disorderedClusterFile,
                    orderedClusterFile,
                    disorderedSymmetryGroup,
                    orderedSymmetryGroup,
                    transformationMatrix,
                    translationVector,
                    numComponents);
        }
    }
}
