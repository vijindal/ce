package org.ce.workbench.util.context;

import org.ce.workbench.backend.data.AllClusterData;
import org.ce.workbench.model.SystemIdentity;

/**
 * Context holder for a CVM free-energy calculation.
 *
 * <p>Extends {@link AbstractCalculationContext} with CVM-specific parameters:
 * tolerance and the pre-computed {@link AllClusterData} (Stages 1–3).</p>
 */
public class CVMCalculationContext extends AbstractCalculationContext {

    private final double tolerance;

    private AllClusterData allClusterData;

    public CVMCalculationContext(
            SystemIdentity system,
            double temperature,
            double composition,
            double tolerance) {
        super(system, temperature, composition);
        this.tolerance = tolerance;
    }

    // -------------------------------------------------------------------------
    // CVM-specific Accessors
    // -------------------------------------------------------------------------

    public double getTolerance() { return tolerance; }
    public AllClusterData getAllClusterData() { return allClusterData; }

    // -------------------------------------------------------------------------
    // CVM-specific Setter
    // -------------------------------------------------------------------------

    public void setAllClusterData(AllClusterData data) {
        this.allClusterData = data;
        validateReadiness();
    }

    // -------------------------------------------------------------------------
    // Abstract Method Implementations
    // -------------------------------------------------------------------------

    @Override
    protected void validateReadiness() {
        if (allClusterData == null || eci == null) return;

        if (!allClusterData.isComplete()) {
            this.isReady = false;
            this.readinessError = "AllClusterData is incomplete (first missing: Stage "
                    + allClusterData.getFirstIncompleteStage() + ")";
            return;
        }

        if (validateECICount()) {
            this.isReady = true;
            this.readinessError = null;
        }
    }

    @Override
    protected int getClusterTypeCount() {
        return (allClusterData != null && allClusterData.getStage1() != null)
                ? allClusterData.getStage1().getTc()
                : 0;
    }

    @Override
    protected String getMethodName() {
        return "CVM";
    }

    @Override
    protected String getMethodSpecificSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Tolerance: ").append(tolerance).append("\n");
        sb.append("AllClusterData: ");
        if (allClusterData == null) {
            sb.append("NOT LOADED\n");
        } else {
            sb.append(allClusterData).append("\n");
        }
        return sb.toString();
    }
}
