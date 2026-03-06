package org.ce.domain.cvm;

import java.util.ArrayList;
import java.util.List;

/**
 * Newton-Raphson solver for CVM free-energy minimization.
 *
 * <p>Based on proven working implementation with simple 4-loop structure.
 * Minimizes G(u) = H(u) - TÂ·S(u) over non-point correlation functions.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>LU decomposition for solving linear system</li>
 *   <li>Step limiting to keep all CVs positive</li>
 *   <li>Random-state initial guess: u[icf] = (2xB - 1)^rank</li>
 * </ul>
 */
public final class NewtonRaphsonSolverSimple {

    /** Gas constant R = 1 (normalized units). */
    private static final double R = 1.0;

    /** Function tolerance for convergence. */
    private static final double TOLF = 1.0e-8;

    /** Step tolerance for convergence. */
    private static final double TOLX = 1.0e-12;

    /** Minimum CV value threshold. */
    private static final double CV_MIN = 1.0e-30;

    /** Maximum iterations. */
    private static final int MAX_ITER = 400;

    private NewtonRaphsonSolverSimple() { /* utility class */ }

    // =========================================================================
    // Data holder for all CVM parameters
    // =========================================================================

    /**
     * Holds all CVM data needed for the N-R solver.
     */
    public static final class CVMData {
        public final int tcdis;           // number of HSP cluster types
        public final int tcf;             // total number of CFs
        public final int ncf;             // non-point CFs (optimization variables)
        public final int[] lc;            // ordered clusters per HSP type
        public final double[] kb;         // Kikuchi-Baker coefficients
        public final List<Double> msdis;  // HSP multiplicities
        public final double[][] m;        // normalized multiplicities mh[t][j]
        public final int[][] lcv;         // CV counts per (type, group)
        public final List<List<int[]>> wcv;     // CV weights
        public final List<List<double[][]>> cmat; // C-matrix
        public final int[][] cfRank;      // CF ranks (from cfBasisIndices.length)
        public final double[] eci;        // effective cluster interactions
        public final double temperature;
        public final double xB;           // composition (mole fraction of B)
        public final double[] moleFractions;
        public final int numElements;
        public final int[][] lcf;
        public final int[][] cfBasisIndices;

        public CVMData(
                int tcdis, int tcf, int ncf, int[] lc,
                double[] kb, List<Double> msdis, double[][] m,
                int[][] lcv, List<List<int[]>> wcv, List<List<double[][]>> cmat,
                int[][] cfBasisIndices, int[][] lcf,
                double[] eci, double temperature, double xB,
                double[] moleFractions, int numElements) {
            this.tcdis = tcdis;
            this.tcf = tcf;
            this.ncf = ncf;
            this.lc = lc;
            this.kb = kb;
            this.msdis = msdis;
            this.m = m;
            this.lcv = lcv;
            this.wcv = wcv;
            this.cmat = cmat;
            this.eci = eci;
            this.temperature = temperature;
            this.xB = xB;
            this.moleFractions = moleFractions.clone();
            this.numElements = numElements;
            this.lcf = lcf;
            this.cfBasisIndices = cfBasisIndices;

            // Extract CF ranks from cfBasisIndices
            this.cfRank = new int[tcf][];
            for (int icf = 0; icf < tcf; icf++) {
                this.cfRank[icf] = new int[]{cfBasisIndices[icf].length};
            }
        }

        /** Get CF rank (number of sites in the cluster). */
        public int getRank(int icf) {
            return cfRank[icf][0];
        }
    }

    // =========================================================================
    // Main solver
    // =========================================================================

    /**
     * Solves the CVM equilibrium problem.
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

        // For binary: xB = moleFractions[1]
        double xB = moleFractions.length > 1 ? moleFractions[1] : moleFractions[0];

        CVMData data = new CVMData(tcdis, tcf, ncf, lc, kb, mhdis, mh,
                lcv, wcv, cmat, cfBasisIndices, lcf,
                eci, temperature, xB, moleFractions, numElements);

        return minimize(data, maxIter, tolerance);
    }

    /**
     * Convenience overload using default parameters.
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
                MAX_ITER, tolerance, 1.0);
    }

    // =========================================================================
    // N-R minimization (from proven working code)
    // =========================================================================

    /**
     * Newton-Raphson minimization loop.
     */
    private static CVMSolverResult minimize(CVMData data, int maxIter, double tolerance) {
        int ncf = data.ncf;

        // Initialize CFs at random state
        double[] u = getURand(data);
        
        // Compute initial CVs
        double[][][] cv = updateCV(data, u);

        System.out.println("\n[NR-Simple] Starting Newton-Raphson minimization");
        System.out.println("  ncf=" + ncf + " tcf=" + data.tcf + " T=" + data.temperature);
        System.out.println("  xB=" + data.xB + " tolerance=" + tolerance);
        System.out.println("  Initial CFs (random state):");
        for (int i = 0; i < Math.min(5, ncf); i++) {
            System.out.printf("    u[%d] = %.10e%n", i, u[i]);
        }

        double[] Gu = new double[ncf];
        double[][] Guu = new double[ncf][ncf];

        double G = 0, H = 0, S = 0;
        double gradNorm = Double.MAX_VALUE;
        int iter = 0;
        List<CVMSolverResult.IterationSnapshot> trace = new ArrayList<>();

        for (iter = 1; iter <= maxIter; iter++) {
            // Evaluate function and derivatives
            double[] vals = usrfun(data, u, Gu, Guu);
            G = vals[0];
            H = vals[1];
            S = vals[2];

            // Check convergence on gradient norm
            gradNorm = 0.0;
            for (int i = 0; i < ncf; i++) {
                gradNorm += Gu[i] * Gu[i];
            }
            gradNorm = Math.sqrt(gradNorm);

                trace.add(new CVMSolverResult.IterationSnapshot(
                    iter,
                    G,
                    H,
                    S,
                    gradNorm,
                    u.clone(),
                    Gu.clone()
                ));

            if (iter == 1 || iter % 20 == 0 || gradNorm < 1e-6) {
                System.out.printf("[NR] Iter %3d: G=%.8e H=%.8e S=%.8e ||Gu||=%.8e%n",
                        iter, G, H, S, gradNorm);
            }

            if (gradNorm < tolerance) {
                System.out.println("  âœ“ CONVERGED (gradient norm < tolerance)");
                return new CVMSolverResult(u, G, H, S, iter, gradNorm, true, trace);
            }

            // Solve Guu Â· du = -Gu using Gaussian elimination
            double[] du;
            try {
                double[] negGu = new double[ncf];
                for (int i = 0; i < ncf; i++) negGu[i] = -Gu[i];
                du = LinearAlgebraUtils.solve(Guu, negGu);
            } catch (IllegalArgumentException e) {
                System.out.println("  âœ— SINGULAR HESSIAN: " + e.getMessage());
                return new CVMSolverResult(u, G, H, S, iter, gradNorm, false, trace);
            }

            // Step limiting to keep CVs positive
            double stpmax = stpmx(data, u, du, cv);
            
            // Update CFs
            for (int i = 0; i < ncf; i++) {
                u[i] += stpmax * du[i];
            }

            // Update CVs
            cv = updateCV(data, u);

            // Check for small step (secondary convergence)
            double stepNorm = 0.0;
            for (int i = 0; i < ncf; i++) {
                stepNorm += du[i] * du[i];
            }
            stepNorm = Math.sqrt(stepNorm) * stpmax;

            if (stepNorm < TOLX) {
                // Recalculate final values and check true stationarity
                vals = usrfun(data, u, Gu, Guu);
                double finalGradNorm = 0.0;
                for (int i = 0; i < ncf; i++) {
                    finalGradNorm += Gu[i] * Gu[i];
                }
                finalGradNorm = Math.sqrt(finalGradNorm);

                if (finalGradNorm < tolerance) {
                    System.out.println("  âœ“ CONVERGED (step size < TOLX and gradient < tolerance)");
                    return new CVMSolverResult(u, vals[0], vals[1], vals[2], iter, finalGradNorm, true, trace);
                }

                System.out.printf("  âœ— STALLED (step size < TOLX but ||Gu||=%.8e > tolerance=%.8e)%n",
                        finalGradNorm, tolerance);
                return new CVMSolverResult(u, vals[0], vals[1], vals[2], iter, finalGradNorm, false, trace);
            }
        }

        System.out.printf("[NR] NO CONVERGENCE after %d iterations (||Gu||=%.8e)%n", maxIter, gradNorm);
        return new CVMSolverResult(u, G, H, S, maxIter, gradNorm, false, trace);
    }

    // =========================================================================
    // usrfun: evaluate G, Gu, Guu
    // =========================================================================

    /**
     * Evaluates Gibbs energy, gradient, and Hessian.
     *
     * @return [G, H, S]
     */
    private static double[] usrfun(CVMData data, double[] u,
                                    double[] Gu, double[][] Guu) {
        CVMFreeEnergy.EvalResult eval = CVMFreeEnergy.evaluate(
                u,
                data.moleFractions,
                data.numElements,
                data.temperature,
                data.eci,
                data.msdis,
                data.kb,
                data.m,
                data.lc,
                data.cmat,
                data.lcv,
                data.wcv,
                data.tcdis,
                data.tcf,
                data.ncf,
                data.lcf,
                data.cfBasisIndices
        );

        System.arraycopy(eval.Gcu, 0, Gu, 0, data.ncf);
        for (int i = 0; i < data.ncf; i++) {
            System.arraycopy(eval.Gcuu[i], 0, Guu[i], 0, data.ncf);
        }

        return new double[]{eval.G, eval.H, eval.S};
    }

    // =========================================================================
    // Enthalpy calculations
    // =========================================================================

    /**
     * H = Î£_t msdis[t] Â· Î£_icf eci[icf] Â· u[icf]
     */
    private static double calHu(CVMData data, double[] u) {
        double H = 0.0;
        int cfOffset = 0;
        for (int itc = 0; itc < data.tcdis - 1; itc++) { // exclude point cluster
            double msd = data.msdis.get(itc);
            int nCFs = countCFsForType(data, itc);
            for (int i = 0; i < nCFs; i++) {
                int icf = cfOffset + i;
                if (icf < data.ncf) {
                    H += msd * data.eci[icf] * u[icf];
                }
            }
            cfOffset += nCFs;
        }
        return H;
    }

    /**
     * Hcu[icf] = msdis[t] Â· eci[icf]
     */
    private static double[] calHcu(CVMData data) {
        double[] Hcu = new double[data.ncf];
        int cfOffset = 0;
        for (int itc = 0; itc < data.tcdis - 1; itc++) {
            double msd = data.msdis.get(itc);
            int nCFs = countCFsForType(data, itc);
            for (int i = 0; i < nCFs; i++) {
                int icf = cfOffset + i;
                if (icf < data.ncf) {
                    Hcu[icf] = msd * data.eci[icf];
                }
            }
            cfOffset += nCFs;
        }
        return Hcu;
    }

    // =========================================================================
    // Entropy calculations (proven 4-loop structure)
    // =========================================================================

    /**
     * S = -R Ã— Î£_t msdis[t] Ã— kb[t] Ã— Î£_j m[t][j] Ã— Î£_v wcv[v] Ã— cv[v] Ã— ln(cv[v])
     */
    private static double calSu(CVMData data, double[][][] cv) {
        double S = 0.0;
        for (int itc = 0; itc < data.tcdis; itc++) {
            double kbVal = data.kb[itc];
            double msdVal = data.msdis.get(itc);
            for (int inc = 0; inc < data.lc[itc]; inc++) {
                double mVal = data.m[itc][inc];
                int[] w = data.wcv.get(itc).get(inc);
                int nv = data.lcv[itc][inc];
                for (int incv = 0; incv < nv; incv++) {
                    double cvVal = cv[itc][inc][incv];
                    if (cvVal > CV_MIN) {
                        S -= R * msdVal * kbVal * mVal * w[incv] * cvVal * Math.log(cvVal);
                    }
                }
            }
        }
        return S;
    }

    /**
     * Scu[icf] = -R Ã— Î£_t msdis[t] Ã— kb[t] Ã— Î£_j m[t][j] Ã— Î£_v wcv[v] Ã— cmat[v][icf] Ã— ln(cv[v])
     */
    private static double[] calScu(CVMData data, double[][][] cv) {
        int ncf = data.ncf;
        double[] Scu = new double[ncf];

        for (int itc = 0; itc < data.tcdis; itc++) {
            double kbVal = data.kb[itc];
            double msdVal = data.msdis.get(itc);
            for (int inc = 0; inc < data.lc[itc]; inc++) {
                double mVal = data.m[itc][inc];
                double[][] cm = data.cmat.get(itc).get(inc);
                int[] w = data.wcv.get(itc).get(inc);
                int nv = data.lcv[itc][inc];

                for (int incv = 0; incv < nv; incv++) {
                    double cvVal = cv[itc][inc][incv];
                    double logCv = (cvVal > CV_MIN) ? Math.log(cvVal) : Math.log(CV_MIN);
                    double prefix = R * msdVal * kbVal * mVal * w[incv];

                    for (int icf = 0; icf < ncf; icf++) {
                        double cVal = cm[incv][icf];
                        if (cVal != 0.0) {
                            Scu[icf] -= prefix * cVal * logCv;
                        }
                    }
                }
            }
        }
        return Scu;
    }

    /**
     * Scuu[icf1][icf2] = -R Ã— Î£_t msdis[t] Ã— kb[t] Ã— Î£_j m[t][j] Ã— Î£_v wcv[v] Ã— cmat[v][icf1] Ã— cmat[v][icf2] / cv[v]
     */
    private static double[][] calScuu(CVMData data, double[][][] cv) {
        int ncf = data.ncf;
        double[][] Scuu = new double[ncf][ncf];

        for (int itc = 0; itc < data.tcdis; itc++) {
            double kbVal = data.kb[itc];
            double msdVal = data.msdis.get(itc);
            for (int inc = 0; inc < data.lc[itc]; inc++) {
                double mVal = data.m[itc][inc];
                double[][] cm = data.cmat.get(itc).get(inc);
                int[] w = data.wcv.get(itc).get(inc);
                int nv = data.lcv[itc][inc];

                for (int incv = 0; incv < nv; incv++) {
                    double cvVal = cv[itc][inc][incv];
                    double invCv = (cvVal > CV_MIN) ? 1.0 / cvVal : 1.0 / CV_MIN;
                    double prefix = R * msdVal * kbVal * mVal * w[incv];

                    for (int icf1 = 0; icf1 < ncf; icf1++) {
                        double c1 = cm[incv][icf1];
                        if (c1 == 0.0) continue;
                        for (int icf2 = icf1; icf2 < ncf; icf2++) {
                            double c2 = cm[incv][icf2];
                            if (c2 == 0.0) continue;
                            double val = -prefix * c1 * c2 * invCv;
                            Scuu[icf1][icf2] += val;
                            if (icf1 != icf2) {
                                Scuu[icf2][icf1] += val;
                            }
                        }
                    }
                }
            }
        }
        return Scuu;
    }

    // =========================================================================
    // CV calculation
    // =========================================================================

    /**
     * CV[t][j][v] = Î£_icf cmat[v][icf] Ã— u[icf] + cmat[v][tcf] (constant term)
     */
    private static double[][][] updateCV(CVMData data, double[] u) {
        double[][][] cv = new double[data.tcdis][][];

        // Build full CF vector (non-point + point CFs)
        double[] uFull = new double[data.tcf];
        System.arraycopy(u, 0, uFull, 0, data.ncf);
        
        // Point CFs from composition: for binary, pointCF = 2*xB - 1
        double pointCF = 2.0 * data.xB - 1.0;
        for (int i = data.ncf; i < data.tcf; i++) {
            uFull[i] = pointCF;
        }

        for (int itc = 0; itc < data.tcdis; itc++) {
            cv[itc] = new double[data.lc[itc]][];
            for (int inc = 0; inc < data.lc[itc]; inc++) {
                int nv = data.lcv[itc][inc];
                cv[itc][inc] = new double[nv];
                double[][] cm = data.cmat.get(itc).get(inc);

                for (int incv = 0; incv < nv; incv++) {
                    double sum = cm[incv][data.tcf]; // constant term
                    for (int icf = 0; icf < data.tcf; icf++) {
                        sum += cm[incv][icf] * uFull[icf];
                    }
                    cv[itc][inc][incv] = sum;
                }
            }
        }
        return cv;
    }

    // =========================================================================
    // Initial guess: random state
    // =========================================================================

    /**
     * u[icf] = (2*xB - 1)^rank  for random (disordered) state
     */
    private static double[] getURand(CVMData data) {
        double[] u = new double[data.ncf];
        double sigma = 2.0 * data.xB - 1.0; // point CF value

        for (int icf = 0; icf < data.ncf; icf++) {
            int rank = data.getRank(icf);
            u[icf] = Math.pow(sigma, rank);
        }
        return u;
    }

    // =========================================================================
    // Step limiting (stpmx)
    // =========================================================================

    /**
     * Find maximum step size that keeps all CVs positive.
     *
     * <p>For each CV: cv_new = cv_old + stpmax Ã— Î”cv
     * where Î”cv = Î£_icf cmat[v][icf] Ã— du[icf]</p>
     *
     * <p>If Î”cv < 0, max step = -cv_old / Î”cv (but slightly smaller)</p>
     */
    private static double stpmx(CVMData data, double[] u, double[] du, double[][][] cv) {
        double stpmax = 1.0;
        final double MARGIN = 0.99; // safety margin

        for (int itc = 0; itc < data.tcdis; itc++) {
            for (int inc = 0; inc < data.lc[itc]; inc++) {
                double[][] cm = data.cmat.get(itc).get(inc);
                int nv = data.lcv[itc][inc];

                for (int incv = 0; incv < nv; incv++) {
                    // Compute change in CV per unit step
                    double dcv = 0.0;
                    for (int icf = 0; icf < data.ncf; icf++) {
                        dcv += cm[incv][icf] * du[icf];
                    }

                    // If CV would decrease, limit step
                    if (dcv < -CV_MIN) {
                        double cvOld = cv[itc][inc][incv];
                        if (cvOld > CV_MIN) {
                            double maxStep = -MARGIN * cvOld / dcv;
                            if (maxStep < stpmax && maxStep > 0) {
                                stpmax = maxStep;
                            }
                        }
                    }
                }
            }
        }

        // Minimum step floor
        return Math.max(stpmax, 1.0e-6);
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    /**
     * Count number of CFs for a given cluster type.
     * For binary: each type has 1 CF.
     */
    private static int countCFsForType(CVMData data, int typeIdx) {
        // In the current structure, CFs are indexed sequentially
        // For simplicity, assume 1 CF per ordered cluster group
        // This matches binary case; for multi-component, adjust based on lcf
        return data.lc[typeIdx];
    }
}

