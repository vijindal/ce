package org.ce.workbench.gui.model;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Container for calculation results from MCS or CVM.
 * Stores all computed thermodynamic properties and structural data.
 */
public class CalculationResults {
    
    private final String id;
    private final CalculationConfig config;
    private final LocalDateTime completionTime;
    
    // Thermodynamic properties
    private double gibbsFreeEnergy;      // eV/atom
    private double enthalpy;             // eV/atom
    private double entropy;              // kb/atom
    private double heatCapacity;         // kb/atom
    
    private double configurationalEntropy; // kb/atom
    private double vibrationialEntropy;   // kb/atom
    
    // Structural properties
    private double[] correlationFunctions; // CF values
    private double[] shortRangeOrder;      // SRO values
    private double longRangeOrder;         // Long-range order parameter
    private double warrenCowleyOrder;      // Warren-Cowley SRO
    
    // MC statistics
    private double acceptanceRate;
    private double energyStdDev;
    private int mcStepsCompleted;
    
    // Additional metadata
    private long wallClockTimeMs;
    private double convarianceParameter; // for convergence assessment
    private String notes;
    
    private Map<String, Object> customProperties = new HashMap<>();
    
    public CalculationResults(String id, CalculationConfig config) {
        this.id = Objects.requireNonNull(id, "id");
        this.config = Objects.requireNonNull(config, "config");
        this.completionTime = LocalDateTime.now();
    }
    
    // Getters
    public String getId() { return id; }
    public CalculationConfig getConfig() { return config; }
    public LocalDateTime getCompletionTime() { return completionTime; }
    
    public double getGibbsFreeEnergy() { return gibbsFreeEnergy; }
    public double getEnthalpy() { return enthalpy; }
    public double getEntropy() { return entropy; }
    public double getHeatCapacity() { return heatCapacity; }
    
    public double getConfiguralionalEntropy() { return configurationalEntropy; }
    public double getVibrationialEntropy() { return vibrationialEntropy; }
    
    public double[] getCorrelationFunctions() { return correlationFunctions; }
    public double[] getShortRangeOrder() { return shortRangeOrder; }
    public double getLongRangeOrder() { return longRangeOrder; }
    public double getWarrenCowleyOrder() { return warrenCowleyOrder; }
    
    public double getAcceptanceRate() { return acceptanceRate; }
    public double getEnergyStdDev() { return energyStdDev; }
    public int getMcStepsCompleted() { return mcStepsCompleted; }
    
    public long getWallClockTimeMs() { return wallClockTimeMs; }
    public double getConvergenceParameter() { return convarianceParameter; }
    public String getNotes() { return notes; }
    
    // Setters
    public void setGibbsFreeEnergy(double value) { this.gibbsFreeEnergy = value; }
    public void setEnthalpy(double value) { this.enthalpy = value; }
    public void setEntropy(double value) { this.entropy = value; }
    public void setHeatCapacity(double value) { this.heatCapacity = value; }
    
    public void setConfiguralionalEntropy(double value) { this.configurationalEntropy = value; }
    public void setVibrationialEntropy(double value) { this.vibrationialEntropy = value; }
    
    public void setCorrelationFunctions(double[] values) { this.correlationFunctions = values; }
    public void setShortRangeOrder(double[] values) { this.shortRangeOrder = values; }
    public void setLongRangeOrder(double value) { this.longRangeOrder = value; }
    public void setWarrenCowleyOrder(double value) { this.warrenCowleyOrder = value; }
    
    public void setAcceptanceRate(double rate) { this.acceptanceRate = rate; }
    public void setEnergyStdDev(double dev) { this.energyStdDev = dev; }
    public void setMcStepsCompleted(int steps) { this.mcStepsCompleted = steps; }
    
    public void setWallClockTimeMs(long ms) { this.wallClockTimeMs = ms; }
    public void setConvergenceParameter(double param) { this.convarianceParameter = param; }
    public void setNotes(String notes) { this.notes = notes; }
    
    public void setCustomProperty(String key, Object value) {
        customProperties.put(key, value);
    }
    
    public Object getCustomProperty(String key) {
        return customProperties.get(key);
    }
    
    /**
     * Returns a human-readable summary of key results.
     */
    public String getSummary() {
        return String.format(
            "G=%.4f eV/atom, H=%.4f eV/atom, S=%.4f kb/atom, T=%.0f K, x=%.2f",
            gibbsFreeEnergy,
            enthalpy,
            entropy,
            config.getTemperature(),
            config.getComposition()
        );
    }
    
    @Override
    public String toString() {
        return String.format("%s @ %s", config, completionTime);
    }
}
