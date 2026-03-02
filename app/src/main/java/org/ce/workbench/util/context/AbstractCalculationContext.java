package org.ce.workbench.util.context;

import org.ce.workbench.model.SystemIdentity;

/**
 * Abstract base class for calculation contexts (MCS and CVM).
 *
 * <p>Contains the common fields shared by all calculation methods:
 * system info, temperature, composition, ECI values, and readiness state.
 * Subclasses provide method-specific parameters and cluster data types.</p>
 */
public abstract class AbstractCalculationContext {

    protected final SystemIdentity system;
    protected final double temperature;
    protected final double composition;

    protected double[] eci;
    protected boolean isReady;
    protected String readinessError;

    protected AbstractCalculationContext(
            SystemIdentity system,
            double temperature,
            double composition) {
        this.system = system;
        this.temperature = temperature;
        this.composition = composition;
        this.isReady = false;
    }

    // -------------------------------------------------------------------------
    // Common Accessors
    // -------------------------------------------------------------------------

    public SystemIdentity getSystem() { return system; }
    public double getTemperature() { return temperature; }
    public double getComposition() { return composition; }
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
        sb.append("Composition: ").append(composition).append("\n");
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
