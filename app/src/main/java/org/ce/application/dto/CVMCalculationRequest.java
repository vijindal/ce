package org.ce.application.dto;

/**
 * Immutable request object for Cluster Variation Method (CVM) calculations.
 * 
 * <p>Encapsulates all parameters needed to run a CVM free-energy minimization,
 * separating input validation from the UI layer.</p>
 */
public final class CVMCalculationRequest {

    private final String systemId;
    private final double temperature;
    private final double composition;              // Backward compat: scalar for binary systems
    private final double[] compositionArray;       // Multi-component: array for any K
    private final int numComponents;               // Number of components (K)
    private final double tolerance;

    private CVMCalculationRequest(Builder builder) {
        this.systemId = builder.systemId;
        this.temperature = builder.temperature;
        this.composition = builder.composition;
        this.compositionArray = builder.compositionArray != null
                ? builder.compositionArray.clone()
                : null;
        this.numComponents = builder.numComponents;
        this.tolerance = builder.tolerance;
    }

    public String getSystemId() { return systemId; }
    public double getTemperature() { return temperature; }
    public double getComposition() { return composition; }
    public double[] getCompositionArray() { return compositionArray != null ? compositionArray.clone() : null; }
    public int getNumComponents() { return numComponents; }
    public double getTolerance() { return tolerance; }
    
    /**
     * Returns a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for CVMCalculationRequest.
     * Supports both binary (scalar composition) and multi-component (array composition).
     */
    public static final class Builder {
        private String systemId;
        private double temperature = 800.0;
        private double composition = 0.5;
        private double[] compositionArray;       // For multi-component (K ≥ 2)
        private int numComponents = 2;           // Default: binary (K=2)
        private double tolerance = 1e-6;

        private Builder() {}

        public Builder systemId(String systemId) {
            this.systemId = systemId;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder composition(double composition) {
            this.composition = composition;
            return this;
        }

        /**
         * Sets composition as an array for multi-component systems.
         * When used, must also call {@link #numComponents(int)} with matching length.
         */
        public Builder compositionArray(double[] compositionArray) {
            this.compositionArray = compositionArray != null ? compositionArray.clone() : null;
            return this;
        }

        /**
         * Sets the number of chemical components (K).
         * Required when using {@link #compositionArray(double[])}.
         */
        public Builder numComponents(int numComponents) {
            this.numComponents = numComponents;
            return this;
        }

        public Builder tolerance(double tolerance) {
            this.tolerance = tolerance;
            return this;
        }
        
        /**
         * Builds the request.
         * 
         * @return the immutable request object
         * @throws IllegalArgumentException if validation fails
         */
        public CVMCalculationRequest build() {
            validate();
            return new CVMCalculationRequest(this);
        }
        
        private void validate() {
            if (systemId == null || systemId.isBlank()) {
                throw new IllegalArgumentException("System ID is required");
            }
            if (temperature <= 0) {
                throw new IllegalArgumentException("Temperature must be positive");
            }
            if (numComponents < 2) {
                throw new IllegalArgumentException("numComponents must be >= 2, got " + numComponents);
            }

            // Validate composition: either array OR scalar (binary default)
            if (compositionArray != null) {
                // Multi-component case: array must match numComponents
                if (compositionArray.length != numComponents) {
                    throw new IllegalArgumentException(
                            "compositionArray length (" + compositionArray.length
                            + ") must equal numComponents (" + numComponents + ")");
                }
                // Check array sums to approximately 1.0
                double sum = 0.0;
                for (double x : compositionArray) {
                    if (x < 0 || x > 1) {
                        throw new IllegalArgumentException("Each composition value must be between 0 and 1");
                    }
                    sum += x;
                }
                if (Math.abs(sum - 1.0) > 1e-6) {
                    throw new IllegalArgumentException("Composition array must sum to 1.0 (sum=" + sum + ")");
                }
            } else {
                // Binary case: scalar composition (backward compat)
                if (numComponents != 2) {
                    throw new IllegalArgumentException(
                            "When compositionArray is not set, numComponents must be 2 (binary), got " + numComponents);
                }
                if (composition < 0 || composition > 1) {
                    throw new IllegalArgumentException("Composition must be between 0 and 1");
                }
            }

            if (tolerance <= 0) {
                throw new IllegalArgumentException("Tolerance must be positive");
            }
        }
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CVMCalculationRequest{");
        sb.append("systemId='").append(systemId).append('\'');
        sb.append(", temperature=").append(temperature);
        if (compositionArray != null) {
            sb.append(", compositionArray=[");
            for (int i = 0; i < compositionArray.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(String.format("%.4f", compositionArray[i]));
            }
            sb.append("], numComponents=").append(numComponents);
        } else {
            sb.append(", composition=").append(composition);
            sb.append(", numComponents=").append(numComponents);
        }
        sb.append(", tolerance=").append(tolerance);
        sb.append('}');
        return sb.toString();
    }
}

