package org.ce.domain.mcs;

/**
 * Real-time update event for MCS simulations.
 * Contains energy and convergence metrics for monitoring.
 *
 * <p>Uses delta-E optimization:
 * - E_total is accumulated as E_initial + sum(deltaE)
 * - Per-step deltaE calculation is much cheaper than full energy recalculation.</p>
 */
public class MCSUpdate {

    /**
     * Phase of the simulation (equilibration or averaging).
     */
    public enum Phase {
        EQUILIBRATION, AVERAGING
    }

    private final int step;
    private final double E_total;
    private final double deltaE;
    private final double sigmaDE;
    private final double meanDE;
    private final Phase phase;
    private final double acceptanceRate;
    private final long timestampMs;
    private final long elapsedMs;

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

    public int getStep() { return step; }
    public double getE_total() { return E_total; }
    public double getDeltaE() { return deltaE; }
    public double getSigmaDE() { return sigmaDE; }
    public double getMeanDE() { return meanDE; }
    public Phase getPhase() { return phase; }
    public double getAcceptanceRate() { return acceptanceRate; }
    public long getTimestampMs() { return timestampMs; }
    public long getElapsedMs() { return elapsedMs; }

    public enum ConvergenceStatus {
        CONVERGED,
        CONVERGING,
        EARLY_STAGE,
        FAR_FROM_EQ
    }

    public ConvergenceStatus getConvergenceStatus() {
        if (sigmaDE < 1e-12) return ConvergenceStatus.CONVERGED;

        double driftRatio = Math.abs(meanDE) / (sigmaDE + 1e-12);

        if (driftRatio < 0.05) return ConvergenceStatus.CONVERGED;
        if (driftRatio < 0.15) return ConvergenceStatus.CONVERGING;
        if (driftRatio < 0.35) return ConvergenceStatus.EARLY_STAGE;
        return ConvergenceStatus.FAR_FROM_EQ;
    }

    public String getStatusColor() {
        return switch (getConvergenceStatus()) {
            case CONVERGED -> "#43A047";
            case CONVERGING -> "#FBC02D";
            case EARLY_STAGE -> "#FB8C00";
            case FAR_FROM_EQ -> "#D32F2F";
        };
    }

    public String getStatusLabel() {
        return switch (getConvergenceStatus()) {
            case CONVERGED -> "Converged";
            case CONVERGING -> "Converging";
            case EARLY_STAGE -> "Early Stage";
            case FAR_FROM_EQ -> "Drift Dominant";
        };
    }

    @Override
    public String toString() {
        return String.format(
            "MCSUpdate{step=%d, E=%.4f, dE=%.6f, sigma(dE)=%.6f, mean(dE)=%.6f, phase=%s, acceptance=%.1f%%, elapsed=%dms}",
            step, E_total, deltaE, sigmaDE, meanDE, phase, acceptanceRate * 100, elapsedMs);
    }
}

