package org.ce.domain.cvm;

import org.ce.application.usecase.CVMConfiguration;
import org.ce.application.usecase.CVMPipeline;
import org.ce.domain.identification.cluster.CFIdentificationResult;
import org.ce.domain.identification.cluster.ClusterIdentificationResult;
import org.ce.domain.identification.geometry.Vector3D;
import org.ce.domain.model.data.AllClusterData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: full CVM pipeline for ternary (K=3) systems.
 *
 * <p>This test confirms that the RC-2 (initial guess) and RC-3 (step limiter) fixes
 * in {@link NewtonRaphsonSolverSimple} work correctly for multi-component (K≥3) systems.</p>
 *
 * <p>Cluster topology is computed on-the-fly via {@link CVMPipeline#identify} with
 * {@code numComponents=3}, using the BCC A2/B2 tetrahedron model
 * (same files as the binary test, 3 components instead of 2).
 * No pre-built ternary cache file is required.</p>
 *
 * <h2>Invariants verified (Step 6)</h2>
 * <ol>
 *   <li>Solver converges for ternary equimolar {1/3, 1/3, 1/3} with ECI=0</li>
 *   <li>Solver converges for ternary off-equimolar {0.5, 0.3, 0.2} with ECI=0</li>
 *   <li>All cluster variables ≥ 0 after convergence</li>
 *   <li>G = H − T·S holds at convergence</li>
 *   <li>H = 0 at convergence when ECI=0</li>
 *   <li>G &lt; 0 at T=1000K with ECI=0 (G = −T·S, S &gt; 0)</li>
 *   <li>Entropy magnitude is in physical J/(mol·K) range (confirms R=R_GAS)</li>
 *   <li>Initial guess for equimolar ternary: computeRandomCFs gives all zeros</li>
 * </ol>
 */
class CVMTernaryIntegrationTest {

    private static AllClusterData ternaryData;

    /** NR solver tolerance. */
    private static final double TOLS = 1.0e-8;

    @BeforeAll
    static void buildTernaryClusterData() {
        // Build ternary cluster topology using the same BCC A2/B2 model files
        // as the binary test, but with numComponents=3
        CVMConfiguration config = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/B2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("B2-SG")
                .transformationMatrix(new double[][]{{1, 0, 0}, {0, 1, 0}, {0, 0, 1}})
                .translationVector(new Vector3D(0, 0, 0))
                .numComponents(3)
                .build();

        ternaryData = CVMPipeline.identify(config);
        assertNotNull(ternaryData, "CVMPipeline must produce non-null AllClusterData for ternary");
        assertTrue(ternaryData.isComplete(),
                "Ternary AllClusterData must be complete (all 3 stages)");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CVMSolverResult solve(double[] moleFractions, double temperature, double[] eci) {
        ClusterIdentificationResult stage1 = ternaryData.getStage1();
        CFIdentificationResult stage2 = ternaryData.getStage2();
        CMatrixResult stage3 = ternaryData.getStage3();

        return NewtonRaphsonSolverSimple.solve(
                moleFractions,
                3,
                temperature,
                eci,
                stage1.getDisClusterData().getMultiplicities(),
                stage1.getKbCoefficients(),
                stage1.getMh(),
                stage1.getLc(),
                stage3.getCmat(),
                stage3.getLcv(),
                stage3.getWcv(),
                stage1.getTcdis(),
                stage2.getTcf(),
                stage2.getNcf(),
                stage2.getLcf(),
                stage3.getCfBasisIndices(),
                TOLS);
    }

    private double[] zeroECI() {
        return new double[ternaryData.getStage2().getNcf()];
    }

    // =========================================================================
    // Invariant 1 & 2: Convergence
    // =========================================================================

    /**
     * Solver must converge for ternary equimolar composition with ECI=0.
     *
     * <p>This is the key test for RC-2: the initial guess computed by the fixed
     * {@code getURand()} must be at (or very near) the minimum for ECI=0 at
     * equimolar composition.</p>
     */
    @Test
    void ternary_equimolar_ECI0_converges() {
        double[] x = {1.0 / 3, 1.0 / 3, 1.0 / 3};
        CVMSolverResult result = solve(x, 1000.0, zeroECI());
        assertTrue(result.isConverged(),
                "Solver must converge for ternary equimolar {1/3,1/3,1/3} with ECI=0; "
                        + "iterations=" + result.getIterations());
    }

    /**
     * Solver must converge for ternary off-equimolar composition with ECI=0.
     */
    @Test
    void ternary_offEquimolar_ECI0_converges() {
        double[] x = {0.5, 0.3, 0.2};
        CVMSolverResult result = solve(x, 1000.0, zeroECI());
        assertTrue(result.isConverged(),
                "Solver must converge for ternary {0.5,0.3,0.2} with ECI=0; "
                        + "iterations=" + result.getIterations());
    }

    // =========================================================================
    // Invariant 3: All CVs non-negative after convergence
    // =========================================================================

    @Test
    void ternary_equimolar_allCVsNonNegative() {
        double[] x = {1.0 / 3, 1.0 / 3, 1.0 / 3};
        checkCVsNonNegative(x, 1000.0, zeroECI());
    }

    @Test
    void ternary_offEquimolar_allCVsNonNegative() {
        double[] x = {0.5, 0.3, 0.2};
        checkCVsNonNegative(x, 1000.0, zeroECI());
    }

    private void checkCVsNonNegative(double[] moleFractions, double T, double[] eci) {
        CVMSolverResult result = solve(moleFractions, T, eci);
        assertTrue(result.isConverged(), "Solver must converge before checking CVs");

        ClusterIdentificationResult stage1 = ternaryData.getStage1();
        CFIdentificationResult stage2 = ternaryData.getStage2();
        CMatrixResult stage3 = ternaryData.getStage3();

        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();

        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                result.getEquilibriumCFs(),
                moleFractions,
                3,
                stage3.getCfBasisIndices(),
                ncf,
                tcf);

        double[][][] cv = ClusterVariableEvaluator.evaluate(
                uFull,
                stage3.getCmat(),
                stage3.getLcv(),
                stage1.getTcdis(),
                stage1.getLc());

        for (int t = 0; t < stage1.getTcdis(); t++) {
            for (int j = 0; j < stage1.getLc()[t]; j++) {
                for (int v = 0; v < stage3.getLcv()[t][j]; v++) {
                    assertTrue(cv[t][j][v] >= -1.0e-10,
                            String.format("CV[%d][%d][%d] = %.4e is negative after ternary convergence",
                                    t, j, v, cv[t][j][v]));
                }
            }
        }
    }

    // =========================================================================
    // Invariant 4: G = H − T·S
    // =========================================================================

    @Test
    void ternary_gibbsEnergyConsistency_equimolar() {
        double[] x = {1.0 / 3, 1.0 / 3, 1.0 / 3};
        double T = 1000.0;
        CVMSolverResult r = solve(x, T, zeroECI());
        assertTrue(r.isConverged(), "Solver must converge");
        assertEquals(r.getEnthalpy() - T * r.getEntropy(), r.getGibbsEnergy(), 1.0e-9,
                "G must equal H − T·S at ternary convergence");
    }

    // =========================================================================
    // Invariant 5: H = 0 with ECI=0
    // =========================================================================

    @Test
    void ternary_enthalpyIsZero_withZeroECI() {
        double[] x = {1.0 / 3, 1.0 / 3, 1.0 / 3};
        CVMSolverResult r = solve(x, 1000.0, zeroECI());
        assertTrue(r.isConverged(), "Solver must converge");
        assertEquals(0.0, r.getEnthalpy(), 1.0e-9,
                "H must be zero at convergence when ECI=0, got H=" + r.getEnthalpy());
    }

    // =========================================================================
    // Invariant 6: G < 0 at T=1000K, ECI=0
    // =========================================================================

    @Test
    void ternary_gibbsEnergyIsNegative_ECI0() {
        double[] x = {1.0 / 3, 1.0 / 3, 1.0 / 3};
        CVMSolverResult r = solve(x, 1000.0, zeroECI());
        assertTrue(r.isConverged(), "Solver must converge");
        assertTrue(r.getGibbsEnergy() < 0.0,
                "G = −T·S must be negative at T=1000K with ECI=0 (ternary), got G=" + r.getGibbsEnergy());
    }

    // =========================================================================
    // Invariant 7: Entropy in physical J/(mol·K) range
    // =========================================================================

    /**
     * Entropy at ternary equimolar must be in physical J/(mol·K) range.
     *
     * <p>For ideal ternary mixing: S_ideal = R·ln(3) ≈ 9.13 J/(mol·K).
     * The CVM entropy will be different but should be positive and substantial.</p>
     */
    @Test
    void ternary_entropyMagnitudeInPhysicalRange() {
        double[] x = {1.0 / 3, 1.0 / 3, 1.0 / 3};
        CVMSolverResult r = solve(x, 1000.0, zeroECI());
        assertTrue(r.isConverged(), "Solver must converge");

        // Physical lower bound: at least 1 J/(mol·K) for ternary
        double physicalLowerBound = 1.0;
        // Upper bound: generous multiple of R·ln(3)
        double physicalUpperBound = CVMFreeEnergy.R_GAS * Math.log(3) * 3;

        assertTrue(r.getEntropy() > physicalLowerBound,
                String.format("Ternary entropy S=%.4f J/(mol·K) is below physical lower bound %.1f J/(mol·K). "
                        + "Check R_GAS constant in CVMFreeEnergy.", r.getEntropy(), physicalLowerBound));
        assertTrue(r.getEntropy() < physicalUpperBound,
                String.format("Ternary entropy S=%.4f J/(mol·K) exceeds physical upper bound %.4f J/(mol·K)",
                        r.getEntropy(), physicalUpperBound));
    }

    // =========================================================================
    // Invariant 8: Initial guess for equimolar ternary (Step 5 extension)
    // =========================================================================

    /**
     * For ternary equimolar {1/3, 1/3, 1/3}, verifies that computeRandomCFs gives
     * physically consistent initial CF values that lead to non-negative cluster variables.
     *
     * <p>Unlike binary equimolar (where all CFs are zero), the ternary equimolar state
     * has non-zero second-basis CFs. For basis {-1, 0, 1}:
     * <ul>
     *   <li>σ¹ = Σ x_i · t_i¹ = (1/3)(-1) + (1/3)(0) + (1/3)(1) = 0 (by symmetry)</li>
     *   <li>σ² = Σ x_i · t_i² = (1/3)(1) + (1/3)(0) + (1/3)(1) = 2/3 ≠ 0</li>
     * </ul>
     * CFs decorated with σ² will therefore be non-zero at equimolar ternary.
     * This is the correct disordered-state value that keeps all CVs non-negative.</p>
     */
    @Test
    void initialGuess_ternary_equimolar_allCVsNonNegative() {
        CFIdentificationResult stage2 = ternaryData.getStage2();
        CMatrixResult stage3 = ternaryData.getStage3();
        ClusterIdentificationResult stage1 = ternaryData.getStage1();

        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();

        double[] uRandom = ClusterVariableEvaluator.computeRandomCFs(
                new double[]{1.0 / 3, 1.0 / 3, 1.0 / 3},
                3,
                stage3.getCfBasisIndices(),
                ncf,
                tcf);

        assertEquals(ncf, uRandom.length, "computeRandomCFs must return ncf values");

        // Build full CF vector and evaluate CVs at the initial guess
        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                uRandom, new double[]{1.0 / 3, 1.0 / 3, 1.0 / 3}, 3,
                stage3.getCfBasisIndices(), ncf, tcf);

        double[][][] cv = ClusterVariableEvaluator.evaluate(
                uFull, stage3.getCmat(), stage3.getLcv(), stage1.getTcdis(), stage1.getLc());

        for (int t = 0; t < stage1.getTcdis(); t++) {
            for (int j = 0; j < stage1.getLc()[t]; j++) {
                for (int v = 0; v < stage3.getLcv()[t][j]; v++) {
                    assertTrue(cv[t][j][v] >= -1.0e-12,
                            String.format("CV[%d][%d][%d] = %.4e must be >= 0 at ternary equimolar initial guess",
                                    t, j, v, cv[t][j][v]));
                }
            }
        }
    }

    /**
     * G decreases with increasing T for ternary system (dG/dT = -S < 0).
     */
    @Test
    void ternary_gibbsEnergy_decreasesWithIncreasingT() {
        double[] x = {1.0 / 3, 1.0 / 3, 1.0 / 3};

        CVMSolverResult rLowT  = solve(x, 300.0,  zeroECI());
        CVMSolverResult rHighT = solve(x, 1000.0, zeroECI());

        assertTrue(rLowT.isConverged(),  "Low-T solver must converge");
        assertTrue(rHighT.isConverged(), "High-T solver must converge");

        // dG/dT = -S < 0, so G(T=300) > G(T=1000)
        assertTrue(rLowT.getGibbsEnergy() > rHighT.getGibbsEnergy(),
                String.format("G(T=300)=%.4f must be > G(T=1000)=%.4f (dG/dT = -S < 0)",
                        rLowT.getGibbsEnergy(), rHighT.getGibbsEnergy()));
    }
}
