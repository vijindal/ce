package org.ce.mcs;

import java.util.Arrays;
import java.util.Random;

/**
 * Flat occupation array representing the atomic configuration of a periodic
 * supercell for any number of chemical components.
 *
 * <h2>Occupation encoding</h2>
 * <p>Each lattice site {@code i} holds an integer occupation drawn from
 * {@code {0, 1, …, numComp−1}}:</p>
 * <ul>
 *   <li>{@code 0} — species A (majority / host)</li>
 *   <li>{@code 1} — species B (first solute)</li>
 *   <li>{@code 2} — species C (second solute)</li>
 *   <li>…and so on.</li>
 * </ul>
 *
 * <p>The flat array layout mirrors the site-index convention of
 * {@link EmbeddingData}: site index {@code i} in {@link #getOccupation(int)}
 * is the same index {@code i} referenced in {@link Embedding#getSiteIndices()}.
 * For a BCC L×L×L supercell {@code N = 2·L³}.</p>
 *
 * <h2>Basis function evaluation</h2>
 * <p>Physical observables (cluster products, CFs) are computed by
 * {@link LocalEnergyCalc} using the {@link SiteOperatorBasis} supplied
 * at construction.  {@code LatticeConfig} itself stores only the raw integer
 * occupations.</p>
 *
 * @author  CE Project
 * @version 1.0
 * @see     SiteOperatorBasis
 * @see     LocalEnergyCalc
 * @see     ExchangeStep
 */
public class LatticeConfig {

    /** Number of chemical components. */
    private final int numComp;

    /** Occupation values in {@code [0, numComp)}. */
    private final int[] occ;

    /** Basis used to evaluate site operators (shared, not copied). */
    private final SiteOperatorBasis basis;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a configuration of {@code N} sites with all occupations set to
     * {@code 0} (pure-A state).
     *
     * @param N       number of lattice sites; must be ≥ 1
     * @param numComp number of chemical components; must be ≥ 2
     */
    public LatticeConfig(int N, int numComp) {
        if (N < 1)       throw new IllegalArgumentException("N must be >= 1, got " + N);
        if (numComp < 2) throw new IllegalArgumentException("numComp must be >= 2, got " + numComp);
        this.numComp = numComp;
        this.occ     = new int[N];   // all zeros = pure A
        this.basis   = new SiteOperatorBasis(numComp);
    }

    /**
     * Creates a configuration from a pre-existing occupation array.
     *
     * @param occ     occupation values in {@code [0, numComp)}; a defensive copy is made
     * @param numComp number of chemical components; must be ≥ 2
     * @throws IllegalArgumentException if any entry is outside {@code [0, numComp)}
     */
    public LatticeConfig(int[] occ, int numComp) {
        if (numComp < 2) throw new IllegalArgumentException("numComp must be >= 2, got " + numComp);
        this.numComp = numComp;
        this.occ     = occ.clone();
        this.basis   = new SiteOperatorBasis(numComp);
        for (int i = 0; i < this.occ.length; i++) {
            if (this.occ[i] < 0 || this.occ[i] >= numComp)
                throw new IllegalArgumentException(
                        "occ[" + i + "] = " + occ[i] + " out of [0," + (numComp-1) + "]");
        }
    }

    // -------------------------------------------------------------------------
    // Core accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the occupation at site {@code i}.
     *
     * @param i site index (0-based)
     * @return occupation in {@code [0, numComp)}
     */
    public int getOccupation(int i) { return occ[i]; }

    /**
     * Sets the occupation at site {@code i}.
     *
     * @param i   site index (0-based)
     * @param occ new occupation in {@code [0, numComp)}
     * @throws IllegalArgumentException if {@code occ} is out of range
     */
    public void setOccupation(int i, int occ) {
        if (occ < 0 || occ >= numComp)
            throw new IllegalArgumentException(
                    "occ must be in [0," + (numComp-1) + "], got " + occ);
        this.occ[i] = occ;
    }

    /**
     * Returns the total number of lattice sites.
     *
     * @return {@code N} ≥ 1
     */
    public int getN() { return occ.length; }

    /**
     * Returns the number of chemical components.
     *
     * @return {@code numComp} ≥ 2
     */
    public int getNumComp() { return numComp; }

    /**
     * Returns the {@link SiteOperatorBasis} associated with this configuration.
     *
     * @return basis; never {@code null}
     */
    public SiteOperatorBasis getBasis() { return basis; }

    // -------------------------------------------------------------------------
    // Initialisation
    // -------------------------------------------------------------------------

    /**
     * Randomly assigns occupations so that the fraction of each species {@code c}
     * is {@code xFrac[c]}, as closely as possible.
     *
     * <p>The exact count for species {@code c} is
     * {@code round(xFrac[c] × N)}.  Sites are assigned by a partial
     * Fisher-Yates shuffle.  Species 0 fills any remaining sites after all
     * other species are placed.</p>
     *
     * @param xFrac composition fractions; must sum to ≈ 1.0; length must equal
     *              {@code numComp}
     * @param rng   random number generator
     * @throws IllegalArgumentException if {@code xFrac.length != numComp}
     */
    public void randomise(double[] xFrac, Random rng) {
        if (xFrac.length != numComp)
            throw new IllegalArgumentException(
                    "xFrac length " + xFrac.length + " != numComp " + numComp);

        Arrays.fill(occ, 0);
        int[] indices = new int[occ.length];
        for (int i = 0; i < occ.length; i++) indices[i] = i;

        int placed = 0;
        // Assign species 1, 2, … (species 0 fills remainder)
        for (int c = 1; c < numComp; c++) {
            int count = (int) Math.round(xFrac[c] * occ.length);
            count = Math.min(count, occ.length - placed);
            for (int i = placed; i < placed + count; i++) {
                int j = i + rng.nextInt(occ.length - i);
                int tmp = indices[i]; indices[i] = indices[j]; indices[j] = tmp;
                occ[indices[i]] = c;
            }
            placed += count;
        }
    }

    /**
     * Convenience overload for binary systems.
     * Sets the B-fraction ({@code x[1] = xB}), A fills the rest.
     *
     * @param xB  B-fraction in {@code [0, 1]}
     * @param rng random number generator
     */
    public void randomiseBinary(double xB, Random rng) {
        randomise(new double[]{1.0 - xB, xB}, rng);
    }

    /**
     * Returns a deep copy of this configuration.
     *
     * @return independent {@code LatticeConfig} with the same occupations
     */
    public LatticeConfig copy() {
        return new LatticeConfig(occ, numComp);
    }

    // -------------------------------------------------------------------------
    // Composition queries
    // -------------------------------------------------------------------------

    /**
     * Counts the number of sites occupied by species {@code c}.
     *
     * @param c species index in {@code [0, numComp)}
     * @return count in {@code [0, N]}
     */
    public int countSpecies(int c) {
        int count = 0;
        for (int o : occ) if (o == c) count++;
        return count;
    }

    /**
     * Returns the composition fractions for all species.
     *
     * @return array of length {@code numComp}; {@code x[c] = N_c / N}
     */
    public double[] composition() {
        double[] x = new double[numComp];
        for (int o : occ) x[o]++;
        for (int c = 0; c < numComp; c++) x[c] /= occ.length;
        return x;
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    /**
     * Prints a compact debug summary to standard output.
     *
     * <p>Output format (binary example):</p>
     * <pre>
     * [LatticeConfig]
     *   N          : 128
     *   numComp    : 2
     *   x[0] (A)   : 0.75000  (96 sites)
     *   x[1] (B)   : 0.25000  (32 sites)
     *   occ[0..7]  : 0 0 1 0 0 0 1 0
     * </pre>
     */
    public void printDebug() {
        System.out.println("[LatticeConfig]");
        System.out.println("  N          : " + occ.length);
        System.out.println("  numComp    : " + numComp);
        String[] labels = {"A","B","C","D","E"};
        double[] x = composition();
        for (int c = 0; c < numComp; c++) {
            String lbl = c < labels.length ? labels[c] : ("C"+c);
            System.out.printf("  x[%d] (%s)  : %.5f  (%d sites)%n",
                    c, lbl, x[c], countSpecies(c));
        }
        int preview = Math.min(8, occ.length);
        StringBuilder sb = new StringBuilder("  occ[0.." + (preview-1) + "] : ");
        for (int i = 0; i < preview; i++) sb.append(occ[i]).append(' ');
        System.out.println(sb.toString().trim());
    }
}
