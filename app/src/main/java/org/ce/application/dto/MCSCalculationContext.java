package org.ce.application.dto;

import org.ce.domain.identification.result.ClusCoordListResult;
import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.system.SystemIdentity;

/**
 * Context holder for a Monte Carlo Simulation (MCS) calculation.
 *
 * <p>Extends {@link AbstractCalculationContext} with MCS-specific parameters:
 * supercell size, equilibration steps, averaging steps, and random seed.
 * Supports any number of components (K≥2).</p>
 */
public class MCSCalculationContext extends AbstractCalculationContext {

    private final int supercellSize;
    private final int equilibrationSteps;
    private final int averagingSteps;
    private final long seed;

    private ClusCoordListResult clusterData;
    private AllClusterData allClusterData;

    /**
     * Constructor for MCS calculations (binary, ternary, or higher-order systems).
     *
     * @param system system identity
     * @param temperature temperature in K
     * @param compositionArray composition fractions for each component (sum ≈ 1.0)
     * @param numComponents number of chemical components (≥ 2)
     * @param supercellSize supercell dimension L
     * @param equilibrationSteps equilibration sweeps
     * @param averagingSteps averaging sweeps
     * @param seed random seed
     * @throws IllegalArgumentException if array length doesn't match numComponents
     */
    public MCSCalculationContext(
            SystemIdentity system,
            double temperature,
            double[] compositionArray,
            int numComponents,
            int supercellSize,
            int equilibrationSteps,
            int averagingSteps,
            long seed) {
        super(system, temperature, compositionArray, numComponents);
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
        // ECI arrays are ncf-length (non-point cluster functions only).
        // Point and empty clusters are constants with ECI=0 (canonical ensemble) and are not
        // generated as embeddings in EmbeddingGenerator. Use ncf from Stage 2 as validation target.
        return (allClusterData != null && allClusterData.getStage2() != null)
            ? allClusterData.getStage2().getNcf() : 0;
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
