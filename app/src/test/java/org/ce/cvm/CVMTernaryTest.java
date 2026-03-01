package org.ce.cvm;

import org.ce.core.CVMConfiguration;
import org.ce.core.CVMPipeline;
import org.ce.core.CVMResult;
import org.ce.identification.cf.CFIdentificationResult;
import org.ce.identification.cluster.ClusterIdentificationResult;
import org.ce.identification.geometry.Cluster;
import org.ce.identification.geometry.Vector3D;
import org.ce.input.InputLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CVM solver tests for the ternary (K=3) A2-T system.
 *
 * <p>Validates multi-component generalisation of the CVM solver pipeline.
 * Uses the same BCC A2 tetrahedron cluster data with {@code numComponents=3}.</p>
 */
@DisplayName("CVM Solver — A2-T ternary (K=3)")
public class CVMTernaryTest {

    // Shared pipeline results (built once)
    private static ClusterIdentificationResult stage1;
    private static CFIdentificationResult stage2;
    private static CMatrixResult stage3;
    private static int tcdis, tcf, ncf;
    private static double[] kb;
    private static double[][] mh;
    private static int[] lc;
    private static List<Double> mhdis;
    private static int[][] lcf;

    @BeforeAll
    static void buildPipeline() {
        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("A2-SG")
                .transformationMatrix(new double[][] {
                        {1, 0, 0}, {0, 1, 0}, {0, 0, 1}
                })
                .translationVector(new Vector3D(0, 0, 0))
                .numComponents(3)
                .build();

        CVMResult result = CVMPipeline.identify(config);
        stage1 = result.getClusterIdentification();
        stage2 = result.getCorrelationFunctionIdentification();

        List<Cluster> ordMaxClus =
                InputLoader.parseClusterFile(config.getOrderedClusterFile());
        stage3 = CMatrixBuilder.build(stage1, stage2, ordMaxClus, 3);

        tcdis = stage1.getTcdis();
        tcf   = stage2.getTcf();
        ncf   = stage2.getNcf();
        lcf   = stage2.getLcf();
        kb    = stage1.getKbCoefficients();
        mh    = stage1.getMh();
        lc    = stage1.getLc();
        mhdis = stage1.getDisClusterData().getMultiplicities();

        System.out.println("[CVMTernaryTest] tcdis=" + tcdis
                + " tcf=" + tcf + " ncf=" + ncf);
        System.out.printf("[CVMTernaryTest] kb = [");
        for (int t = 0; t < tcdis; t++) System.out.printf("%s%.0f", t > 0 ? ", " : "", kb[t]);
        System.out.println("]");
        System.out.printf("[CVMTernaryTest] mhdis = [");
        for (int i = 0; i < mhdis.size(); i++) System.out.printf("%s%.1f", i > 0 ? ", " : "", mhdis.get(i));
        System.out.println("]");
        
        // Diagnostic: print lcf structure
        System.out.println("[CVMTernaryTest] lcf structure (stage2.getLcf()):");
        for (int t = 0; t < lcf.length; t++) {
            System.out.print("  lcf[" + t + "] = [");
            for (int j = 0; j < lcf[t].length; j++) {
                System.out.print((j > 0 ? ", " : "") + lcf[t][j]);
            }
            System.out.println("]");
        }
    }

    // =========================================================================
    // 1. Pipeline dimensions for ternary
    // =========================================================================

    @Test
    @DisplayName("Ternary tcf > binary tcf (more CFs for K=3)")
    void ternaryHasMoreCFsThanBinary() {
        // Binary A2-T: tcf=5, ncf=4
        // Ternary: tcf > 5, ncf = tcf - 2 (2 point CFs for K=3)
        assertTrue(tcf > 5,
                "Ternary tcf=" + tcf + " should be > 5 (binary)");
        assertEquals(tcf - 2, ncf,
                "ncf should be tcf - (K-1) = tcf - 2 for ternary");
    }

    @Test
    @DisplayName("C-matrix built successfully for ternary")
    void cmatrixBuiltSuccessfully() {
        assertNotNull(stage3, "C-matrix result should not be null");
        assertNotNull(stage3.getCmat(), "cmat should not be null");
        assertEquals(tcdis, stage3.getCmat().size(), "cmat outer size = tcdis");
    }

    // =========================================================================
    // 2. CV positivity at equimolar random state
    // =========================================================================

    @Test
    @DisplayName("All CVs positive at equimolar random state via high-T solver")
    void allCVsPositiveAtEquimolarRandom() {
        // At random state for ternary, the non-point CFs are NOT all zero.
        // Use the NR solver at very high T with zero ECIs to find the
        // random-state CFs, then verify all CVs are positive.
        double[] moleFractions = {1.0 / 3, 1.0 / 3, 1.0 / 3};
        double[] eciZero = new double[ncf];

        CVMSolverResult result = NewtonRaphsonSolver.solve(
                moleFractions, 3, 100000.0, eciZero,
                mhdis, kb, mh, lc,
                stage3.getCmat(), stage3.getLcv(), stage3.getWcv(),
                tcdis, tcf, ncf, lcf, stage3.getCfBasisIndices(),
                200, 1e-10, 0.99);

        assertTrue(result.isConverged(), "Should converge at very high T");

        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                result.getEquilibriumCFs(), moleFractions, 3, stage3.getCfBasisIndices(), ncf, tcf);
        double[][][] cv = ClusterVariableEvaluator.evaluate(
                uFull, stage3.getCmat(), stage3.getLcv(), tcdis, lc);

        int[][] lcvArr = stage3.getLcv();
        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                for (int v = 0; v < lcvArr[t][j]; v++) {
                    assertTrue(cv[t][j][v] > -1e-15,
                            String.format("CV[t=%d][j=%d][v=%d] = %.12f must be ≥ 0",
                                    t, j, v, cv[t][j][v]));
                }
            }
        }
    }

    @Test
    @DisplayName("WCV weights sum to K^n for ternary")
    void wcvSumToKPowerN() {
        // Cluster site counts for A2-T: tetrahedral, triangular, pair1, pair2, point
        int[] nSites = {4, 3, 2, 2, 1};
        List<List<int[]>> wcv = stage3.getWcv();
        int[][] lcv = stage3.getLcv();

        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                int sum = 0;
                int[] w = wcv.get(t).get(j);
                for (int v = 0; v < lcv[t][j]; v++) {
                    sum += w[v];
                }
                int expected = (int) Math.pow(3, nSites[t]);
                assertEquals(expected, sum,
                        String.format("wcv sum for type %d, group %d = %d, expected 3^%d = %d",
                                t, j, sum, nSites[t], expected));
            }
        }
    }

    // =========================================================================
    // 3. Entropy at equimolar random state = ln(3)
    // =========================================================================

    @Test
    @DisplayName("Entropy at equimolar random state equals ln(3)")
    void entropyAtEquimolarEqualsLn3() {
        // At random state for equimolar ternary: all non-point CFs should be
        // at their random values.  For equimolar (x_i = 1/3), the point CFs
        // from ⟨s^k⟩ = Σ x_i · t_i^k with basis {-1, 0, 1} are:
        //   σ₁ = (1/3)(-1) + (1/3)(0) + (1/3)(1) = 0
        //   σ₂ = (1/3)(1)  + (1/3)(0) + (1/3)(1) = 2/3
        // The non-point CFs at random = products of point CFs raised to
        // appropriate cluster powers.  For simplicity, test with zero non-point
        // CFs first — the entropy formula should still give a value close to ln(3)
        // if the point CFs are correctly set.

        // Actually, at the random state the non-point CFs are NOT zero for
        // non-equimolar compositions. For equimolar ternary with basis {-1,0,1}:
        //   σ₁ = 0, σ₂ = 2/3
        // So multi-site CFs at random = Π σ^power values.
        // We need to compute these correctly.

        // Let's use the NR solver approach: set up zero ECIs and verify entropy
        // We need the random-state CFs for ternary.

        double[] moleFractions = {1.0 / 3, 1.0 / 3, 1.0 / 3};
        double[] eciZero = new double[ncf];
        double temperature = 1.0;

        // First find the random-state non-point CFs.
        // At random state for K=3 equimolar, σ₁=0, σ₂=2/3
        // The non-point CFs are products of these for their respective cluster.
        // For now, try all zeros and see if entropy is close.
        // The proper approach is to compute via NR at very high T.

        // Use high T to get essentially random state
        CVMSolverResult highTResult = NewtonRaphsonSolver.solve(
                moleFractions, 3, 10000.0, eciZero,
                mhdis, kb, mh, lc,
                stage3.getCmat(), stage3.getLcv(), stage3.getWcv(),
                tcdis, tcf, ncf, lcf, stage3.getCfBasisIndices(),
                200, 1e-10, 0.99);

        assertTrue(highTResult.isConverged(),
                "Solver should converge at T=10000 with zero ECIs");

        double expectedS = Math.log(3.0);
        System.out.printf("[Ternary entropy] S = %.12f, expected ln(3) = %.12f, diff = %.2e%n",
                highTResult.getEntropy(), expectedS,
                Math.abs(highTResult.getEntropy() - expectedS));

        assertEquals(expectedS, highTResult.getEntropy(), 1e-4,
                "Entropy at high T with zero ECIs should approach ln(3)");
    }

    // =========================================================================
    // 4. NR solver convergence
    // =========================================================================

    @Test
    @DisplayName("NR solver converges for equimolar ternary at T=6.2")
    void nrConvergesEquimolarT6p2() {
        // Model ECIs: only NN pair interaction
        double[] eci = new double[ncf];
        // Identify pair-1 CF index in the ternary CF list.
        // The pair types are at index 2 (pair1) and 3 (pair2) in the cluster
        // type list, same as binary.  But there are more CFs per type in ternary.
        // For a safe test, use weak interaction on first CF only.
        eci[0] = -1.0;

        double[] moleFractions = {1.0 / 3, 1.0 / 3, 1.0 / 3};
        double temperature = 6.2;

        CVMSolverResult result = NewtonRaphsonSolver.solve(
                moleFractions, 3, temperature, eci,
                mhdis, kb, mh, lc,
                stage3.getCmat(), stage3.getLcv(), stage3.getWcv(),
                tcdis, tcf, ncf, lcf, stage3.getCfBasisIndices(),
                200, 1e-10, 0.99);

        System.out.println("[Ternary NR T=6.2]\n" + result.toSummary());

        assertTrue(result.isConverged(),
                "Solver should converge. ||Gcu||=" + result.getGradientNorm());

        assertTrue(result.getEntropy() > 0,
                "Entropy should be positive: S=" + result.getEntropy());
    }

    @Test
    @DisplayName("NR solver converges for non-equimolar ternary")
    void nrConvergesNonEquimolar() {
        double[] eci = new double[ncf];
        eci[0] = -0.5;

        double[] moleFractions = {0.5, 0.3, 0.2};
        double temperature = 10.0;

        CVMSolverResult result = NewtonRaphsonSolver.solve(
                moleFractions, 3, temperature, eci,
                mhdis, kb, mh, lc,
                stage3.getCmat(), stage3.getLcv(), stage3.getWcv(),
                tcdis, tcf, ncf, lcf, stage3.getCfBasisIndices(),
                200, 1e-10, 0.99);

        System.out.println("[Ternary NR non-equimolar]\n" + result.toSummary());

        assertTrue(result.isConverged(),
                "Solver should converge for non-equimolar. ||Gcu||="
                        + result.getGradientNorm());

        assertTrue(result.getEntropy() > 0,
                "Entropy should be positive: S=" + result.getEntropy());

        assertTrue(result.getGibbsEnergy() < 0,
                "G should be negative (stabilised by mixing)");
    }

    @Test
    @DisplayName("Ternary entropy approaches ln(3) as T → ∞")
    void entropyApproachesLn3AtHighT() {
        double[] eci = new double[ncf];
        eci[0] = -1.0;

        double[] moleFractions = {1.0 / 3, 1.0 / 3, 1.0 / 3};
        double ln3 = Math.log(3.0);

        double prevDiff = Double.MAX_VALUE;
        for (double T : new double[]{10, 50, 100, 1000}) {
            CVMSolverResult result = NewtonRaphsonSolver.solve(
                    moleFractions, 3, T, eci,
                    mhdis, kb, mh, lc,
                    stage3.getCmat(), stage3.getLcv(), stage3.getWcv(),
                    tcdis, tcf, ncf, lcf, stage3.getCfBasisIndices(),
                    200, 1e-10, 0.99);

            assertTrue(result.isConverged(),
                    "Should converge at T=" + T);

            double diff = Math.abs(result.getEntropy() - ln3);
            System.out.printf("[Ternary High-T] T=%.0f  S=%.10f  |S-ln3|=%.2e%n",
                    T, result.getEntropy(), diff);

            assertTrue(diff < prevDiff,
                    "S should approach ln(3) as T increases. T=" + T
                            + " diff=" + diff + " prevDiff=" + prevDiff);
            prevDiff = diff;
        }

        assertTrue(prevDiff < 1e-3,
                "At T=1000, S should be ≈ ln(3). Diff=" + prevDiff);
    }

    // =========================================================================
    // 5. Point CFs for ternary
    // =========================================================================

    @Test
    @DisplayName("Point CFs correctly computed for ternary equimolar")
    void pointCFsCorrectForEquimolar() {
        double[] uZero = new double[ncf];
        double[] moleFractions = {1.0 / 3, 1.0 / 3, 1.0 / 3};

        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                uZero, moleFractions, 3, stage3.getCfBasisIndices(), ncf, tcf);

        // For K=3, basis = {-1, 0, 1}
        // σ₁ = (1/3)(-1) + (1/3)(0) + (1/3)(1) = 0
        // σ₂ = (1/3)(-1)² + (1/3)(0)² + (1/3)(1)² = 2/3
        int nxcf = tcf - ncf;
        assertEquals(2, nxcf, "Ternary should have 2 point CFs");

        // Point CF column ordering follows cfBasisIndices (not necessarily ascending power)
        int[][] cfbi = stage3.getCfBasisIndices();
        double[] expected = {0.0, 2.0 / 3.0}; // σ₁=0, σ₂=2/3
        for (int k = 0; k < nxcf; k++) {
            int col = ncf + k;
            int power = cfbi[col][0]; // 1-based
            double actual = uFull[col];
            System.out.printf("[Ternary point CF] col=%d basisIndex=%d σ_%d = %.10f (expect %.10f)%n",
                    col, power, power, actual, expected[power - 1]);
            assertEquals(expected[power - 1], actual, 1e-12,
                    "σ_" + power + " at col " + col);
        }
    }

    @Test
    @DisplayName("Point CFs correctly computed for non-equimolar ternary")
    void pointCFsCorrectForNonEquimolar() {
        double[] uZero = new double[ncf];
        double[] moleFractions = {0.5, 0.3, 0.2};

        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                uZero, moleFractions, 3, stage3.getCfBasisIndices(), ncf, tcf);

        // Basis for K=3: {-1, 0, 1}
        // σ₁ = 0.5×(-1) + 0.3×(0) + 0.2×(1) = -0.3
        // σ₂ = 0.5×(-1)² + 0.3×(0)² + 0.2×(1)² = 0.7
        double[] expectedSigma = {-0.3, 0.7}; // σ₁, σ₂

        int[][] cfbi = stage3.getCfBasisIndices();
        int nxcf = tcf - ncf;
        for (int k = 0; k < nxcf; k++) {
            int col = ncf + k;
            int power = cfbi[col][0]; // 1-based
            double actual = uFull[col];
            System.out.printf("[Ternary point CF] col=%d basisIndex=%d σ_%d = %.10f (expect %.4f)%n",
                    col, power, power, actual, expectedSigma[power - 1]);
            assertEquals(expectedSigma[power - 1], actual, 1e-12,
                    "σ_" + power + " for {0.5, 0.3, 0.2}");
        }
    }

    // =========================================================================
    // 6. KB coefficients identity for ternary
    // =========================================================================

    @Test
    @DisplayName("KB coefficients sum yields entropy = 1 × ln(K) at equimolar random")
    void kbCoefficientsSumTest() {
        // S(random equimolar) = R·ln(K) · Σ_t kb[t] · ms[t] · n_t
        // For single sublattice this sum must equal 1
        int[] nSites = {4, 3, 2, 2, 1};
        double sum = 0;
        for (int t = 0; t < tcdis; t++) {
            sum += kb[t] * mhdis.get(t) * nSites[t];
        }
        System.out.printf("[Ternary KB sum] Σ kb·ms·n = %.6f (expected 1.0)%n", sum);
        assertEquals(1.0, sum, 1e-10,
                "KB·mhdis·nSites sum should be 1 for single sublattice");
    }
}
