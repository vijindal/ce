package org.ce.mcs;

import java.util.Arrays;

/**
 * Immutable result from a completed Monte Carlo simulation.
 *
 * <p>Composition is stored as a full array {@code x[c]} (one entry per
 * component) rather than a single {@code x_B}, supporting any number of
 * chemical components.</p>
 *
 * @author  CE Project
 * @version 1.0
 * @see     MCSRunner
 */
public class MCResult {

    private final double   temperature;
    private final double[] composition;      // x[c] for each component c
    private final double[] avgCFs;
    private final double   energyPerSite;
    private final double   heatCapacityPerSite;
    private final double   acceptRate;
    private final long     nEquilSweeps;
    private final long     nAvgSweeps;
    private final int      supercellSize;
    private final int      nSites;

    // -------------------------------------------------------------------------
    // Constructor (package-private)
    // -------------------------------------------------------------------------

    MCResult(double temperature, double[] composition, double[] avgCFs,
             double energyPerSite, double heatCapacityPerSite,
             double acceptRate, long nEquilSweeps, long nAvgSweeps,
             int supercellSize, int nSites) {
        this.temperature         = temperature;
        this.composition         = composition.clone();
        this.avgCFs              = avgCFs.clone();
        this.energyPerSite       = energyPerSite;
        this.heatCapacityPerSite = heatCapacityPerSite;
        this.acceptRate          = acceptRate;
        this.nEquilSweeps        = nEquilSweeps;
        this.nAvgSweeps          = nAvgSweeps;
        this.supercellSize       = supercellSize;
        this.nSites              = nSites;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public double   getTemperature()         { return temperature; }
    /** Returns composition fractions for all components: {@code x[c] = N_c/N}. */
    public double[] getComposition()         { return composition.clone(); }
    /** Convenience: returns {@code x[1]} (B-fraction) for binary systems. */
    public double   getCompositionB()        { return composition.length > 1 ? composition[1] : 0; }
    public double[] getAvgCFs()              { return avgCFs.clone(); }
    public int      getNumClusterTypes()     { return avgCFs.length; }
    public double   getEnergyPerSite()       { return energyPerSite; }
    public double   getHeatCapacityPerSite() { return heatCapacityPerSite; }
    public double   getAcceptRate()          { return acceptRate; }
    public long     getNEquilSweeps()        { return nEquilSweeps; }
    public long     getNAvgSweeps()          { return nAvgSweeps; }
    public int      getSupercellSize()       { return supercellSize; }
    public int      getNSites()              { return nSites; }

    // -------------------------------------------------------------------------
    // Debug
    // -------------------------------------------------------------------------

    public void printDebug() {
        System.out.println("[MCResult]");
        System.out.printf( "  T              : %.1f K%n",     temperature);
        System.out.printf( "  N              : %d  (L=%d)%n", nSites, supercellSize);
        System.out.printf( "  equil sweeps   : %d%n",         nEquilSweeps);
        System.out.printf( "  avg   sweeps   : %d%n",         nAvgSweeps);
        System.out.printf( "  accept rate    : %.4f%n",       acceptRate);
        System.out.printf( "  ⟨E⟩/site       : %+.6f%n",     energyPerSite);
        System.out.printf( "  Cv/site        : %+.4e%n",      heatCapacityPerSite);
        String[] lbl = {"A","B","C","D","E"};
        System.out.println("  composition:");
        for (int c = 0; c < composition.length; c++)
            System.out.printf("    x[%d] (%s)  : %.5f%n",
                    c, c < lbl.length ? lbl[c] : "C"+c, composition[c]);
        System.out.println("  ⟨u_t⟩:");
        for (int t = 0; t < avgCFs.length; t++)
            System.out.printf("    t=%d  u=%+.6f%n", t, avgCFs[t]);
    }

    @Override
    public String toString() {
        return "MCResult{T=" + temperature + ", x=" + Arrays.toString(composition)
             + ", ⟨E⟩/site=" + String.format("%.4f", energyPerSite)
             + ", acceptRate=" + String.format("%.3f", acceptRate) + "}";
    }
}
