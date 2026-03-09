package org.ce.infrastructure.context;

import org.ce.domain.identification.result.ClusCoordListResult;
import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.system.SystemIdentity;

/**
 * Context holder for a Monte Carlo Simulation (MCS) calculation.
 *
 * <p>Extends {@link AbstractCalculationContext} with MCS-specific parameters:
 * supercell size, equilibration steps, averaging steps, and random seed.</p>
 */
public class MCSCalculationContext extends AbstractCalculationContext {

    private final int supercellSize;
    private final int equilibrationSteps;
    private final int averagingSteps;
    private final long seed;

    private ClusCoordListResult clusterData;
    private AllClusterData allClusterData;

    public MCSCalculationContext(
            SystemIdentity system,
            double temperature,
            double composition,
            int supercellSize,
            int equilibrationSteps,
            int averagingSteps,
            long seed) {
        super(system, temperature, composition);
        this.supercellSize = supercellSize;
        this.equilibrationSteps = equilibrationSteps;
        this.averagingSteps = averagingSteps;
        this.seed = seed;
    }

    // -------------------------------------------------------------------------
    // MCS-specific Accessors
    // -------------------------------------------------------------------------

    public int getSupercellSize() { return supercellSize; }
    public int getEquilibrationSteps() { return equilibrationSteps; }
    public int getAveragingSteps() { return averagingSteps; }
    public long getSeed() { return seed; }
    public ClusCoordListResult getClusterData() { return clusterData; }
    public AllClusterData getAllClusterData() { return allClusterData; }

    // -------------------------------------------------------------------------
    // MCS-specific Setter
    // -------------------------------------------------------------------------

    public void setClusterData(ClusCoordListResult clusterData) {
        this.clusterData = clusterData;
        validateReadiness();
    }

    public void setAllClusterData(AllClusterData allClusterData) {
        this.allClusterData = allClusterData;
        validateReadiness();
    }

    // -------------------------------------------------------------------------
    // Abstract Method Implementations
    // -------------------------------------------------------------------------

    @Override
    protected void validateReadiness() {
        if (clusterData == null || eci == null) return;

        if (validateECICount()) {
            this.isReady = true;
            this.readinessError = null;
        }
    }

    @Override
    protected int getClusterTypeCount() {
        // For MCS, ECI is expanded to tc (total cluster types including point/empty).
        // Validation must check ECI length against tc, not ncf.
        return (clusterData != null) ? clusterData.getTc() : 0;
    }

    @Override
    protected String getMethodName() {
        return "MCS";
    }

    @Override
    protected String getMethodSpecificSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Supercell size: ").append(supercellSize).append("\n");
        sb.append("Equilibration: ").append(equilibrationSteps).append(" steps\n");
        sb.append("Averaging: ").append(averagingSteps).append(" steps\n");
        sb.append("Seed: ").append(seed).append("\n");
        if (clusterData != null) {
            sb.append("Cluster types (tc): ").append(clusterData.getTc()).append("\n");
        }
        if (allClusterData != null && allClusterData.getStage2() != null) {
            sb.append("Non-point CFs (ncf): ").append(allClusterData.getStage2().getNcf()).append("\n");
        }
        return sb.toString();
    }
}


