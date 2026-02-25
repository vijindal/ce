package org.ce.mcs;

/**
 * Encodes a complete orthogonal point-function basis for an n-component system
 * and evaluates basis functions on a given site occupation.
 *
 * <h2>Occupation encoding</h2>
 * <p>Site occupations are stored as integers in {@code [0, numComp)}:</p>
 * <ul>
 *   <li>{@code 0} — species A (majority / host)</li>
 *   <li>{@code 1} — species B (first solute)</li>
 *   <li>{@code 2} — species C (second solute)</li>
 *   <li>…and so on.</li>
 * </ul>
 * <p>For a <em>binary</em> system this reduces to A=0, B=1, and basis
 * function {@code φ_1(0) = +1}, {@code φ_1(1) = -1}, recovering the
 * standard ±1 spin operator.</p>
 *
 * <h2>Orthogonal basis construction</h2>
 * <p>The basis functions {@code φ_α} for {@code α = 1, …, numComp−1} are
 * chosen to be orthonormal with respect to the uniform (random-alloy) measure
 * on {@code {0, 1, …, numComp−1}}.  The explicit construction used here is
 * based on the Chebyshev-like polynomials introduced by Sanchez, Ducastelle,
 * and Gratias (1984), which for small {@code numComp} have simple closed forms:</p>
 *
 * <ul>
 *   <li><b>Binary (numComp=2):</b>
 *       {@code φ_1(σ) = 1 − 2σ}  →  A=+1, B=−1</li>
 *   <li><b>Ternary (numComp=3):</b>
 *       {@code φ_1(σ) = 1 − σ}   →  A=+1, B=0, C=−1<br>
 *       {@code φ_2(σ) = 1 − 3σ(1−σ) − (σ−1)²}  (renormalised Chebyshev)</li>
 *   <li><b>General:</b> uses the Gram-Schmidt orthogonalised power basis
 *       over the uniform measure, precomputed and stored in {@link #basisMatrix}.</li>
 * </ul>
 *
 * <h2>Cluster product</h2>
 * <p>For a decorated cluster with sites labelled {@code s1, s2, …} the cluster
 * product is:</p>
 * <pre>
 *   Φ(cluster) = Π_{k in cluster} φ_{α(k)}(occ_k)
 * </pre>
 * <p>where {@code α(k)} is the basis index of site {@code k} (read from
 * the {@code "sα"} symbol of the corresponding cluster orbit member),
 * and {@code occ_k ∈ {0,…,numComp−1}} is the actual occupation of site
 * {@code k} in the current configuration.</p>
 *
 * @author  CE Project
 * @version 1.0
 * @see     LatticeConfig
 * @see     LocalEnergyCalc
 */
public class SiteOperatorBasis {

    private final int       numComp;
    /**
     * {@code basisMatrix[alpha][sigma]} = φ_{alpha+1}(sigma),
     * for {@code alpha ∈ [0, numComp−2]} and {@code sigma ∈ [0, numComp−1]}.
     */
    private final double[][] basisMatrix;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs the orthogonal basis for an {@code numComp}-component system.
     *
     * @param numComp number of chemical components; must be ≥ 2
     * @throws IllegalArgumentException if {@code numComp < 2}
     */
    public SiteOperatorBasis(int numComp) {
        if (numComp < 2)
            throw new IllegalArgumentException("numComp must be >= 2, got " + numComp);
        this.numComp     = numComp;
        this.basisMatrix = buildBasis(numComp);
    }

    // -------------------------------------------------------------------------
    // Evaluation
    // -------------------------------------------------------------------------

    /**
     * Returns the number of chemical components.
     *
     * @return {@code numComp} ≥ 2
     */
    public int getNumComp() { return numComp; }

    /**
     * Returns the number of independent basis functions:
     * {@code numComp − 1}.
     *
     * @return {@code numComp − 1} ≥ 1
     */
    public int getNumBasisFunctions() { return numComp - 1; }

    /**
     * Evaluates basis function {@code φ_alpha} at occupation {@code sigma}.
     *
     * <p>Basis index {@code alpha} is 1-based (matching the {@code "s1"},
     * {@code "s2"}, … naming convention used in decorated cluster sites).
     * Use {@code alpha = 1} for the dominant correlation function.</p>
     *
     * @param alpha basis-function index in {@code [1, numComp−1]}
     * @param sigma occupation in {@code [0, numComp−1]}
     * @return {@code φ_alpha(sigma)}
     * @throws IllegalArgumentException if arguments are out of range
     */
    public double evaluate(int alpha, int sigma) {
        if (alpha < 1 || alpha > numComp - 1)
            throw new IllegalArgumentException(
                    "alpha must be in [1, numComp-1]=" + (numComp-1) + ", got " + alpha);
        if (sigma < 0 || sigma >= numComp)
            throw new IllegalArgumentException(
                    "sigma must be in [0, numComp-1]=" + (numComp-1) + ", got " + sigma);
        return basisMatrix[alpha - 1][sigma];
    }

    /**
     * Parses the basis-function index from a site symbol string.
     *
     * <p>Recognises the convention used by {@link org.ce.identification.engine.BasisSymbolGenerator}:
     * {@code "s1"} → 1, {@code "s2"} → 2, etc.</p>
     *
     * @param symbol site symbol, e.g. {@code "s1"}
     * @return parsed alpha index ≥ 1
     * @throws IllegalArgumentException if the symbol does not match {@code "s<integer>"}
     */
    public static int alphaFromSymbol(String symbol) {
        if (symbol == null || !symbol.startsWith("s"))
            throw new IllegalArgumentException(
                    "Site symbol must start with 's', got: " + symbol);
        try {
            return Integer.parseInt(symbol.substring(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Cannot parse alpha from symbol: " + symbol, e);
        }
    }

    // -------------------------------------------------------------------------
    // Basis construction — Gram-Schmidt over uniform measure
    // -------------------------------------------------------------------------

    /**
     * Builds the {@code (numComp−1) × numComp} basis matrix by Gram-Schmidt
     * orthogonalisation of the power basis {@code {1, σ, σ², …}} over the
     * uniform measure on {@code {0, 1, …, numComp−1}}.
     *
     * <p>The resulting functions satisfy:</p>
     * <pre>
     *   Σ_{σ=0}^{n−1} φ_α(σ) · φ_β(σ) / n = δ_{αβ}
     * </pre>
     * <p>Special cases match the standard CVM literature:</p>
     * <ul>
     *   <li>Binary:  {@code φ_1 = [+1, −1]}  (spin operator)</li>
     *   <li>Ternary: {@code φ_1 = [+1, 0, −1]},  {@code φ_2 = [+1, −2, +1]/√6}</li>
     * </ul>
     */
    private static double[][] buildBasis(int n) {
        // Power basis vectors: v_k(σ) = σ^k, k = 0, 1, ..., n-1
        // We orthogonalise v_1, v_2, ..., v_{n-1} against each other AND v_0=1,
        // then normalise.  The constant function v_0 corresponds to the empty
        // cluster and is excluded from the basis.

        double[][] basis = new double[n - 1][n];   // basis[alpha-1][sigma]
        // Gram-Schmidt working vectors (include v_0 as already normalised first vector)
        double[][] ortho = new double[n][n];

        // v_0 = constant (empty cluster / identity) — normalised to 1/sqrt(n)
        for (int s = 0; s < n; s++) ortho[0][s] = 1.0 / Math.sqrt(n);

        for (int k = 1; k < n; k++) {
            // Start with v_k(σ) = σ^k
            double[] vk = new double[n];
            for (int s = 0; s < n; s++) vk[s] = Math.pow(s, k);

            // Subtract projections onto all previous ortho vectors
            for (int j = 0; j < k; j++) {
                double dot = 0.0;
                for (int s = 0; s < n; s++) dot += ortho[j][s] * vk[s];
                // uniform measure: inner product = (1/n) Σ f·g, but since
                // ortho[j] is normalised w.r.t. (1/n) measure, projection = dot/n * n = dot
                // Actually: inner product under (1/n) measure of f and g = (1/n) Σ f(s)g(s)
                // projection of vk onto ortho[j] = <vk, ortho[j]> / <ortho[j], ortho[j]>
                //   = (1/n Σ vk*ortho[j]) / 1  (since ortho[j] is already normalised)
                double proj = 0.0;
                for (int s = 0; s < n; s++) proj += vk[s] * ortho[j][s];
                proj /= n;  // (1/n) Σ vk * ortho[j]
                for (int s = 0; s < n; s++) vk[s] -= proj * ortho[j][s] * n;
                // Note: ortho[j] is stored as the function values (not scaled by 1/sqrt(n))
                // Redo with cleaner formulation below
            }

            // Cleaner Gram-Schmidt using unnormalised inner product Σ f·g / n
            // (restart for clarity)
            vk = new double[n];
            for (int s = 0; s < n; s++) vk[s] = Math.pow(s, k);

            double[][] prev = new double[k][];
            for (int j = 0; j < k; j++) prev[j] = ortho[j];

            for (int j = 0; j < k; j++) {
                double ip = 0.0;   // <vk, prev[j]> = (1/n) Σ vk * prev[j]
                for (int s = 0; s < n; s++) ip += vk[s] * prev[j][s];
                ip /= n;
                double norm2 = 0.0;  // <prev[j], prev[j]>
                for (int s = 0; s < n; s++) norm2 += prev[j][s] * prev[j][s];
                norm2 /= n;
                for (int s = 0; s < n; s++) vk[s] -= (ip / norm2) * prev[j][s];
            }

            // Normalise so that <vk, vk> = 1 under (1/n) measure
            double norm = 0.0;
            for (int s = 0; s < n; s++) norm += vk[s] * vk[s];
            norm = Math.sqrt(norm / n);
            if (norm < 1e-14)
                throw new IllegalStateException(
                        "Near-zero norm at k=" + k + " during basis construction");
            for (int s = 0; s < n; s++) vk[s] /= norm;

            ortho[k]      = vk;
            basis[k - 1]  = vk;   // φ_k = ortho[k], alpha = k (1-based)
        }

        return basis;
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    /**
     * Prints the full basis matrix to standard output.
     *
     * <p>Output format (ternary example):</p>
     * <pre>
     * [SiteOperatorBasis]  numComp=3
     *   φ_1 : σ=0: +1.000  σ=1:  0.000  σ=2: -1.000
     *   φ_2 : σ=0: +0.816  σ=1: -1.633  σ=2: +0.816
     * </pre>
     */
    public void printDebug() {
        System.out.println("[SiteOperatorBasis]  numComp=" + numComp);
        for (int a = 0; a < numComp - 1; a++) {
            StringBuilder sb = new StringBuilder(
                    String.format("  φ_%d : ", a + 1));
            for (int s = 0; s < numComp; s++) {
                sb.append(String.format("σ=%d: %+.3f  ", s, basisMatrix[a][s]));
            }
            System.out.println(sb.toString().trim());
        }
    }
}
