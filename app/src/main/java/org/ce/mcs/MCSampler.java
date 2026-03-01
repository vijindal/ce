package org.ce.mcs;

import org.ce.identification.geometry.Cluster;

import java.util.List;

/**
 * Accumulates running averages of thermodynamic observables during the
 * averaging phase of a Monte Carlo simulation.
 *
 * <h2>Observables</h2>
 * <ul>
 *   <li>{@code ⟨H⟩} and {@code ⟨H²⟩} — for heat-capacity estimation</li>
 *   <li>{@code ⟨u_t⟩} for each cluster type {@code t} — supercell-averaged
 *       correlation functions</li>
 * </ul>
 *
 * <h2>CF formula</h2>
 * <pre>
 *   u_t = Σ_{e ∈ allEmbeddings, e.type==t} Φ(e)
 *         ──────────────────────────────────
 *               embedCount_t
 * </pre>
 * <p>CORRECTED FORMULA (v2): Normalizes by the total embedding count for each
 * cluster type. This gives the average cluster product, which is the
 * mathematically correct basis for the CVM. {@link LocalEnergyCalc#clusterProduct}
 * is called with orbit data to evaluate {@code Φ(e)} for any number of
 * components.</p>
 *
 * @author  CE Project
 * @version 2.0
 * @see     MCEngine
 */
public class MCSampler {

    /** Boltzmann constant in eV/K. */
    public static final double K_B = 8.617333262e-5;

    private final int                              tc;
    private final int[]                            orbitSizes;
    private final int                              N;
    private final List<List<Cluster>>              orbits;
    private final double                           R;

    private double   sumE  = 0.0;
    private double   sumE2 = 0.0;
    private double[] sumCF;
    private long     nSamples = 0;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a sampler.
     *
     * @param N          number of lattice sites
     * @param orbitSizes {@code orbitSizes[t]} = orbit size for cluster type {@code t}
     * @param orbits     orbit list from {@code ClusCoordListResult.getOrbitList()},
     *                   needed to evaluate decorated cluster products
     */
    public MCSampler(int N, int[] orbitSizes,
                     List<List<Cluster>> orbits,
                     double R) {
        if (N <= 0) throw new IllegalArgumentException("N must be > 0, got " + N);
        this.N          = N;
        this.tc         = orbitSizes.length;
        this.orbitSizes = orbitSizes.clone();
        this.orbits     = orbits;
        this.sumCF      = new double[tc];
        if (R <= 0) throw new IllegalArgumentException("R must be > 0");
        this.R = R;
    }

    // -------------------------------------------------------------------------
    // Sampling
    // -------------------------------------------------------------------------

    /**
     * Accumulates one sample of observables.
     *
     * <h2>Updated CF formula</h2>
     * <pre>
     *   u_t = Σ_{e ∈ allEmbeddings, e.type==t} Φ(e)
     *         ─────────────────────────────────────
     *              embedCount_t
     * </pre>
     * <p>This averages the cluster product over all embeddings of type t,
     * which is the mathematically correct normalization for the CVM.</p>
     *
     * @param config current configuration
     * @param emb    embedding data
     * @param eci    effective cluster interactions
     */
    public void sample(LatticeConfig config,
                       EmbeddingData emb,
                       double[] eci) {
        long __p = Profiler.tic("MCSampler.sample");

        double H = LocalEnergyCalc.totalEnergy(config, emb, eci, orbits);
        sumE  += H;
        sumE2 += H * H;

        // Accumulate CF numerators and embedding counts per cluster type
        double[] cfNum = new double[tc];
        int[] embedCount = new int[tc];
        for (Embedding e : emb.getAllEmbeddings()) {
            int t = e.getClusterType();
            if (t < tc) {
                embedCount[t]++;
                cfNum[t] += LocalEnergyCalc.clusterProduct(e, config, orbits);
            }
        }
        // Normalize by embedding count for each type
        for (int t = 0; t < tc; t++) {
            if (embedCount[t] > 0) {
                sumCF[t] += cfNum[t] / embedCount[t];
            }
        }
        nSamples++;

        Profiler.toc("MCSampler.sample", __p);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public long     getSampleCount()              { return nSamples; }
    public double   meanEnergyPerSite()           { return nSamples == 0 ? 0.0 : (sumE / nSamples) / N; }

    public double heatCapacityPerSite(double T) {
        if (nSamples < 2) return 0.0;
        double mH  = sumE  / nSamples;
        double mH2 = sumE2 / nSamples;
        return (mH2 - mH * mH) / ((double) N * R * T * T);
    }

    public double[] meanCFs() {
        double[] r = new double[tc];
        if (nSamples == 0) return r;
        for (int t = 0; t < tc; t++) r[t] = sumCF[t] / nSamples;
        return r;
    }

    public void reset() {
        sumE = 0; sumE2 = 0;
        sumCF = new double[tc];
        nSamples = 0;
    }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    public void printDebug(double T) {
        System.out.println("[MCSampler]");
        System.out.printf("  samples    : %d%n", nSamples);
        System.out.printf("  ⟨E⟩/site   : %+.6f%n", meanEnergyPerSite());
        System.out.printf("  Cv/site    : %+.4e  (T=%.1f K)%n", heatCapacityPerSite(T), T);
        double[] cfs = meanCFs();
        System.out.println("  ⟨u_t⟩:");
        for (int t = 0; t < tc; t++)
            System.out.printf("    t=%d  orb=%-3d  u=%+.6f%n", t, orbitSizes[t], cfs[t]);
    }
}
