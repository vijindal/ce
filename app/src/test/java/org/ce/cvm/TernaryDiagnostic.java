package org.ce.cvm;

import org.ce.core.CVMConfiguration;
import org.ce.core.CVMPipeline;
import org.ce.core.CVMResult;
import org.ce.identification.cf.CFIdentificationResult;
import org.ce.identification.cluster.ClusterIdentificationResult;
import org.ce.identification.geometry.Cluster;
import org.ce.identification.geometry.Vector3D;
import org.ce.input.InputLoader;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * Diagnostic: trace point-cluster C-matrix for binary then ternary.
 */
public class TernaryDiagnostic {

    /**
     * Binary A2-T: trace the point cluster's C-matrix, CFs, and CVs.
     */
    @Test
    void diagnoseBinaryPointCluster() {
        System.out.println("═══════════════════════════════════════");
        System.out.println("BINARY (K=2) POINT CLUSTER DIAGNOSIS");
        System.out.println("═══════════════════════════════════════");

        // R-matrix for K=2
        double[][] rMat = RMatrixCalculator.buildRMatrix(2);
        double[] basis = RMatrixCalculator.buildBasis(2);
        System.out.println("Basis: " + Arrays.toString(basis));
        System.out.println("R-matrix (inv Vandermonde):");
        for (double[] row : rMat) System.out.println("  " + Arrays.toString(row));

        // P-coefficients per element
        PRules pRules = PRulesBuilder.build(4, 2);  // 4 sites for A2-T
        for (int e = 0; e < 2; e++) {
            System.out.printf("Element %d → p-coeffs: %s%n", e,
                    Arrays.toString(pRules.coefficientsFor(0, e)));
        }

        // Build pipeline
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
        ClusterIdentificationResult stage1 = result.getClusterIdentification();
        CFIdentificationResult stage2 = result.getCorrelationFunctionIdentification();
        List<Cluster> ordMax = InputLoader.parseClusterFile(config.getOrderedClusterFile());
        CMatrixResult stage3 = CMatrixBuilder.build(stage1, stage2, ordMax, 2);

        int tcdis = stage1.getTcdis();
        int tcf = stage2.getTcf();
        int ncf = stage2.getNcf();
        int[] lc = stage1.getLc();

        // Point type = last type (tcdis-1)
        int pt = tcdis - 1;
        System.out.printf("%nPoint type t=%d, lc[t]=%d%n", pt, lc[pt]);

        // Show C-matrix for point cluster
        double[][] cmPoint = stage3.getCmat().get(pt).get(0);
        int[] wPoint = stage3.getWcv().get(pt).get(0);
        int nv = stage3.getLcv()[pt][0];
        System.out.printf("Point cluster: %d CVs, C-matrix has %d cols (tcf=%d + 1 constant)%n",
                nv, cmPoint[0].length, tcf);
        for (int v = 0; v < nv; v++) {
            System.out.printf("  CV[%d]: w=%d  row=%s%n", v, wPoint[v], Arrays.toString(cmPoint[v]));
        }

        // Evaluate CVs at x=0.5 (equimolar)
        double[] uZero = new double[ncf];
        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(uZero, 0.5, stage3.getCfBasisIndices(), ncf, tcf);
        System.out.printf("%nuFull (binary, x=0.5): %s%n", Arrays.toString(uFull));

        double[][][] cv = ClusterVariableEvaluator.evaluate(
                uFull, stage3.getCmat(), stage3.getLcv(), tcdis, lc);
        System.out.println("Point CVs (binary, x=0.5):");
        for (int v = 0; v < nv; v++) {
            System.out.printf("  CV[%d] = %.10f (expected = 0.5)%n", v, cv[pt][0][v]);
        }
    }

    /**
     * Ternary A2-T: trace the point cluster's C-matrix, CFs, and CVs.
     */
    @Test
    void diagnoseTernaryPointCluster() {
        System.out.println("\n═══════════════════════════════════════");
        System.out.println("TERNARY (K=3) POINT CLUSTER DIAGNOSIS");
        System.out.println("═══════════════════════════════════════");

        // R-matrix for K=3
        double[][] rMat = RMatrixCalculator.buildRMatrix(3);
        double[] basis = RMatrixCalculator.buildBasis(3);
        System.out.println("Basis: " + Arrays.toString(basis));
        System.out.println("R-matrix (inv Vandermonde):");
        for (double[] row : rMat) System.out.println("  " + Arrays.toString(row));

        // P-coefficients per element
        PRules pRules = PRulesBuilder.build(4, 3);
        for (int e = 0; e < 3; e++) {
            System.out.printf("Element %d (basis val=%.0f) → p-coeffs: %s%n",
                    e, basis[e], Arrays.toString(pRules.coefficientsFor(0, e)));
        }

        // Build pipeline
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
        ClusterIdentificationResult stage1 = result.getClusterIdentification();
        CFIdentificationResult stage2 = result.getCorrelationFunctionIdentification();
        List<Cluster> ordMax = InputLoader.parseClusterFile(config.getOrderedClusterFile());
        CMatrixResult stage3 = CMatrixBuilder.build(stage1, stage2, ordMax, 3);

        int tcdis = stage1.getTcdis();
        int tcf = stage2.getTcf();
        int ncf = stage2.getNcf();
        int[] lc = stage1.getLc();

        // Point type = last type (tcdis-1)
        int pt = tcdis - 1;
        System.out.printf("%nPoint type t=%d, lc[t]=%d%n", pt, lc[pt]);

        // Show C-matrix for point cluster
        double[][] cmPoint = stage3.getCmat().get(pt).get(0);
        int[] wPoint = stage3.getWcv().get(pt).get(0);
        int nv = stage3.getLcv()[pt][0];
        System.out.printf("Point cluster: %d CVs, C-matrix has %d cols (tcf=%d + 1 constant)%n",
                nv, cmPoint[0].length, tcf);
        for (int v = 0; v < nv; v++) {
            System.out.printf("  CV[%d]: w=%d  row=%s%n", v, wPoint[v], Arrays.toString(cmPoint[v]));
        }

        // Evaluate CVs at equimolar (x_i = 1/3)
        double[] moleFractions = {1.0/3, 1.0/3, 1.0/3};
        double[] uRandom = ClusterVariableEvaluator.computeRandomCFs(
                moleFractions, 3, stage3.getCfBasisIndices(), ncf, tcf);
        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                uRandom, moleFractions, 3, stage3.getCfBasisIndices(), ncf, tcf);

        System.out.printf("%nuFull (ternary, equimolar, random CFs):%n");
        for (int l = 0; l < tcf; l++) {
            String label = l < ncf ? "non-point CF[" + l + "]" : "point CF[" + (l - ncf) + "]";
            System.out.printf("  uFull[%d] = %.10f  (%s)%n", l, uFull[l], label);
        }

        double[][][] cv = ClusterVariableEvaluator.evaluate(
                uFull, stage3.getCmat(), stage3.getLcv(), tcdis, lc);
        System.out.println("\nPoint CVs (ternary, equimolar):");
        for (int v = 0; v < nv; v++) {
            System.out.printf("  CV[%d] = %.10f  (expected = 1/3 = 0.333...)%n",
                    v, cv[pt][0][v]);
        }

        // Also show ALL CVs for all types
        System.out.println("\nAll CVs at random state:");
        int[][] lcvArr = stage3.getLcv();
        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                for (int v = 0; v < lcvArr[t][j]; v++) {
                    double cvVal = cv[t][j][v];
                    String flag = cvVal <= 0 ? " *** NEGATIVE ***" : "";
                    if (cvVal <= 1e-6 || t == pt) {
                        System.out.printf("  CV[t=%d][j=%d][v=%d] = %.10f%s%n",
                                t, j, v, cvVal, flag);
                    }
                }
            }
        }

        // Verify equal CVs within each cluster type
        System.out.println("\nCVs should be EQUAL within each cluster type:");
        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                double expectedCV = cv[t][j][0];
                boolean allEqual = true;
                for (int v = 1; v < lcvArr[t][j]; v++) {
                    if (Math.abs(cv[t][j][v] - expectedCV) > 1e-10) {
                        allEqual = false;
                        break;
                    }
                }
                System.out.printf("  Type t=%d group j=%d: %s (CV=%.10f)%n", 
                        t, j, allEqual ? "✓ EQUAL" : "✗ NOT EQUAL", expectedCV);
            }
        }

        // Show what u=0 gives (all CFs zero except point CFs)
        System.out.println("\n--- Compare: CVs at u=0 (all non-point CFs = 0) ---");
        double[] uZero = new double[ncf];
        double[] uFullZero = ClusterVariableEvaluator.buildFullCFVector(
                uZero, moleFractions, 3, stage3.getCfBasisIndices(), ncf, tcf);
        double[][][] cvZero = ClusterVariableEvaluator.evaluate(
                uFullZero, stage3.getCmat(), stage3.getLcv(), tcdis, lc);
        System.out.println("Point CVs at u=0:");
        for (int v = 0; v < nv; v++) {
            System.out.printf("  CV[%d] = %.10f%n", v, cvZero[pt][0][v]);
        }
    }
}
