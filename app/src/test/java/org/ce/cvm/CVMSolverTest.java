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
 * Comprehensive CVM solver tests for the BCC A2-T binary system.
 *
 * <p>Verifies:</p>
 * <ol>
 *   <li>C-matrix correctness: all CVs positive at random state</li>
 *   <li>Entropy check: S(random, x=0.5) = ln(2) ≈ 0.6931</li>
 *   <li>Newton-Raphson solver: model A-B system at T=6.2</li>
 * </ol>
 */
@DisplayName("CVM Solver — BCC A2-T binary")
public class CVMSolverTest {

    // Shared pipeline results (built once)
    private static ClusterIdentificationResult stage1;
    private static CFIdentificationResult stage2;
    private static CMatrixResult stage3;
    private static int tcdis, tcf, ncf;
    private static double[] kb;
    private static double[][] mh;
    private static int[] lc;
    private static List<Double> mhdis;

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
                .numComponents(2)
                .build();

        CVMResult result = CVMPipeline.identify(config);
        stage1 = result.getClusterIdentification();
        stage2 = result.getCorrelationFunctionIdentification();

        List<Cluster> ordMaxClus =
                InputLoader.parseClusterFile(config.getOrderedClusterFile());
        stage3 = CMatrixBuilder.build(stage1, stage2, ordMaxClus, 2);

        tcdis = stage1.getTcdis();
        tcf   = stage2.getTcf();
        ncf   = stage2.getNcf();
        kb    = stage1.getKbCoefficients();
        mh    = stage1.getMh();
        lc    = stage1.getLc();
        mhdis = stage1.getDisClusterData().getMultiplicities();

        System.out.println("[CVMSolverTest] tcdis=" + tcdis + " tcf=" + tcf + " ncf=" + ncf);
        System.out.printf("[CVMSolverTest] kb = [");
        for (int t = 0; t < tcdis; t++) System.out.printf("%s%.0f", t > 0 ? ", " : "", kb[t]);
        System.out.println("]");
        System.out.printf("[CVMSolverTest] mhdis = [");
        for (int t = 0; t < mhdis.size(); t++) System.out.printf("%s%.1f", t > 0 ? ", " : "", mhdis.get(t));
        System.out.println("]");
        
        // Diagnostic: print lcf structure
        int[][] lcfActual = stage2.getLcf();
        System.out.println("[CVMSolverTest] lcf structure (stage2.getLcf()):");
        for (int t = 0; t < lcfActual.length; t++) {
            System.out.print("  lcf[" + t + "] = [");
            for (int j = 0; j < lcfActual[t].length; j++) {
                System.out.print((j > 0 ? ", " : "") + lcfActual[t][j]);
            }
            System.out.println("]");
        }
    }

    // =========================================================================
    // 1. C-matrix structure
    // =========================================================================

    @Test
    @DisplayName("C-matrix dimensions match expected A2-T binary")
    void cmatrixDimensions() {
        // BCC A2-T binary: tcdis=5, tcf=5, ncf=4
        assertEquals(5, tcdis, "tcdis (HSP types excl. empty)");
        assertEquals(5, tcf, "tcf (total CFs)");
        assertEquals(4, ncf, "ncf (non-point CFs)");

        // Unique CVs per cluster type: equivalent configs are merged
        // by the C-matrix builder (configs with identical polynomial rows).
        // tetra: 6 (AAAA, 4×AAAB, 4×AABB/ABAB, 2×ABBA, 4×ABBB, BBBB)
        // triangle: 6 (AAA, 2×AAB/ABA, BAA, ABB, 2×BAB/BBA, BBB)
        // pair: 3 (AA, 2×AB, BB)
        int[][] lcv = stage3.getLcv();
        assertEquals(6, lcv[0][0], "tetrahedron CVs");
        assertEquals(6, lcv[1][0], "triangle CVs");
        assertEquals(3, lcv[2][0], "pair1 CVs");
        assertEquals(3, lcv[3][0], "pair2 CVs");
        assertEquals(2, lcv[4][0], "point CVs");
    }

    // =========================================================================
    // 2. CV positivity at randomstate
    // =========================================================================

    @Test
    @DisplayName("All CVs are positive at random state (x=0.5, all CFs=0)")
    void allCVsPositiveAtRandomState() {
        // Random state: all non-point CFs = 0, composition = 0.5
        double[] uZero = new double[ncf]; // all zeros
        double composition = 0.5;

        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(uZero, composition, stage3.getCfBasisIndices(), ncf, tcf);
        double[][][] cv = ClusterVariableEvaluator.evaluate(
                uFull, stage3.getCmat(), stage3.getLcv(), tcdis, lc);

        int[][] lcv = stage3.getLcv();
        String[] typeNames = {"tetra", "triangle", "pair1", "pair2", "point"};

        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                for (int v = 0; v < lcv[t][j]; v++) {
                    double cvVal = cv[t][j][v];
                    assertTrue(cvVal > 0,
                            String.format("CV[%s][j=%d][v=%d] = %.12f must be > 0",
                                    typeNames[t], j, v, cvVal));
                }
            }
        }
    }

    @Test
    @DisplayName("CVs at random state equal (1/2)^n for all cluster configs")
    void cvsEqualHalfPowerNAtRandomState() {
        double[] uZero = new double[ncf];
        double composition = 0.5;

        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(uZero, composition, stage3.getCfBasisIndices(), ncf, tcf);
        double[][][] cv = ClusterVariableEvaluator.evaluate(
                uFull, stage3.getCmat(), stage3.getLcv(), tcdis, lc);

        // Expected: all CVs for n-site cluster = (1/2)^n
        // tetra: 0.0625, tri: 0.125, pair: 0.25, point: 0.5
        int[] nSites = {4, 3, 2, 2, 1};
        String[] typeNames = {"tetra", "triangle", "pair1", "pair2", "point"};
        int[][] lcv = stage3.getLcv();

        for (int t = 0; t < tcdis; t++) {
            double expected = Math.pow(0.5, nSites[t]);
            for (int j = 0; j < lc[t]; j++) {
                for (int v = 0; v < lcv[t][j]; v++) {
                    assertEquals(expected, cv[t][j][v], 1e-10,
                            String.format("CV[%s][j=%d][v=%d]: expected (1/2)^%d = %.6f",
                                    typeNames[t], j, v, nSites[t], expected));
                }
            }
        }
    }

    @Test
    @DisplayName("CV weights sum to 2^n for each cluster type")
    void wcvSumToPowerOf2() {
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
                int expected = (int) Math.pow(2, nSites[t]);
                assertEquals(expected, sum,
                        String.format("wcv sum for type %d = %d, expected 2^%d = %d",
                                t, sum, nSites[t], expected));
            }
        }
    }

    // =========================================================================
    // 3. Entropy at random state = ln(2)
    // =========================================================================

    @Test
    @DisplayName("Entropy at random state (x=0.5, all CFs=0) equals ln(2)")
    void entropyAtRandomStateEqualsLn2() {
        // At random state: all non-point CFs = 0
        double[] uZero = new double[ncf];
        double composition = 0.5;
        double temperature = 1.0; // Arbitrary — entropy doesn't depend on T

        // Model ECI: all zero (entropy-only)
        double[] eciZero = new double[ncf];

        CVMFreeEnergy.EvalResult eval = CVMFreeEnergy.evaluate(
                uZero, composition, temperature, eciZero,
                mhdis, kb, mh, lc,
                stage3.getCmat(), stage3.getLcv(), stage3.getWcv(),
                tcdis, tcf, ncf, stage3.getCfBasisIndices());

        // S_random = -R·ln(N) = ln(2) for binary with R=1
        double expectedS = Math.log(2.0);
        System.out.printf("[Entropy check] S = %.12f, expected ln(2) = %.12f%n",
                eval.S, expectedS);

        assertEquals(expectedS, eval.S, 1e-10,
                "Entropy at random state must equal ln(2) = " + expectedS);

        // H should be 0 (all ECIs are 0)
        assertEquals(0.0, eval.H, 1e-15, "H should be 0 with zero ECIs");

        // G = H - T*S = -T*ln(2)
        assertEquals(-temperature * expectedS, eval.G, 1e-10,
                "G = -T·ln(2) at random state with zero ECIs");
    }

    @Test
    @DisplayName("Gradient at random state is zero (equilibrium) with zero ECIs")
    void gradientZeroAtRandomStateWithZeroECI() {
        double[] uZero = new double[ncf];
        double[] eciZero = new double[ncf];

        CVMFreeEnergy.EvalResult eval = CVMFreeEnergy.evaluate(
                uZero, 0.5, 1.0, eciZero,
                mhdis, kb, mh, lc,
                stage3.getCmat(), stage3.getLcv(), stage3.getWcv(),
                tcdis, tcf, ncf, stage3.getCfBasisIndices());

        double gradNorm = 0;
        for (double g : eval.Gcu) gradNorm += g * g;
        gradNorm = Math.sqrt(gradNorm);

        System.out.printf("[Gradient check] ||Gcu|| = %.6e%n", gradNorm);
        assertTrue(gradNorm < 1e-10,
                "Gradient should be near zero at random state with zero ECIs. ||Gcu|| = " + gradNorm);
    }

    // =========================================================================
    // 4. Model A-B system at T=6.2
    // =========================================================================

    @Test
    @DisplayName("NR solver converges for model A-B system at T=6.2 (NN pair only)")
    void modelABSystemNNPairT6p2() {
        // Model system: only NN pair interaction, e_pair = -1
        // ECI array: [tetra, triangle, pair1, pair2] = [0, 0, -1, 0]
        double[] eci = {0.0, 0.0, -1.0, 0.0};
        double composition = 0.5;
        double temperature = 6.2;

        CVMSolverResult result = NewtonRaphsonSolver.solve(
                composition, temperature, eci,
                mhdis, kb, mh, lc,
                stage3.getCmat(), stage3.getLcv(), stage3.getWcv(),
                tcdis, tcf, ncf, stage3.getCfBasisIndices(),
                200, 1e-10, 0.99);

        System.out.println("[Model A-B T=6.2]\n" + result.toSummary());

        assertTrue(result.isConverged(),
                "Solver should converge. Iterations=" + result.getIterations()
                        + " ||Gcu||=" + result.getGradientNorm());

        // Entropy should be positive (ordered, but above transition)
        assertTrue(result.getEntropy() > 0,
                "Entropy should be positive: S=" + result.getEntropy());

        // Gibbs energy should be negative (stabilised by mixing)
        assertTrue(result.getGibbsEnergy() < 0,
                "G should be negative: G=" + result.getGibbsEnergy());

        // Equilibrium CFs should be in valid range [-1, 1]
        for (int i = 0; i < ncf; i++) {
            double cf = result.getEquilibriumCFs()[i];
            assertTrue(cf >= -1.0 && cf <= 1.0,
                    "CF[" + i + "] = " + cf + " out of range [-1, 1]");
        }
    }

    @Test
    @DisplayName("NR solver converges at x=0.5, T=6.2 with all pair interactions")
    void modelABBothPairsT6p2() {
        // Both pair interactions
        double[] eci = {0.0, 0.0, -1.0, -0.5};
        double composition = 0.5;
        double temperature = 6.2;

        CVMSolverResult result = NewtonRaphsonSolver.solve(
                composition, temperature, eci,
                mhdis, kb, mh, lc,
                stage3.getCmat(), stage3.getLcv(), stage3.getWcv(),
                tcdis, tcf, ncf, stage3.getCfBasisIndices(),
                200, 1e-10, 0.99);

        System.out.println("[Model A-B both pairs T=6.2]\n" + result.toSummary());

        assertTrue(result.isConverged(),
                "Should converge. ||Gcu||=" + result.getGradientNorm());
    }

    @Test
    @DisplayName("Entropy monotonically approaches ln(2) as T → ∞")
    void entropyApproachesLn2AtHighT() {
        double[] eci = {0.0, 0.0, -1.0, 0.0};
        double composition = 0.5;
        double ln2 = Math.log(2.0);

        double prevDiff = Double.MAX_VALUE;
        for (double T : new double[]{10, 50, 100, 1000}) {
            CVMSolverResult result = NewtonRaphsonSolver.solve(
                    composition, T, eci,
                    mhdis, kb, mh, lc,
                    stage3.getCmat(), stage3.getLcv(), stage3.getWcv(),
                    tcdis, tcf, ncf, stage3.getCfBasisIndices(),
                    200, 1e-10, 0.99);

            assertTrue(result.isConverged(),
                    "Should converge at T=" + T);

            double diff = Math.abs(result.getEntropy() - ln2);
            System.out.printf("[High-T check] T=%.0f  S=%.10f  |S-ln2|=%.2e%n",
                    T, result.getEntropy(), diff);

            assertTrue(diff < prevDiff,
                    "S should approach ln(2) as T increases. T=" + T
                            + " diff=" + diff + " prevDiff=" + prevDiff);
            prevDiff = diff;
        }

        // At T=1000, S should be very close to ln(2)
        assertTrue(prevDiff < 1e-5,
                "At T=1000, S should be ≈ ln(2). Diff=" + prevDiff);
    }

    @Test
    @DisplayName("Entropy at various compositions with zero ECIs equals -Σ xi ln(xi)")
    void entropyAtVariousCompositions() {
        double[] eciZero = new double[ncf];
        double temperature = 1.0;

        // At the random state for composition x, the non-point CFs are NOT
        // all zero!  The orthogonal-basis CFs at random equal σ^n where
        // σ = 2·x_B − 1 (point CF) and n = number of sites in the cluster.
        // This is because different sites are uncorrelated in the random state.
        int[] nSites = {4, 3, 2, 2}; // tetra, triangle, pair1, pair2

        for (double x : new double[]{0.1, 0.2, 0.3, 0.4, 0.5}) {
            double sigma = 2.0 * x - 1.0; // point CF value
            double[] uRandom = new double[ncf];
            for (int i = 0; i < ncf; i++) {
                uRandom[i] = Math.pow(sigma, nSites[i]);
            }

            CVMFreeEnergy.EvalResult eval = CVMFreeEnergy.evaluate(
                    uRandom, x, temperature, eciZero,
                    mhdis, kb, mh, lc,
                    stage3.getCmat(), stage3.getLcv(), stage3.getWcv(),
                    tcdis, tcf, ncf, stage3.getCfBasisIndices());

            // Expected: S = -(xA·ln(xA) + xB·ln(xB))
            double xA = 1.0 - x;
            double xB = x;
            double expectedS = -(xA * Math.log(xA) + xB * Math.log(xB));

            System.out.printf("[S at x=%.1f] sigma=%.4f u=[%.4f,%.4f,%.4f,%.4f] S=%.10f, expected=%.10f, diff=%.2e%n",
                    x, sigma, uRandom[0], uRandom[1], uRandom[2], uRandom[3],
                    eval.S, expectedS, Math.abs(eval.S - expectedS));

            assertEquals(expectedS, eval.S, 1e-10,
                    "S(random, x=" + x + ") should equal ideal mixing entropy");
        }
    }

    // =========================================================================
    // 5. Consistency checks
    // =========================================================================

    @Test
    @DisplayName("Point CVs equal compositions after substitution")
    void pointCVsEqualCompositions() {
        for (double x : new double[]{0.1, 0.3, 0.5, 0.7, 0.9}) {
            double[] uZero = new double[ncf];
            double[] uFull = ClusterVariableEvaluator.buildFullCFVector(uZero, x, stage3.getCfBasisIndices(), ncf, tcf);
            double[][][] cv = ClusterVariableEvaluator.evaluate(
                    uFull, stage3.getCmat(), stage3.getLcv(), tcdis, lc);

            // Point cluster: t = tcdis-1 = 4
            int pointType = tcdis - 1;
            double cvA = cv[pointType][0][0]; // first config = element A
            double cvB = cv[pointType][0][1]; // second config = element B

            System.out.printf("[Point CV at x=%.1f] CV_A=%.10f (expect %.1f), CV_B=%.10f (expect %.1f)%n",
                    x, cvA, 1 - x, cvB, x);

            assertEquals(1 - x, cvA, 1e-10,
                    "Point CV for A should equal x_A = " + (1 - x));
            assertEquals(x, cvB, 1e-10,
                    "Point CV for B should equal x_B = " + x);
        }
    }

    @Test
    @DisplayName("KB coefficients sum yields entropy = 1 × ln(2) at random")
    void kbCoefficientsSumTest() {
        // S(random) = R·ln(2) · Σ_t kb[t] · ms[t] · n_t
        // For binary on single sublattice, this sum must equal 1
        int[] nSites = {4, 3, 2, 2, 1};
        double sum = 0;
        for (int t = 0; t < tcdis; t++) {
            sum += kb[t] * mhdis.get(t) * nSites[t];
        }
        System.out.printf("[KB sum check] Σ kb·ms·n = %.6f (expected 1.0)%n", sum);
        assertEquals(1.0, sum, 1e-10,
                "KB·mhdis·nSites sum should be 1 for single sublattice");
    }
}
