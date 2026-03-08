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
 * Unit tests for CVMFreeEnergy.
 *
 * Verifies:
 * 1. H = 0 when ECI = 0 (any state)
 * 2. G = H - T*S identity (always)
 * 3. S > 0 at the disordered state
 * 4. S is in physical J/(mol*K) range: |S| > 1.0 at equimolar binary
 *    — this test FAILS when R=1.0 and PASSES when R=R_GAS=8.3144598
 * 5. Gradient Gcu is near-zero at the random state with ECI=0
 */
class CVMFreeEnergyTest {

    private static AllClusterData clusterData;

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

    private CVMFreeEnergy.EvalResult evaluateAtDisorderedState(double xB, double temperature, double[] eci) {
        ClusterIdentificationResult stage1 = clusterData.getStage1();
        CFIdentificationResult stage2 = clusterData.getStage2();
        CMatrixResult stage3 = clusterData.getStage3();

        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();
        int[][] cfBasisIndices = stage3.getCfBasisIndices();

        // Disordered-state non-point CFs
        double[] uRandom = ClusterVariableEvaluator.computeRandomCFs(
                new double[]{1.0 - xB, xB}, 2, cfBasisIndices, ncf, tcf);

        return CVMFreeEnergy.evaluate(
                uRandom,
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
                cfBasisIndices);
    }

    // =========================================================================
    // Tests
    // =========================================================================

    /**
     * H must be exactly zero when all ECI are zero.
     * Enthalpy is linear in ECI: H = sum_t mhdis[t] * sum_l eci[l] * u[l]
     * so ECI=0 => H=0 regardless of CFs, composition, or temperature.
     */
    @Test
    void zeroECI_enthalpyIsZero() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMFreeEnergy.EvalResult result = evaluateAtDisorderedState(0.5, 1000.0, zeroECI);

        assertEquals(0.0, result.H, 1.0e-12,
                "Enthalpy must be exactly 0 when all ECI are zero");
    }

    /**
     * G = H - T*S must hold to machine precision for all states.
     */
    @Test
    void gibbsEnergyConsistency_zeroECI() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];
        double T = 1000.0;

        CVMFreeEnergy.EvalResult result = evaluateAtDisorderedState(0.5, T, zeroECI);

        double gExpected = result.H - T * result.S;
        assertEquals(gExpected, result.G, 1.0e-10,
                "G must equal H - T*S to machine precision");
    }

    /**
     * G = H - T*S must hold for non-zero ECI as well.
     */
    @Test
    void gibbsEnergyConsistency_nbTiECI() {
        // Nb-Ti pair ECI: 6 clusters, ncf = (non-point clusters before point)
        int ncf = clusterData.getStage2().getNcf();
        // Use first two pair ECI values from Nb-Ti system (-390, -260 J/mol)
        double[] eci = new double[ncf];
        if (ncf >= 2) {
            eci[0] = -390.0;
            eci[1] = -260.0;
        } else if (ncf >= 1) {
            eci[0] = -390.0;
        }
        double T = 1000.0;

        CVMFreeEnergy.EvalResult result = evaluateAtDisorderedState(0.5, T, eci);

        double gExpected = result.H - T * result.S;
        assertEquals(gExpected, result.G, 1.0e-9,
                "G must equal H - T*S to high precision for non-zero ECI");
    }

    /**
     * Entropy must be strictly positive at the disordered binary state.
     * S = -R * sum(weighted CV * ln(CV)) > 0 because
     * all CVs are in (0,1) so ln(CV) < 0, and the Kikuchi-Baker
     * coefficients are positive for physical cluster topologies.
     */
    @Test
    void entropyIsPositiveAtDisorderedState() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMFreeEnergy.EvalResult result = evaluateAtDisorderedState(0.5, 1000.0, zeroECI);

        assertTrue(result.S > 0.0,
                "Entropy must be positive at the disordered state, got S=" + result.S);
    }

    /**
     * Physical units check: entropy at x=0.5 must be in J/(mol*K) range.
     *
     * <p>The ideal mixing entropy for binary at equimolar is R*ln(2) ≈ 5.763 J/(mol*K).
     * The CVM entropy for the BCC_A2_T tetrahedron model at disordered state will be
     * slightly different, but must be positive and on the same order.
     *
     * <p><b>This test detects the RC-1 gas constant bug:</b>
     * <ul>
     *   <li>With {@code R = 1.0}: S ≈ 0.57 (dimensionless) → test FAILS (0.57 < 1.0)</li>
     *   <li>With {@code R = 8.3144598}: S ≈ 4.7 J/(mol*K) → test PASSES (4.7 > 1.0)</li>
     * </ul>
     */
    @Test
    void entropyMagnitude_mustBeInPhysicalJPerMolKRange() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMFreeEnergy.EvalResult result = evaluateAtDisorderedState(0.5, 1000.0, zeroECI);

        // The CVM configurational entropy at the BCC_A2_T disordered state
        // must be a substantial fraction of R*ln(2) = 5.763 J/(mol*K).
        // Physical lower bound: at least 1 J/(mol*K) per mole of sites.
        // This fails when R=1.0 (gives ~0.57) and passes when R=R_GAS (gives ~4.7).
        double physicalLowerBound = 1.0; // J/(mol*K)
        assertTrue(result.S > physicalLowerBound,
                String.format("Entropy S=%.4f J/(mol*K) is below the physical lower bound %.1f J/(mol*K). "
                        + "This indicates R is not set to R_GAS=8.3144598 in CVMFreeEnergy.",
                        result.S, physicalLowerBound));

        // Also check upper bound: S cannot exceed R*ln(K) = R*ln(2) for binary
        double physicalUpperBound = CVMFreeEnergy.R_GAS * Math.log(2) * 2; // generous upper bound
        assertTrue(result.S < physicalUpperBound,
                String.format("Entropy S=%.4f J/(mol*K) exceeds physical upper bound %.4f J/(mol*K)",
                        result.S, physicalUpperBound));
    }

    /**
     * At the disordered state with ECI=0, the gradient Gcu should be very small.
     *
     * <p>With ECI=0: Gcu = Hcu - T*Scu = 0 - T*Scu.
     * At the disordered random state, Scu is not exactly zero in general;
     * the solver will still need a few iterations. But the gradient should be
     * below 1e-2 at the initial disordered state, making convergence fast.</p>
     */
    @Test
    void gradientAtDisorderedState_zeroECI_isSmall() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMFreeEnergy.EvalResult result = evaluateAtDisorderedState(0.5, 1000.0, zeroECI);

        double gradNorm = 0.0;
        for (double g : result.Gcu) {
            gradNorm += g * g;
        }
        gradNorm = Math.sqrt(gradNorm);

        // At ECI=0, x=0.5 (symmetric), the gradient should be very close to 0
        // because the disordered state IS the minimum for the symmetric case
        assertTrue(gradNorm < 1.0e-6,
                String.format("Gradient norm at disordered state with ECI=0 should be < 1e-6, got %.4e", gradNorm));
    }

    /**
     * G must be negative at T > 0 with ECI=0 (since G = -T*S and S > 0).
     */
    @Test
    void gibbsEnergyIsNegative_zeroECI_positiveT() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMFreeEnergy.EvalResult result = evaluateAtDisorderedState(0.5, 1000.0, zeroECI);

        assertTrue(result.G < 0.0,
                "G = H - T*S must be negative at T=1000K with ECI=0 (S>0 so G=-T*S<0), got G=" + result.G);
    }

    /**
     * Gradient must have correct size (ncf elements).
     */
    @Test
    void gradientAndHessianDimensions() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMFreeEnergy.EvalResult result = evaluateAtDisorderedState(0.5, 1000.0, zeroECI);

        assertEquals(ncf, result.Gcu.length, "Gradient must have ncf elements");
        assertEquals(ncf, result.Gcuu.length, "Hessian row count must be ncf");
        assertEquals(ncf, result.Gcuu[0].length, "Hessian column count must be ncf");
    }

    /**
     * Hessian must be symmetric: Gcuu[i][j] == Gcuu[j][i].
     */
    @Test
    void hessianIsSymmetric() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];

        CVMFreeEnergy.EvalResult result = evaluateAtDisorderedState(0.5, 1000.0, zeroECI);

        double maxAsymmetry = 0.0;
        for (int i = 0; i < ncf; i++) {
            for (int j = 0; j < ncf; j++) {
                maxAsymmetry = Math.max(maxAsymmetry,
                        Math.abs(result.Gcuu[i][j] - result.Gcuu[j][i]));
            }
        }
        assertEquals(0.0, maxAsymmetry, 1.0e-12,
                "Hessian must be symmetric, max asymmetry=" + maxAsymmetry);
    }
}
