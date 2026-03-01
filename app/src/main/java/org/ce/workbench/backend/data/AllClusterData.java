package org.ce.workbench.backend.data;

import org.ce.core.CVMResult;
import org.ce.cvm.CMatrixResult;
import org.ce.identification.cluster.ClusterIdentificationResult;
import org.ce.identification.cf.CFIdentificationResult;

/**
 * Unified container for all pre-computed cluster data across Stages 1-3.
 *
 * <p>Holds the complete output of the cluster identification, correlation function
 * identification, and C-matrix construction pipeline. This data is computed once
 * for a given structure/phase/model/numComponents combination and reused across
 * multiple CVM calculations at different temperatures and compositions.</p>
 *
 * <h2>Data completeness</h2>
 * <ul>
 *   <li><b>Stage 1</b>: Cluster identification (tcdis, kb coefficients, multiplicities)</li>
 *   <li><b>Stage 2</b>: CF identification (tcf, lcf, mcf, grouped CF data)</li>
 *   <li><b>Stage 3</b>: C-matrix construction (cmat, lcv, wcv)</li>
 * </ul>
 *
 * <p>A system is ready for CVM calculations when all three stages are complete and non-null.</p>
 *
 * @see ClusterIdentificationResult
 * @see CFIdentificationResult
 * @see CMatrixResult
 */
public class AllClusterData {

    private final String systemId;           // e.g., "Ti-Nb_BCC_A2_T_bin"
    private final int numComponents;
    private final long computationTimeMs;   // Total time to compute all stages

    // Stage results
    private final ClusterIdentificationResult stage1;  // Cluster identification
    private final CFIdentificationResult stage2;       // CF identification
    private final CMatrixResult stage3;                // C-matrix construction

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Constructs a complete cluster data bundle.
     *
     * @param systemId system identifier (e.g., "Ti-Nb_BCC_A2_T_bin")
     * @param numComponents number of chemical components (2, 3, 4+)
     * @param stage1 cluster identification result
     * @param stage2 CF identification result (must include lcv, wcv from C-matrix)
     * @param stage3 C-matrix result (cmat, lcv, wcv)
     * @param computationTimeMs total milliseconds to compute all stages
     */
    public AllClusterData(
            String systemId,
            int numComponents,
            ClusterIdentificationResult stage1,
            CFIdentificationResult stage2,
            CMatrixResult stage3,
            long computationTimeMs) {

        this.systemId = systemId;
        this.numComponents = numComponents;
        this.stage1 = stage1;
        this.stage2 = stage2;
        this.stage3 = stage3;
        this.computationTimeMs = computationTimeMs;
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    public String getSystemId() {
        return systemId;
    }

    public int getNumComponents() {
        return numComponents;
    }

    /**
     * Returns Stage 1 cluster identification result.
     *
     * @return {@link ClusterIdentificationResult}; may be {@code null}
     */
    public ClusterIdentificationResult getStage1() {
        return stage1;
    }

    /**
     * Returns Stage 2 correlation function identification result.
     *
     * @return {@link CFIdentificationResult}; may be {@code null}
     */
    public CFIdentificationResult getStage2() {
        return stage2;
    }

    /**
     * Returns Stage 3 C-matrix construction result.
     *
     * @return {@link CMatrixResult}; may be {@code null}
     */
    public CMatrixResult getStage3() {
        return stage3;
    }

    public long getComputationTimeMs() {
        return computationTimeMs;
    }

    // =========================================================================
    // Convenience accessors for critical quantities
    // =========================================================================

    /**
     * Total number of distinct HSP cluster types (from Stage 1).
     *
     * @return tcdis; -1 if Stage 1 not computed
     */
    public int getTcdis() {
        return (stage1 != null) ? stage1.getTcdis() : -1;
    }

    /**
     * Total number of correlation functions (from Stage 2).
     *
     * @return tcf; -1 if Stage 2 not computed
     */
    public int getTcf() {
        return (stage2 != null) ? stage2.getTcf() : -1;
    }

    /**
     * C-matrix data (from Stage 3).
     *
     * @return 3D C-matrix array; {@code null} if Stage 3 not computed
     */
    public Object getCmat() {
        return (stage3 != null) ? stage3.getCmat() : null;
    }

    /**
     * Cluster variable counts per cluster type (from Stage 3).
     *
     * @return lcv array; {@code null} if Stage 3 not computed
     */
    public int[][] getLcv() {
        return (stage3 != null) ? stage3.getLcv() : null;
    }

    /**
     * Cluster variable weight patterns (from Stage 3).
     *
     * @return wcv array; {@code null} if Stage 3 not computed
     */
    public Object getWcv() {
        return (stage3 != null) ? stage3.getWcv() : null;
    }

    // =========================================================================
    // Data completeness checks
    // =========================================================================

    /**
     * Checks if all three stages have been computed.
     *
     * @return true if stage1, stage2, and stage3 are all non-null
     */
    public boolean isComplete() {
        return stage1 != null && stage2 != null && stage3 != null;
    }

    /**
     * Checks if Stage 1 (cluster identification) is complete.
     *
     * @return true if stage1 is non-null
     */
    public boolean isStage1Complete() {
        return stage1 != null;
    }

    /**
     * Checks if Stage 2 (CF identification) is complete.
     *
     * @return true if stage2 is non-null
     */
    public boolean isStage2Complete() {
        return stage2 != null;
    }

    /**
     * Checks if Stage 3 (C-matrix) is complete.
     *
     * @return true if stage3 is non-null and cmat is non-null
     */
    public boolean isStage3Complete() {
        return stage3 != null && stage3.getCmat() != null;
    }

    /**
     * Get the first incomplete stage.
     *
     * @return stage number (1, 2, or 3) of first incomplete stage; 0 if complete
     */
    public int getFirstIncompleteStage() {
        if (!isStage1Complete()) return 1;
        if (!isStage2Complete()) return 2;
        if (!isStage3Complete()) return 3;
        return 0;
    }

    // =========================================================================
    // Diagnostics
    // =========================================================================

    /**
     * Human-readable summary of data availability.
     *
     * @return multi-line string with stage-by-stage status
     */
    public String getCompletionStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("System: ").append(systemId).append("\n");
        sb.append("Components: ").append(numComponents).append("\n");
        sb.append("─────────────────────────\n");

        sb.append("Stage 1 (Cluster ID): ");
        if (isStage1Complete()) {
            sb.append("✓ tcdis=").append(stage1.getTcdis());
        } else {
            sb.append("✗ Not computed");
        }
        sb.append("\n");

        sb.append("Stage 2 (CF ID):      ");
        if (isStage2Complete()) {
            sb.append("✓ tcf=").append(stage2.getTcf());
        } else {
            sb.append("✗ Not computed");
        }
        sb.append("\n");

        sb.append("Stage 3 (C-matrix):   ");
        if (isStage3Complete()) {
            sb.append("✓ Ready");
        } else {
            sb.append("✗ Not computed");
        }
        sb.append("\n");

        sb.append("─────────────────────────\n");
        if (isComplete()) {
            sb.append("✓ COMPLETE - Ready for CVM\n");
            sb.append("Computed in: ").append(computationTimeMs).append(" ms");
        } else {
            sb.append("✗ INCOMPLETE - Cannot run CVM\n");
            sb.append("First missing: Stage ").append(getFirstIncompleteStage());
        }

        return sb.toString();
    }

    /**
     * Debug print with full details.
     */
    public void printDebug() {
        System.out.println(getCompletionStatus());
        if (stage1 != null) {
            stage1.printDebug();
        }
        if (stage2 != null) {
            stage2.printDebug();
        }
    }

    @Override
    public String toString() {
        return String.format(
            "AllClusterData{id=%s, comp=%d, complete=%s, time=%dms}",
            systemId, numComponents, isComplete(), computationTimeMs);
    }
}
