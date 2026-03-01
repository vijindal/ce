package org.ce.mcs;

import org.ce.identification.geometry.Cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * One canonical Monte Carlo step: selects two sites of different occupation,
 * computes ΔE, and accepts or rejects via the Metropolis criterion.
 *
 * <h2>Canonical ensemble</h2>
 * <p>Swapping occupations at two sites conserves the count of every species,
 * so this step samples the <em>canonical</em> ensemble at fixed composition.
 * Works for any number of components: a move picks one site of species A and
 * one site of species B (A ≠ B), chosen uniformly at random.</p>
 *
 * <h2>Acceptance criterion</h2>
 * <pre>
 *   P_accept = min(1, exp(−β · ΔE)),   β = 1/(k_B T)
 * </pre>
 *
 * @author  CE Project
 * @version 1.0
 * @see     FlipStep
 * @see     LocalEnergyCalc
 */
public class ExchangeStep {

    /** (Phase) gas constant used in acceptance exponent (eV/K). */
    // Note: original code used a per-phase `R` value. We pass R in constructors.

    private final EmbeddingData                              emb;
    private final double[]                                   eci;
    private final List<List<org.ce.identification.geometry.Cluster>> orbits;
    private final double                                     beta;
    private final double                                     R;
    private final int                                        numComp;
    private final Random                                     rng;

    // Per-species cached site index lists — maintained incrementally
    private java.util.ArrayList<Integer>[] speciesSites;  // speciesSites[c] = site indices with occupation c
    private boolean cacheInitialized = false;

    private long attempts = 0;
    private long accepted = 0;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    /**
     * Constructs a canonical exchange step actor.
     *
     * @param emb     embedding data for the supercell
     * @param eci     effective cluster interactions
     * @param orbits  orbit list from {@code ClusCoordListResult.getOrbitList()}
     * @param numComp number of chemical components
     * @param T       temperature in Kelvin; must be &gt; 0
     * @param rng     random number generator
     */
    public ExchangeStep(EmbeddingData emb,
                        double[] eci,
                        List<List<Cluster>> orbits,
                        int numComp,
                        double T,
                        double R,
                        Random rng) {
        if (T <= 0) throw new IllegalArgumentException("T must be > 0, got " + T);
        if (R <= 0) throw new IllegalArgumentException("R must be > 0, got " + R);
        this.emb     = emb;
        this.eci     = eci;
        this.orbits  = orbits;
        this.numComp = numComp;
        this.R       = R;
        this.beta    = 1.0 / (R * T);
        this.rng     = rng;
    }

    // -------------------------------------------------------------------------
    // Single step
    // -------------------------------------------------------------------------

    /**
     * Attempts one canonical exchange on {@code config}.
     *
     * <ol>
     *   <li>Rebuild species-site caches if stale.</li>
     *   <li>Pick a random non-empty species {@code c1} and a different random
     *       non-empty species {@code c2}.</li>
     *   <li>Pick a random site {@code i} from {@code c1}'s list and a random
     *       site {@code j} from {@code c2}'s list.</li>
     *   <li>Compute ΔE via {@link LocalEnergyCalc#deltaEExchange}.</li>
     *   <li>Accept with Metropolis probability; if accepted, swap occupations
     *       and mark cache stale.</li>
     * </ol>
     *
     * @param config configuration to update in-place
     * @return ΔE if the move was accepted, 0.0 if rejected (energy unchanged)
     */
    public double attempt(LatticeConfig config) {
        long __p = Profiler.tic("ExchangeStep.attempt");
        attempts++;
        rebuildCacheIfNeeded(config);

        // Pick two distinct occupied species
        int c1 = randomNonEmptySpecies(-1);
        int c2 = randomNonEmptySpecies(c1);
        if (c1 < 0 || c2 < 0) {
            Profiler.toc("ExchangeStep.attempt", __p);
            return 0.0;  // only one species present, no energy change
        }

        java.util.ArrayList<Integer> list1 = speciesSites[c1];
        java.util.ArrayList<Integer> list2 = speciesSites[c2];
        int i = list1.get(rng.nextInt(list1.size()));
        int j = list2.get(rng.nextInt(list2.size()));

        double dE = LocalEnergyCalc.deltaEExchange(i, j, config, emb, eci, orbits);

        if (accept(dE)) {
            updateCacheForFlip(i, j, c1, c2);
            config.setOccupation(i, c2);
            config.setOccupation(j, c1);
            accepted++;
            Profiler.toc("ExchangeStep.attempt", __p);
            return dE;  // Accepted: return the energy change
        }
        Profiler.toc("ExchangeStep.attempt", __p);
        return 0.0;  // Rejected: energy unchanged
    }

    // -------------------------------------------------------------------------
    // Statistics
    // -------------------------------------------------------------------------

    public long   getAttempts()  { return attempts; }
    public long   getAccepted()  { return accepted; }
    public double acceptRate()   { return attempts == 0 ? 0.0 : (double) accepted / attempts; }
    public void   resetCounters(){ attempts = 0; accepted = 0; }
    public void   invalidateCache() { cacheInitialized = false; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean accept(double dE) {
        if (dE <= 0.0) return true;
        return rng.nextDouble() < Math.exp(-beta * dE);
    }

    /** Returns a random species index that has at least one site, excluding {@code exclude}. */
    private int randomNonEmptySpecies(int exclude) {
        // Collect non-empty species (excluding the given one)
        int count = 0;
        for (int c = 0; c < numComp; c++)
            if (c != exclude && speciesSites[c].size() > 0) count++;
        if (count == 0) return -1;
        int pick = rng.nextInt(count);
        int idx  = 0;
        for (int c = 0; c < numComp; c++) {
            if (c != exclude && speciesSites[c].size() > 0) {
                if (idx == pick) return c;
                idx++;
            }
        }
        return -1;
    }

    private void rebuildCacheIfNeeded(LatticeConfig config) {
        long __p = Profiler.tic("ExchangeStep.rebuildCacheIfNeeded");
        if (cacheInitialized) {
            Profiler.toc("ExchangeStep.rebuildCacheIfNeeded", __p);
            return;
        }
        @SuppressWarnings("unchecked")
        java.util.ArrayList<Integer>[] temp = new java.util.ArrayList[numComp];
        for (int c = 0; c < numComp; c++) temp[c] = new java.util.ArrayList<>(64);
        for (int k = 0; k < config.getN(); k++)
            temp[config.getOccupation(k)].add(k);
        speciesSites = temp;
        cacheInitialized = true;
        Profiler.toc("ExchangeStep.rebuildCacheIfNeeded", __p);
    }

    private void updateCacheForFlip(int i, int j, int c1, int c2) {
        long __p = Profiler.tic("ExchangeStep.updateCacheForFlip");
        speciesSites[c1].remove((Integer) i);
        speciesSites[c2].add(i);
        speciesSites[c2].remove((Integer) j);
        speciesSites[c1].add(j);
        Profiler.toc("ExchangeStep.updateCacheForFlip", __p);
    }
}
