package org.ce.application.dto;

/**
 * Immutable request object for Cluster Variation Method (CVM) calculations.
 *
 * <p>Encapsulates all parameters needed to run a CVM free-energy minimization,
 * separating input validation from the UI layer. Common fields
 * ({@code systemId}, {@code temperature}, {@code composition},
 * {@code compositionArray}, {@code numComponents}) are inherited from
 * {@link ThermodynamicCalculationRequest}.</p>
 */
public final class CVMCalculationRequest extends ThermodynamicCalculationRequest {

    private final double tolerance;

    private CVMCalculationRequest(Builder builder) {
        super(builder);
        this.tolerance = builder.tolerance;
    }

    public double getTolerance() { return tolerance; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder
            extends AbstractBuilder<Builder> {

        private double tolerance = 1e-6;

        private Builder() {}

        public Builder tolerance(double tolerance) {
            this.tolerance = tolerance;
            return this;
        }

        public CVMCalculationRequest build() {
            validate();
            return new CVMCalculationRequest(this);
        }

        private void validate() {
            validateCommon(systemId, temperature, compositionArray, numComponents);
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
            sb.append(", composition=").append(java.util.Arrays.toString(compositionArray));
            sb.append(", numComponents=").append(numComponents);
        }
        sb.append(", tolerance=").append(tolerance);
        sb.append('}');
        return sb.toString();
    }
}