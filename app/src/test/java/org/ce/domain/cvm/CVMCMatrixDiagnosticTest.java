package org.ce.domain.cvm;

import org.ce.domain.identification.cluster.CFIdentificationResult;
import org.ce.domain.identification.cluster.ClusterIdentificationResult;
import org.ce.domain.model.data.AllClusterData;
import org.ce.infrastructure.persistence.AllClusterDataCache;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Diagnostic / print test for CVM C-matrix debugging.
 *
 * <p>All tests always pass — they print concrete intermediate values at each
 * pipeline stage so you can inspect whether numbers are physically correct.
 * No assertions fail; everything is sent to System.out for manual inspection.</p>
 *
 * <h2>Run with:</h2>
 * <pre>
 *   ./gradlew test --tests CVMCMatrixDiagnosticTest --info 2>&1 | grep -v "^[[:space:]]*at "
 * </pre>
 *
 * <h2>Stages printed</h2>
 * <ol>
 *   <li>Stage 1 — ClusterIdentificationResult: tcdis, lc, multiplicities, kbCoeff, mh</li>
 *   <li>Stage 2 — CFIdentificationResult: ncf, tcf, lcf, cfBasisIndices layout</li>
 *   <li>Stage 3 — CMatrixResult: lcv, wcv, cmat entries (first few), cfBasisIndices</li>
 *   <li>CV values at random state (x=0.5 and x=0.25)</li>
 *   <li>CV values at equilibrium after NR convergence (x=0.5, T=1000, Nb-Ti ECI)</li>
 *   <li>Free energy components (G, H, S, gradient) at several (T, x) points</li>
 *   <li>NR solver trace: convergence path for a reference case</li>
 * </ol>
 */
class CVMCMatrixDiagnosticTest {

    private static AllClusterData clusterData;
    private static ClusterIdentificationResult stage1;
    private static CFIdentificationResult stage2;
    private static CMatrixResult stage3;

    // Nb-Ti ECI as stored in cec.json: [tet=0, tri=0, pair1=-390, pair2=-260, point=0, empty=0]
    // Correct CVM ECI takes first ncf values: [0, 0, -390, -260]
    private static final double[] ECI_NBTI = {0.0, 0.0, -390.0, -260.0};

    private static final double SOLVER_TOL = 1.0e-8;

    @BeforeAll
    static void load() throws Exception {
        Optional<AllClusterData> opt = AllClusterDataCache.load("BCC_A2_T_bin");
        assertTrue(opt.isPresent(), "BCC_A2_T_bin cluster cache must be present");
        clusterData = opt.get();
        assertTrue(clusterData.isComplete());
        stage1 = clusterData.getStage1();
        stage2 = clusterData.getStage2();
        stage3 = clusterData.getStage3();
    }

    // =========================================================================
    // Stage 1 — ClusterIdentificationResult
    // =========================================================================

    @Test
    void print_stage1_clusterIdentificationResult() {
        banner("STAGE 1 — ClusterIdentificationResult");

        int tcdis = stage1.getTcdis();
        int[] lc = stage1.getLc();
        List<Double> msdis = stage1.getDisClusterData().getMultiplicities();
        double[] kb = stage1.getKbCoefficients();
        double[][] mh = stage1.getMh();

        System.out.printf("  tcdis = %d   (number of HSP cluster types incl. point)%n", tcdis);
        System.out.printf("  tc    = %d   (total ordered clusters in dis phase)%n",
                stage1.getDisClusterData().getTc());
        System.out.println();

        System.out.println("  HSP cluster types (index 0..tcdis-1):");
        System.out.printf("    %-6s  %-10s  %-12s  %-30s%n",
                "type t", "lc[t]", "msdis[t]", "kbCoeff[t]");
        System.out.printf("    %-6s  %-10s  %-12s  %-30s%n",
                "------", "-----", "--------", "----------");
        for (int t = 0; t < tcdis; t++) {
            System.out.printf("    %-6d  %-10d  %-12.4f  %-30.8f%n",
                    t, lc[t], msdis.get(t), kb[t]);
        }
        System.out.println();

        System.out.println("  mh[t][j]  (normalised multiplicities per ordered-phase group):");
        for (int t = 0; t < tcdis; t++) {
            System.out.printf("    t=%d: ", t);
            for (int j = 0; j < lc[t]; j++) {
                System.out.printf("mh[%d][%d]=%.6f  ", t, j, mh[t][j]);
            }
            System.out.println();
        }
        System.out.println();
        System.out.println("  NOTE: kbCoeff signs should follow inclusion-exclusion.");
        System.out.println("        For BCC_A2_T binary: expect alternating signs.");
    }

    // =========================================================================
    // Stage 2 — CFIdentificationResult
    // =========================================================================

    @Test
    void print_stage2_cfIdentificationResult() {
        banner("STAGE 2 — CFIdentificationResult");

        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();
        int[][] lcf = stage2.getLcf();
        int tcdis = stage1.getTcdis();
        int[] lc = stage1.getLc();

        System.out.printf("  ncf = %d   (non-point CFs — optimisation variables)%n", ncf);
        System.out.printf("  tcf = %d   (total CFs including point CFs)%n", tcf);
        System.out.printf("  nxcf = %d  (point CFs = tcf - ncf, one per K-1 component)%n",
                tcf - ncf);
        System.out.println();

        System.out.println("  CFs per cluster type (lcf[t][j]):");
        System.out.printf("    %-6s  %-6s  %-6s%n", "type t", "group j", "lcf[t][j]");
        System.out.printf("    %-6s  %-6s  %-6s%n", "------", "-------", "---------");
        int colSum = 0;
        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                System.out.printf("    %-6d  %-6d  %-6d%n", t, j, lcf[t][j]);
                colSum += lcf[t][j];
            }
        }
        System.out.printf("  Total lcf sum = %d  (should equal tcf=%d)%n", colSum, tcf);
        System.out.println();

        System.out.println("  cfBasisIndices (one row per CF column, from Stage 3):");
        int[][] cfBasisIndices = stage3.getCfBasisIndices();
        System.out.printf("    %-6s  %-12s  %-40s%n", "col", "type", "basisIndices[]");
        System.out.printf("    %-6s  %-12s  %-40s%n", "---", "----", "--------------");
        for (int col = 0; col < tcf; col++) {
            String type = col < ncf ? "non-point" : "POINT";
            System.out.printf("    %-6d  %-12s  %s%n",
                    col, type, Arrays.toString(cfBasisIndices[col]));
        }
        System.out.println();
        System.out.println("  NOTE: Point CF columns should each have a single basisIndex.");
        System.out.println("        Non-point (multi-site) CF columns have ≥1 basisIndices.");
    }

    // =========================================================================
    // Stage 3 — CMatrixResult: structure and selected entries
    // =========================================================================

    @Test
    void print_stage3_cmatrixStructure() {
        banner("STAGE 3 — CMatrixResult: structure");

        int tcdis = stage1.getTcdis();
        int[] lc = stage1.getLc();
        int[][] lcv = stage3.getLcv();
        List<List<int[]>> wcv = stage3.getWcv();
        int tcf = stage2.getTcf();

        int totalCVs = 0;
        System.out.println("  CV structure (lcv[t][j] = number of distinct cluster configurations):");
        System.out.printf("    %-6s  %-6s  %-6s  %-20s%n", "type t", "group j", "lcv", "wcv[v] (weights)");
        System.out.printf("    %-6s  %-6s  %-6s  %-20s%n", "------", "-------", "---", "----------------");
        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                int nv = lcv[t][j];
                totalCVs += nv;
                int[] w = wcv.get(t).get(j);
                System.out.printf("    %-6d  %-6d  %-6d  %s%n",
                        t, j, nv, Arrays.toString(w));
            }
        }
        System.out.printf("  Total CVs across all (t,j): %d%n", totalCVs);
        System.out.println();

        // Verify wcv sums = K^numSites for each group
        System.out.println("  wcv weight sums per group (should = K^numSites for K=2):");
        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                int[] w = wcv.get(t).get(j);
                int sum = 0;
                for (int wv : w) sum += wv;
                System.out.printf("    (t=%d,j=%d): Σwcv = %d%n", t, j, sum);
            }
        }
        System.out.println();
        System.out.println("  NOTE: For binary K=2, Σwcv should be 2^(body count of cluster).");
        System.out.printf("        Cluster width = tcf+1 = %d columns in cmat.%n", tcf + 1);
    }

    @Test
    void print_stage3_cmatrixEntries_perGroup() {
        banner("STAGE 3 — CMatrixResult: full C-matrix entries per (t,j)");

        int tcdis = stage1.getTcdis();
        int[] lc = stage1.getLc();
        int[][] lcv = stage3.getLcv();
        List<List<double[][]>> cmat = stage3.getCmat();
        List<List<int[]>> wcv = stage3.getWcv();
        int tcf = stage2.getTcf();
        int ncf = stage2.getNcf();

        // Header line for columns
        System.out.print("  col meaning:  ");
        for (int k = 0; k < ncf; k++) {
            System.out.printf("CF%-2d  ", k);
        }
        for (int k = ncf; k < tcf; k++) {
            System.out.printf("pt%-2d  ", k - ncf);
        }
        System.out.println("CONST  | wcv");
        System.out.println();

        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                System.out.printf("  === Cluster type t=%d, group j=%d  (lcv=%d) ===%n",
                        t, j, lcv[t][j]);
                double[][] cm = cmat.get(t).get(j);
                int[] w = wcv.get(t).get(j);
                int nv = lcv[t][j];
                for (int v = 0; v < nv; v++) {
                    System.out.printf("    CV[%d]:  ", v);
                    for (int k = 0; k <= tcf; k++) {
                        System.out.printf("%7.4f  ", cm[v][k]);
                    }
                    System.out.printf("| w=%d%n", w[v]);
                }
                System.out.println();
            }
        }

        System.out.println("  FORMAT: each row = [CF0 CF1 ... CFncf-1 | point_CF0 ... | CONST] | weight");
        System.out.println("  CV[v] = Σ_k cmat[v][k] * uFull[k] + cmat[v][tcf]");
        System.out.println();
        System.out.println("  EXPECTED for binary K=2 pair cluster (if present):");
        System.out.println("    CV_AA: coeff for pair CF = +1/2, coeff for point CF = +1/2, const=+1/4  → w=1");
        System.out.println("    CV_AB: coeff for pair CF = -1/2, coeff for point CF =  0,  const=+1/2  → w=2");
        System.out.println("    CV_BB: coeff for pair CF = +1/2, coeff for point CF = -1/2, const=+1/4 → w=1");
    }

    // =========================================================================
    // Stage 4 — CV values at random state
    // =========================================================================

    @Test
    void print_cvValues_atRandomState_equimolar() {
        banner("STAGE 4 — CV values at disordered (random) state: x_B = 0.5");

        double xB = 0.5;
        double[] moleFractions = {1.0 - xB, xB};
        printCVAtComposition(moleFractions, 2, "x_B=0.5 (equimolar)");

        System.out.println("  EXPECTED for binary K=2 at x=0.5 (equimolar):");
        System.out.println("    Point CF σ¹ = 2·x_B - 1 = 0.0");
        System.out.println("    All non-point CFs at random state = 0.0");
        System.out.println("    All CVs = 1/(K^numSites) = 0.25 for pairs, 0.0625 for tetrahedra");
        System.out.println("    Normalization: Σ_v wcv[v]·CV[v] = 1 for each (t,j)");
    }

    @Test
    void print_cvValues_atRandomState_offEquimolar() {
        banner("STAGE 4 — CV values at disordered state: x_B = 0.3");

        double xB = 0.3;
        double[] moleFractions = {1.0 - xB, xB};
        printCVAtComposition(moleFractions, 2, "x_B=0.3");

        System.out.println("  EXPECTED for binary K=2 at x=0.3:");
        System.out.println("    Point CF σ¹ = 2·0.3 - 1 = -0.4");
        System.out.println("    Pair random CFs = (σ¹)^2 = 0.16 (for 2-site CF with basis [1,1])");
        System.out.println("    Pair CVs: CV_AA = x_A^2 = 0.49, CV_BB = x_B^2 = 0.09, CV_AB = 2·x_A·x_B = 0.42");
    }

    private void printCVAtComposition(double[] moleFractions, int K, String label) {
        int ncf = stage2.getNcf();
        int tcf = stage2.getTcf();
        int[][] cfBasisIndices = stage3.getCfBasisIndices();
        int tcdis = stage1.getTcdis();
        int[] lc = stage1.getLc();

        double[] uRandom = ClusterVariableEvaluator.computeRandomCFs(
                moleFractions, K, cfBasisIndices, ncf, tcf);
        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                uRandom, moleFractions, K, cfBasisIndices, ncf, tcf);

        System.out.printf("  Composition: %s → moleFractions=%s%n",
                label, Arrays.toString(moleFractions));
        System.out.printf("  uRandom (ncf=%d non-point CFs): %s%n",
                ncf, formatDoubles(uRandom));
        System.out.printf("  uFull   (tcf=%d total CFs):     %s%n",
                tcf, formatDoubles(uFull));
        System.out.println();

        double[][][] cv = ClusterVariableEvaluator.evaluate(
                uFull, stage3.getCmat(), stage3.getLcv(), tcdis, lc);

        List<List<int[]>> wcv = stage3.getWcv();
        int[][] lcv = stage3.getLcv();

        System.out.println("  Cluster variables CV[t][j][v] and normalization check:");
        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                int nv = lcv[t][j];
                int[] w = wcv.get(t).get(j);
                double normSum = 0.0;
                System.out.printf("    (t=%d,j=%d) %d CVs: ", t, j, nv);
                for (int v = 0; v < nv; v++) {
                    System.out.printf("CV[%d]=%.6f(w=%d)  ", v, cv[t][j][v], w[v]);
                    normSum += w[v] * cv[t][j][v];
                }
                System.out.printf("  ΣwCV=%.8f %s%n",
                        normSum, Math.abs(normSum - 1.0) < 1e-10 ? "✓" : "✗ WRONG");
            }
        }
        System.out.println();
    }

    // =========================================================================
    // Stage 5 — Free energy components at specific (T, x) points
    // =========================================================================

    @Test
    void print_freeEnergy_atSeveralPoints() {
        banner("STAGE 5 — Free energy G, H, S at various (T, x)");

        double[][] cases = {
                {1000.0, 0.5},
                {1000.0, 0.3},
                {500.0,  0.5},
                {1500.0, 0.5},
                {300.0,  0.5},
        };

        int ncf = stage2.getNcf();
        double[] eci = Arrays.copyOf(ECI_NBTI, ncf);

        System.out.printf("  Using Nb-Ti ECI: %s%n", Arrays.toString(eci));
        System.out.println();
        System.out.printf("  %-8s  %-6s  %-12s  %-12s  %-12s  %-12s  %-10s%n",
                "T(K)", "x_B", "G(J/mol)", "H(J/mol)", "S(J/mol·K)", "||∇G||", "converged");
        System.out.printf("  %-8s  %-6s  %-12s  %-12s  %-12s  %-12s  %-10s%n",
                "----", "---", "--------", "--------", "----------", "------", "---------");

        for (double[] tc : cases) {
            double T = tc[0];
            double xB = tc[1];
            CVMSolverResult r = solve(xB, T, eci);
            System.out.printf("  %-8.0f  %-6.2f  %-12.4f  %-12.4f  %-12.6f  %-12.3e  %-10s%n",
                    T, xB,
                    r.getGibbsEnergy(),
                    r.getEnthalpy(),
                    r.getEntropy(),
                    r.getGradientNorm(),
                    r.isConverged() ? "YES" : "NO");
        }
        System.out.println();
        System.out.println("  EXPECTED (physical sanity):");
        System.out.println("    G = H − T·S  (identity, always)");
        System.out.println("    H < 0 for Nb-Ti (attractive pair interactions)");
        System.out.println("    S > 0 always (configurational entropy)");
        System.out.println("    G decreases with increasing T (dG/dT = −S < 0)");
        System.out.println("    At T=1000, x=0.5: H_mix ≈ −T·ECI·CF·mult (rough: −390·mult·0.25)");
    }

    @Test
    void print_freeEnergy_zeroECI() {
        banner("STAGE 5 — Free energy with zero ECI (pure entropy check)");

        int ncf = stage2.getNcf();
        double[] zeroECI = new double[ncf];
        double T = 1000.0;

        System.out.println("  With ECI=0: H must be 0, G = −T·S, S must be > 0");
        System.out.println();
        System.out.printf("  %-6s  %-12s  %-12s  %-12s  %-20s%n",
                "x_B", "G(J/mol)", "H(J/mol)", "S(J/mol·K)", "−T·S check");
        System.out.printf("  %-6s  %-12s  %-12s  %-12s  %-20s%n",
                "---", "--------", "--------", "----------", "----------");

        for (double xB : new double[]{0.1, 0.2, 0.3, 0.4, 0.5}) {
            CVMSolverResult r = solve(xB, T, zeroECI);
            if (!r.isConverged()) {
                System.out.printf("  %-6.2f  NOT CONVERGED%n", xB);
                continue;
            }
            double negTS = -T * r.getEntropy();
            System.out.printf("  %-6.2f  %-12.4f  %-12.6f  %-12.6f  negTS=%-12.4f  %s%n",
                    xB,
                    r.getGibbsEnergy(),
                    r.getEnthalpy(),
                    r.getEntropy(),
                    negTS,
                    Math.abs(r.getGibbsEnergy() - negTS) < 1e-8 ? "G=−TS ✓" : "G≠−TS ✗");
        }
        System.out.println();
        System.out.println("  Reference: CVM entropy at x=0.5 for ideal binary ≈ R·ln(2) = "
                + String.format("%.6f", CVMFreeEnergy.R_GAS * Math.log(2)) + " J/(mol·K)");
        System.out.println("  (CVM entropy differs from ideal due to multi-body cluster correlations)");
    }

    // =========================================================================
    // Stage 6 — Gradient vector inspection
    // =========================================================================

    @Test
    void print_gradientVector_atEquilibrium() {
        banner("STAGE 6 — Gradient vector ∂G/∂u at equilibrium");

        int ncf = stage2.getNcf();
        double[] eci = Arrays.copyOf(ECI_NBTI, ncf);
        double T = 1000.0;
        double xB = 0.5;

        CVMSolverResult r = solve(xB, T, eci);

        System.out.printf("  T=%.0f K, x_B=%.2f, Nb-Ti ECI=%s%n", T, xB, Arrays.toString(eci));
        System.out.printf("  Converged: %s, iterations: %d%n", r.isConverged(), r.getIterations());
        System.out.println();

        double[] uEq = r.getEquilibriumCFs();
        System.out.printf("  Equilibrium u (ncf=%d non-point CFs): %s%n",
                ncf, formatDoubles(uEq));
        System.out.println();

        // Re-evaluate free energy at the equilibrium point to get gradient
        double[] moleFractions = {1.0 - xB, xB};
        int tcdis = stage1.getTcdis();
        int tcf = stage2.getTcf();
        int[][] cfBasisIndices = stage3.getCfBasisIndices();
        int[][] lcf = stage2.getLcf();

        CVMFreeEnergy.EvalResult fe = CVMFreeEnergy.evaluate(
                uEq,
                moleFractions,
                2,
                T,
                eci,
                stage1.getDisClusterData().getMultiplicities(),
                stage1.getKbCoefficients(),
                stage1.getMh(),
                stage1.getLc(),
                stage3.getCmat(),
                stage3.getLcv(),
                stage3.getWcv(),
                tcdis,
                tcf,
                ncf,
                lcf,
                cfBasisIndices);

        System.out.printf("  G = %.8f J/mol%n", fe.G);
        System.out.printf("  H = %.8f J/mol%n", fe.H);
        System.out.printf("  S = %.8f J/(mol·K)%n", fe.S);
        System.out.println();

        System.out.println("  Gradient ∂G/∂u[l] at equilibrium (all should be ≈ 0):");
        double gradNorm = 0.0;
        for (int l = 0; l < ncf; l++) {
            System.out.printf("    ∂G/∂u[%d] = %+.6e%n", l, fe.Gcu[l]);
            gradNorm += fe.Gcu[l] * fe.Gcu[l];
        }
        System.out.printf("  ||∇G|| = %.6e  (should be < %.2e)%n",
                Math.sqrt(gradNorm), SOLVER_TOL);
        System.out.println();

        System.out.println("  Hessian diagonal ∂²G/∂u[l]² (positive definite → stable minimum):");
        for (int l = 0; l < ncf; l++) {
            System.out.printf("    Gcuu[%d][%d] = %+.6e%n", l, l, fe.Gcuu[l][l]);
        }
    }

    // =========================================================================
    // Stage 7 — CV values at equilibrium vs. random state comparison
    // =========================================================================

    @Test
    void print_cvValues_randomVsEquilibrium() {
        banner("STAGE 7 — CV values: random state vs. equilibrium (T=1000, x=0.5, Nb-Ti)");

        int ncf = stage2.getNcf();
        double[] eci = Arrays.copyOf(ECI_NBTI, ncf);
        double T = 1000.0;
        double xB = 0.5;
        double[] moleFractions = {1.0 - xB, xB};

        int tcdis = stage1.getTcdis();
        int[] lc = stage1.getLc();
        int tcf = stage2.getTcf();
        int[][] cfBasisIndices = stage3.getCfBasisIndices();
        int[][] lcv = stage3.getLcv();
        List<List<int[]>> wcv = stage3.getWcv();

        // Random state CVs
        double[] uRandom = ClusterVariableEvaluator.computeRandomCFs(
                moleFractions, 2, cfBasisIndices, ncf, tcf);
        double[] uFullRandom = ClusterVariableEvaluator.buildFullCFVector(
                uRandom, moleFractions, 2, cfBasisIndices, ncf, tcf);
        double[][][] cvRandom = ClusterVariableEvaluator.evaluate(
                uFullRandom, stage3.getCmat(), lcv, tcdis, lc);

        // Equilibrium CVs after NR
        CVMSolverResult r = solve(xB, T, eci);
        double[] uEq = r.getEquilibriumCFs();
        double[] uFullEq = ClusterVariableEvaluator.buildFullCFVector(
                uEq, moleFractions, 2, cfBasisIndices, ncf, tcf);
        double[][][] cvEq = ClusterVariableEvaluator.evaluate(
                uFullEq, stage3.getCmat(), lcv, tcdis, lc);

        System.out.printf("  T=%.0f K, x_B=%.2f, converged: %s%n", T, xB, r.isConverged());
        System.out.printf("  u_random:      %s%n", formatDoubles(uRandom));
        System.out.printf("  u_equilibrium: %s%n", formatDoubles(uEq));
        System.out.println();

        System.out.printf("  %-14s  %-14s  %-8s  %-14s  %-14s  %-8s%n",
                "CV[t][j][v]", "CV_random", "w", "CV_equil", "Δ(eq-rand)", "w");
        System.out.printf("  %-14s  %-14s  %-8s  %-14s  %-14s  %-8s%n",
                "-----------", "---------", "-", "--------", "----------", "-");

        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                int nv = lcv[t][j];
                int[] w = wcv.get(t).get(j);
                for (int v = 0; v < nv; v++) {
                    double rand = cvRandom[t][j][v];
                    double eq = cvEq[t][j][v];
                    System.out.printf("  CV[%d][%d][%d]     %-14.6f  %-8d  %-14.6f  %-14.6f  %d%n",
                            t, j, v, rand, w[v], eq, eq - rand, w[v]);
                }
            }
        }
        System.out.println();
        System.out.println("  EXPECTED: at equilibrium, ordering interactions cause unlike-pair");
        System.out.println("  CVs to increase and like-pair CVs to decrease vs. random state.");
        System.out.println("  (Negative Nb-Ti pair ECIs → ordering tendency → more AB pairs)");
    }

    // =========================================================================
    // Stage 8 — Enthalpy decomposition per cluster type
    // =========================================================================

    @Test
    void print_enthalpy_perClusterType() {
        banner("STAGE 8 — Enthalpy decomposition H per cluster type");

        int ncf = stage2.getNcf();
        double[] eci = Arrays.copyOf(ECI_NBTI, ncf);
        double T = 1000.0;
        double xB = 0.5;
        double[] moleFractions = {1.0 - xB, xB};

        CVMSolverResult r = solve(xB, T, eci);
        double[] uEq = r.getEquilibriumCFs();

        List<Double> msdis = stage1.getDisClusterData().getMultiplicities();
        int tcdis = stage1.getTcdis();
        int[][] lcf = stage2.getLcf();
        int[] lc = stage1.getLc();

        System.out.println("  H = Σ_t msdis[t] · Σ_l ECI[l] · u[l]  (per cluster type)");
        System.out.println();
        System.out.printf("  %-6s  %-10s  %-30s  %-20s  %-12s%n",
                "type t", "msdis[t]", "CFs in type (col range)", "ECI[l] · u[l]", "H contrib");
        System.out.printf("  %-6s  %-10s  %-30s  %-20s  %-12s%n",
                "------", "--------", "----------------------", "-------------", "---------");

        int cfOffset = 0;
        double Htotal = 0.0;
        for (int t = 0; t < tcdis - 1; t++) { // skip point type (last)
            int nCFsForType = 0;
            for (int j = 0; j < lc[t]; j++) nCFsForType += lcf[t][j];

            double mhd = msdis.get(t);
            StringBuilder cfDetail = new StringBuilder();
            double Htype = 0.0;
            for (int i = 0; i < nCFsForType; i++) {
                int l = cfOffset + i;
                double contrib = mhd * eci[l] * uEq[l];
                Htype += contrib;
                cfDetail.append(String.format("u[%d]=%.4f·ECI=%.1f→%.4f  ", l, uEq[l], eci[l], contrib));
            }
            Htotal += Htype;
            System.out.printf("  %-6d  %-10.2f  %-30s  %-20s  %-12.4f%n",
                    t, mhd,
                    "cols " + cfOffset + ".." + (cfOffset + nCFsForType - 1),
                    cfDetail.toString(),
                    Htype);
            cfOffset += nCFsForType;
        }
        System.out.printf("  TOTAL H = %.6f J/mol%n", Htotal);
        System.out.printf("  Solver H = %.6f J/mol  %s%n",
                r.getEnthalpy(),
                Math.abs(Htotal - r.getEnthalpy()) < 1e-6 ? "✓ matches" : "✗ MISMATCH");
        System.out.println();
        System.out.println("  EXPECTED: for Nb-Ti ECI=[0,0,-390,-260]:");
        System.out.println("    t=0 (tet, ECI=0):  H_tet = 0");
        System.out.println("    t=1 (tri, ECI=0):  H_tri = 0");
        System.out.println("    t=2 (1nn pair, ECI=-390): H_pair1 = msdis[2]·(-390)·u_pair1");
        System.out.println("    t=3 (2nn pair, ECI=-260): H_pair2 = msdis[3]·(-260)·u_pair2");
    }

    // =========================================================================
    // Stage 9 — Full N-R trace: CFs and CVs at every iteration
    // =========================================================================

    @Test
    void print_nrTrace_cfsAndCvs_perIteration_equimolar_nbTi() {
        banner("STAGE 9 — N-R trace: CFs and CVs at every iteration  (T=1000, x=0.5, Nb-Ti)");
        printNRTrace(0.5, 1000.0, Arrays.copyOf(ECI_NBTI, stage2.getNcf()));
    }

    @Test
    void print_nrTrace_cfsAndCvs_perIteration_offEquimolar_nbTi() {
        banner("STAGE 9b — N-R trace: CFs and CVs at every iteration  (T=1000, x=0.3, Nb-Ti)");
        printNRTrace(0.3, 1000.0, Arrays.copyOf(ECI_NBTI, stage2.getNcf()));
    }

    @Test
    void print_nrTrace_cfsAndCvs_perIteration_lowT_nbTi() {
        banner("STAGE 9c — N-R trace: CFs and CVs at every iteration  (T=300, x=0.5, Nb-Ti)");
        printNRTrace(0.5, 300.0, Arrays.copyOf(ECI_NBTI, stage2.getNcf()));
    }

    @Test
    void print_nrTrace_cfsAndCvs_perIteration_zeroECI() {
        banner("STAGE 9d — N-R trace: CFs and CVs at every iteration  (T=1000, x=0.5, ECI=0)");
        printNRTrace(0.5, 1000.0, new double[stage2.getNcf()]);
    }

    private void printNRTrace(double xB, double T, double[] eci) {
        int ncf  = stage2.getNcf();
        int tcf  = stage2.getTcf();
        int tcdis = stage1.getTcdis();
        int[] lc  = stage1.getLc();
        int[][] lcv          = stage3.getLcv();
        int[][] cfBasisIndices = stage3.getCfBasisIndices();
        List<List<int[]>> wcv = stage3.getWcv();
        double[] moleFractions = {1.0 - xB, xB};

        CVMSolverResult result = solve(xB, T, eci);
        List<CVMSolverResult.IterationSnapshot> trace = result.getIterationTrace();

        System.out.printf("  T=%.0f K  x_B=%.2f  ECI=%s%n", T, xB, Arrays.toString(eci));
        System.out.printf("  Converged: %s  total iterations: %d  final ||∇G||=%.3e%n",
                result.isConverged(), result.getIterations(), result.getGradientNorm());

        // Pre-compute uFull and CVs for every iteration (reused in both tables)
        List<double[]>   uFullPerIter = new ArrayList<>();
        List<double[][][]> cvArrPerIter = new ArrayList<>();
        for (CVMSolverResult.IterationSnapshot snap : trace) {
            double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                    snap.getCf(), moleFractions, 2, cfBasisIndices, ncf, tcf);
            uFullPerIter.add(uFull);
            cvArrPerIter.add(ClusterVariableEvaluator.evaluate(
                    uFull, stage3.getCmat(), lcv, tcdis, lc));
        }

        // ── TABLE 1: full CF vector (uFull[0..tcf-1]) at every iteration ──
        System.out.println();
        System.out.println("  ── Full CF vector uFull[k] at each N-R iteration ──");
        System.out.println("     (cols 0..ncf-1 = optimisation variables; cols ncf..tcf-1 = point CFs, composition-fixed)");
        System.out.println();

        // Header row
        System.out.printf("  %-6s │", "iter");
        for (int k = 0; k < tcf; k++) {
            String label = k < ncf ? String.format("uFull[%d]", k) : String.format("pt[%d]", k - ncf);
            System.out.printf(" %-12s│", label);
        }
        System.out.println();

        String cfSep = "  " + "─".repeat(6) + "┼" + ("─".repeat(13) + "┼").repeat(tcf);
        System.out.println(cfSep);

        for (int i = 0; i < trace.size(); i++) {
            CVMSolverResult.IterationSnapshot snap = trace.get(i);
            double[] uFull = uFullPerIter.get(i);
            System.out.printf("  %6d │", snap.getIteration());
            for (int k = 0; k < tcf; k++) {
                System.out.printf(" %+11.6f │", uFull[k]);
            }
            System.out.println();
        }
        System.out.println(cfSep);

        // ── TABLE 2: gradient dG/du[l] (ncf entries) at every iteration ──
        System.out.println();
        System.out.println("  ── Gradient dG/du[l] at each N-R iteration ──");
        System.out.println();

        System.out.printf("  %-6s │", "iter");
        for (int l = 0; l < ncf; l++) System.out.printf(" %-12s│", "dG/du[" + l + "]");
        System.out.printf(" %-12s│%n", "||∇G||");

        String gradSep = "  " + "─".repeat(6) + "┼" + ("─".repeat(13) + "┼").repeat(ncf + 1);
        System.out.println(gradSep);

        for (CVMSolverResult.IterationSnapshot snap : trace) {
            System.out.printf("  %6d │", snap.getIteration());
            for (double g : snap.getDGdu()) System.out.printf(" %+11.4e │", g);
            System.out.printf(" %+11.4e │%n", snap.getGradientNorm());
        }
        System.out.println(gradSep);

        // ── TABLE 3: G, H, S at every iteration ──
        System.out.println();
        System.out.println("  ── Thermodynamic values at each N-R iteration ──");
        System.out.println();

        System.out.printf("  %-6s │ %-14s │ %-14s │ %-14s │%n",
                "iter", "G (J/mol)", "H (J/mol)", "S (J/mol·K)");
        String thSep = "  " + "─".repeat(6) + "┼" + ("─".repeat(16) + "┼").repeat(3);
        System.out.println(thSep);
        for (CVMSolverResult.IterationSnapshot snap : trace) {
            System.out.printf("  %6d │ %+14.6f │ %+14.6f │ %+14.8f │%n",
                    snap.getIteration(),
                    snap.getGibbsEnergy(), snap.getEnthalpy(), snap.getEntropy());
        }
        System.out.println(thSep);

        // ── TABLE 4: all CVs at every iteration (one row per CV) ──
        System.out.println();
        System.out.println("  ── Cluster Variables CV[t][j][v] at each N-R iteration ──");
        System.out.println();

        // Build flat CV label/weight lists
        List<String> cvLabels  = new ArrayList<>();
        List<Integer> cvW      = new ArrayList<>();
        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                int[] w = wcv.get(t).get(j);
                for (int v = 0; v < lcv[t][j]; v++) {
                    cvLabels.add(String.format("CV[%d][%d][%d]", t, j, v));
                    cvW.add(w[v]);
                }
            }
        }
        int totalCVs = cvLabels.size();

        // Flatten per-iteration CV arrays
        List<double[]> cvFlatPerIter = new ArrayList<>();
        for (double[][][] cvArr : cvArrPerIter) {
            double[] flat = new double[totalCVs];
            int idx = 0;
            for (int t = 0; t < tcdis; t++)
                for (int j = 0; j < lc[t]; j++)
                    for (int v = 0; v < lcv[t][j]; v++)
                        flat[idx++] = cvArr[t][j][v];
            cvFlatPerIter.add(flat);
        }

        // Header
        System.out.printf("  %-14s │ w │", "CV");
        for (CVMSolverResult.IterationSnapshot snap : trace)
            System.out.printf(" iter%-2d     │", snap.getIteration());
        System.out.println();

        String cvSep = "  " + "─".repeat(14) + "┼───┼"
                + ("─".repeat(12) + "┼").repeat(trace.size());
        System.out.println(cvSep);

        for (int ci = 0; ci < totalCVs; ci++) {
            System.out.printf("  %-14s │%2d │", cvLabels.get(ci), cvW.get(ci));
            for (double[] flat : cvFlatPerIter)
                System.out.printf(" %10.6f │", flat[ci]);
            System.out.println();
        }
        System.out.println(cvSep);

        // ── Normalization Σ(w·CV) = 1 check at final iteration ──
        System.out.println();
        System.out.println("  ── Normalization Σ(w·CV) per cluster group at final iteration ──");
        double[][][] cvFinal = cvArrPerIter.get(cvArrPerIter.size() - 1);
        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                int[] w = wcv.get(t).get(j);
                double normSum = 0.0;
                for (int v = 0; v < lcv[t][j]; v++) normSum += w[v] * cvFinal[t][j][v];
                System.out.printf("    (t=%d,j=%d): Σ(w·CV) = %.10f  %s%n",
                        t, j, normSum,
                        Math.abs(normSum - 1.0) < 1e-10 ? "✓" : "✗ WRONG");
            }
        }
        System.out.println();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private CVMSolverResult solve(double xB, double T, double[] eci) {
        return NewtonRaphsonSolverSimple.solve(
                new double[]{1.0 - xB, xB},
                2,
                T,
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
                SOLVER_TOL);
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("  " + title);
        System.out.println("=".repeat(70));
    }

    private static String formatDoubles(double[] arr) {
        if (arr == null) return "null";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.6f", arr[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}
