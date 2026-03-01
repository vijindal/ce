package org.ce.workbench.util.context;

import org.ce.workbench.backend.data.AllClusterData;
import org.ce.workbench.gui.model.SystemInfo;

/**
 * Context holder for a CVM free-energy calculation.
 *
 * <p>Contains all required data: the pre-computed {@link AllClusterData}
 * (Stages 1–3), ECI values, temperature, and composition.  An instance is
 * considered <em>ready</em> once both {@code allClusterData} and {@code eci}
 * have been set and their dimensions are consistent.</p>
 */
public class CVMCalculationContext {

    private final SystemInfo system;
    private final double temperature;
    private final double composition;
    private final double tolerance;

    private AllClusterData allClusterData;
    private double[] eci;
    private boolean isReady;
    private String readinessError;

    public CVMCalculationContext(
            SystemInfo system,
            double temperature,
            double composition,
            double tolerance) {

        this.system      = system;
        this.temperature = temperature;
        this.composition = composition;
        this.tolerance   = tolerance;
        this.isReady     = false;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public SystemInfo      getSystem()         { return system; }
    public double          getTemperature()    { return temperature; }
    public double          getComposition()    { return composition; }
    public double          getTolerance()      { return tolerance; }
    public AllClusterData  getAllClusterData()  { return allClusterData; }
    public double[]        getECI()            { return eci; }
    public boolean         isReady()           { return isReady; }
    public String          getReadinessError() { return readinessError; }

    // -------------------------------------------------------------------------
    // Setters (trigger validation)
    // -------------------------------------------------------------------------

    public void setAllClusterData(AllClusterData data) {
        this.allClusterData = data;
        validateReadiness();
    }

    public void setECI(double[] eci) {
        this.eci = eci;
        validateReadiness();
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private void validateReadiness() {
        if (allClusterData == null || eci == null) return;

        if (!allClusterData.isComplete()) {
            this.isReady = false;
            this.readinessError = "AllClusterData is incomplete (first missing: Stage "
                    + allClusterData.getFirstIncompleteStage() + ")";
            return;
        }

        // ECI count must match the total ordered cluster-type count (tc) from Stage 1
        int requiredLength = allClusterData.getStage1().getTc();
        if (eci.length != requiredLength) {
            this.isReady = false;
            this.readinessError =
                    "ECI length (" + eci.length + ") ≠ cluster type count ("
                    + requiredLength + ") for " + system.getName();
            return;
        }

        this.isReady = true;
        this.readinessError = null;
    }

    // -------------------------------------------------------------------------
    // Summary
    // -------------------------------------------------------------------------

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("System: ").append(system.getName()).append("\n");
        sb.append("Temperature: ").append(temperature).append(" K\n");
        sb.append("Composition: ").append(composition).append("\n");
        sb.append("Tolerance: ").append(tolerance).append("\n");
        sb.append("AllClusterData: ")
          .append(allClusterData == null ? "NOT LOADED" : allClusterData)
          .append("\n");
        sb.append("ECI: ")
          .append(eci == null ? "NOT LOADED" : eci.length + " values")
          .append("\n");
        sb.append("Ready: ").append(isReady);
        if (!isReady && readinessError != null) {
            sb.append("  (").append(readinessError).append(")");
        }
        return sb.toString();
    }
}
