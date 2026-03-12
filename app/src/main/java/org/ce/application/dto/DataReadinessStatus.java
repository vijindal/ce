package org.ce.application.dto;

/**
 * Immutable status record for data readiness before calculation submission.
 *
 * <p>Captures the availability of required data artifacts (cluster cache, CEC database, CFs)
 * and provides convenience methods to check if a system is ready for specific calculation types.</p>
 */
public record DataReadinessStatus(
    boolean clusterAvailable,
    boolean cecAvailable,
    boolean cfsAvailable,
    String message
) {

    /**
     * Checks if system has all data required for MCS calculations.
     * MCS requires: cluster cache + CEC database
     */
    public boolean isReadyForMCS() {
        return clusterAvailable && cecAvailable;
    }

    /**
     * Checks if system has all data required for CVM calculations.
     * CVM requires: cluster cache + CEC database + CF identification (CFs computed)
     */
    public boolean isReadyForCVM() {
        return clusterAvailable && cecAvailable && cfsAvailable;
    }

    /**
     * Creates a readiness status with all components available.
     */
    public static DataReadinessStatus ready() {
        return new DataReadinessStatus(true, true, true, "All data available");
    }

    /**
     * Creates a readiness status with missing cluster data.
     */
    public static DataReadinessStatus missingClusters(String systemId) {
        return new DataReadinessStatus(
            false, false, false,
            "System '" + systemId + "' has not completed cluster identification. " +
            "Run the identification pipeline first."
        );
    }

    /**
     * Creates a readiness status with missing CEC data.
     */
    public static DataReadinessStatus missingCEC(String systemId) {
        return new DataReadinessStatus(
            true, false, false,
            "System '" + systemId + "' has no CEC data in database. " +
            "Use Data > CEC Database to add it."
        );
    }

    /**
     * Creates a readiness status with missing CF data.
     */
    public static DataReadinessStatus missingCFs(String systemId) {
        return new DataReadinessStatus(
            true, true, false,
            "System '" + systemId + "' has not completed CF identification. " +
            "Run the full identification pipeline first."
        );
    }

    /**
     * Factory for custom readiness state.
     */
    public static DataReadinessStatus of(boolean cluster, boolean cec, boolean cfs, String msg) {
        return new DataReadinessStatus(cluster, cec, cfs, msg);
    }
}
