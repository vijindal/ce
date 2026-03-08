package org.ce.domain.cvm;

import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.identification.cluster.ClusterIdentificationResult;
import org.ce.domain.identification.cluster.CFIdentificationResult;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CMatrixBuilder / CMatrixResult correctness.
 *
 * <p>Verifies structural properties of the built C-matrix using the
 * BCC_A2_T_bin cluster data as the test fixture.</p>
 *
 * <p>All tests rely on analytical invariants only — no external reference values.</p>
 */
class CMatrixBuilderTest {

    private static AllClusterData clusterData;
    private static ClusterIdentificationResult stage1;
    private static CFIdentificationResult stage2;
    private static CMatrixResult stage3;

    @BeforeAll
    static void loadClusterData() throws Exception {
        Optional<AllClusterData> opt = AllClusterDataCache.load("BCC_A2_T_bin");
        assertTrue(opt.isPresent(), "BCC_A2_T_bin cluster cache must be present");
        clusterData = opt.get();
        assertTrue(clusterData.isComplete(), "BCC_A2_T_bin cluster data must be complete (all 3 stages)");
        stage1 = clusterData.getStage1();
        stage2 = clusterData.getStage2();
        stage3 = clusterData.getStage3();
    }

    // =========================================================================
    // Dimension / structure tests
    // =========================================================================

    /**
     * For each (t, j), the C-matrix row count must equal lcv[t][j].
     * Each row must have exactly tcf+1 columns (tcf CF slots + 1 constant).
     */
    @Test
    void cmatDimensions_matchLcvAndTcfPlusOne() {
        List<List<double[][]>> cmat = stage3.getCmat();
        int[][] lcv = stage3.getLcv();
        int tcdis = stage1.getTcdis();
        int[] lc = stage1.getLc();
        int tcf = stage2.getTcf();

        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                double[][] cm = cmat.get(t).get(j);
                int expectedRows = lcv[t][j];
                assertEquals(expectedRows, cm.length,
                        String.format("cmat[%d][%d] must have %d rows (= lcv[%d][%d])", t, j, expectedRows, t, j));
                for (int v = 0; v < cm.length; v++) {
                    assertEquals(tcf + 1, cm[v].length,
                            String.format("cmat[%d][%d][%d] must have tcf+1=%d columns", t, j, v, tcf + 1));
                }
            }
        }
    }

    /**
     * wcv[t][j][v] must be strictly positive for all valid (t, j, v).
     *
     * <p>Weights are symmetry orbit sizes — they are always positive integers.</p>
     */
    @Test
    void wcvWeights_arePositive() {
        List<List<int[]>> wcv = stage3.getWcv();
        int[][] lcv = stage3.getLcv();
        int tcdis = stage1.getTcdis();
        int[] lc = stage1.getLc();

        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                int[] w = wcv.get(t).get(j);
                int nv = lcv[t][j];
                for (int v = 0; v < nv; v++) {
                    assertTrue(w[v] > 0,
                            String.format("wcv[%d][%d][%d] = %d must be positive", t, j, v, w[v]));
                }
            }
        }
    }

    /**
     * cfBasisIndices must have exactly ncf entries (one per non-point CF column).
     * Each entry must be non-empty (every CF has at least 1 site).
     */
    @Test
    void cfBasisIndices_hasNcfEntries_eachNonEmpty() {
        int[][] cfBasisIndices = stage3.getCfBasisIndices();
        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();

        // cfBasisIndices has tcf rows total, one per CF column
        assertEquals(tcf, cfBasisIndices.length,
                "cfBasisIndices must have one row per CF column (tcf=" + tcf + ")");

        // All entries must have at least 1 basis index
        for (int col = 0; col < tcf; col++) {
            assertNotNull(cfBasisIndices[col],
                    "cfBasisIndices[" + col + "] must not be null");
            assertTrue(cfBasisIndices[col].length > 0,
                    "cfBasisIndices[" + col + "] must have at least 1 entry");
        }
    }

    /**
     * cfBasisIndices values must be positive (1-based basis indices, ≥ 1).
     */
    @Test
    void cfBasisIndices_valuesArePositive() {
        int[][] cfBasisIndices = stage3.getCfBasisIndices();
        int ncf = stage2.getNcf();

        for (int col = 0; col < ncf; col++) {
            for (int k = 0; k < cfBasisIndices[col].length; k++) {
                assertTrue(cfBasisIndices[col][k] >= 1,
                        String.format("cfBasisIndices[%d][%d] = %d must be >= 1 (1-based)", col, k, cfBasisIndices[col][k]));
            }
        }
    }

    // =========================================================================
    // CV positivity at the disordered state
    // =========================================================================

    /**
     * All cluster variables must be non-negative at the disordered (random) state.
     *
     * <p>At the disordered state, the C-matrix relationship
     * CV = cmat · u_full + constant must produce CV ≥ 0 for all cluster types.
     * This invariant must hold for the C-matrix to represent a physically valid
     * probability distribution.</p>
     *
     * <p>This test uses binary x=0.5 (equimolar composition).</p>
     */
    @Test
    void allCVsAtDisorderedState_binary_areNonNegative() {
        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();
        int[][] cfBasisIndices = stage3.getCfBasisIndices();
        int tcdis = stage1.getTcdis();

        double[] moleFractions = {0.5, 0.5};

        // Build the full CF vector at the disordered state
        double[] uRandom = ClusterVariableEvaluator.computeRandomCFs(
                moleFractions, 2, cfBasisIndices, ncf, tcf);
        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                uRandom, moleFractions, 2, cfBasisIndices, ncf, tcf);

        // Evaluate all cluster variables
        double[][][] cv = ClusterVariableEvaluator.evaluate(
                uFull, stage3.getCmat(), stage3.getLcv(), tcdis, stage1.getLc());

        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < stage1.getLc()[t]; j++) {
                int nv = stage3.getLcv()[t][j];
                for (int v = 0; v < nv; v++) {
                    assertTrue(cv[t][j][v] >= -1.0e-12,
                            String.format("CV[%d][%d][%d] = %.6e is negative at the disordered state (x=0.5)",
                                    t, j, v, cv[t][j][v]));
                }
            }
        }
    }

    /**
     * Same CV positivity check for off-equimolar composition (x=0.25).
     */
    @Test
    void allCVsAtDisorderedState_offEquimolar_areNonNegative() {
        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();
        int[][] cfBasisIndices = stage3.getCfBasisIndices();
        int tcdis = stage1.getTcdis();

        double[] moleFractions = {0.75, 0.25};

        double[] uRandom = ClusterVariableEvaluator.computeRandomCFs(
                moleFractions, 2, cfBasisIndices, ncf, tcf);
        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                uRandom, moleFractions, 2, cfBasisIndices, ncf, tcf);

        double[][][] cv = ClusterVariableEvaluator.evaluate(
                uFull, stage3.getCmat(), stage3.getLcv(), tcdis, stage1.getLc());

        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < stage1.getLc()[t]; j++) {
                int nv = stage3.getLcv()[t][j];
                for (int v = 0; v < nv; v++) {
                    assertTrue(cv[t][j][v] >= -1.0e-12,
                            String.format("CV[%d][%d][%d] = %.6e is negative at the disordered state (x=0.25)",
                                    t, j, v, cv[t][j][v]));
                }
            }
        }
    }

    /**
     * For each cluster type and group, the probability-weighted sum of CVs must equal 1.
     *
     * <p>The normalization condition is: Σ_v wcv[t][j][v] · CV[t][j][v] = 1.
     * Here wcv[v] is the orbit multiplicity (number of symmetry-equivalent configurations)
     * and CV[v] is the probability per configuration.
     *
     * <p>Example for binary pair cluster at x=0.5:
     * <pre>
     *   w_AA=1, CV_AA=0.25; w_AB=2, CV_AB=0.25; w_BB=1, CV_BB=0.25
     *   Sum = 1·0.25 + 2·0.25 + 1·0.25 = 1.0 ✓
     * </pre>
     */
    @Test
    void cvWeightedSum_equalsOne_atDisorderedState() {
        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();
        int[][] cfBasisIndices = stage3.getCfBasisIndices();
        int tcdis = stage1.getTcdis();
        List<List<int[]>> wcv = stage3.getWcv();
        int[][] lcv = stage3.getLcv();

        double[] moleFractions = {0.5, 0.5};

        double[] uRandom = ClusterVariableEvaluator.computeRandomCFs(
                moleFractions, 2, cfBasisIndices, ncf, tcf);
        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                uRandom, moleFractions, 2, cfBasisIndices, ncf, tcf);

        double[][][] cv = ClusterVariableEvaluator.evaluate(
                uFull, stage3.getCmat(), lcv, tcdis, stage1.getLc());

        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < stage1.getLc()[t]; j++) {
                int nv = lcv[t][j];
                int[] w = wcv.get(t).get(j);

                // Σ_v wcv[v] · CV[v] must equal 1 (probability normalization)
                double weightedSum = 0.0;
                for (int v = 0; v < nv; v++) {
                    weightedSum += w[v] * cv[t][j][v];
                }

                assertEquals(1.0, weightedSum, 1.0e-10,
                        String.format("Σ wcv·CV for (t=%d,j=%d) must equal 1.0, got %.10f",
                                t, j, weightedSum));
            }
        }
    }
}
