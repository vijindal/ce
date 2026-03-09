package org.ce.domain.mcs;

import org.ce.domain.identification.geometry.Cluster;

import java.util.List;
import java.util.logging.Logger;

/**
 * Accumulates running averages of thermodynamic observables during the
 * averaging phase of a Monte Carlo simulation.
 *
 * <h2>Observables tracked</h2>
 * <ul>
 *   <li>{@code <Hmix>} and {@code <Hmix^2>} for heat-capacity estimation via
 *       fluctuation formula</li>
 *   <li>{@code <u_t>} for each multi-site cluster type {@code t}</li>
 * </ul>
 *
 * <h2>Why empty and point clusters are skipped</h2>
 * <ul>
 *   <li><b>Empty (size=0):</b> Phi=1 always; contributes a constant ECI[0] per
 *       site to H_total regardless of configuration.  Cancels in delta-E
 *       (Metropolis criterion), in Var(H) (hence Cv), and in Hmix by definition.</li>
 *   <li><b>Point (size=1) in canonical ensemble:</b> composition is fixed, so
 *       u_point is constant throughout the simulation.  Contributes a constant
 *       to H, which again cancels in delta-E, Var(H), and Hmix.</li>
 * </ul>
 * <p>Only multi-site clusters (size &gt; 1) carry configuration-dependent
 * information relevant to Hmix, Gmix, and Smix.</p>
 *
 * <h2>Single-pass CF + energy formula</h2>
 * <pre>
 *   u_t      = sum_{e: type==t} Phi(e) / embedCount_t      [size > 1 types only]
 *
 *   Hmix/site = sum_t  hmixCoeff[t] * u_t                  [= CVM Hmix formula]
 * </pre>
 * <p>One embedding loop computes {@code Phi(e)} once, accumulates CF numerators,
 * and from the normalised CFs derives Hmix via the precomputed coefficient array
 * (structurally identical to the CVM energy formula).</p>
 *
 * @author  CE Project
 * @version 3.0
 * @see     MCEngine
 */
public class MCSampler {

    private static final Logger LOG = Logger.getLogger(MCSampler.class.getName());

    /** Boltzmann constant in eV/K. */
    public static final double K_B = 8.617333262e-5;

    private final int                 tc;
    private final int[]               orbitSizes;
    private final int                 N;
    private final List<List<Cluster>> orbits;
    private final double              R;

    /**
     * Per-cluster-type Hmix coefficients: {@code hmixCoeff[t] = ECI[t] * msdis[t]}
     * for size &gt; 1 types; zero for empty and point clusters.
     * From {@link EmbeddingData#computeHmixCoeff}.
     * <p>Used both to compute Hmix/site from CFs and to accumulate the
     * energy running sum for the heat-capacity fluctuation formula.</p>
     */
    private final double[]            hmixCoeff;

    /**
     * Per-cluster-type count of multi-site embeddings (size &gt; 1).
     * Topology-invariant, precomputed at construction to avoid per-call recounting.
     * From {@link EmbeddingData#multiSiteEmbedCountsPerType}.
     */
    private final int[]               multiSiteEmbedCounts;

    /**
     * Scratch array for accumulating cluster-product numerators during sample().
     * Pre-allocated once to avoid per-call heap allocations.
     */
    private final double[]            cfNumScratch;

    private double   sumHmix  = 0.0;
    private double   sumHmix2 = 0.0;
    private double[] sumCF;
    private long     nSamples = 0;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a sampler.
     *
     * @param N                      number of lattice sites
     * @param orbitSizes             {@code orbitSizes[t]} = orbit size for cluster type t
     * @param orbits                 orbit list for evaluating decorated cluster products
     * @param R                      gas constant (in energy units matching ECI)
     * @param hmixCoeff              per-type Hmix coefficients;
     *                               from {@link EmbeddingData#computeHmixCoeff}
     * @param multiSiteEmbedCounts   per-type count of multi-site embeddings (size > 1);
     *                               from {@link EmbeddingData#multiSiteEmbedCountsPerType}
     */
    public MCSampler(int N, int[] orbitSizes,
                     List<List<Cluster>> orbits, double R,
                     double[] hmixCoeff, int[] multiSiteEmbedCounts) {
        if (N <= 0) throw new IllegalArgumentException("N must be > 0, got " + N);
        if (R <= 0) throw new IllegalArgumentException("R must be > 0");
        this.N                     = N;
        this.tc                    = orbitSizes.length;
        this.orbitSizes            = orbitSizes.clone();
        this.orbits                = orbits;
        this.sumCF                 = new double[tc];
        this.R                     = R;
        this.hmixCoeff             = hmixCoeff.clone();
        this.multiSiteEmbedCounts  = multiSiteEmbedCounts.clone();
        this.cfNumScratch          = new double[tc];
        LOG.fine("MCSampler -- CREATED: N=" + N + " sites, tc=" + tc + " cluster types, R=" + R);
    }

    // -------------------------------------------------------------------------
    // Sampling
    // -------------------------------------------------------------------------

    /**
     * Accumulates one configuration snapshot into all running averages.
     *
     * <p>Single embedding loop over multi-site clusters only (size &gt; 1).
     * Empty and point clusters are skipped because their contributions are
     * constant in the canonical ensemble and cancel in Hmix, delta-E, and Var(H).</p>
     *
     * <pre>
     *   u_t        = cfNum[t] / embedCount_t       (multi-site types only)
     *   Hmix/site  = sum_t  hmixCoeff[t] * u_t     (CVM-aligned formula)
     * </pre>
     *
     * @param config current configuration
     * @param emb    embedding data
     */
    public void sample(LatticeConfig config, EmbeddingData emb) {
        long __p = Profiler.tic("MCSampler.sample");

        // Zero out scratch array; use precomputed embedding counts
        for (int t = 0; t < tc; t++) cfNumScratch[t] = 0.0;

        // Single pass: multi-site clusters only (size > 1)
        for (Embedding e : emb.getAllEmbeddings()) {
            int t    = e.getClusterType();
            int size = e.size();
            if (t >= tc || size <= 1) continue;   // skip empty (size=0) and point (size=1)
            double phi = LocalEnergyCalc.clusterProduct(e, config, orbits);
            cfNumScratch[t] += phi;
        }

        // Normalise CFs; derive Hmix from CFs via precomputed coefficients
        // Uses precomputed embedding counts (multiSiteEmbedCounts) to avoid per-call recounting
        double hmix_per_site = 0.0;
        for (int t = 0; t < tc; t++) {
            int embedCnt = multiSiteEmbedCounts[t];
            if (embedCnt > 0) {
                double u = cfNumScratch[t] / embedCnt;
                sumCF[t]      += u;
                hmix_per_site += hmixCoeff[t] * u;
            }
        }

        // Accumulate Hmix (total, not per-site) for fluctuation formula
        double Hmix = hmix_per_site * N;
        sumHmix  += Hmix;
        sumHmix2 += Hmix * Hmix;
        nSamples++;

        Profiler.toc("MCSampler.sample", __p);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public long getSampleCount() { return nSamples; }

    /** Returns mean Hmix per site averaged over all samples. */
    public double meanHmixPerSite() {
        return nSamples == 0 ? 0.0 : (sumHmix / nSamples) / N;
    }

    /**
     * Returns heat capacity per site from the Hmix fluctuation formula:
     * {@code Cv/site = Var(Hmix) / (N * R * T^2)}.
     *
     * <p>Equivalent to the full H_total fluctuation in the canonical ensemble
     * because empty and point cluster contributions are configuration-independent
     * (their variance is zero).</p>
     */
    public double heatCapacityPerSite(double T) {
        if (nSamples < 2) return 0.0;
        double mH  = sumHmix  / nSamples;
        double mH2 = sumHmix2 / nSamples;
        return (mH2 - mH * mH) / ((double) N * R * T * T);
    }

    /** Returns time-averaged CFs for multi-site cluster types; zero for size&le;1 types. */
    public double[] meanCFs() {
        double[] r = new double[tc];
        if (nSamples == 0) return r;
        for (int t = 0; t < tc; t++) r[t] = sumCF[t] / nSamples;
        return r;
    }

    public void reset() {
        sumHmix = 0; sumHmix2 = 0;
        sumCF = new double[tc];
        nSamples = 0;
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    public void printDebug(double T) {
        LOG.fine("[MCSampler]");
        LOG.fine(String.format("  samples      : %d", nSamples));
        LOG.fine(String.format("  Hmix/site    : %+.6f", meanHmixPerSite()));
        LOG.fine(String.format("  Cv/site      : %+.4e  (T=%.1f K)", heatCapacityPerSite(T), T));
        double[] cfs = meanCFs();
        LOG.fine("  <u_t> (multi-site only):");
        for (int t = 0; t < tc; t++)
            if (cfs[t] != 0.0)
                LOG.fine(String.format("    t=%d  orb=%-3d  u=%+.6f", t, orbitSizes[t], cfs[t]));
    }
}
