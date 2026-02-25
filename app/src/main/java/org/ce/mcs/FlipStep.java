package org.ce.mcs;

import org.ce.identification.engine.Cluster;

import java.util.List;
import java.util.Random;

/**
 * One grand-canonical Monte Carlo step: changes a randomly chosen site's
 * occupation to any other species, with a Metropolis acceptance criterion
 * that includes a chemical-potential contribution.
 *
 * <h2>Grand-canonical ensemble</h2>
 * <p>A single-site occupation change alters the composition, so this step
 * samples the <em>grand-canonical</em> ensemble at fixed chemical potentials.
 * Works for any number of components: the new occupation is drawn uniformly
 * from the {@code numComp − 1} species different from the current one.</p>
 *
 * <h2>Chemical-potential correction</h2>
 * <p>The effective energy change includes a μ correction:</p>
 * <pre>
 *   ΔE_total = ΔE_cluster + Σ_c  Δμ[c] · Δx[c]
 * </pre>
 * <p>where {@code Δμ[c] = μ_c − μ_0} (chemical potential of species c
 * relative to species 0), and {@code Δx[c] = ±1/N} depending on whether
 * species c is gained or lost.  Pass an all-zero array (or use the
 * two-argument constructor) for unbiased sampling.</p>
 *
 * @author  CE Project
 * @version 1.0
 * @see     ExchangeStep
 * @see     LocalEnergyCalc
 */
public class FlipStep {

    /** (Phase) gas constant used in acceptance exponent (eV/K). */
    // Passed into constructors as `R` to match original code's `R_local`.

    private final EmbeddingData                              emb;
    private final double[]                                   eci;
    private final List<List<Cluster>>                        orbits;
    private final double                                     beta;
    private final double                                     R;
    private final int                                        numComp;
    /**
     * Chemical-potential differences relative to species 0:
     * {@code deltaMu[c] = μ_c − μ_0}.
     * {@code deltaMu[0]} is ignored (always 0 by convention).
     */
    private final double[]                                   deltaMu;
    private final Random                                     rng;

    private long attempts = 0;
    private long accepted = 0;

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Constructs a grand-canonical flip step actor with chemical-potential bias.
     *
     * @param emb     embedding data for the supercell
     * @param eci     effective cluster interactions
     * @param orbits  orbit list from {@code ClusCoordListResult.getOrbitList()}
     * @param numComp number of chemical components
     * @param T       temperature in Kelvin; must be &gt; 0
     * @param deltaMu chemical-potential differences {@code μ_c − μ_0} in eV;
     *                length must equal {@code numComp}; {@code deltaMu[0]} ignored
     * @param rng     random number generator
     */
    public FlipStep(EmbeddingData emb,
                    double[] eci,
                    List<List<Cluster>> orbits,
                    int numComp,
                    double T,
                    double[] deltaMu,
                    double R,
                    Random rng) {
        if (T <= 0) throw new IllegalArgumentException("T must be > 0, got " + T);
        if (R <= 0) throw new IllegalArgumentException("R must be > 0, got " + R);
        if (deltaMu.length != numComp)
            throw new IllegalArgumentException("deltaMu.length must equal numComp");
        this.emb     = emb;
        this.eci     = eci;
        this.orbits  = orbits;
        this.numComp = numComp;
        this.R       = R;
        this.beta    = 1.0 / (R * T);
        this.deltaMu = deltaMu.clone();
        this.rng     = rng;
    }

    /**
     * Constructs a grand-canonical flip step actor with zero chemical-potential
     * bias (all {@code Δμ = 0}).
     *
     * @param emb     embedding data
     * @param eci     effective cluster interactions
     * @param orbits  orbit list
     * @param numComp number of chemical components
     * @param T       temperature in Kelvin; must be &gt; 0
     * @param rng     random number generator
     */
    public FlipStep(EmbeddingData emb,
                    double[] eci,
                    List<List<Cluster>> orbits,
                    int numComp,
                    double T,
                    double R,
                    Random rng) {
        this(emb, eci, orbits, numComp, T, new double[numComp], R, rng);
    }

    // -------------------------------------------------------------------------
    // Single step
    // -------------------------------------------------------------------------

    /**
     * Attempts one grand-canonical occupation change on {@code config}.
     *
     * <ol>
     *   <li>Pick a random site {@code i}.</li>
     *   <li>Pick a random new occupation {@code newOcc ≠ oldOcc}.</li>
     *   <li>Compute cluster ΔE via
     *       {@link LocalEnergyCalc#deltaESingleSite}.</li>
     *   <li>Add chemical-potential correction:
     *       {@code ΔE_μ = (Δμ[newOcc] − Δμ[oldOcc]) / N}.</li>
     *   <li>Accept with Metropolis probability and update in-place.</li>
     * </ol>
     *
     * @param config configuration to update in-place
     * @return {@code true} if the move was accepted
     */
    public boolean attempt(LatticeConfig config) {
        attempts++;

        int i      = rng.nextInt(config.getN());
        int oldOcc = config.getOccupation(i);

        // Pick a new occupation != oldOcc uniformly from numComp-1 choices
        int newOcc = rng.nextInt(numComp - 1);
        if (newOcc >= oldOcc) newOcc++;   // skip oldOcc in [0, numComp)

        double dECluster = LocalEnergyCalc.deltaESingleSite(
                i, newOcc, config, emb, eci, orbits);

        // Chemical-potential correction: Δx[newOcc] = +1/N, Δx[oldOcc] = -1/N
        double dEMu = (deltaMu[newOcc] - deltaMu[oldOcc]) / config.getN();

        if (accept(dECluster + dEMu)) {
            config.setOccupation(i, newOcc);
            accepted++;
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Statistics
    // -------------------------------------------------------------------------

    public long   getAttempts()  { return attempts; }
    public long   getAccepted()  { return accepted; }
    public double acceptRate()   { return attempts == 0 ? 0.0 : (double) accepted / attempts; }
    public void   resetCounters(){ attempts = 0; accepted = 0; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean accept(double dE) {
        if (dE <= 0.0) return true;
        return rng.nextDouble() < Math.exp(-beta * dE);
    }
}
