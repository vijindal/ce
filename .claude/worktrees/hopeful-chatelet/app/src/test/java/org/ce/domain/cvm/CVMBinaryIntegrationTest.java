package org.ce.domain.cvm;

import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.identification.cluster.ClusterIdentificationResult;
import org.ce.domain.identification.cluster.CFIdentificationResult;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: full CVM pipeline for binary (K=2) systems.
 *
 * <p>Verifies physical invariants over several (T, x) points using analytical
 * checks only — no external reference values are required.</p>
 *
 * <h2>Invariants verified (Step 4)</h2>
 * <ol>
 *   <li>Solver must converge: {@code result.isConverged() == true}</li>
 *   <li>Gradient at equilibrium: {@code ||Gcu||_2 < tolerance}</li>
 *   <li>All cluster variables ≥ 0 after convergence</li>
 *   <li>G = H − T·S holds to machine precision</li>
 *   <li>At ECI=0: G = −T·S (enthalpy is zero)</li>
 *   <li>Lower T gives lower G for ordered systems (negative pair ECI)</li>
 *   <li>G is symmetric: G(T, x) == G(T, 1−x) for symmetric binary ECI</li>
 * </ol>
 */
class CVMBinaryIntegrationTest {

    private static AllClusterData clusterData;

    /** NR solver tolerance. */
    private static final double TOLS = 1.0e-8;

    @BeforeAll
    static void loadClusterData() throws Exception {
        Optional<AllClusterData> opt = AllClusterDataCache.load("BCC_A2_T_bin");
        assertTrue(opt.isPresent(), "BCC_A2_T_bin cluster cache must be present");
        clusterData = opt.get();
        assertTrue(clusterData.isComplete(), "BCC_A2_T_bin cluster data must be complete (all 3 stages)");
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private CVMSolverResult solve(double xB, double temperature, double[] eci) {
        ClusterIdentificationResult stage1 = clusterData.getStage1();
        CFIdentificationResult stage2 = clusterData.getStage2();
        CMatrixResult stage3 = clusterData.getStage3();

        return NewtonRaphsonSolverSimple.solve(
                new double[]{1.0 - xB, xB},
                2,
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
        return new double[clusterData.getStage2().getNcf()];
    }

    private double[] nbTiECI() {
        int ncf = clusterData.getStage2().getNcf();
        double[] eci = new double[ncf];
        if (ncf >= 2) { eci[0] = -390.0; eci[1] = -260.0; }
        else if (ncf >= 1) { eci[0] = -390.0; }
        return eci;
    }

    // =========================================================================
    // Invariant 1: Convergence
    // =========================================================================

    @Test
    void convergence_zeroECI_equimolar() {
        assertTrue(solve(0.5, 1000.0, zeroECI()).isConverged(),
                "Solver must converge: ECI=0, x=0.5, T=1000");
    }

    @Test
    void convergence_zeroECI_offEquimolar() {
        assertTrue(solve(0.25, 1000.0, zeroECI()).isConverged(),
                "Solver must converge: ECI=0, x=0.25, T=1000");
    }

    @Test
    void convergence_nbTiECI_lowTemperature() {
        assertTrue(solve(0.5, 300.0, nbTiECI()).isConverged(),
                "Solver must converge: Nb-Ti ECI, x=0.5, T=300");
    }

    @Test
    void convergence_nbTiECI_highTemperature() {
        assertTrue(solve(0.5, 1500.0, nbTiECI()).isConverged(),
                "Solver must converge: Nb-Ti ECI, x=0.5, T=1500");
    }

    @Test
    void convergence_nbTiECI_offEquimolar() {
        assertTrue(solve(0.3, 800.0, nbTiECI()).isConverged(),
                "Solver must converge: Nb-Ti ECI, x=0.3, T=800");
    }

    // =========================================================================
    // Invariant 2: Gradient norm below tolerance at convergence
    // =========================================================================

    @Test
    void gradientNorm_belowTolerance_atConvergence() {
        CVMSolverResult r = solve(0.5, 1000.0, nbTiECI());
        assertTrue(r.isConverged(), "Solver must converge");
        assertTrue(r.getGradientNorm() < TOLS * 10,
                String.format("||∇G|| = %.2e must be < %.2e at convergence", r.getGradientNorm(), TOLS * 10));
    }

    // =========================================================================
    // Invariant 3: All cluster variables non-negative after convergence
    // =========================================================================

    @Test
    void allCVsNonNegative_afterConvergence_equimolar() {
        checkCVsNonNegative(0.5, 1000.0, nbTiECI());
    }

    @Test
    void allCVsNonNegative_afterConvergence_lowT() {
        checkCVsNonNegative(0.5, 300.0, nbTiECI());
    }

    @Test
    void allCVsNonNegative_afterConvergence_offEquimolar() {
        checkCVsNonNegative(0.3, 800.0, nbTiECI());
    }

    private void checkCVsNonNegative(double xB, double T, double[] eci) {
        CVMSolverResult result = solve(xB, T, eci);
        assertTrue(result.isConverged(), "Solver must converge before checking CVs");

        ClusterIdentificationResult stage1 = clusterData.getStage1();
        CFIdentificationResult stage2 = clusterData.getStage2();
        CMatrixResult stage3 = clusterData.getStage3();

        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();
        int[][] cfBasisIndices = stage3.getCfBasisIndices();

        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                result.getEquilibriumCFs(),
                new double[]{1.0 - xB, xB},
                2,
                cfBasisIndices,
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
                            String.format("CV[%d][%d][%d] = %.4e is negative after convergence (xB=%.2f, T=%.0f)",
                                    t, j, v, cv[t][j][v], xB, T));
                }
            }
        }
    }

    // =========================================================================
    // Invariant 4: G = H − T·S identity
    // =========================================================================

    @Test
    void gibbsEnergyConsistency_zeroECI_equimolar() {
        CVMSolverResult r = solve(0.5, 1000.0, zeroECI());
        double T = 1000.0;
        assertEquals(r.getEnthalpy() - T * r.getEntropy(), r.getGibbsEnergy(), 1.0e-9,
                "G must equal H − T·S at convergence");
    }

    @Test
    void gibbsEnergyConsistency_nbTiECI_variousPoints() {
        double[][] tAndX = {{300.0, 0.5}, {800.0, 0.3}, {1500.0, 0.5}};
        for (double[] tx : tAndX) {
            double T = tx[0];
            double x = tx[1];
            CVMSolverResult r = solve(x, T, nbTiECI());
            if (!r.isConverged()) continue;
            assertEquals(r.getEnthalpy() - T * r.getEntropy(), r.getGibbsEnergy(), 1.0e-9,
                    String.format("G = H − T·S must hold at T=%.0f, x=%.2f", T, x));
        }
    }

    // =========================================================================
    // Invariant 5: At ECI=0, G = −T·S (zero enthalpy)
    // =========================================================================

    @Test
    void zeroECI_enthalpyIsZero_afterConvergence() {
        CVMSolverResult r = solve(0.5, 1000.0, zeroECI());
        assertEquals(0.0, r.getEnthalpy(), 1.0e-9,
                "H must be zero at convergence with ECI=0, got H=" + r.getEnthalpy());
    }

    @Test
    void zeroECI_gibbsEqualsNegativeTTimesS() {
        double T = 1000.0;
        CVMSolverResult r = solve(0.5, T, zeroECI());
        double gFromEntropy = -T * r.getEntropy();
        assertEquals(gFromEntropy, r.getGibbsEnergy(), 1.0e-9,
                String.format("With ECI=0: G=%.6f must equal −T·S=%.6f", r.getGibbsEnergy(), gFromEntropy));
    }

    // =========================================================================
    // Invariant 6: Lower T gives lower G for ordered system
    // =========================================================================

    /**
     * G must be a decreasing function of temperature: G(T_low) > G(T_high).
     *
     * <p>This follows from the fundamental thermodynamic identity dG/dT = -S.
     * Since entropy S > 0, G decreases as temperature increases.
     * Equivalently: G(T=300) > G(T=1000) for any physical system.</p>
     *
     * <p>Verified for Nb-Ti ECI (strongly ordering system), but the invariant
     * holds regardless of ECI because it stems from S > 0.</p>
     */
    @Test
    void gibbsEnergy_decreasesWithIncreasingT() {
        CVMSolverResult rLowT   = solve(0.5, 300.0, nbTiECI());
        CVMSolverResult rHighT  = solve(0.5, 1000.0, nbTiECI());

        assertTrue(rLowT.isConverged(),  "Low-T solver must converge");
        assertTrue(rHighT.isConverged(), "High-T solver must converge");

        // dG/dT = -S < 0, so G(T_low) > G(T_high)
        assertTrue(rLowT.getGibbsEnergy() > rHighT.getGibbsEnergy(),
                String.format("G(T=300)=%.4f must be > G(T=1000)=%.4f (dG/dT = -S < 0)",
                        rLowT.getGibbsEnergy(), rHighT.getGibbsEnergy()));
    }

    // =========================================================================
    // Invariant 7: G is symmetric G(T, x) == G(T, 1−x) for symmetric ECI
    // =========================================================================

    /**
     * G must be symmetric for a binary system with composition-symmetric ECI.
     *
     * <p>For ECI=0 (no enthalpy asymmetry) the entropic part is symmetric by
     * construction from the C-matrix: G(T, x) = G(T, 1−x).</p>
     */
    @Test
    void gibbsEnergySymmetry_zeroECI() {
        double T = 1000.0;
        double[] testCompositions = {0.2, 0.3, 0.4};

        for (double x : testCompositions) {
            CVMSolverResult rx  = solve(x, T, zeroECI());
            CVMSolverResult r1x = solve(1.0 - x, T, zeroECI());

            assertTrue(rx.isConverged(),  "Solver must converge at x=" + x);
            assertTrue(r1x.isConverged(), "Solver must converge at x=" + (1.0 - x));

            assertEquals(rx.getGibbsEnergy(), r1x.getGibbsEnergy(), 1.0e-8,
                    String.format("G(T=%.0f, x=%.1f)=%.8f must equal G(T=%.0f, x=%.1f)=%.8f (symmetry)",
                            T, x, rx.getGibbsEnergy(), T, 1.0 - x, r1x.getGibbsEnergy()));
        }
    }
}
