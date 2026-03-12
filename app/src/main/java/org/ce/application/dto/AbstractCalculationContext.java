package org.ce.application.dto;

import org.ce.domain.system.SystemIdentity;

/**
 * Abstract base class for calculation contexts (MCS and CVM).
 *
 * <p>Contains the common fields shared by all calculation methods:
 * system info, temperature, composition array, ECI values, and readiness state.
 * Subclasses provide method-specific parameters and cluster data types.</p>
 *
 * <h2>Composition representation</h2>
 * <p>All systems (binary K=2, ternary K=3, or higher) use composition arrays:
 * <ul>
 *   <li>compositionArray: fractions for all K components, sum ≈ 1.0</li>
 *   <li>numComponents: K (number of chemical elements/species)</li>
 *   <li>Validation: {@code compositionArray.length == numComponents} (enforced)</li>
 * </ul>
 * </p>
 */
public abstract class AbstractCalculationContext {

    protected final SystemIdentity system;
    protected final double temperature;
    protected final double[] compositionArray;
    protected final int numComponents;

    protected double[] eci;
    protected boolean isReady;
    protected String readinessError;

    /**
     * Constructor for any system (binary, ternary, or higher-order).
     *
     * @param system system identity
     * @param temperature temperature in K
     * @param compositionArray composition fractions for each component (sum ≈ 1.0)
     * @param numComponents number of chemical components (≥ 2)
     * @throws IllegalArgumentException if array length doesn't match numComponents
     */
    protected AbstractCalculationContext(
            SystemIdentity system,
            double temperature,
            double[] compositionArray,
            int numComponents) {
        if (numComponents < 2) {
            throw new IllegalArgumentException("numComponents must be >= 2, got " + numComponents);
        }
        if (compositionArray.length != numComponents) {
            throw new IllegalArgumentException(
                    "compositionArray length (" + compositionArray.length
                    + ") must equal numComponents (" + numComponents + ")");
        }
        this.system = system;
        this.temperature = temperature;
        this.compositionArray = compositionArray.clone();
        this.numComponents = numComponents;
        this.isReady = false;
    }

    // -------------------------------------------------------------------------
    // Common Accessors
    // -------------------------------------------------------------------------

    public SystemIdentity getSystem() { return system; }
    public double getTemperature() { return temperature; }
    /**
     * Returns composition as array (all systems: binary, ternary, higher-order).
     * @return array of length numComponents; x[c] = N_c/N for component c
     */
    public double[] getComposition() { return compositionArray.clone(); }
    /**
     * Returns the number of chemical components.
     * @return numComponents (≥ 2)
     */
    public int getNumComponents() { return numComponents; }
    public double[] getECI() { return eci; }
    public boolean isReady() { return isReady; }
    public String getReadinessError() { return readinessError; }

    // -------------------------------------------------------------------------
    // Common Setter
    // -------------------------------------------------------------------------

    public void setECI(double[] eci) {
        this.eci = eci;
        validateReadiness();
    }

    // -------------------------------------------------------------------------
    // Abstract Methods (subclass-specific)
    // -------------------------------------------------------------------------

    /**
     * Validates that all required data is present and consistent.
     * Updates {@link #isReady} and {@link #readinessError} accordingly.
     */
    protected abstract void validateReadiness();

    /**
     * Returns method-specific summary lines (e.g., supercell size for MCS,
     * tolerance for CVM).
     */
    protected abstract String getMethodSpecificSummary();

    /**
     * Returns the cluster type count for ECI validation.
     */
    protected abstract int getClusterTypeCount();

    /**
     * Returns the name of the calculation method (e.g., "MCS", "CVM").
     */
    protected abstract String getMethodName();

    // -------------------------------------------------------------------------
    // Common Summary
    // -------------------------------------------------------------------------

    /**
     * Returns a summary of the calculation context.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("System: ").append(system.getName()).append("\n");
        sb.append("Temperature: ").append(temperature).append(" K\n");
        sb.append("Composition (K=").append(numComponents).append("): [");
        String[] labels = {"A", "B", "C", "D", "E"};
        for (int c = 0; c < compositionArray.length; c++) {
            if (c > 0) sb.append(", ");
            String label = (c < labels.length) ? labels[c] : ("C" + c);
            sb.append(label).append("=").append(String.format("%.4f", compositionArray[c]));
        }
        sb.append("]\n");
        sb.append(getMethodSpecificSummary());
        sb.append("ECI: ");
        if (eci == null) {
            sb.append("NOT LOADED\n");
        } else {
            sb.append(eci.length).append(" values\n");
        }
        sb.append("Ready: ").append(isReady);
        if (!isReady && readinessError != null) {
            sb.append("  (").append(readinessError).append(")");
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * Common ECI validation logic. Returns true if ECI count matches cluster types.
     */
    protected boolean validateECICount() {
        int required = getClusterTypeCount();
        if (eci.length != required) {
            this.isReady = false;
            this.readinessError = "ECI length (" + eci.length + ") does not match cluster type count ("
                    + required + ") for system " + system.getName();
            return false;
        }
        return true;
    }
}
