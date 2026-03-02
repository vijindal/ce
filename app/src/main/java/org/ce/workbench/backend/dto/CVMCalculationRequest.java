package org.ce.workbench.backend.dto;

/**
 * Immutable request object for Cluster Variation Method (CVM) calculations.
 * 
 * <p>Encapsulates all parameters needed to run a CVM free-energy minimization,
 * separating input validation from the UI layer.</p>
 */
public final class CVMCalculationRequest {
    
    private final String systemId;
    private final double temperature;
    private final double composition;
    private final double tolerance;
    
    private CVMCalculationRequest(Builder builder) {
        this.systemId = builder.systemId;
        this.temperature = builder.temperature;
        this.composition = builder.composition;
        this.tolerance = builder.tolerance;
    }
    
    public String getSystemId() { return systemId; }
    public double getTemperature() { return temperature; }
    public double getComposition() { return composition; }
    public double getTolerance() { return tolerance; }
    
    /**
     * Returns a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for CVMCalculationRequest.
     */
    public static final class Builder {
        private String systemId;
        private double temperature = 800.0;
        private double composition = 0.5;
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
            if (composition < 0 || composition > 1) {
                throw new IllegalArgumentException("Composition must be between 0 and 1");
            }
            if (tolerance <= 0) {
                throw new IllegalArgumentException("Tolerance must be positive");
            }
        }
    }
    
    @Override
    public String toString() {
        return "CVMCalculationRequest{" +
            "systemId='" + systemId + '\'' +
            ", temperature=" + temperature +
            ", composition=" + composition +
            ", tolerance=" + tolerance +
            '}';
    }
}
