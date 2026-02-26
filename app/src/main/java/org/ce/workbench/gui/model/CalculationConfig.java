package org.ce.workbench.gui.model;

import java.util.Objects;

/**
 * Configuration parameters for MCS or CVM calculations.
 * Separates setup parameters from execution state.
 */
public class CalculationConfig {
    
    public enum CalculationType {
        MCS("Monte Carlo Simulation"),
        CVM("Cluster Variation Method");
        
        private final String displayName;
        CalculationType(String displayName) { this.displayName = displayName; }
        public String getDisplayName() { return displayName; }
    }
    
    private final CalculationType type;
    private final SystemInfo system;
    
    // Common parameters
    private double[] eci; // Energy Cluster Interaction vector
    private double temperature;
    
    // MCS-specific
    private double composition; // Binary: 0 to 1
    private int supercellSize; // L parameter
    private int equilibrationSteps;
    private int averagingSteps;
    private long randomSeed;
    
    // CVM-specific (if applicable)
    private String cvmApproximation; // e.g., "Pair approximation"
    
    // Optional sweeps
    private boolean temperatureSweepEnabled;
    private double temperatureSweepMin;
    private double temperatureSweepMax;
    private double temperatureSweepDelta;
    
    private boolean compositionSweepEnabled;
    private double compositionSweepMin;
    private double compositionSweepMax;
    private double compositionSweepDelta;
    
    public CalculationConfig(CalculationType type, SystemInfo system) {
        this.type = Objects.requireNonNull(type, "type");
        this.system = Objects.requireNonNull(system, "system");
        
        // Default values
        this.temperature = 1000.0;
        this.composition = 0.5;
        this.supercellSize = 8;
        this.equilibrationSteps = 10000;
        this.averagingSteps = 50000;
        this.randomSeed = System.currentTimeMillis();
        this.eci = new double[10]; // Default size, adjustable
    }
    
    // Getters
    public CalculationType getType() { return type; }
    public SystemInfo getSystem() { return system; }
    public double[] getEci() { return eci; }
    public double getTemperature() { return temperature; }
    public double getComposition() { return composition; }
    public int getSupercellSize() { return supercellSize; }
    public int getEquilibrationSteps() { return equilibrationSteps; }
    public int getAveragingSteps() { return averagingSteps; }
    public long getRandomSeed() { return randomSeed; }
    public String getCvmApproximation() { return cvmApproximation; }
    
    public boolean isTemperatureSweepEnabled() { return temperatureSweepEnabled; }
    public double getTemperatureSweepMin() { return temperatureSweepMin; }
    public double getTemperatureSweepMax() { return temperatureSweepMax; }
    public double getTemperatureSweepDelta() { return temperatureSweepDelta; }
    
    public boolean isCompositionSweepEnabled() { return compositionSweepEnabled; }
    public double getCompositionSweepMin() { return compositionSweepMin; }
    public double getCompositionSweepMax() { return compositionSweepMax; }
    public double getCompositionSweepDelta() { return compositionSweepDelta; }
    
    // Setters
    public void setEci(double[] eci) { this.eci = Objects.requireNonNull(eci); }
    public void setTemperature(double temp) { this.temperature = temp; }
    public void setComposition(double comp) { this.composition = Math.max(0, Math.min(1, comp)); }
    public void setSupercellSize(int size) { this.supercellSize = size; }
    public void setEquilibrationSteps(int steps) { this.equilibrationSteps = steps; }
    public void setAveragingSteps(int steps) { this.averagingSteps = steps; }
    public void setRandomSeed(long seed) { this.randomSeed = seed; }
    public void setCvmApproximation(String approx) { this.cvmApproximation = approx; }
    
    public void setTemperatureSweep(boolean enabled, double min, double max, double delta) {
        this.temperatureSweepEnabled = enabled;
        this.temperatureSweepMin = min;
        this.temperatureSweepMax = max;
        this.temperatureSweepDelta = delta;
    }
    
    public void setCompositionSweep(boolean enabled, double min, double max, double delta) {
        this.compositionSweepEnabled = enabled;
        this.compositionSweepMin = min;
        this.compositionSweepMax = max;
        this.compositionSweepDelta = delta;
    }
    
    /**
     * Validates the configuration is complete and valid.
     */
    public boolean isValid() {
        if (system == null) return false;
        if (eci == null || eci.length == 0) return false;
        if (temperature <= 0) return false;
        if (composition < 0 || composition > 1) return false;
        if (supercellSize < 2) return false;
        if (equilibrationSteps < 100) return false;
        if (averagingSteps < 100) return false;
        return true;
    }
    
    @Override
    public String toString() {
        return type.getDisplayName() + " @ " + temperature + "K, x=" + 
               String.format("%.2f", composition);
    }
}
