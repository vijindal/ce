package org.ce.cvm;

import java.util.List;

/**
 * Newton-Raphson solver for CVM free-energy minimisation.
 *
 * <p>Minimises {@code G(u) = H(u) − T·S(u)} over the non-point correlation
 * functions {@code u[0..ncf-1]} by iterating:</p>
 * <pre>
 *   δu = LinearSolve(Gcuu, −Gcu)
 *   u  ← u + step · δu
 * </pre>
 * until {@code ||Gcu|| < tolerance}.
 *
 * <p>Corresponds to Mathematica Section 10 "Internal Equilibrium (Gibbs Energy
 * Minimization)" — the manual Newton-Raphson method with step damping.</p>
 *
 * @see CVMFreeEnergy
 * @see LinearAlgebraUtils
 */
public final class NewtonRaphsonSolver {

    /** Default step damping factor (matches Mathematica {@code stp = 0.99}). */
    public static final double DEFAULT_STEP = 0.99;

    /** Default maximum iterations. */
    public static final int DEFAULT_MAX_ITER = 200;

    /** Default convergence tolerance on gradient norm. */
    public static final double DEFAULT_TOLERANCE = 1.0e-10;

    private NewtonRaphsonSolver() { /* utility class */ }

    /**
     * Solves the CVM equilibrium problem for a K-component system.
     *
     * @param moleFractions mole fractions of all K components (length K, Σ = 1)
     * @param numElements   number of chemical components K (≥ 2)
     * @param temperature   temperature in Kelvin
     * @param eci           effective cluster interactions (length ncf)
     * @param mhdis         HSP cluster multiplicities (size tcdis)
     * @param kb            Kikuchi-Baker entropy coefficients (length tcdis)
     * @param mh            normalised multiplicities: mh[t][j]
     * @param lc            ordered clusters per HSP type: lc[t]
     * @param cmat          C-matrix data
     * @param lcv           CV counts: lcv[t][j]
     * @param wcv           CV weights
     * @param tcdis         number of HSP cluster types
     * @param tcf           total CFs
     * @param ncf           number of non-point CFs
     * @param lcf           CF count per (type, group): lcf[t][j]
     * @param cfBasisIndices per-CF basis-index decorations from CMatrixResult
     * @param maxIter       maximum NR iterations
     * @param tolerance     convergence tolerance on ||Gcu||
     * @param step          NR step damping factor (0 < step ≤ 1)
     * @return solver result
     */
    public static CVMSolverResult solve(
            double[] moleFractions,
            int numElements,
            double temperature,
            double[] eci,
            List<Double> mhdis,
            double[] kb,
            double[][] mh,
            int[] lc,
            List<List<double[][]>> cmat,
            int[][] lcv,
            List<List<int[]>> wcv,
            int tcdis,
            int tcf,
            int ncf,
            int[][] lcf,
            int[][] cfBasisIndices,
            int maxIter,
            double tolerance,
            double step) {

        // Initial guess: random-state CFs (Mathematica: uRandRules).
        // For each non-point CF, the random value = Π pointCF[basisIndex].
        // This ensures all CVs are positive from the start, which is
        // critical for K≥3 where u=0 maps to a pure-component boundary.
        double[] u = ClusterVariableEvaluator.computeRandomCFs(
                moleFractions, numElements, cfBasisIndices, ncf, tcf);

        System.out.println("\n[NR Solver] Starting Newton-Raphson minimization");
        System.out.println("  numElements=" + numElements + " temperature=" + temperature + " tolerance=" + tolerance);
        System.out.println("  moleFractions=" + java.util.Arrays.toString(moleFractions));
        System.out.println("  Problem dimension: ncf=" + ncf + " (optimization variables), tcf=" + tcf + " (total CFs)");
        System.out.println("    → CFs u[0.." + (ncf-1) + "] are OPTIMIZED");
        System.out.println("    → CFs u[" + ncf + ".." + (tcf-1) + "] are FIXED (point/empty CFs)");
        System.out.println("  maxIter=" + maxIter + " step=" + step);
        System.out.println("\nInitial random-state CFs:");
        double[] uInitial = ClusterVariableEvaluator.buildFullCFVector(u, moleFractions, numElements, cfBasisIndices, ncf, tcf);
        
        // Diagnostic: show cfBasisIndices for first few and last CFs
        if (numElements > 2) {  // Only for multi-component
            System.out.println("  cfBasisIndices (basis decorations):");
            for (int l = 0; l < Math.min(5, ncf); l++) {
                System.out.print("    CF[" + l + "]: [");
                for (int b : cfBasisIndices[l]) {
                    System.out.print(b + " ");
                }
                System.out.println("], u[" + l + "] = " + String.format("%.10e", u[l]));
            }
            if (ncf > 10) {
                for (int l = Math.max(5, ncf-3); l < ncf; l++) {
                    System.out.print("    CF[" + l + "]: [");
                    for (int b : cfBasisIndices[l]) {
                        System.out.print(b + " ");
                    }
                    System.out.println("], u[" + l + "] = " + String.format("%.10e", u[l]));
                }
            }
            System.out.println("  Point CFs:");
            for (int l = ncf; l < tcf; l++) {
                System.out.print("    CF[" + l + "]: [");
                for (int b : cfBasisIndices[l]) {
                    System.out.print(b + " ");
                }
                System.out.println("], value = " + String.format("%.10e", uInitial[l]));
            }
        }
        
        System.out.println("  Non-point (optim): [");
        for (int l = 0; l < ncf; l++) {
            System.out.printf("    u[%2d] = %.10e%n", l, u[l]);
        }
        System.out.println("  ]");
        System.out.println("  Point/Empty (fixed): [");
        for (int l = ncf; l < tcf; l++) {
            System.out.printf("    u[%2d] = %.10e%n", l, uInitial[l]);
        }
        System.out.println("  ]");
        System.out.println();

        double gradNorm = Double.MAX_VALUE;
        int iter = 0;
        double G = 0, H = 0, S = 0;

        for (iter = 1; iter <= maxIter; iter++) {
            // Evaluate free-energy, gradient, and Hessian
            CVMFreeEnergy.EvalResult eval = CVMFreeEnergy.evaluate(
                    u, moleFractions, numElements, temperature, eci,
                    mhdis, kb, mh, lc, cmat, lcv, wcv,
                    tcdis, tcf, ncf, lcf, cfBasisIndices);

            G = eval.G;
            H = eval.H;
            S = eval.S;

            // Check convergence
            gradNorm = norm(eval.Gcu);
            System.out.printf("[NR] Iteration %3d:  G=%.8e  H=%.8e  S=%.8e  ||Gcu||=%.8e", 
                    iter, G, H, S, gradNorm);
            
            // Print detail (gradient + CFs) only for first iteration and every 20 iterations
            if (iter == 1 || iter % 20 == 0 || gradNorm < 1e-6) {
                System.out.println();
                System.out.println("      Gradient Gcu[ncf=" + ncf + "]: [");
                for (int l = 0; l < ncf; l++) {
                    System.out.printf("        Gcu[%2d] = %.10e%n", l, eval.Gcu[l]);
                }
                System.out.println("      ]");
                
                // Print all CFs (non-point and point)
                double[] uFull = ClusterVariableEvaluator.buildFullCFVector(u, moleFractions, numElements, cfBasisIndices, ncf, tcf);
                System.out.println("      CFs[tcf=" + tcf + "]: [");
                for (int l = 0; l < tcf; l++) {
                    String label = l < ncf ? "OPTIM[" + l + "]" : "FIXED[" + (l - ncf) + "]";
                    System.out.printf("        u[%2d] (%s) = %.10e%n", l, label, uFull[l]);
                }
                System.out.println("      ]");
            } else {
                System.out.println();
            }
            
            if (gradNorm < tolerance) {
                System.out.println("  ✓ CONVERGED");
                return new CVMSolverResult(u, G, H, S, iter, gradNorm, true);
            }

            // Newton-Raphson step: δu = solve(Gcuu, -Gcu)
            double[] negGcu = new double[ncf];
            for (int l = 0; l < ncf; l++) {
                negGcu[l] = -eval.Gcu[l];
            }

            double[] deltaU;
            try {
                deltaU = LinearAlgebraUtils.solve(eval.Gcuu, negGcu);
            } catch (IllegalArgumentException e) {
                // Singular Hessian — cannot continue
                System.out.println("  ✗ SINGULAR HESSIAN");
                System.err.println("[NR] Singular Hessian at iteration " + iter
                        + " (||Gcu||=" + gradNorm + "): " + e.getMessage());
                return new CVMSolverResult(u, G, H, S, iter, gradNorm, false);
            }

            // --- Backtracking line search ---
            // Find the largest alpha in (0, step] such that all CVs remain
            // strictly positive after updating u ← u + alpha·δu.
            double alpha = Math.min(step, 1.0);
            alpha = findMaxSafeStep(u, deltaU, alpha, moleFractions, numElements,
                    cmat, lcv, tcdis, lc, ncf, tcf, cfBasisIndices);

            // Update CFs
            for (int l = 0; l < ncf; l++) {
                u[l] += alpha * deltaU[l];
            }
            
            System.out.printf("      Step: α=%.6f  ||δu||=%.8e%n%n", alpha, norm(deltaU));
        }

        // Did not converge within maxIter
        System.out.printf("[NR] NO CONVERGENCE after %d iterations (||Gcu||=%.8e, tolerance=%.8e)%n", 
                maxIter, gradNorm, tolerance);
        return new CVMSolverResult(u, G, H, S, maxIter, gradNorm, false);
    }

    /**
     * Convenience overload using default step, maxIter, and tolerance.
     */
    public static CVMSolverResult solve(
            double[] moleFractions,
            int numElements,
            double temperature,
            double[] eci,
            List<Double> mhdis,
            double[] kb,
            double[][] mh,
            int[] lc,
            List<List<double[][]>> cmat,
            int[][] lcv,
            List<List<int[]>> wcv,
            int tcdis,
            int tcf,
            int ncf,
            int[][] lcf,
            int[][] cfBasisIndices,
            double tolerance) {

        return solve(moleFractions, numElements, temperature, eci, mhdis, kb, mh, lc,
                cmat, lcv, wcv, tcdis, tcf, ncf, lcf, cfBasisIndices,
                DEFAULT_MAX_ITER, tolerance, DEFAULT_STEP);
    }

    /**
     * Binary convenience overload: accepts a single composition value.
     *
     * <p>Requires {@code cfBasisIndices} from CMatrixResult for computing
     * the random-state initial guess.</p>
     */
    public static CVMSolverResult solve(
            double composition,
            double temperature,
            double[] eci,
            List<Double> mhdis,
            double[] kb,
            double[][] mh,
            int[] lc,
            List<List<double[][]>> cmat,
            int[][] lcv,
            List<List<int[]>> wcv,
            int tcdis,
            int tcf,
            int ncf,
            int[][] cfBasisIndices,
            int maxIter,
            double tolerance,
            double step) {

        // Binary: each type has 1 group with 1 CF
        int[][] binaryLcf = new int[tcdis][];
        for (int t = 0; t < tcdis; t++) binaryLcf[t] = new int[]{1};

        return solve(new double[]{1.0 - composition, composition}, 2,
                temperature, eci, mhdis, kb, mh, lc,
                cmat, lcv, wcv, tcdis, tcf, ncf, binaryLcf, cfBasisIndices,
                maxIter, tolerance, step);
    }

    /**
     * Binary convenience overload with default parameters.
     */
    public static CVMSolverResult solve(
            double composition,
            double temperature,
            double[] eci,
            List<Double> mhdis,
            double[] kb,
            double[][] mh,
            int[] lc,
            List<List<double[][]>> cmat,
            int[][] lcv,
            List<List<int[]>> wcv,
            int tcdis,
            int tcf,
            int ncf,
            int[][] cfBasisIndices,
            double tolerance) {

        // Binary: each type has 1 group with 1 CF
        int[][] binaryLcf = new int[tcdis][];
        for (int t = 0; t < tcdis; t++) binaryLcf[t] = new int[]{1};

        return solve(new double[]{1.0 - composition, composition}, 2,
                temperature, eci, mhdis, kb, mh, lc,
                cmat, lcv, wcv, tcdis, tcf, ncf, binaryLcf, cfBasisIndices,
                DEFAULT_MAX_ITER, tolerance, DEFAULT_STEP);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /** Euclidean norm of a vector. */
    private static double norm(double[] v) {
        double sum = 0.0;
        for (double x : v) sum += x * x;
        return Math.sqrt(sum);
    }

    /**
     * Finds the largest safe step alpha such that all cluster variables
     * remain strictly positive when u is updated to u + alpha * deltaU.
     *
     * <p>Each CV is linear in u:
     * {@code cv_new = cv_old + alpha * Σ_k cmat[v][k] * delta_full[k]}
     * For each CV where the linear coefficient is negative, we compute
     * the maximum alpha that keeps cv_new > 0.</p>
     *
     * @param u         current CF values (length ncf)
     * @param deltaU    NR step (length ncf)
     * @param maxAlpha  maximum allowed alpha
     * @param composition mole fraction
     * @param cmat      C-matrix
     * @param lcv       CV counts
     * @param tcdis     HSP cluster types
     * @param lc        ordered clusters per HSP type
     * @param ncf       non-point CFs
     * @param tcf       total CFs
     * @return safe step size alpha in (0, maxAlpha]
     */
    private static double findMaxSafeStep(
            double[] u, double[] deltaU, double maxAlpha,
            double[] moleFractions, int numElements,
            List<List<double[][]>> cmat, int[][] lcv,
            int tcdis, int[] lc, int ncf, int tcf,
            int[][] cfBasisIndices) {

        // Safety margin: keep CVs at least this far from zero
        final double CV_MIN = 1.0e-12;
        // Minimum step we'll take regardless
        final double ALPHA_FLOOR = 1.0e-6;

        // Build full CF vectors for current and delta
        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(u, moleFractions, numElements, cfBasisIndices, ncf, tcf);
        double[] deltaFull = new double[tcf];
        System.arraycopy(deltaU, 0, deltaFull, 0, ncf);
        // Point CFs don't change

        double alpha = maxAlpha;

        for (int t = 0; t < tcdis; t++) {
            for (int j = 0; j < lc[t]; j++) {
                double[][] cm = cmat.get(t).get(j);
                int nv = lcv[t][j];
                for (int v = 0; v < nv; v++) {
                    // Current CV value
                    double cvOld = cm[v][tcf]; // constant
                    for (int k = 0; k < tcf; k++) {
                        cvOld += cm[v][k] * uFull[k];
                    }

                    // Change in CV per unit alpha
                    double dcv = 0.0;
                    for (int k = 0; k < ncf; k++) {
                        dcv += cm[v][k] * deltaFull[k];
                    }

                    // If dcv < 0, CV will decrease. Find max alpha keeping CV > CV_MIN.
                    // Only constrain if CV is currently positive — for already-
                    // negative CVs (K≥3 initial state), we let the solver move
                    // freely to escape the infeasible region.
                    if (cvOld > CV_MIN && dcv < -1.0e-30) {
                        double maxA = (cvOld - CV_MIN) / (-dcv);
                        if (maxA < alpha && maxA > ALPHA_FLOOR) {
                            alpha = maxA;
                        } else if (maxA <= ALPHA_FLOOR) {
                            alpha = ALPHA_FLOOR;
                        }
                    }
                }
            }
        }

        return Math.max(alpha, ALPHA_FLOOR);
    }
}
