package org.ce.workbench.util;

import org.ce.identification.engine.ClusCoordListResult;
import org.ce.workbench.gui.model.SystemInfo;

/**
 * Context holder for a calculation session.
 * Contains all required data to execute an MCS or CVM calculation.
 */
public class CalculationContext {
    
    private final SystemInfo system;
    private final double temperature;
    private final double composition;
    private final int supercellSize;
    private final int equilibrationSteps;
    private final int averagingSteps;
    private final long seed;
    
    private ClusCoordListResult clusterData;
    private double[] eci;
    private boolean isReady;
    private String readinessError; // Human-readable reason when isReady=false
    
    public CalculationContext(
        SystemInfo system,
        double temperature,
        double composition,
        int supercellSize,
        int equilibrationSteps,
        int averagingSteps,
        long seed
    ) {
        this.system = system;
        this.temperature = temperature;
        this.composition = composition;
        this.supercellSize = supercellSize;
        this.equilibrationSteps = equilibrationSteps;
        this.averagingSteps = averagingSteps;
        this.seed = seed;
        this.isReady = false;
    }
    
    // Getters
    public SystemInfo getSystem() { return system; }
    public double getTemperature() { return temperature; }
    public double getComposition() { return composition; }
    public int getSupercellSize() { return supercellSize; }
    public int getEquilibrationSteps() { return equilibrationSteps; }
    public int getAveragingSteps() { return averagingSteps; }
    public long getSeed() { return seed; }
    
    public ClusCoordListResult getClusterData() { return clusterData; }
    public double[] getECI() { return eci; }
    public boolean isReady() { return isReady; }
    public String getReadinessError() { return readinessError; }
    
    // Setters
    public void setClusterData(ClusCoordListResult clusterData) {
        this.clusterData = clusterData;
        validateReadiness();
    }
    
    public void setECI(double[] eci) {
        this.eci = eci;
        validateReadiness();
    }
    
    /**
     * Validates that all required data is present and consistent.
     * Never throws — callers check {@link #isReady()} and {@link #getReadinessError()}.
     */
    private void validateReadiness() {
        if (clusterData != null && eci != null) {
            // ECI array must have one value for each cluster type
            if (eci.length == clusterData.getTc()) {
                this.isReady = true;
                this.readinessError = null;
            } else {
                this.isReady = false;
                this.readinessError =
                    "ECI length (" + eci.length + ") does not match cluster type count ("
                    + clusterData.getTc() + ") for system " + system.getName()
                    + ". CEC values must be provided for all " + clusterData.getTc()
                    + " cluster types.";
            }
        }
    }
    
    /**
     * Returns a summary of the calculation context.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("System: ").append(system.getName()).append("\n");
        sb.append("Temperature: ").append(temperature).append(" K\n");
        sb.append("Composition: ").append(composition).append("\n");
        sb.append("Supercell size: ").append(supercellSize).append("\n");
        sb.append("Equilibration: ").append(equilibrationSteps).append(" steps\n");
        sb.append("Averaging: ").append(averagingSteps).append(" steps\n");
        sb.append("Seed: ").append(seed).append("\n");
        if (clusterData != null) {
            sb.append("Cluster types: ").append(clusterData.getTc()).append("\n");
        }
        if (eci != null) {
            sb.append("ECI loaded: ").append(eci.length).append(" values\n");
        }
        sb.append("Ready: ").append(isReady).append("\n");
        return sb.toString();
    }
}