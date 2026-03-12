package org.ce.application.dto;

/**
 * Abstract base for thermodynamic calculation requests.
 *
 * <p>Holds the five fields that are identical across all engine-specific request
 * types (CVM, MCS): {@code systemId}, {@code temperature}, {@code composition},
 * {@code compositionArray}, and {@code numComponents}. Subclasses add
 * engine-specific parameters (e.g. {@code tolerance} for CVM, supercell
 * parameters for MCS).</p>
 *
 * <p>Validation of the shared fields is centralised in
 * {@link #validateCommon(String, double, double[], double, int)} and is called
 * from each subclass builder's {@code validate()} method.</p>
 */
public abstract class ThermodynamicCalculationRequest {

    protected final String systemId;
    protected final double temperature;
    protected final double composition;           // binary scalar (backward compat)
    protected final double[] compositionArray;    // multi-component array
    protected final int numComponents;

    protected ThermodynamicCalculationRequest(AbstractBuilder<?> builder) {
        this.systemId = builder.systemId;
        this.temperature = builder.temperature;
        this.composition = builder.composition;
        this.compositionArray = builder.compositionArray != null
                ? builder.compositionArray.clone()
                : null;
        this.numComponents = builder.numComponents;
    }

    public String getSystemId() { return systemId; }
    public double getTemperature() { return temperature; }
    public double getComposition() { return composition; }
    public double[] getCompositionArray() {
        return compositionArray != null ? compositionArray.clone() : null;
    }
    public int getNumComponents() { return numComponents; }

    // -------------------------------------------------------------------------
    // Shared validation
    // -------------------------------------------------------------------------

    /**
     * Validates the five shared fields; throws {@link IllegalArgumentException}
     * on the first violation found.
     */
    protected static void validateCommon(
            String systemId, double temperature,
            double[] compositionArray, double composition, int numComponents) {

        if (systemId == null || systemId.isBlank()) {
            throw new IllegalArgumentException("System ID is required");
        }
        if (temperature <= 0) {
            throw new IllegalArgumentException("Temperature must be positive");
        }
        if (numComponents < 2) {
            throw new IllegalArgumentException(
                    "numComponents must be >= 2, got " + numComponents);
        }
        if (compositionArray != null) {
            if (compositionArray.length != numComponents) {
                throw new IllegalArgumentException(
                        "compositionArray length (" + compositionArray.length
                        + ") must equal numComponents (" + numComponents + ")");
            }
            double sum = 0.0;
            for (double x : compositionArray) {
                if (x < 0 || x > 1) {
                    throw new IllegalArgumentException(
                            "Each composition value must be between 0 and 1");
                }
                sum += x;
            }
            if (Math.abs(sum - 1.0) > 1e-6) {
                throw new IllegalArgumentException(
                        "Composition array must sum to 1.0 (sum=" + sum + ")");
            }
        } else {
            if (numComponents != 2) {
                throw new IllegalArgumentException(
                        "When compositionArray is not set, numComponents must be 2 "
                        + "(binary), got " + numComponents);
            }
            if (composition < 0 || composition > 1) {
                throw new IllegalArgumentException(
                        "Composition must be between 0 and 1");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Base builder
    // -------------------------------------------------------------------------

    /**
     * Self-referential builder base — provides fluent setters for the five
     * shared fields. Subclass builders extend this and return {@code this}
     * (cast to {@code B}) from each setter.
     *
     * @param <B> the concrete builder type (for fluent chaining)
     */
    @SuppressWarnings("unchecked")
    public abstract static class AbstractBuilder<B extends AbstractBuilder<B>> {

        protected String systemId;
        protected double temperature = 800.0;
        protected double composition = 0.5;
        protected double[] compositionArray;
        protected int numComponents = 2;

        protected AbstractBuilder() {}

        public B systemId(String systemId) {
            this.systemId = systemId;
            return (B) this;
        }

        public B temperature(double temperature) {
            this.temperature = temperature;
            return (B) this;
        }

        public B composition(double composition) {
            this.composition = composition;
            return (B) this;
        }

        public B compositionArray(double[] compositionArray) {
            this.compositionArray = compositionArray != null
                    ? compositionArray.clone() : null;
            return (B) this;
        }

        public B numComponents(int numComponents) {
            this.numComponents = numComponents;
            return (B) this;
        }
    }
}
