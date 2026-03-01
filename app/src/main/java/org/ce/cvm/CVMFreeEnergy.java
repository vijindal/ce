package org.ce.cvm;

import java.util.List;

/**
 * Computes the CVM free-energy functional and its derivatives.
 *
 * <p>The Gibbs energy of mixing is:</p>
 * <pre>
 *   G = H − T · S
 * </pre>
 *
 * <h2>Enthalpy (linear in CFs)</h2>
 * <pre>
 *   H    = Σ_{l=0}^{ncf-1} mhdis[l] · ECI[l] · u[l]
 *   Hcu[l]      = mhdis[l] · ECI[l]           (gradient)
 *   Hcuu[l1][l2] = 0                           (Hessian — enthalpy is linear)
 * </pre>
 *
 * <h2>Entropy (CVM Kikuchi–Barker formula)</h2>
 * <pre>
 *   S = −R · Σ_t kb[t] · ms[t] · Σ_j mh[t][j] · Σ_v wcv[t][j][v] · CV · ln(CV)
 *
 *   Scu[l] = −R · Σ_t kb[t] · ms[t] · Σ_j mh[t][j]
 *            · Σ_v wcv[t][j][v] · cmat[t][j][v][l] · ln(CV[t][j][v])
 *
 *   Scuu[l1][l2] = −R · Σ_t kb[t] · ms[t] · Σ_j mh[t][j]
 *                  · Σ_v wcv[t][j][v] · cmat[t][j][v][l1] · cmat[t][j][v][l2] / CV[t][j][v]
 * </pre>
 *
 * <p>Note: in the Mathematica code, {@code ms[t] = mhdis[t]} (HSP multiplicities
 * are used as the "system parameter" multiplicities in the entropy).</p>
 *
 * <h2>Gibbs energy derivatives</h2>
 * <pre>
 *   Gcu  = Hcu − T · Scu
 *   Gcuu = −T · Scuu          (since Hcuu = 0)
 * </pre>
 *
 * @see ClusterVariableEvaluator
 */
public final class CVMFreeEnergy {

    /** Gas constant in J/(mol·K). */
    public static final double R_GAS = 8.3144598;

    private CVMFreeEnergy() { /* utility class */ }

    // =========================================================================
    // Result container
    // =========================================================================

    /**
     * Holds the evaluated free-energy functional and its first/second derivatives.
     */
    public static final class EvalResult {
        /** Gibbs energy of mixing G = H − T·S. */
        public final double G;
        /** Enthalpy of mixing. */
        public final double H;
        /** Entropy of mixing. */
        public final double S;
        /** Gradient dG/du (length ncf). */
        public final double[] Gcu;
        /** Hessian d²G/du² (ncf × ncf). */
        public final double[][] Gcuu;

        public EvalResult(double G, double H, double S, double[] Gcu, double[][] Gcuu) {
            this.G = G;
            this.H = H;
            this.S = S;
            this.Gcu = Gcu;
            this.Gcuu = Gcuu;
        }
    }

    // =========================================================================
    // Main evaluation
    // =========================================================================

    /**
     * Evaluates the CVM free-energy functional, gradient, and Hessian at the
     * given CF values.
     *
     * @param u             non-point CF values (length ncf) — the optimisation variables
     * @param moleFractions mole fractions of all K components (length K, Σ = 1)
     * @param numElements   number of chemical components K (≥ 2)
     * @param temperature   temperature in Kelvin
     * @param eci           effective cluster interactions (length ncf)
     * @param mhdis         HSP cluster multiplicities (size tcdis)
     * @param kb            Kikuchi-Baker entropy coefficients (length tcdis)
     * @param mh            normalised multiplicities: mh[t][j]
     * @param lc            ordered clusters per HSP type: lc[t]
     * @param cmat          C-matrix: cmat.get(t).get(j)[v][k], last col = constant
     * @param lcv           CV counts: lcv[t][j]
     * @param wcv           CV weights: wcv.get(t).get(j)[v]
     * @param tcdis         number of HSP cluster types
     * @param tcf           total number of CFs
     * @param ncf           number of non-point CFs
     * @param lcf           CF count per (type, group): lcf[t][j]
     * @param cfBasisIndices per-CF basis-index decorations from CMatrixResult
     * @return evaluation result containing G, H, S, gradient, and Hessian
     */
    public static EvalResult evaluate(
            double[] u,
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
            int[][] cfBasisIndices) {

        // Build full CF vector and evaluate cluster variables
        double[] uFull = ClusterVariableEvaluator.buildFullCFVector(
                u, moleFractions, numElements, cfBasisIndices, ncf, tcf);
        double[][][] cv = ClusterVariableEvaluator.evaluate(uFull, cmat, lcv, tcdis, lc);

        // --- Enthalpy ---
        // H = Σ_t mhdis[t] · Σ_{l ∈ type t} eci[l] · u[l]
        // CFs are ordered sequentially by type: type 0's CFs, type 1's, etc.
        // The number of CFs for type t = Σ_j lcf[t][j].
        double Hval = 0.0;
        double[] Hcu = new double[ncf];
        {
            int cfOffset = 0;
            for (int t = 0; t < tcdis; t++) {
                int nCFsForType = 0;
                for (int j = 0; j < lcf[t].length; j++) {
                    nCFsForType += lcf[t][j];
                }
                if (t == tcdis - 1) break; // point type — not in optimisation
                double mhd = mhdis.get(t);
                for (int i = 0; i < nCFsForType; i++) {
                    int l = cfOffset + i;
                    Hcu[l] = mhd * eci[l];
                    Hval += Hcu[l] * u[l];
                }
                cfOffset += nCFsForType;
            }
        }

        // --- Entropy ---
        // S = −R · Σ_t kb[t] · ms[t] · Σ_j mh[t][j] · Σ_v w · cv · ln(cv)
        // Scu[l] = −R · Σ_t kb[t] · ms[t] · Σ_j mh[t][j] · Σ_v w · cmat[l] · ln(cv)
        // Scuu[l1][l2] = −R · Σ_t kb[t] · ms[t] · Σ_j mh[t][j] · Σ_v w · cmat[l1] · cmat[l2] / cv
        double Sval = 0.0;
        double[] Scu = new double[ncf];
        double[][] Scuu = new double[ncf][ncf];

        // Matching the Mathematica convention: R = 1 (normalised).
        // The CVM formulas use R as a scale factor in the entropy.
        // With R = 1, the user must provide T and ECI in consistent units.
        // For physical units (ECI in J/mol, T in K), multiply S by R_GAS
        // after the calculation.
        double R = 1.0;

        for (int t = 0; t < tcdis; t++) {
            double coeff_t = kb[t] * mhdis.get(t); // kb[t] · ms[t]
            for (int j = 0; j < lc[t]; j++) {
                double mh_tj = mh[t][j];
                double[][] cm = cmat.get(t).get(j);
                int[] w = wcv.get(t).get(j);
                int nv = lcv[t][j];

                for (int v = 0; v < nv; v++) {
                    double cvVal = cv[t][j][v];
                    int wv = w[v];

                    // Smooth entropy extension for CV ≤ EPS.
                    // For CV > EPS: use exact cv·ln(cv), ln(cv), 1/cv.
                    // For CV ≤ EPS: use a C² quadratic extension that creates
                    // a soft barrier pushing the solver toward positive CVs.
                    // This is critical for K≥3 where the all-zero initial
                    // guess produces negative CVs.
                    final double EPS = 1.0e-6;
                    double sContrib; // cv·ln(cv) or smooth extension
                    double logEff;   // effective ln(cv) for gradient
                    double invEff;   // effective 1/cv for Hessian

                    if (cvVal > EPS) {
                        double logCv = Math.log(cvVal);
                        sContrib = cvVal * logCv;
                        logEff = logCv;
                        invEff = 1.0 / cvVal;
                    } else {
                        double logEps = Math.log(EPS);
                        double d = cvVal - EPS;
                        sContrib = EPS * logEps + (1.0 + logEps) * d + 0.5 / EPS * d * d;
                        logEff = logEps + d / EPS;
                        invEff = 1.0 / EPS;
                    }

                    double prefix = coeff_t * mh_tj * wv;

                    // Entropy value
                    Sval -= R * prefix * sContrib;

                    // Gradient: only first ncf columns of cmat contribute
                    // (point-CF columns and constant column are not optimisation variables)
                    for (int l = 0; l < ncf; l++) {
                        double cml = cm[v][l];
                        if (cml == 0.0) continue;
                        // d(cv·ln(cv))/du_l = cmat[v][l] · (1 + ln(cv))
                        // But the "+1" term vanishes due to normalization constraint:
                        // Σ_v wcv · cmat[v][l] = 0 for l > 0 (non-trivial CFs)
                        // So: Scu[l] = −R · Σ prefix · cmat[v][l] · ln(cv)
                        Scu[l] -= R * prefix * cml * logEff;
                    }

                    // Hessian
                    for (int l1 = 0; l1 < ncf; l1++) {
                        double cml1 = cm[v][l1];
                        if (cml1 == 0.0) continue;
                        for (int l2 = l1; l2 < ncf; l2++) {
                            double cml2 = cm[v][l2];
                            if (cml2 == 0.0) continue;
                            double val = -R * prefix * cml1 * cml2 * invEff;
                            Scuu[l1][l2] += val;
                            if (l1 != l2) {
                                Scuu[l2][l1] += val;
                            }
                        }
                    }
                }
            }
        }

        // --- Gibbs energy ---
        double Gval = Hval - temperature * Sval;
        double[] Gcu = new double[ncf];
        double[][] Gcuu = new double[ncf][ncf];

        for (int l = 0; l < ncf; l++) {
            Gcu[l] = Hcu[l] - temperature * Scu[l];
        }

        for (int l1 = 0; l1 < ncf; l1++) {
            for (int l2 = 0; l2 < ncf; l2++) {
                // Hcuu = 0, so Gcuu = −T · Scuu
                Gcuu[l1][l2] = -temperature * Scuu[l1][l2];
            }
        }

        return new EvalResult(Gval, Hval, Sval, Gcu, Gcuu);
    }

    /**
     * Convenience overload for binary systems.
     *
     * @param u           non-point CF values (length ncf)
     * @param composition mole fraction of component B (binary shorthand)
     * @param temperature temperature in Kelvin
     * @param eci         effective cluster interactions (length ncf)
     * @param mhdis       HSP cluster multiplicities
     * @param kb          Kikuchi-Baker entropy coefficients
     * @param mh          normalised multiplicities: mh[t][j]
     * @param lc          ordered clusters per HSP type: lc[t]
     * @param cmat        C-matrix
     * @param lcv         CV counts
     * @param wcv         CV weights
     * @param tcdis       number of HSP cluster types
     * @param tcf         total number of CFs
     * @param ncf         number of non-point CFs
     * @return evaluation result
     */
    public static EvalResult evaluate(
            double[] u,
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
            int[][] cfBasisIndices) {

        // Binary: each type has exactly 1 group with 1 CF
        int[][] binaryLcf = new int[tcdis][];
        for (int t = 0; t < tcdis; t++) binaryLcf[t] = new int[]{1};

        return evaluate(u, new double[]{1.0 - composition, composition}, 2,
                temperature, eci, mhdis, kb, mh, lc, cmat, lcv, wcv,
                tcdis, tcf, ncf, binaryLcf, cfBasisIndices);
    }
}
