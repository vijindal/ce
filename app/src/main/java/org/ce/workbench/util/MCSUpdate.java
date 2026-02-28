package org.ce.workbench.util;

/**
 * Real-time update event for MCS simulations.
 * Contains energy and convergence metrics for GUI monitoring.
 * 
 * Uses ΔE (energy change) optimization:
 * - E_total is accumulated as E_initial + Σ(ΔE)
 * - Per-step ΔE calculation is ~1000x cheaper than full energy recalculation
 * - Enables real-time monitoring with minimal performance overhead
 */
public class MCSUpdate {
    
    /**
     * Phase of the simulation (equilibration or averaging)
     */
    public enum Phase {
        EQUILIBRATION, AVERAGING
    }
    
    private final int step;                        // Current MC sweep number
    private final double E_total;                  // Cumulative energy: E_initial + Σ(ΔE)
    private final double deltaE;                   // Energy change for this step
    private final double sigmaDE;                  // σ(ΔE) over rolling window (stability metric)
    private final double meanDE;                   // mean(ΔE) over rolling window (drift check)
    private final Phase phase;                     // EQUILIBRATION or AVERAGING
    private final double acceptanceRate;           // Fraction of accepted moves
    private final long timestampMs;                // Wall-clock time of this update
    private final long elapsedMs;                  // Total elapsed time since start
    
    // Constructor
    public MCSUpdate(
            int step,
            double E_total,
            double deltaE,
            double sigmaDE,
            double meanDE,
            Phase phase,
            double acceptanceRate,
            long timestampMs,
            long elapsedMs) {
        this.step = step;
        this.E_total = E_total;
        this.deltaE = deltaE;
        this.sigmaDE = sigmaDE;
        this.meanDE = meanDE;
        this.phase = phase;
        this.acceptanceRate = acceptanceRate;
        this.timestampMs = timestampMs;
        this.elapsedMs = elapsedMs;
    }
    
    // Getters
    public int getStep() { return step; }
    public double getE_total() { return E_total; }
    public double getDeltaE() { return deltaE; }
    public double getSigmaDE() { return sigmaDE; }
    public double getMeanDE() { return meanDE; }
    public Phase getPhase() { return phase; }
    public double getAcceptanceRate() { return acceptanceRate; }
    public long getTimestampMs() { return timestampMs; }
    public long getElapsedMs() { return elapsedMs; }
    
    /**
     * Convergence status based on σ(ΔE).
     * σ(ΔE) indicates how wide the energy fluctuations are.
     */
    public enum ConvergenceStatus {
        CONVERGED,      // σ(ΔE) < 0.05 - Ready to average
        CONVERGING,     // 0.05 ≤ σ(ΔE) < 0.15 - Still equilibrating
        EARLY_STAGE,    // 0.15 ≤ σ(ΔE) < 0.50 - Early equilibration
        FAR_FROM_EQ     // σ(ΔE) ≥ 0.50 - Very far from equilibrium
    }
    
    /**
     * Get convergence status based on σ(ΔE).
     * @return convergence status indicator
     */
    public ConvergenceStatus getConvergenceStatus() {
        if (sigmaDE < 0.05) return ConvergenceStatus.CONVERGED;
        if (sigmaDE < 0.15) return ConvergenceStatus.CONVERGING;
        if (sigmaDE < 0.50) return ConvergenceStatus.EARLY_STAGE;
        return ConvergenceStatus.FAR_FROM_EQ;
    }
    
    /**
     * Color code for convergence status.
     * @return hex color string for status indicator
     */
    public String getStatusColor() {
        return switch (getConvergenceStatus()) {
            case CONVERGED -> "#43A047";      // Green
            case CONVERGING -> "#FBC02D";     // Yellow
            case EARLY_STAGE -> "#FB8C00";    // Orange
            case FAR_FROM_EQ -> "#D32F2F";    // Red
        };
    }
    
    /**
     * Human-readable convergence status.
     * @return status string
     */
    public String getStatusLabel() {
        return switch (getConvergenceStatus()) {
            case CONVERGED -> "✅ Converged";
            case CONVERGING -> "🟡 Converging";
            case EARLY_STAGE -> "🟠 Early Stage";
            case FAR_FROM_EQ -> "🔴 Far from Equilib.";
        };
    }
    
    @Override
    public String toString() {
        return String.format(
            "MCSUpdate{step=%d, E=%.4f, ΔE=%.6f, σ(ΔE)=%.6f, μ(ΔE)=%.6f, " +
            "phase=%s, acceptance=%.1f%%, elapsed=%dms}",
            step, E_total, deltaE, sigmaDE, meanDE, phase, acceptanceRate * 100, elapsedMs);
    }
}
