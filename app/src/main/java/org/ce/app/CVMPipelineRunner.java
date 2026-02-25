package org.ce.app;

import org.ce.app.CVMConfiguration;
import org.ce.app.CVMPipeline;
import org.ce.app.CVMResult;
import org.ce.identification.cf.CFIdentificationResult;
import org.ce.identification.cluster.ClusterIdentificationResult;
import org.ce.identification.engine.Vector3D;

/**
 * Integration test for the two-stage CVM identification pipeline using the
 * unified {@link CVMPipeline} orchestrator.
 *
 * <h2>Test cases</h2>
 * <ol>
 *   <li><b>A2 binary (A2-T, binary)</b> — simplest case: phase == HSP,
 *       identity transform, numComp=2.  Validates Nij table and KB coefficients
 *       against known values for the BCC tetrahedron approximation.</li>
 *   <li><b>A2 ternary (A2-T, ternary)</b> — same structure, numComp=3.
 *       Validates that CF count increases correctly with components.</li>
 * </ol>
 *
 * @author  CVM Project
 * @version 1.0
 * @see     CVMPipeline
 * @see     CVMConfiguration
 */
public class CVMPipelineRunner {

    public static void main(String[] args) throws Exception {

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  CVM TWO-STAGE PIPELINE TEST (Using CVMPipeline API) ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");

        // ============================================================
        // TEST 1: A2 binary — phase == HSP, identity transform
        // ============================================================
        System.out.println("\n\n══════════════════════════════════════════════════════");
        System.out.println("TEST 1: A2-T  binary (numComp=2)");
        System.out.println("══════════════════════════════════════════════════════");

        CVMConfiguration binaryConfig = CVMConfiguration.builder()
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

        CVMResult binaryResult = CVMPipeline.identify(binaryConfig);
        binaryResult.printDebug();

        // Extract and validate results
        ClusterIdentificationResult a2BinClus =
                binaryResult.getClusterIdentification();
        CFIdentificationResult a2BinCF =
                binaryResult.getCorrelationFunctionIdentification();

        System.out.println("\n── Validation ──");
        validateA2BinaryClusterResult(a2BinClus);
        validateA2BinaryCFResult(a2BinCF);

        // ============================================================
        // TEST 2: A2 ternary — same structure, more CFs (reuse Stage 1)
        // ============================================================
        System.out.println("\n\n══════════════════════════════════════════════════════");
        System.out.println("TEST 2: A2-T  ternary (numComp=3)");
        System.out.println("══════════════════════════════════════════════════════");

        System.out.println("Stage 1 result: identical to binary (component-independent)");

        // For ternary, reuse the same config but with numComp=3
        CVMConfiguration ternaryConfig = CVMConfiguration.builder()
                .disorderedClusterFile("cluster/A2-T.txt")
                .orderedClusterFile("cluster/A2-T.txt")
                .disorderedSymmetryGroup("A2-SG")
                .orderedSymmetryGroup("A2-SG")
                .transformationMatrix(new double[][] {
                        {1, 0, 0}, {0, 1, 0}, {0, 0, 1}
                })
                .translationVector(new Vector3D(0, 0, 0))
                .numComponents(3)  // Changed to ternary
                .build();

        CVMResult ternaryResult = CVMPipeline.identify(ternaryConfig);
        ternaryResult.printDebug();

        CFIdentificationResult a2TernCF =
                ternaryResult.getCorrelationFunctionIdentification();

        validateA2TernaryCFResult(a2BinClus, a2TernCF);

        System.out.println("\n══════════════════════════════════════════════════════");
        System.out.println("All tests complete.");
        System.out.println("══════════════════════════════════════════════════════");
    }

    // -------------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------------

    /**
     * Validates Stage 1 results for A2-T binary.
     */
    private static void validateA2BinaryClusterResult(
            ClusterIdentificationResult r) {

        boolean pass = true;

        // ---- Scalar counts ----
        pass &= check("tcdis == 5", r.getTcdis() == 5);
        pass &= check("nxcdis == 1", r.getNxcdis() == 1);

        // ---- For A2 (phase == HSP): lc[t] == 1 for all t ----
        int[] lc = r.getLc();
        for (int t = 0; t < r.getTcdis(); t++) {
            pass &= check("lc[" + t + "] == 1", lc[t] == 1);
        }

        // ---- mh[t][0] == 1.0 ----
        double[][] mh = r.getMh();
        for (int t = 0; t < r.getTcdis(); t++) {
            pass &= check("mh[" + t + "][0] ≈ 1.0", Math.abs(mh[t][0] - 1.0) < 1e-9);
        }

        // ---- Nij diagonal: nij[t][t] == 1 for all t ----
        int[][] nij = r.getNijTable();
        for (int t = 0; t < r.getTcdis(); t++) {
            pass &= check("nij[" + t + "][" + t + "] == 1", nij[t][t] == 1);
        }

        // ---- Nij geometric facts ----
        pass &= check("nij[0][1] == 4 (tet has 4 triangles)", nij[0][1] == 4);
        pass &= check("nij[0][2] == 4 (tet has 4 1NN-pairs)", nij[0][2] == 4);
        pass &= check("nij[0][3] == 2 (tet has 2 2NN-pairs)", nij[0][3] == 2);
        pass &= check("nij[0][4] == 4 (tet has 4 points)", nij[0][4] == 4);

        // ---- KB coefficient checks ----
        double[] kb = r.getKbCoefficients();
        pass &= check("kb[0] == 1.0 (maximal cluster)", Math.abs(kb[0] - 1.0) < 1e-9);

        double[] mhdis = new double[r.getTcdis()];
        for (int t = 0; t < r.getTcdis(); t++) {
            mhdis[t] = r.getDisClusterData().getMultiplicities().get(t);
        }

        double kbCountSum = 0, kbWeightedSum = 0;
        for (int t = 0; t < r.getTcdis(); t++) {
            kbCountSum    += kb[t];
            kbWeightedSum += kb[t] * mhdis[t];
        }
        pass &= check("Σ kb[t] ≈ 1", Math.abs(kbCountSum - 1.0) < 1e-6);
        pass &= check("Σ kb[t]*m[t] ≈ 0", Math.abs(kbWeightedSum) < 1e-6);

        System.out.println("  KB coefficients:");
        for (int t = 0; t < kb.length; t++) {
            System.out.printf("    kb[%d] = %+.6f  (m=%.4f)%n", t, kb[t], mhdis[t]);
        }

        System.out.println(pass ? "✅ ALL A2 BINARY CLUSTER CHECKS PASSED"
                                : "❌ SOME A2 BINARY CLUSTER CHECKS FAILED");
    }

    /**
     * Validates Stage 2 CF results for A2-T binary.
     */
    private static void validateA2BinaryCFResult(CFIdentificationResult r) {
        boolean pass = true;

        pass &= check("tcf == 5 for binary A2-T", r.getTcf() == 5);
        pass &= check("nxcf == 1 for binary A2-T", r.getNxcf() == 1);
        pass &= check("ncf == 4 for binary A2-T",  r.getNcf() == 4);

        int[][] lcf = r.getLcf();
        pass &= check("lcf has 5 rows", lcf.length == 5);
        for (int t = 0; t < lcf.length; t++) {
            pass &= check("lcf[" + t + "][0] == 1", lcf[t][0] == 1);
        }

        System.out.println(pass ? "✅ ALL A2 BINARY CF CHECKS PASSED"
                                : "❌ SOME A2 BINARY CF CHECKS FAILED");
    }

    /**
     * Validates Stage 2 CF results for A2-T ternary.
     */
    private static void validateA2TernaryCFResult(
            ClusterIdentificationResult clus,
            CFIdentificationResult      ternCF) {
        boolean pass = true;

        int tcdis = clus.getTcdis();

        pass &= check("tcf_ternary > 5", ternCF.getTcf() > 5);

        int[][] lcf = ternCF.getLcf();
        pass &= check("lcf has tcdis=" + tcdis + " rows", lcf.length == tcdis);
        pass &= check("lcf[0][0] > 1 (ternary tet has multiple CFs)",
                lcf.length > 0 && lcf[0].length > 0 && lcf[0][0] > 1);

        int total = 0;
        for (int[] row : lcf)
            for (int v : row) total += v;
        pass &= check("Σ lcf[t][j] == tcf", total == ternCF.getTcf());

        System.out.println(pass ? "✅ ALL A2 TERNARY CF CHECKS PASSED"
                                : "❌ SOME A2 TERNARY CF CHECKS FAILED");
    }

    private static boolean check(String label, boolean condition) {
        System.out.println("  " + (condition ? "✓" : "✗") + "  " + label);
        return condition;
    }
}
