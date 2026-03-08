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
 * Tests for NewtonRaphsonSolverSimple — binary convergence (Step 3)
 * and initial-guess correctness (Step 5).
 *
 * <p>All tests use analytical invariants only — no external reference values.</p>
 *
 * <h2>Step 3 — NR convergence for binary (K=2)</h2>
 * <ul>
 *   <li>ECI=0, x=0.5: solver must converge; G = H − T·S; ||Gcu|| &lt; tolerance</li>
 *   <li>Nb-Ti ECI, x=0.5: solver must converge; G &lt; G_disordered (minimization works)</li>
 * </ul>
 *
 * <h2>Step 5 — Initial-guess correctness</h2>
 * <ul>
 *   <li>Binary equimolar: {@code computeRandomCFs} gives all-zeros (σ=0, u=0^rank=0)</li>
 *   <li>Binary equimolar ECI=0: solver converges in very few iterations because
 *       the disordered state IS the minimum for the symmetric case</li>
 * </ul>
 */
class NewtonRaphsonTest {

    private static AllClusterData clusterData;

    /** NR tolerance used in all tests. */
    private static final double TOLS = 1.0e-8;

    @BeforeAll
    static void loadClusterData() throws Exception {
        Optional<AllClusterData> opt = AllClusterDataCache.load("BCC_A2_T_bin");
        assertTrue(opt.isPresent(), "BCC_A2_T_bin cluster cache must be present");
        clusterData = opt.get();
        assertTrue(clusterData.isComplete(), "BCC_A2_T_bin cluster data must be complete (all 3 stages)");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Invokes the NR solver for binary at the given composition and temperature.
     */
    private CVMSolverResult solve(double xB, double temperature, double[] eci) {
        ClusterIdentificationResult stage1 = clusterData.getStage1();
        CFIdentificationResult stage2 = clusterData.getStage2();
        CMatrixResult stage3 = clusterData.getStage3();

        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();

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
                tcf,
                ncf,
                stage2.getLcf(),
                stage3.getCfBasisIndices(),
                TOLS);
    }

    // =========================================================================
    // Step 3: Newton-Raphson convergence for binary (ECI = 0)
    // =========================================================================

    /**
     * Solver must converge for binary equimolar composition with ECI=0.
     *
     * <p>With ECI=0 the free energy is purely entropic: G = −T·S.
     * The disordered state is the unique minimum, so NR should converge
     * immediately (the initial guess IS already the solution).</p>
     */
    @Test
    void binaryECI0_solverConverges() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMSolverResult result = solve(0.5, 1000.0, zeroECI);

        assertTrue(result.isConverged(),
                "Solver must converge with ECI=0 at x=0.5, T=1000; iterations=" + result.getIterations());
    }

    /**
     * G = H − T·S must hold at convergence (ECI=0).
     */
    @Test
    void binaryECI0_gibbsEnergyConsistency() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];
        double T = 1000.0;

        CVMSolverResult result = solve(0.5, T, zeroECI);

        double gExpected = result.getEnthalpy() - T * result.getEntropy();
        assertEquals(gExpected, result.getGibbsEnergy(), 1.0e-9,
                "G must equal H − T·S at convergence (ECI=0)");
    }

    /**
     * Gradient norm must be below tolerance at convergence (ECI=0).
     */
    @Test
    void binaryECI0_gradientNormBelowTolerance() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMSolverResult result = solve(0.5, 1000.0, zeroECI);

        assertTrue(result.isConverged(), "Solver must converge before checking gradient norm");
        assertTrue(result.getGradientNorm() < TOLS * 10,
                String.format("Gradient norm at convergence must be < %.1e, got %.4e",
                        TOLS * 10, result.getGradientNorm()));
    }

    /**
     * G must be negative at T=1000 K with ECI=0 (G = −T·S, S > 0).
     */
    @Test
    void binaryECI0_gibbsEnergyIsNegative() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMSolverResult result = solve(0.5, 1000.0, zeroECI);

        assertTrue(result.getGibbsEnergy() < 0.0,
                "G = −T·S must be negative at T=1000K with ECI=0, got G=" + result.getGibbsEnergy());
    }

    /**
     * Entropy at equilibrium (ECI=0) must be in physical J/(mol·K) range.
     *
     * <p>Same invariant as in CVMFreeEnergyTest — confirms the NR solver
     * propagates the physical R_GAS correctly through its evaluation calls.</p>
     */
    @Test
    void binaryECI0_entropyMagnitudeInPhysicalRange() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMSolverResult result = solve(0.5, 1000.0, zeroECI);

        double physicalLowerBound = 1.0; // J/(mol·K)
        double physicalUpperBound = CVMFreeEnergy.R_GAS * Math.log(2) * 2;
        assertTrue(result.getEntropy() > physicalLowerBound,
                String.format("Entropy S=%.4f J/(mol·K) is below physical lower bound %.1f J/(mol·K)",
                        result.getEntropy(), physicalLowerBound));
        assertTrue(result.getEntropy() < physicalUpperBound,
                String.format("Entropy S=%.4f J/(mol·K) exceeds physical upper bound %.4f J/(mol·K)",
                        result.getEntropy(), physicalUpperBound));
    }

    // =========================================================================
    // Step 3: NR convergence for binary with non-zero ECI (Nb-Ti values)
    // =========================================================================

    /**
     * Solver must converge for binary equimolar with non-zero Nb-Ti ECI.
     */
    @Test
    void binaryNbTiECI_solverConverges() {
        int ncf = clusterData.getStage2().getNcf();
        double[] eci = new double[ncf];
        if (ncf >= 2) {
            eci[0] = -390.0;
            eci[1] = -260.0;
        } else if (ncf >= 1) {
            eci[0] = -390.0;
        }

        CVMSolverResult result = solve(0.5, 1000.0, eci);

        assertTrue(result.isConverged(),
                "Solver must converge with Nb-Ti ECI at x=0.5, T=1000; iterations=" + result.getIterations());
    }

    /**
     * G = H − T·S must hold at convergence with non-zero ECI.
     */
    @Test
    void binaryNbTiECI_gibbsEnergyConsistency() {
        int ncf = clusterData.getStage2().getNcf();
        double[] eci = new double[ncf];
        if (ncf >= 2) {
            eci[0] = -390.0;
            eci[1] = -260.0;
        } else if (ncf >= 1) {
            eci[0] = -390.0;
        }
        double T = 1000.0;

        CVMSolverResult result = solve(0.5, T, eci);

        double gExpected = result.getEnthalpy() - T * result.getEntropy();
        assertEquals(gExpected, result.getGibbsEnergy(), 1.0e-9,
                "G must equal H − T·S at convergence with non-zero ECI");
    }

    /**
     * G at equilibrium must be ≤ G at the disordered state for Nb-Ti ECI.
     *
     * <p>Newton-Raphson minimizes G, so the equilibrium G must be no greater
     * than the free energy at the initial (disordered) guess. For large negative
     * ECI (pair interaction strongly favors ordering), the equilibrium G should
     * be significantly less than the disordered G.</p>
     */
    @Test
    void binaryNbTiECI_equilibriumGibbsLessThanDisordered() {
        int ncf = clusterData.getStage2().getNcf();
        double[] eci = new double[ncf];
        if (ncf >= 2) {
            eci[0] = -390.0;
            eci[1] = -260.0;
        } else if (ncf >= 1) {
            eci[0] = -390.0;
        }
        double T = 1000.0;

        // G at the disordered state (no optimization)
        ClusterIdentificationResult stage1 = clusterData.getStage1();
        CFIdentificationResult stage2 = clusterData.getStage2();
        CMatrixResult stage3 = clusterData.getStage3();
        int tcf = stage2.getTcf();
        double[] uDisordered = ClusterVariableEvaluator.computeRandomCFs(
                new double[]{0.5, 0.5}, 2, stage3.getCfBasisIndices(), ncf, tcf);

        CVMFreeEnergy.EvalResult disorderedEval = CVMFreeEnergy.evaluate(
                uDisordered, new double[]{0.5, 0.5}, 2, T, eci,
                stage1.getDisClusterData().getMultiplicities(),
                stage1.getKbCoefficients(),
                stage1.getMh(),
                stage1.getLc(),
                stage3.getCmat(),
                stage3.getLcv(),
                stage3.getWcv(),
                stage1.getTcdis(),
                tcf, ncf,
                stage2.getLcf(),
                stage3.getCfBasisIndices());

        // G at NR equilibrium
        CVMSolverResult result = solve(0.5, T, eci);
        assertTrue(result.isConverged(), "Solver must converge before comparing G values");

        assertTrue(result.getGibbsEnergy() <= disorderedEval.G + 1.0e-6,
                String.format("Equilibrium G=%.6f must be ≤ disordered G=%.6f (NR minimizes G)",
                        result.getGibbsEnergy(), disorderedEval.G));
    }

    // =========================================================================
    // Step 5: Initial-guess correctness (binary)
    // =========================================================================

    /**
     * For binary equimolar (x=0.5), computeRandomCFs must return all zeros.
     *
     * <p>Binary point CF: σ = 2·x_B − 1 = 2·0.5 − 1 = 0.
     * Non-point CFs: u[l] = σ^rank = 0^rank = 0 (for rank ≥ 1).
     * This is both the disordered state AND the minimum of G for ECI=0 at x=0.5.</p>
     */
    @Test
    void initialGuess_binary_equimolar_isAllZeros() {
        int ncf = clusterData.getStage2().getNcf();
        int tcf = clusterData.getStage2().getTcf();
        int[][] cfBasisIndices = clusterData.getStage3().getCfBasisIndices();

        double[] uRandom = ClusterVariableEvaluator.computeRandomCFs(
                new double[]{0.5, 0.5}, 2, cfBasisIndices, ncf, tcf);

        assertEquals(ncf, uRandom.length, "computeRandomCFs must return ncf values");
        for (int i = 0; i < ncf; i++) {
            assertEquals(0.0, uRandom[i], 1.0e-14,
                    String.format("u[%d] at binary equimolar must be 0.0, got %.4e", i, uRandom[i]));
        }
    }

    /**
     * For binary off-equimolar (x=0.25), computeRandomCFs must return non-trivial CFs.
     *
     * <p>Binary point CF: σ = 2·x_B − 1 = 2·0.25 − 1 = −0.5.
     * Non-point CFs: u[l] = (−0.5)^rank, non-zero for rank ≥ 1.</p>
     */
    @Test
    void initialGuess_binary_offEquimolar_isNonZero() {
        int ncf = clusterData.getStage2().getNcf();
        int tcf = clusterData.getStage2().getTcf();
        int[][] cfBasisIndices = clusterData.getStage3().getCfBasisIndices();

        double[] uRandom = ClusterVariableEvaluator.computeRandomCFs(
                new double[]{0.75, 0.25}, 2, cfBasisIndices, ncf, tcf);

        assertEquals(ncf, uRandom.length, "computeRandomCFs must return ncf values");

        // At least one CF must be non-zero for off-equimolar composition
        boolean hasNonZero = false;
        for (double val : uRandom) {
            if (Math.abs(val) > 1.0e-14) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue(hasNonZero,
                "At off-equimolar x=0.25, computeRandomCFs must return at least one non-zero CF");
    }

    /**
     * With ECI=0 at x=0.5, the NR solver should converge very quickly.
     *
     * <p>The disordered state (u=0) IS the minimum of G for ECI=0 at x=0.5
     * (by symmetry). If the initial guess is correct, the gradient at the
     * initial point should be near-zero and NR should converge in ≤ 3 iterations.</p>
     */
    @Test
    void initialGuess_binary_equimolar_ECI0_fewIterations() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMSolverResult result = solve(0.5, 1000.0, zeroECI);

        assertTrue(result.isConverged(), "Solver must converge");
        assertTrue(result.getIterations() <= 3,
                "At x=0.5 with ECI=0, NR should converge in ≤ 3 iterations (disordered state is the minimum), "
                        + "got " + result.getIterations());
    }
}
