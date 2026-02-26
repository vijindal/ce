package org.ce.core;

import org.ce.identification.cluster.ClusterIdentificationResult;
import org.ce.identification.cf.CFIdentificationResult;
import java.util.Objects;

/**
 * Unified result object from the two-stage CVM pipeline.
 *
 * <p>Encapsulates the complete output of both cluster identification (Stage 1)
 * and correlation function identification (Stage 2).  Clients use this object
 * to access results from either stage without needing to understand the internal
 * orchestration.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * CVMResult result = pipeline.identify(config);
 *
 * // Access Stage 1 results (cluster identification)
 * ClusterIdentificationResult clusterResult = result.getClusterIdentification();
 *
 * // Access Stage 2 results (correlation function identification)
 * CFIdentificationResult cfResult = result.getCorrelationFunctionIdentification();
 * }</pre>
 *
 * @author  CVM Project
 * @version 1.0
 * @see     CVMPipeline
 * @see     CVMConfiguration
 * @see     ClusterIdentificationResult
 * @see     CFIdentificationResult
 */
public class CVMResult {

    private final ClusterIdentificationResult clusterIdentification;
    private final CFIdentificationResult correlationFunctionIdentification;

    /**
     * Constructs a CVMResult from the outputs of both identification stages.
     *
     * @param clusterIdentification result of Stage 1 (cluster identification);
     *                              must not be {@code null}
     * @param correlationFunctionIdentification result of Stage 2 (CF identification);
     *                              must not be {@code null}
     */
    public CVMResult(
            ClusterIdentificationResult clusterIdentification,
            CFIdentificationResult correlationFunctionIdentification) {

        this.clusterIdentification = Objects.requireNonNull(
                clusterIdentification, "clusterIdentification");
        this.correlationFunctionIdentification = Objects.requireNonNull(
                correlationFunctionIdentification, "correlationFunctionIdentification");
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /**
     * Returns the Stage 1 (Cluster Identification) result.
     *
     * <p>This result contains:
     * <ul>
     *   <li>Distinct cluster types for the CVM approximation</li>
     *   <li>Multiplicities and Kikuchi-Baker coefficients</li>
     *   <li>Classification of ordered-phase clusters into HSP types</li>
     * </ul></p>
     *
     * @return the {@link ClusterIdentificationResult}; never {@code null}
     */
    public ClusterIdentificationResult getClusterIdentification() {
        return clusterIdentification;
    }

    /**
     * Returns the Stage 2 (Correlation Function Identification) result.
     *
     * <p>This result contains:
     * <ul>
     *   <li>Distinct correlation functions (decorated clusters) for the given component count</li>
     *   <li>Grouping of CFs by HSP cluster type and ordered-phase cluster group</li>
     *   <li>Multiplicities for each CF</li>
     * </ul></p>
     *
     * @return the {@link CFIdentificationResult}; never {@code null}
     */
    public CFIdentificationResult getCorrelationFunctionIdentification() {
        return correlationFunctionIdentification;
    }

    /**
     * Convenience method: returns the Stage 1 result (alias for
     * {@link #getClusterIdentification()}).
     *
     * @return the {@link ClusterIdentificationResult}
     */
    public ClusterIdentificationResult getClusters() {
        return clusterIdentification;
    }

    /**
     * Convenience method: returns the Stage 2 result (alias for
     * {@link #getCorrelationFunctionIdentification()}).
     *
     * @return the {@link CFIdentificationResult}
     */
    public CFIdentificationResult getCorrelationFunctions() {
        return correlationFunctionIdentification;
    }

    // =========================================================================
    // Debug
    // =========================================================================

    /**
     * Prints a structured debug summary of both identification stages.
     */
    public void printDebug() {
        System.out.println("\n========================================");
        System.out.println("[CVMResult] Two-Stage Pipeline Output");
        System.out.println("========================================");

        System.out.println("\n--- Stage 1: Cluster Identification ---");
        if (clusterIdentification != null) {
            clusterIdentification.printDebug();
        }

        System.out.println("\n--- Stage 2: Correlation Function Identification ---");
        if (correlationFunctionIdentification != null) {
            correlationFunctionIdentification.printDebug();
        }

        System.out.println("========================================");
    }
}
