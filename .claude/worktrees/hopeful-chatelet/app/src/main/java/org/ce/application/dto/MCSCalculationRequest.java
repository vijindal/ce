package org.ce.application.dto;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable request object for Monte Carlo Simulation (MCS) calculations.
 * 
 * <p>Encapsulates all parameters needed to run an MCS calculation,
 * separating input validation from the UI layer.</p>
 */
public final class MCSCalculationRequest {
    
    private final String systemId;
    private final double temperature;
    private final double composition;
    private final int supercellSize;
    private final int equilibrationSteps;
    private final int averagingSteps;
    private final long seed;
    
    private MCSCalculationRequest(Builder builder) {
        this.systemId = builder.systemId;
        this.temperature = builder.temperature;
        this.composition = builder.composition;
        this.supercellSize = builder.supercellSize;
        this.equilibrationSteps = builder.equilibrationSteps;
        this.averagingSteps = builder.averagingSteps;
        this.seed = builder.seed;
    }
    
    public String getSystemId() { return systemId; }
    public double getTemperature() { return temperature; }
    public double getComposition() { return composition; }
    public int getSupercellSize() { return supercellSize; }
    public int getEquilibrationSteps() { return equilibrationSteps; }
    public int getAveragingSteps() { return averagingSteps; }
    public long getSeed() { return seed; }
    
    /**
     * Returns a new builder instance.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder for MCSCalculationRequest.
     */
    public static final class Builder {
        private String systemId;
        private double temperature = 800.0;
        private double composition = 0.5;
        private int supercellSize = 4;
        private int equilibrationSteps = 5000;
        private int averagingSteps = 10000;
        private long seed = -1; // -1 means auto-generate
        
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
        
        public Builder supercellSize(int supercellSize) {
            this.supercellSize = supercellSize;
            return this;
        }
        
        public Builder equilibrationSteps(int equilibrationSteps) {
            this.equilibrationSteps = equilibrationSteps;
            return this;
        }
        
        public Builder averagingSteps(int averagingSteps) {
            this.averagingSteps = averagingSteps;
            return this;
        }
        
        public Builder seed(long seed) {
            this.seed = seed;
            return this;
        }
        
        /**
         * Builds the request, auto-generating seed if not provided.
         * 
         * @return the immutable request object
         * @throws IllegalArgumentException if validation fails
         */
        public MCSCalculationRequest build() {
            validate();
            if (seed < 0) {
                seed = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
            }
            return new MCSCalculationRequest(this);
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
            if (supercellSize < 1) {
                throw new IllegalArgumentException("Supercell size must be >= 1");
            }
            if (equilibrationSteps <= 0) {
                throw new IllegalArgumentException("Equilibration steps must be positive");
            }
            if (averagingSteps <= 0) {
                throw new IllegalArgumentException("Averaging steps must be positive");
            }
        }
    }
    
    @Override
    public String toString() {
        return "MCSCalculationRequest{" +
            "systemId='" + systemId + '\'' +
            ", temperature=" + temperature +
            ", composition=" + composition +
            ", supercellSize=" + supercellSize +
            ", equilibrationSteps=" + equilibrationSteps +
            ", averagingSteps=" + averagingSteps +
            ", seed=" + seed +
            '}';
    }
}

