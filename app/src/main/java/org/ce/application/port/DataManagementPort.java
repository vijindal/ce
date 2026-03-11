package org.ce.application.port;

import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.system.SystemIdentity;

import java.util.Optional;

/**
 * Type 1: Data Management facade.
 *
 * This port encapsulates all operations for managing cluster identification,
 * CEC database, and system registry. The port provides a read-only view
 * of the data layer for Type 2 calculations and readiness checks.
 *
 * Implementations must be thread-safe for use in both UI and background threads.
 */
public interface DataManagementPort {

    // ==================== Cluster Data ====================

    /**
     * Check if cluster identification data exists for the given cluster key.
     *
     * @param clusterKey the cluster key (e.g., "BCC_A2_T_bin")
     * @return true if AllClusterData exists in cache
     */
    boolean isClusterDataAvailable(String clusterKey);

    /**
     * Load cluster identification data from cache.
     *
     * @param clusterKey the cluster key
     * @return Optional containing AllClusterData, empty if not found or on error
     */
    Optional<AllClusterData> loadClusterData(String clusterKey);

    // ==================== CEC / ECI Data ====================

    /**
     * Check if CEC data exists for the given CEC key.
     *
     * @param cecKey the CEC key (e.g., "Nb-Ti_BCC_A2_T")
     * @return true if CEC data exists in workspace or classpath
     */
    boolean isCecAvailable(String cecKey);

    /**
     * Load ECI values from the CEC database for the given system parameters.
     *
     * This method loads from disk ONLY — no interactive dialog fallback.
     * If CEC not found, caller should handle the empty Optional gracefully
     * (e.g., mark job as failed with an informative message).
     *
     * @param elements element string (e.g., "Nb-Ti")
     * @param structure crystal structure (e.g., "BCC")
     * @param phase phase name (e.g., "A2")
     * @param model theoretical model (e.g., "T")
     * @param temperature temperature in Kelvin (for polynomial evaluation)
     * @param requiredLength expected ECI array length (ncf for CVM, tc for MCS)
     * @return Optional containing ECI double[] in J/mol, empty if not found
     */
    Optional<double[]> loadECI(String elements, String structure,
                               String phase, String model,
                               double temperature, int requiredLength);

    // ==================== System Registry ====================

    /**
     * Get a registered system by its ID.
     *
     * @param systemId the system identifier (e.g., "Nb-Ti_BCC_A2_T")
     * @return the SystemIdentity if registered, null otherwise
     */
    SystemIdentity getSystem(String systemId);

    /**
     * Check if correlation function data (Stage 2) has been computed for this system.
     *
     * @param systemId the system identifier
     * @return true if CFs are computed
     */
    boolean isCfsComputed(String systemId);

    /**
     * Check if cluster data (Stage 1) has been computed for this system.
     *
     * @param systemId the system identifier
     * @return true if clusters are computed
     */
    boolean isClustersComputed(String systemId);
}
