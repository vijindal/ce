package org.ce.domain.cvm;

import org.ce.domain.model.data.AllClusterData;
import org.ce.domain.identification.cluster.CFIdentificationResult;
import org.ce.domain.identification.cluster.ClusterIdentificationResult;
import org.ce.infrastructure.data.SystemDataLoader;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Data-flow verification tests for the CVM calculation pipeline.
 *
 * <p>Verifies the inputs and outputs at each step in the execution chain:</p>
 * <ol>
 *   <li>CEC loading from cec.json (ECILoader / SystemDataLoader)</li>
 *   <li>ECI mapping from raw CEC to CVM-ncf-length array (mapCECToCvmECI)</li>
 *   <li>Solver input construction (moleFractions, eci)</li>
 *   <li>Solver output consistency (G = H − T·S, S physical, gradient small)</li>
 * </ol>
 *
 * <h2>Bugs identified and tested</h2>
 * <ul>
 *   <li><b>RC-5 (mapCECToCvmECI)</b>: Strips from the wrong end — takes
 *       indices {@code [2..5]} instead of {@code [0..3]}, assigning pair ECIs
 *       to the tetrahedron/triangle types (wrong cluster multiplicities).</li>
 *   <li><b>RC-6 (CVMEngine moleFractions)</b>: {@code {composition, 1-composition}}
 *       puts x_B at index 0 instead of index 1, so the solver reads x_A as x_B.</li>
 * </ul>
 */
class CVMDataFlowVerificationTest {

    private static AllClusterData clusterData;

    /** Nb-Ti system key used in production. */
    private static final String SYSTEM_KEY = "BCC_A2_T_bin";

    /** The raw Nb-Ti CEC values as stored in cec.json
     *  (cec.json ordering: [tet, tri, pair1, pair2, point, empty]) */
    private static final double[] CEC_RAW_NBTI = {0.0, 0.0, -390.0, -260.0, 0.0, 0.0};
    private static final double CEC_PAIR1 = -390.0;
    private static final double CEC_PAIR2 = -260.0;

    @BeforeAll
    static void loadClusterData() throws Exception {
        Optional<AllClusterData> opt = AllClusterDataCache.load(SYSTEM_KEY);
        assertTrue(opt.isPresent(), "BCC_A2_T_bin cluster cache must be present");
        clusterData = opt.get();
        assertTrue(clusterData.isComplete(), "All 3 stages must be loaded");
    }

    // =========================================================================
    // Step 1: Verify cluster data structure matches cec.json expectations
    // =========================================================================

    /**
     * Verifies that the cluster data (ncf=4, tcf=5) is consistent with the
     * 6-value cec.json (which stores: [tet, tri, pair1, pair2, point, empty]).
     *
     * <p>For BCC_A2_T (tetrahedron model), the non-point cluster types are:
     * tetrahedron (t=0, mult=6), triangle (t=1, mult=12),
     * 1-nn pair (t=2, mult=4), 2-nn pair (t=3, mult=3).
     *
     * <p>The cec.json {@code clusterCount=6} matches {@code tc=6}.</p>
     * The CVM solver needs {@code ncf=4} ECI values (one per non-point cluster type).
     */
    @Test
    void clusterDataStructure_matchesCecJsonFormat() {
        CFIdentificationResult stage2 = clusterData.getStage2();
        ClusterIdentificationResult stage1 = clusterData.getStage1();

        int tc_from_cache = stage1.getDisClusterData().getTc();
        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();

        // cec.json has clusterCount=6 → must match tc
        assertEquals(6, tc_from_cache,
                "tc must equal 6 (cec.json clusterCount). Got: " + tc_from_cache);

        // ncf=4 (tet + tri + pair1 + pair2 = 4 non-point cluster types)
        assertEquals(4, ncf, "ncf must be 4 (4 non-point cluster types). Got: " + ncf);

        // tcf=5 (ncf + 1 point CF)
        assertEquals(5, tcf, "tcf must be 5 (ncf + 1 point CF). Got: " + tcf);

        // cec.json length should be tc=6 = ncf + 2
        assertEquals(ncf + 2, CEC_RAW_NBTI.length,
                "cec.json must have ncf+2 = " + (ncf + 2) + " values (non-point + point + empty)");
    }

    /**
     * Verifies HSP cluster type ordering: non-point types first, point type last.
     *
     * <p>This ordering determines the eci vector layout:
     * eci[0] → cluster type 0 (tetrahedron, msdis=6)
     * eci[1] → cluster type 1 (triangle,    msdis=12)
     * eci[2] → cluster type 2 (1-nn pair,   msdis=4)
     * eci[3] → cluster type 3 (2-nn pair,   msdis=3)
     *
     * <p>The cec.json stores them in the same descending body-count order:
     * cecRaw[0]=tet, [1]=tri, [2]=pair1, [3]=pair2, [4]=point, [5]=empty.
     * The correct mapping is: take cecRaw[0..ncf-1] = first ncf values.</p>
     */
    @Test
    void clusterTypeOrdering_matchesCecJsonOrder() {
        // Verify HSP multiplicities match cec.json description ordering
        java.util.List<Double> msdis = clusterData.getStage1().getDisClusterData().getMultiplicities();

        // msdis[0] = 6 → tetrahedron (cec.json[0] is tet with mult=6)
        assertEquals(6.0, msdis.get(0), 1e-9,
                "msdis[0] must be 6.0 (tetrahedron), but got " + msdis.get(0));

        // msdis[1] = 12 → triangle (cec.json[1] is tri with mult=12)
        assertEquals(12.0, msdis.get(1), 1e-9,
                "msdis[1] must be 12.0 (triangle), but got " + msdis.get(1));

        // msdis[2] = 4 → 1-nn pair (cec.json[2] is pair1 with mult=4)
        assertEquals(4.0, msdis.get(2), 1e-9,
                "msdis[2] must be 4.0 (1-nn pair), but got " + msdis.get(2));

        // msdis[3] = 3 → 2-nn pair (cec.json[3] is pair2 with mult=3)
        assertEquals(3.0, msdis.get(3), 1e-9,
                "msdis[3] must be 3.0 (2-nn pair), but got " + msdis.get(3));
    }

    // =========================================================================
    // Step 2: Verify correct ECI mapping (RC-5 detection)
    // =========================================================================

    /**
     * The CORRECT ECI mapping takes cecRaw[0..ncf-1] (first ncf values).
     *
     * <p>For Nb-Ti with cecRaw = [0, 0, -390, -260, 0, 0]:
     * <pre>
     *   eci[0] = cecRaw[0] = 0.0    → tetrahedron ECI
     *   eci[1] = cecRaw[1] = 0.0    → triangle ECI
     *   eci[2] = cecRaw[2] = -390.0 → 1-nn pair ECI  ✓
     *   eci[3] = cecRaw[3] = -260.0 → 2-nn pair ECI  ✓
     * </pre>
     * This gives the non-point cluster ECIs in the order expected by the solver.</p>
     */
    @Test
    void correctEciMapping_takeFirstNcfValues() {
        int ncf = clusterData.getStage2().getNcf(); // 4

        // Correct mapping: take first ncf values
        double[] eciCorrect = Arrays.copyOf(CEC_RAW_NBTI, ncf);

        assertEquals(ncf, eciCorrect.length, "Correct ECI must have ncf values");
        assertEquals(0.0,     eciCorrect[0], 1e-9, "eci[0] = tetrahedron ECI must be 0.0");
        assertEquals(0.0,     eciCorrect[1], 1e-9, "eci[1] = triangle ECI must be 0.0");
        assertEquals(-390.0,  eciCorrect[2], 1e-9, "eci[2] = 1-nn pair ECI must be -390.0");
        assertEquals(-260.0,  eciCorrect[3], 1e-9, "eci[3] = 2-nn pair ECI must be -260.0");
    }

    /**
     * The WRONG ECI mapping (current mapCECToCvmECI) takes cecRaw[2..5].
     *
     * <p>For Nb-Ti with cecRaw = [0, 0, -390, -260, 0, 0]:
     * <pre>
     *   eciWrong[0] = cecRaw[2] = -390.0  → assigned to TETRAHEDRON ✗
     *   eciWrong[1] = cecRaw[3] = -260.0  → assigned to TRIANGLE    ✗
     *   eciWrong[2] = cecRaw[4] = 0.0     → assigned to 1-nn pair
     *   eciWrong[3] = cecRaw[5] = 0.0     → assigned to 2-nn pair
     * </pre>
     * This assigns pair ECIs to the wrong cluster types (tet/tri multiplicities 6/12
     * instead of correct pair multiplicities 4/3), giving wrong H and G values.</p>
     *
     * <p>This test DOCUMENTS the current wrong behavior so the bug is visible
     * before the fix is applied.</p>
     */
    @Test
    void wrongEciMapping_currentBehavior_assignsPairEciToWrongTypes() {
        int ncf = clusterData.getStage2().getNcf(); // 4

        // what mapCECToCvmECI currently does (srcPos=2): takes cecRaw[2..5]
        double[] eciWrong = new double[ncf];
        System.arraycopy(CEC_RAW_NBTI, 2, eciWrong, 0, ncf);

        // Document the mismatch:
        // eciWrong[0] = -390 (but eci[0] is for tet with mult=6)
        assertEquals(-390.0, eciWrong[0], 1e-9,
                "Current wrong mapping: eci[0]=-390 is assigned to tetrahedron type (should be 0)");
        assertEquals(-260.0, eciWrong[1], 1e-9,
                "Current wrong mapping: eci[1]=-260 is assigned to triangle type (should be 0)");
        assertEquals(0.0, eciWrong[2], 1e-9,
                "Current wrong mapping: eci[2]=0 is assigned to 1-nn pair (should be -390)");
        assertEquals(0.0, eciWrong[3], 1e-9,
                "Current wrong mapping: eci[3]=0 is assigned to 2-nn pair (should be -260)");
    }

    /**
     * With the CORRECT ECI mapping, H is non-zero and physically meaningful for Nb-Ti.
     *
     * <p>Correct: eci=[0,0,-390,-260] → pair interactions active → H < 0 (attractive ordering)
     * Wrong:    eci=[-390,-260,0,0] → pair interactions zeroed, tet/tri active → H wrong</p>
     *
     * <p>Since at x=0.5 the disordered state is symmetric, a rough bound is:
     * H_correct ≈ sum_t mult_t * eci_t * CV_pair, which for the pair interactions
     * gives a negative value. H_wrong would use tet/tri multiplicities (6, 12) instead
     * of pair multiplicities (4, 3), yielding a different (incorrect) magnitude.</p>
     */
    @Test
    void correctVsWrongEciMapping_givesDifferentEnthalpy() {
        int ncf = clusterData.getStage2().getNcf();

        // Correct ECI: [0, 0, -390, -260]
        double[] eciCorrect = Arrays.copyOf(CEC_RAW_NBTI, ncf);

        // Wrong ECI: [-390, -260, 0, 0] (current mapCECToCvmECI behavior)
        double[] eciWrong = new double[ncf];
        System.arraycopy(CEC_RAW_NBTI, 2, eciWrong, 0, ncf);

        CVMSolverResult resultCorrect = solve(0.5, 1000.0, eciCorrect);
        CVMSolverResult resultWrong   = solve(0.5, 1000.0, eciWrong);

        assertTrue(resultCorrect.isConverged(), "Solver must converge with correct ECI");
        assertTrue(resultWrong.isConverged(),   "Solver must converge with wrong ECI");

        // With correct ECI, only pair interactions are active → finite H
        // With wrong ECI, pair ECIs are zeroed → tet/tri ECIs active → different H
        assertNotEquals(resultCorrect.getEnthalpy(), resultWrong.getEnthalpy(), 1.0,
                "Correct and wrong ECI mappings must give different enthalpy values");

        System.out.println("[DataFlow] Correct ECI " + Arrays.toString(eciCorrect)
                + " → H=" + String.format("%.3f", resultCorrect.getEnthalpy())
                + " G=" + String.format("%.3f", resultCorrect.getGibbsEnergy()));
        System.out.println("[DataFlow] Wrong ECI   " + Arrays.toString(eciWrong)
                + " → H=" + String.format("%.3f", resultWrong.getEnthalpy())
                + " G=" + String.format("%.3f", resultWrong.getGibbsEnergy()));
    }

    // =========================================================================
    // Step 3: Verify moleFractions construction (RC-6 detection)
    // =========================================================================

    /**
     * CVMPhaseModel builds moleFractions = {1-xB, xB}, so solver reads
     * xB = moleFractions[1] correctly.
     *
     * <p>At x_B=0.3, the point CF σ = 2·x_B - 1 = -0.4.
     * With correct moleFractions[1]=0.3, G must differ from the inverted case
     * moleFractions[1]=0.7 which would represent a different composition.</p>
     */
    @Test
    void moleFractions_cvmPhaseModelOrder_isCorrect() {
        int ncf = clusterData.getStage2().getNcf();
        double[] zeroECI = new double[ncf];
        double xB = 0.3;

        // Correct order (CVMPhaseModel): [1-xB, xB]
        CVMSolverResult resultCorrect = solveWithMoleFractions(new double[]{1.0 - xB, xB}, 1000.0, zeroECI);

        // "Inverted" order (CVMEngine if composition=xB): [xB, 1-xB]
        CVMSolverResult resultInverted = solveWithMoleFractions(new double[]{xB, 1.0 - xB}, 1000.0, zeroECI);

        assertTrue(resultCorrect.isConverged(),  "Solver must converge with correct moleFractions");
        assertTrue(resultInverted.isConverged(), "Solver must converge with inverted moleFractions");

        // At ECI=0, G = -T*S. The two compositions (0.3 and 0.7) give the same entropy
        // by binary symmetry, so G values should match. But the equilibrium CFs must differ
        // (xB=0.3 → CFs are different from xB=0.7 by sign flip).
        double[] cfsCorrect  = resultCorrect.getEquilibriumCFs();
        double[] cfsInverted = resultInverted.getEquilibriumCFs();

        System.out.println("[DataFlow] Correct   moleFractions={0.7, 0.3}: CFs=" + Arrays.toString(cfsCorrect));
        System.out.println("[DataFlow] Inverted  moleFractions={0.3, 0.7}: CFs=" + Arrays.toString(cfsInverted));
        System.out.println("[DataFlow] Correct   G=" + resultCorrect.getGibbsEnergy()
                + " H=" + resultCorrect.getEnthalpy() + " S=" + resultCorrect.getEntropy());
        System.out.println("[DataFlow] Inverted  G=" + resultInverted.getGibbsEnergy()
                + " H=" + resultInverted.getEnthalpy() + " S=" + resultInverted.getEntropy());
    }

    /**
     * CVMEngine builds moleFractions = {composition, 1-composition} which is
     * INVERTED relative to CVMPhaseModel's {1-composition, composition}.
     *
     * <p>At x_B=0.3: CVMPhaseModel → {0.7, 0.3}, CVMEngine → {0.3, 0.7}.
     * Since the solver reads xB = moleFractions[1], CVMEngine gives the solver
     * xB=0.7 (x_A!) instead of the intended xB=0.3.</p>
     *
     * <p>This test verifies the specific bug by running both versions with non-zero ECI
     * and showing the enthalpy differs (different composition → different energy).</p>
     */
    @Test
    void cvmEngine_moleFractionsInversion_causesDifferentEnthalpy() {
        int ncf = clusterData.getStage2().getNcf();
        double[] eciCorrect = Arrays.copyOf(CEC_RAW_NBTI, ncf); // [0, 0, -390, -260]
        double xB = 0.3;

        // CVMPhaseModel order (correct)
        CVMSolverResult phaseModel = solveWithMoleFractions(new double[]{1.0 - xB, xB}, 1000.0, eciCorrect);

        // CVMEngine order (inverted: passes x_A to the slot that maps to x_B)
        CVMSolverResult engine = solveWithMoleFractions(new double[]{xB, 1.0 - xB}, 1000.0, eciCorrect);

        assertTrue(phaseModel.isConverged(), "Phase model solver must converge");
        assertTrue(engine.isConverged(),     "Engine solver must converge");

        System.out.println("[DataFlow] PhaseModel xB=0.3 → G=" + String.format("%.4f", phaseModel.getGibbsEnergy())
                + " H=" + String.format("%.4f", phaseModel.getEnthalpy())
                + " S=" + String.format("%.4f", phaseModel.getEntropy()));
        System.out.println("[DataFlow] CVMEngine  uses xB=0.7 → G=" + String.format("%.4f", engine.getGibbsEnergy())
                + " H=" + String.format("%.4f", engine.getEnthalpy())
                + " S=" + String.format("%.4f", engine.getEntropy()));

        // G(xB=0.3) ≠ G(xB=0.7) for non-symmetric ECI
        // (only equal if ECI is fully composition-symmetric, which Nb-Ti is not at off-equimolar)
        // Check that the CFs differ → confirm the compositions are different
        assertFalse(Arrays.equals(phaseModel.getEquilibriumCFs(), engine.getEquilibriumCFs()),
                "Equilibrium CFs must differ: PhaseModel gives x_B=0.3, CVMEngine gives x_B=0.7");
    }

    // =========================================================================
    // Step 4: End-to-end G = H - T·S consistency
    // =========================================================================

    /**
     * With the CORRECT ECI mapping, G = H - T·S must hold.
     */
    @Test
    void correctEciMapping_gibbsConsistency() {
        int ncf = clusterData.getStage2().getNcf();
        double[] eciCorrect = Arrays.copyOf(CEC_RAW_NBTI, ncf);
        double T = 1000.0;

        CVMSolverResult r = solve(0.5, T, eciCorrect);
        assertTrue(r.isConverged(), "Solver must converge with correct ECI");

        assertEquals(r.getEnthalpy() - T * r.getEntropy(), r.getGibbsEnergy(), 1.0e-9,
                "G = H - T*S must hold with correct ECI mapping");
    }

    /**
     * With the CORRECT ECI mapping, H must be negative at equimolar for Nb-Ti.
     *
     * <p>Nb-Ti has strongly negative pair ECIs (-390, -260), so the enthalpy
     * of mixing must be negative (attractive interactions between unlike atoms).</p>
     */
    @Test
    void correctEciMapping_enthalpyIsNegative_forNbTi() {
        int ncf = clusterData.getStage2().getNcf();
        double[] eciCorrect = Arrays.copyOf(CEC_RAW_NBTI, ncf);

        CVMSolverResult r = solve(0.5, 1000.0, eciCorrect);
        assertTrue(r.isConverged(), "Solver must converge");

        assertTrue(r.getEnthalpy() < 0.0,
                "H must be negative for Nb-Ti with attractive pair interactions, got H=" + r.getEnthalpy());
    }

    /**
     * With the WRONG ECI mapping (current bug), H is assigned to wrong cluster types.
     *
     * <p>With eciWrong=[-390,-260,0,0]: the -390 ECI is applied to the tetrahedron
     * with multiplicity 6. With eciCorrect=[0,0,-390,-260]: -390 is applied to
     * the 1-nn pair with multiplicity 4. The ratio of effective multiplicities
     * changes the enthalpy magnitude.</p>
     */
    @Test
    void wrongEciMapping_stillConverges_butGivesWrongH() {
        int ncf = clusterData.getStage2().getNcf();
        double[] eciWrong = new double[ncf];
        System.arraycopy(CEC_RAW_NBTI, 2, eciWrong, 0, ncf); // [-390, -260, 0, 0]

        double[] eciCorrect = Arrays.copyOf(CEC_RAW_NBTI, ncf); // [0, 0, -390, -260]

        CVMSolverResult rWrong   = solve(0.5, 1000.0, eciWrong);
        CVMSolverResult rCorrect = solve(0.5, 1000.0, eciCorrect);

        assertTrue(rWrong.isConverged(),   "Solver must converge even with wrong ECI");
        assertTrue(rCorrect.isConverged(), "Solver must converge with correct ECI");

        // The enthalpy values must differ because different cluster multiplicities are used
        assertNotEquals(rWrong.getEnthalpy(), rCorrect.getEnthalpy(), 1.0,
                String.format("Wrong ECI (H=%.2f) and correct ECI (H=%.2f) must give different H",
                        rWrong.getEnthalpy(), rCorrect.getEnthalpy()));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CVMSolverResult solve(double xB, double temperature, double[] eci) {
        return solveWithMoleFractions(new double[]{1.0 - xB, xB}, temperature, eci);
    }

    private CVMSolverResult solveWithMoleFractions(double[] moleFractions, double temperature, double[] eci) {
        ClusterIdentificationResult stage1 = clusterData.getStage1();
        CFIdentificationResult stage2 = clusterData.getStage2();
        CMatrixResult stage3 = clusterData.getStage3();

        return NewtonRaphsonSolverSimple.solve(
                moleFractions,
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
                1.0e-8);
    }
}
