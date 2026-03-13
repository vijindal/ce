package org.ce.application.dto;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Immutable request object for Monte Carlo Simulation (MCS) calculations.
 *
 * <p>Encapsulates all parameters needed to run an MCS calculation,
 * separating input validation from the UI layer. Common fields
 * ({@code systemId}, {@code temperature}, {@code composition},
 * {@code compositionArray}, {@code numComponents}) are inherited from
 * {@link ThermodynamicCalculationRequest}.</p>
 */
public final class MCSCalculationRequest extends ThermodynamicCalculationRequest {

    private final int supercellSize;
    private final int equilibrationSteps;
    private final int averagingSteps;
    private final long seed;

    private MCSCalculationRequest(Builder builder) {
        super(builder);
        this.supercellSize = builder.supercellSize;
        this.equilibrationSteps = builder.equilibrationSteps;
        this.averagingSteps = builder.averagingSteps;
        this.seed = builder.seed;
    }

    public int getSupercellSize() { return supercellSize; }
    public int getEquilibrationSteps() { return equilibrationSteps; }
    public int getAveragingSteps() { return averagingSteps; }
    public long getSeed() { return seed; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractBuilder<Builder> {

        private int supercellSize = 4;
        private int equilibrationSteps = 5000;
        private int averagingSteps = 10000;
        private long seed = -1; // -1 means auto-generate

        private Builder() {}

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

        public MCSCalculationRequest build() {
            validate();
            if (seed < 0) {
                seed = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
            }
            return new MCSCalculationRequest(this);
        }

        private void validate() {
            validateCommon(systemId, temperature, compositionArray, numComponents);
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
        StringBuilder sb = new StringBuilder("MCSCalculationRequest{");
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
            sb.append(", composition=").append(java.util.Arrays.toString(compositionArray));
            sb.append(", numComponents=").append(numComponents);
        }
        sb.append(", supercellSize=").append(supercellSize);
        sb.append(", equilibrationSteps=").append(equilibrationSteps);
        sb.append(", averagingSteps=").append(averagingSteps);
        sb.append(", seed=").append(seed);
        sb.append('}');
        return sb.toString();
    }
}