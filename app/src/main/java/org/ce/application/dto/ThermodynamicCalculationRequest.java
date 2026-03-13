package org.ce.application.dto;

/**
 * Abstract base for thermodynamic calculation requests.
 *
 * <p>Holds the shared fields common to all engine-specific request types (CVM,
 * MCS): {@code systemId}, {@code temperature}, {@code compositionArray}, and
 * {@code numComponents}. Subclasses add engine-specific parameters
 * (e.g. {@code tolerance} for CVM, supercell size for MCS).</p>
 *
 * <p>Composition is always expressed as a full mole-fraction array
 * {@code x[0..K-1]} where K = numComponents. This works uniformly for binary,
 * ternary, and any higher-order system — no special-casing per K.</p>
 *
 * <p>For convenience, the builder still accepts a scalar
 * {@link AbstractBuilder#composition(double) composition(x)} shorthand which
 * constructs {@code [1-x, x]} for binary systems. For K &ge; 3, callers must
 * use {@link AbstractBuilder#compositionArray(double[])} directly.</p>
 *
 * <p>Validation is centralised in
 * {@link #validateCommon(String, double, double[], int)} and is called from
 * each subclass builder's {@code validate()} method.</p>
 */
public abstract class ThermodynamicCalculationRequest {

    protected final String systemId;
    protected final double temperature;
    protected final double[] compositionArray;    // x[c] for each component c; always set
    protected final int numComponents;

    protected ThermodynamicCalculationRequest(AbstractBuilder<?> builder) {
        this.systemId = builder.systemId;
        this.temperature = builder.temperature;
        this.numComponents = builder.numComponents;
        // Resolve composition array — always non-null after construction
        if (builder.compositionArray != null) {
            this.compositionArray = builder.compositionArray.clone();
        } else {
            // Binary shorthand: treat builder.composition as x_B = x[1]
            this.compositionArray = new double[]{1.0 - builder.composition, builder.composition};
        }
    }

    public String getSystemId()        { return systemId; }
    public double getTemperature()     { return temperature; }
    public int    getNumComponents()   { return numComponents; }

    /**
     * Returns the full mole-fraction array x[0..K-1].
     * Always non-null; length equals {@link #getNumComponents()}.
     */
    public double[] getCompositionArray() {
        return compositionArray.clone();
    }

    /**
     * Convenience accessor: returns x[1] (B-fraction) for binary systems.
     * For K &ge; 3, prefer {@link #getCompositionArray()}.
     *
     * @deprecated use {@link #getCompositionArray()} for generality
     */
    @Deprecated
    public double getComposition() { return compositionArray[1]; }

    // -------------------------------------------------------------------------
    // Shared validation
    // -------------------------------------------------------------------------

    /**
     * Validates shared request fields; throws {@link IllegalArgumentException}
     * on the first violation.
     *
     * <p>{@code compositionArray} may be null here — it is resolved in the
     * constructor from the builder's scalar {@code composition} shorthand if
     * not explicitly set. Subclass builders must call this before resolution.</p>
     */
    protected static void validateCommon(
            String systemId, double temperature,
            double[] compositionArray, int numComponents) {

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
            // Scalar shorthand — only valid for K=2 (binary)
            if (numComponents != 2) {
                throw new IllegalArgumentException(
                        "compositionArray must be provided for K=" + numComponents
                        + " systems. Use builder.compositionArray(double[]).");
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