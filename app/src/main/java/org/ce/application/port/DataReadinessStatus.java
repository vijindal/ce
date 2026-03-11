package org.ce.application.port;

/**
 * Represents the data readiness state for a given system.
 *
 * Used by Type 2 calculations to determine if all required data is available
 * before running the calculation. The GUI uses this to gate the Run button.
 */
public record DataReadinessStatus(
    boolean clusterAvailable,
    boolean cecAvailable,
    boolean cfsAvailable,
    String message
) {

    /**
     * Check if data is ready for CVM calculation.
     * CVM requires: clusters + CFs (Stage 1+2) + CEC.
     */
    public boolean isReadyForCVM() {
        return clusterAvailable && cecAvailable && cfsAvailable;
    }

    /**
     * Check if data is ready for MCS calculation.
     * MCS requires: clusters (Stage 1) + CEC. CFs (Stage 2) not strictly required but recommended.
     */
    public boolean isReadyForMCS() {
        return clusterAvailable && cecAvailable;
    }

    /**
     * Factory for ready state.
     */
    public static DataReadinessStatus ready() {
        return new DataReadinessStatus(true, true, true, "Ready");
    }

    /**
     * Factory for not ready state with explanation.
     */
    public static DataReadinessStatus notReady(String reason) {
        return new DataReadinessStatus(false, false, false, reason);
    }

    /**
     * Factory for custom readiness.
     */
    public static DataReadinessStatus of(boolean cluster, boolean cec, boolean cfs, String msg) {
        return new DataReadinessStatus(cluster, cec, cfs, msg);
    }
}
